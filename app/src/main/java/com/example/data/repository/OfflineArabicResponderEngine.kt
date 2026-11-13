package com.example.data.repository

import com.example.domain.model.CallerCategory
import com.example.domain.model.HandlingStrategy
import com.example.domain.model.PhoneActionPayload
import com.example.domain.model.PhoneActionType
import com.example.domain.model.ResponderSettings
import com.example.domain.model.StructuredAiReply
import com.example.domain.model.TrainedQaRule

/**
 * Local, zero-internet Arabic NLP and trainable rule engine.
 * Combines user-trained/auto-learned Q&A rules with Arabic morphological normalization,
 * caller classification, diplomatic deflection, and offline alarm/reminder extraction.
 */
class OfflineArabicResponderEngine(
    private val callLogRepository: CallLogRepository
) {

    suspend fun respondOffline(
        callerInput: String,
        settings: ResponderSettings,
        callerContext: CallerContextInfo
    ): StructuredAiReply {
        val normalized = normalizeArabic(callerInput)

        // 1. Check for phone action requests (alarms / timers / reminders)
        val parsedAction = extractOfflinePhoneAction(normalized, callerInput)
        if (parsedAction != null && settings.allowCallerReminders) {
            val actionReply = when (parsedAction.type) {
                PhoneActionType.SET_ALARM ->
                    "تم ضبط المنبه في الهاتف بنجاح حسب طلبك: ${parsedAction.title}."
                PhoneActionType.SET_TIMER ->
                    "تم تشغيل المؤقت لمدة ${parsedAction.delayMinutes ?: 5} دقائق كما طلبت."
                PhoneActionType.CREATE_REMINDER ->
                    "أبشر، قمت بتسجيل تذكير بعنوان: ${parsedAction.title}."
                PhoneActionType.NONE -> settings.greetingMessage
            }
            return StructuredAiReply(
                replyText = actionReply,
                detectedCategory = callerContext.savedProfile?.category ?: CallerCategory.FRIEND,
                strategy = HandlingStrategy.WARM_FRIENDLY,
                intentSummary = "طلب ${parsedAction.type.arabicLabel}: ${parsedAction.title}",
                shouldEndCall = false,
                selfLearningTip = "تم تنفيذ إجراء محلي (${parsedAction.type.arabicLabel}) بدون إنترنت.",
                isOfflineResponse = true,
                phoneAction = parsedAction
            )
        }

        // 2. Match against locally trained Q&A rules (from manual training + prior online calls)
        val rules = callLogRepository.getAllTrainedRulesSnapshot()
        val matchedRule = findBestRuleMatch(normalized, rules)
        if (matchedRule != null) {
            callLogRepository.incrementTrainedRuleHit(matchedRule.id)
            return StructuredAiReply(
                replyText = matchedRule.replyText,
                detectedCategory = matchedRule.category,
                strategy = matchedRule.strategy,
                intentSummary = "مطابقة قاعدة محلية مدربة",
                shouldEndCall = matchedRule.shouldEndCall,
                selfLearningTip = "",
                isOfflineResponse = true
            )
        }

        // 3. Built-in offline Arabic conversational brain
        return evaluateBuiltInIntents(normalized, settings, callerContext)
    }

    private fun findBestRuleMatch(
        normalizedInput: String,
        rules: List<TrainedQaRule>
    ): TrainedQaRule? {
        val inputTokens = normalizedInput.split(" ").filter { it.length >= 2 }.toSet()
        if (inputTokens.isEmpty()) return null

        var bestRule: TrainedQaRule? = null
        var bestScore = 0.0

        for (rule in rules) {
            val triggers = rule.triggerKeywords
                .split(",", "،", " ", "\n")
                .map { normalizeArabic(it) }
                .filter { it.length >= 2 }
            if (triggers.isEmpty()) continue

            val matches = triggers.count { kw ->
                normalizedInput.contains(kw) || inputTokens.contains(kw)
            }
            if (matches == 0) continue

            val score = matches.toDouble() / triggers.size.coerceAtLeast(1)
            if (score >= 0.4 && score > bestScore) {
                bestScore = score
                bestRule = rule
            }
        }
        return bestRule
    }

    private fun evaluateBuiltInIntents(
        normalized: String,
        settings: ResponderSettings,
        callerContext: CallerContextInfo
    ): StructuredAiReply {
        val namePrefix = if (callerContext.resolvedName.isNotBlank()) {
            "يا ${callerContext.resolvedName}، "
        } else {
            ""
        }

        // Goodbye / Call completion
        val goodbyeKeywords = listOf("مع السلامه", "شكرا لك", "يعطيك العافيه", "في امان الله", "باي", "تصبح على خير", "سلام عليكم")
        if (goodbyeKeywords.any { normalized.contains(it) } && normalized.length < 75) {
            return StructuredAiReply(
                replyText = "الله يعافيك ${namePrefix}تحت أمرك في أي وقت. في أمان الله ومع السلامة.",
                detectedCategory = callerContext.savedProfile?.category ?: CallerCategory.INQUIRER,
                strategy = HandlingStrategy.WARM_FRIENDLY,
                intentSummary = "توديع وإنهاء المكالمة",
                shouldEndCall = true,
                isOfflineResponse = true
            )
        }

        // Spam / Sales / Marketing deflection
        val spamKeywords = listOf("عرض", "بطاقه ائتمان", "تمويل", "قرض", "تخفيض", "تامين", "مندوب", "شركه", "استثمار", "اشتراك")
        if (settings.enableDiplomaticDeflection &&
            (callerContext.savedProfile?.isDeflectMode == true ||
                callerContext.savedProfile?.category == CallerCategory.SPAM_SALES ||
                spamKeywords.count { normalized.contains(it) } >= 1)
        ) {
            return StructuredAiReply(
                replyText = "شكراً لتواصلك، لكن صاحب الهاتف غير مهتم بالعروض التسويقية في الوقت الحالي. أتمنى لك يوماً سعيداً، مع السلامة.",
                detectedCategory = CallerCategory.SPAM_SALES,
                strategy = HandlingStrategy.DIPLOMATIC_DEFLECTION,
                intentSummary = "مكالمة تسويقية تم التعامل معها بمراوغة لبقة",
                shouldEndCall = true,
                selfLearningTip = "تطبيق الهَندلة الدبلوماسية أوفلاين لمكالمات العروض والتسويق.",
                isOfflineResponse = true
            )
        }

        // Urgent / Emergency
        val urgentKeywords = listOf("ضروري", "مستعجل", "طارئ", "طوارئ", "مهم جدا", "بسرعه", "حادث")
        if (urgentKeywords.any { normalized.contains(it) }) {
            return StructuredAiReply(
                replyText = "فهمت أن الأمر عاجل ${namePrefix}سأرفع تنبيهاً فورياً على شاشة الهاتف ليتصل بك في أقرب لحظة، هل هناك تفصيل قصير أضيفه؟",
                detectedCategory = callerContext.savedProfile?.category ?: CallerCategory.VIP,
                strategy = HandlingStrategy.URGENT_ESCALATION,
                intentSummary = "أمر عاجل يحتاج متابعة فورية",
                shouldEndCall = false,
                isOfflineResponse = true
            )
        }

        // Friend / Family
        val friendKeywords = listOf("صديقي", "حبيبي", "اخوي", "طمني", "فاضي", "نلتقي", "وينك", "كيف حالك", "ازيك", "عامل ايه")
        if (callerContext.savedProfile?.category == CallerCategory.FRIEND ||
            callerContext.isInContacts ||
            friendKeywords.any { normalized.contains(it) }
        ) {
            return StructuredAiReply(
                replyText = "أهلاً بك ${namePrefix}الحمد لله بخير! هو مشغول قليلاً الآن، أترك له كلمتك وسيتصل بك فور فراغه.",
                detectedCategory = CallerCategory.FRIEND,
                strategy = HandlingStrategy.WARM_FRIENDLY,
                intentSummary = "اتصال شخصي من صديق أو قريب",
                shouldEndCall = false,
                isOfflineResponse = true
            )
        }

        // Client / Business inquiry
        val clientKeywords = listOf("سعر", "اسعار", "تكلفه", "خدمه", "موعد", "حجز", "طلب", "شغل", "عمل", "فاتوره", "تسليم", "عميل")
        if (callerContext.savedProfile?.category == CallerCategory.CLIENT ||
            clientKeywords.any { normalized.contains(it) }
        ) {
            return StructuredAiReply(
                replyText = "أهلاً بحضرتك، تم تسجيل استفسارك بخصوص الخدمة وسيتواصل معك المختص أو صاحب الرقم قريباً بالرد الكامل. هل تود إضافة أي تفاصيل أخرى؟",
                detectedCategory = CallerCategory.CLIENT,
                strategy = HandlingStrategy.PROFESSIONAL_CLIENT,
                intentSummary = "استفسار عميل حول خدمة أو موعد",
                shouldEndCall = false,
                isOfflineResponse = true
            )
        }

        // General graceful fallback
        return StructuredAiReply(
            replyText = "تم تسجيل رسالتك بنجاح ${namePrefix}وسأبلغ صاحب الهاتف فور انتهائه ليتواصل معك. هل هناك شيء آخر؟",
            detectedCategory = callerContext.savedProfile?.category ?: CallerCategory.INQUIRER,
            strategy = HandlingStrategy.PROFESSIONAL_CLIENT,
            intentSummary = "رسالة مسجلة بالوضع المحلي بدون إنترنت",
            shouldEndCall = false,
            isOfflineResponse = true
        )
    }

    private fun extractOfflinePhoneAction(
        normalized: String,
        originalText: String
    ): PhoneActionPayload? {
        val numberRegex = Regex("(\\d+)")
        val firstNumber = numberRegex.find(normalized)?.groupValues?.getOrNull(1)?.toIntOrNull()

        return when {
            normalized.contains("منبه") || normalized.contains("صحي") || normalized.contains("ايقاظ") -> {
                val hour = (firstNumber ?: 8).coerceIn(0, 23)
                val adjustedHour = if (normalized.contains("مساء") && hour < 12) hour + 12 else hour
                PhoneActionPayload(
                    type = PhoneActionType.SET_ALARM,
                    title = "منبه من المجيب الذكي",
                    hour = adjustedHour,
                    minute = 0
                )
            }
            normalized.contains("مؤقت") || normalized.contains("تايمر") -> {
                val mins = (firstNumber ?: 5).coerceIn(1, 180)
                PhoneActionPayload(
                    type = PhoneActionType.SET_TIMER,
                    title = "مؤقت من المجيب الذكي",
                    delayMinutes = mins
                )
            }
            normalized.contains("ذكرني") || normalized.contains("تذكير") || normalized.contains("موعد") && normalized.contains("دقيق") -> {
                val mins = (firstNumber ?: 15).coerceIn(1, 1440)
                PhoneActionPayload(
                    type = PhoneActionType.CREATE_REMINDER,
                    title = originalText.take(60),
                    delayMinutes = mins
                )
            }
            else -> null
        }
    }

    fun extractKeywordsForTraining(callerInput: String): String {
        val stopWords = setOf(
            "من", "في", "على", "الى", "عن", "هل", "ما", "ماذا", "كيف", "متى",
            "انا", "انت", "نحن", "هذا", "هذه", "ان", "لو", "او", "و", "ثم", "يا"
        )
        return normalizeArabic(callerInput)
            .split(" ")
            .map { it.trim() }
            .filter { it.length >= 3 && !stopWords.contains(it) }
            .distinct()
            .take(5)
            .joinToString(", ")
    }

    fun normalizeArabic(input: String): String {
        return input
            .replace(Regex("[\\u064B-\\u065F\\u0670]"), "") // Remove Arabic diacritics
            .replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
            .replace('ة', 'ه')
            .replace('ى', 'ي')
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase()
    }
}
