package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PhoneCallback
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.domain.model.AiProviderMode
import com.example.domain.model.ConnectivityPolicy
import com.example.domain.model.PermissionSummary
import com.example.domain.model.ResponderSettings
import com.example.domain.model.StorageStats
import com.example.ui.viewmodel.ConnectionTestState
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    savedSettings: ResponderSettings,
    storageStats: StorageStats,
    permissions: PermissionSummary,
    connectionTestState: ConnectionTestState,
    onSaveSettings: (ResponderSettings) -> Unit,
    onTestConnection: (ResponderSettings) -> Unit,
    onResetTestState: () -> Unit,
    onTestTts: (ResponderSettings) -> Unit,
    onCleanupNow: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    modifier: Modifier = Modifier
) {
    var aiMode by remember(savedSettings) { mutableStateOf(savedSettings.aiProviderMode) }
    var connectivityPolicy by remember(savedSettings) { mutableStateOf(savedSettings.connectivityPolicy) }
    var customApiKey by remember(savedSettings) { mutableStateOf(savedSettings.customApiKey) }
    var showApiKey by remember { mutableStateOf(false) }
    var customModelName by remember(savedSettings) { mutableStateOf(savedSettings.customModelName) }

    var greetingMessage by remember(savedSettings) { mutableStateOf(savedSettings.greetingMessage) }
    var systemPersona by remember(savedSettings) { mutableStateOf(savedSettings.systemPersona) }
    var fallbackMessage by remember(savedSettings) { mutableStateOf(savedSettings.fallbackMessage) }

    var autoAnswerEnabled by remember(savedSettings) { mutableStateOf(savedSettings.autoAnswerEnabled) }
    var autoAnswerDelay by remember(savedSettings) { mutableIntStateOf(savedSettings.autoAnswerDelaySeconds) }
    var autoHangupEnabled by remember(savedSettings) { mutableStateOf(savedSettings.autoHangupEnabled) }
    var maxTurnsPerCall by remember(savedSettings) { mutableIntStateOf(savedSettings.maxTurnsPerCall) }
    var useSpeakerphone by remember(savedSettings) { mutableStateOf(savedSettings.useSpeakerphoneForReply) }
    var speechRate by remember(savedSettings) { mutableFloatStateOf(savedSettings.ttsSpeechRate) }
    var ttsPitch by remember(savedSettings) { mutableFloatStateOf(savedSettings.ttsPitch) }
    var recognitionLocale by remember(savedSettings) { mutableStateOf(savedSettings.recognitionLocale) }

    var enableSelfLearning by remember(savedSettings) { mutableStateOf(savedSettings.enableSelfLearning) }
    var enableDiplomaticDeflection by remember(savedSettings) { mutableStateOf(savedSettings.enableDiplomaticDeflection) }
    var stealthZeroDrainMode by remember(savedSettings) { mutableStateOf(savedSettings.stealthZeroDrainMode) }
    var floatingOverlayEnabled by remember(savedSettings) { mutableStateOf(savedSettings.floatingOverlayEnabled) }
    var autoTrainOffline by remember(savedSettings) { mutableStateOf(savedSettings.autoTrainOfflineFromOnline) }
    var allowCallerReminders by remember(savedSettings) { mutableStateOf(savedSettings.allowCallerReminders) }
    var retentionDays by remember(savedSettings) { mutableIntStateOf(savedSettings.retentionDays) }
    var maxStoredCalls by remember(savedSettings) { mutableIntStateOf(savedSettings.maxStoredCalls) }

    LaunchedEffect(aiMode, connectivityPolicy, customApiKey, customModelName) {
        onResetTestState()
    }

    fun buildDraft(): ResponderSettings = savedSettings.copy(
        aiProviderMode = aiMode,
        connectivityPolicy = connectivityPolicy,
        customApiKey = customApiKey.trim(),
        customModelName = customModelName.trim(),
        greetingMessage = greetingMessage.trim(),
        systemPersona = systemPersona.trim(),
        fallbackMessage = fallbackMessage.trim(),
        autoAnswerEnabled = autoAnswerEnabled,
        autoAnswerDelaySeconds = autoAnswerDelay,
        autoHangupEnabled = autoHangupEnabled,
        maxTurnsPerCall = maxTurnsPerCall,
        useSpeakerphoneForReply = useSpeakerphone,
        ttsSpeechRate = speechRate,
        ttsPitch = ttsPitch,
        recognitionLocale = recognitionLocale,
        enableSelfLearning = enableSelfLearning,
        enableDiplomaticDeflection = enableDiplomaticDeflection,
        stealthZeroDrainMode = stealthZeroDrainMode,
        floatingOverlayEnabled = floatingOverlayEnabled,
        autoTrainOfflineFromOnline = autoTrainOffline,
        allowCallerReminders = allowCallerReminders,
        retentionDays = retentionDays,
        maxStoredCalls = maxStoredCalls
    )

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "إعدادات المجيب الذكي",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Section 1: Connectivity & Offline Policy
            item {
                SettingsSectionCard(
                    icon = Icons.Default.CloudOff,
                    title = "العمل بدون إنترنت (أوفلاين) والاتصال الهجين"
                ) {
                    Text(
                        text = "سياسة التشغيل الذكي:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ConnectivityPolicy.entries.forEach { policy ->
                            FilterChip(
                                selected = connectivityPolicy == policy,
                                onClick = { connectivityPolicy = policy },
                                label = { Text(policy.arabicLabel) }
                            )
                        }
                    }

                    SettingsSwitchRow(
                        title = "تدريب الأوفلاين تلقائياً من الأونلاين",
                        subtitle = "يحفظ الردود الناجحة من Gemini محلياً ليعمل بها لاحقاً بدون إنترنت",
                        checked = autoTrainOffline,
                        onCheckedChange = { autoTrainOffline = it }
                    )

                    SettingsSwitchRow(
                        title = "السماح بضبط المنبهات والتذكيرات",
                        subtitle = "تنفيذ أوامر المنبه والمؤقت والتذكير أثناء المكالمات تلقائياً",
                        checked = allowCallerReminders,
                        onCheckedChange = { allowCallerReminders = it }
                    )
                }
            }

            // Section 2: Stealth Background Power & Floating Overlay
            item {
                SettingsSectionCard(
                    icon = Icons.Default.BatteryChargingFull,
                    title = "التشغيل الخفي والطاقة والظهور فوق التطبيقات"
                ) {
                    SettingsSwitchRow(
                        title = "وضع الخفاء واستهلاك الطاقة الصفري",
                        subtitle = "لا يستهلك المعالج أو الميكروفون إلا لحظة ورود مكالمة فعلية",
                        checked = stealthZeroDrainMode,
                        onCheckedChange = { stealthZeroDrainMode = it }
                    )

                    SettingsSwitchRow(
                        title = "نافذة تحكم عائمة فوق التطبيقات أثناء المكالمات",
                        subtitle = "إظهار شريط مصغر للتحكم وإنهاء المكالمة فوق أي تطبيق مفتوح",
                        checked = floatingOverlayEnabled,
                        onCheckedChange = { floatingOverlayEnabled = it }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenOverlaySettings,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (permissions.canDrawOverlays) "فوق التطبيقات: مفعّل" else "إذن فوق التطبيقات",
                                maxLines = 1
                            )
                        }

                        OutlinedButton(
                            onClick = onRequestBatteryExemption,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Icon(Icons.Default.BatteryChargingFull, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (permissions.ignoringBatteryOptimizations) "البطارية: مستثنى" else "استثناء البطارية",
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Section 3: AI Engine & Google AI Studio Key
            item {
                SettingsSectionCard(
                    icon = Icons.Default.AutoAwesome,
                    title = "محرك الذكاء الاصطناعي (Gemini)"
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = aiMode == AiProviderMode.FIREBASE_AI_LOGIC,
                            onClick = { aiMode = AiProviderMode.FIREBASE_AI_LOGIC },
                            label = { Text("الذكاء المدمج (تلقائي)") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = aiMode == AiProviderMode.GOOGLE_AI_STUDIO_KEY,
                            onClick = { aiMode = AiProviderMode.GOOGLE_AI_STUDIO_KEY },
                            label = { Text("مفتاح AI Studio") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    AnimatedVisibility(visible = aiMode == AiProviderMode.GOOGLE_AI_STUDIO_KEY) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = customApiKey,
                                onValueChange = { customApiKey = it },
                                label = { Text("مفتاح Google AI Studio API Key") },
                                placeholder = { Text("AIzaSy...") },
                                leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                                trailingIcon = {
                                    IconButton(onClick = { showApiKey = !showApiKey }) {
                                        Icon(
                                            imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "إظهار أو إخفاء المفتاح"
                                        )
                                    }
                                },
                                visualTransformation = if (showApiKey) {
                                    VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("api_key_input")
                            )

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf("gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.0-flash").forEach { model ->
                                    FilterChip(
                                        selected = customModelName == model,
                                        onClick = { customModelName = model },
                                        label = { Text(model, maxLines = 1) }
                                    )
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { onTestConnection(buildDraft()) },
                        enabled = connectionTestState !is ConnectionTestState.Testing,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("test_connection_button")
                    ) {
                        if (connectionTestState is ConnectionTestState.Testing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("جاري اختبار الاتصال...")
                        } else {
                            Text("اختبار محرك الذكاء (أونلاين / أوفلاين)")
                        }
                    }

                    when (connectionTestState) {
                        is ConnectionTestState.Success -> {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column {
                                        Text(
                                            text = "تم الفحص بنجاح!",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = connectionTestState.replySample,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                        is ConnectionTestState.Error -> {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = connectionTestState.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                        else -> Unit
                    }
                }
            }

            // Section 4: Greeting, Persona, Deflection & Self-Learning
            item {
                SettingsSectionCard(
                    icon = Icons.Default.RecordVoiceOver,
                    title = "الترحيب وشخصية الفريق والهندلة"
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = false,
                            onClick = {
                                greetingMessage = "أهلاً وسهلاً، أنا المساعد الشخصي الذكي. كيف أقدر أساعدك اليوم؟"
                                systemPersona = "أنت سكرتير شخصي مهذب وذكي يتحدث العربية بطلاقة ودفء. ميّز بين الأصدقاء والعملاء والمزعجين وتصرف بلباقة."
                            },
                            label = { Text("سكرتير شخصي") }
                        )
                        FilterChip(
                            selected = false,
                            onClick = {
                                greetingMessage = "أهلاً بك في مكتب خدمة العملاء الذكي. تفضل بطرح استفسارك."
                                systemPersona = "أنت فريق استقبال أعمال محترف ولطيف. اجمع طلب العميل ورقم التواصل بدقة، وهندل مكالمات التسويق بأدب."
                            },
                            label = { Text("استقبال أعمال") }
                        )
                        FilterChip(
                            selected = false,
                            onClick = {
                                greetingMessage = "أهلاً بك، صاحب الهاتف يقود السيارة حالياً. أخبرني بأمرك وسأوصله له فوراً."
                                systemPersona = "أنت مساعد صوتي سريع وواضح تساعد المتصلين أثناء قيادة صاحب الهاتف. اطلب الرسالة المهمة بإيجاز."
                            },
                            label = { Text("أثناء القيادة") }
                        )
                    }

                    OutlinedTextField(
                        value = greetingMessage,
                        onValueChange = { greetingMessage = it },
                        label = { Text("رسالة الترحيب التلقائية عند فتح الخط") },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("greeting_input")
                    )

                    OutlinedTextField(
                        value = systemPersona,
                        onValueChange = { systemPersona = it },
                        label = { Text("توجيهات وشخصية الفريق الذكي") },
                        minLines = 3,
                        maxLines = 5,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("persona_input")
                    )

                    OutlinedTextField(
                        value = fallbackMessage,
                        onValueChange = { fallbackMessage = it },
                        label = { Text("الرد الاحتياطي الطارئ") },
                        minLines = 2,
                        maxLines = 3,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("fallback_input")
                    )

                    SettingsSwitchRow(
                        title = "المراوغة والهندلة الدبلوماسية",
                        subtitle = "صرف متصلي التسويق والإزعاج بلباقة وإنهاء الاتصال بأدب",
                        checked = enableDiplomaticDeflection,
                        onCheckedChange = { enableDiplomaticDeflection = it }
                    )

                    SettingsSwitchRow(
                        title = "التطوير الذاتي وحفظ الذاكرة",
                        subtitle = "يتعلم الفريق من كل مكالمة ويُحدّث تصنيف المتصلين تلقائياً",
                        checked = enableSelfLearning,
                        onCheckedChange = { enableSelfLearning = it }
                    )
                }
            }

            // Section 5: Call Handling (Answer & Hangup) & TTS Audio
            item {
                SettingsSectionCard(
                    icon = Icons.Default.PhoneCallback,
                    title = "المكالمات (الرد والإغلاق) والنطق"
                ) {
                    SettingsSwitchRow(
                        title = "الرد التلقائي على المكالمات الواردة",
                        subtitle = "فتح المكالمة تلقائياً بعد انتهاء مهلة الرنين",
                        checked = autoAnswerEnabled,
                        onCheckedChange = { autoAnswerEnabled = it }
                    )

                    AnimatedVisibility(visible = autoAnswerEnabled) {
                        Column {
                            Text(
                                text = "مهلة الرنين قبل الرد التلقائي: $autoAnswerDelay ثوانٍ",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Slider(
                                value = autoAnswerDelay.toFloat(),
                                onValueChange = { autoAnswerDelay = it.roundToInt().coerceIn(1, 10) },
                                valueRange = 1f..10f,
                                steps = 8
                            )
                        }
                    }

                    SettingsSwitchRow(
                        title = "الإغلاق التلقائي عند انتهاء الحوار",
                        subtitle = "يقفل الذكاء الاصطناعي المكالمة تلقائياً بعد الوداع أو إتمام الهدف",
                        checked = autoHangupEnabled,
                        onCheckedChange = { autoHangupEnabled = it }
                    )

                    Text(
                        text = "الحد الأقصى لتبادل الأدوار في المكالمة الواحدة: $maxTurnsPerCall أدوار",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = maxTurnsPerCall.toFloat(),
                        onValueChange = { maxTurnsPerCall = it.roundToInt().coerceIn(3, 20) },
                        valueRange = 3f..20f
                    )

                    SettingsSwitchRow(
                        title = "تفعيل مكبر الصوت أثناء الرد",
                        subtitle = "يساعد على سماع المتصل للرد الصوتي بوضوح",
                        checked = useSpeakerphone,
                        onCheckedChange = { useSpeakerphone = it }
                    )

                    Text(
                        text = "لهجة التعرف الصوتي على المتصل:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "ar-SA" to "السعودية",
                            "ar-EG" to "مصر",
                            "ar-AE" to "الإمارات",
                            "ar-JO" to "الأردن"
                        ).forEach { (code, title) ->
                            FilterChip(
                                selected = recognitionLocale == code,
                                onClick = { recognitionLocale = code },
                                label = { Text(title) }
                            )
                        }
                    }

                    Text(
                        text = "سرعة النطق: ${String.format(java.util.Locale.US, "%.1fx", speechRate)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = speechRate,
                        onValueChange = { speechRate = it },
                        valueRange = 0.7f..1.4f
                    )

                    Text(
                        text = "نبرة الصوت (Pitch): ${String.format(java.util.Locale.US, "%.1f", ttsPitch)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = ttsPitch,
                        onValueChange = { ttsPitch = it },
                        valueRange = 0.8f..1.3f
                    )

                    OutlinedButton(
                        onClick = { onTestTts(buildDraft()) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("test_tts_button")
                    ) {
                        Icon(Icons.Default.RecordVoiceOver, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("سماع تجربة صوت الترحيب")
                    }
                }
            }

            // Section 6: Storage Retention & Auto-Cleanup
            item {
                SettingsSectionCard(
                    icon = Icons.Default.Storage,
                    title = "إدارة المساحة وحذف السجلات تلقائياً"
                ) {
                    val kbUsed = (storageStats.estimatedBytes / 1024L).coerceAtLeast(0L)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "السجلات المحفوظة: ${storageStats.callLogsCount} • الحجم التقديري: $kbUsed كيلوبايت",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "المساحة الحرة المتوفرة بالجهاز: ${storageStats.availableDeviceMb} ميجابايت",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = "حذف سجلات المكالمات الأقدم من:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(1 to "يوم واحد", 3 to "3 أيام", 7 to "7 أيام", 14 to "14 يوماً", 30 to "30 يوماً")
                            .forEach { (days, label) ->
                                FilterChip(
                                    selected = retentionDays == days,
                                    onClick = { retentionDays = days },
                                    label = { Text(label) }
                                )
                            }
                    }

                    Text(
                        text = "الحد الأقصى للمكالمات المحفوظة (لتوفير المساحة):",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(50, 100, 200).forEach { cap ->
                            FilterChip(
                                selected = maxStoredCalls == cap,
                                onClick = { maxStoredCalls = cap },
                                label = { Text("$cap مكالمة") }
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onCleanupNow,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("cleanup_storage_button")
                    ) {
                        Icon(Icons.Default.CleaningServices, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("تنظيف السجلات القديمة الآن")
                    }
                }
            }

            // Save Settings Button
            item {
                Button(
                    onClick = { onSaveSettings(buildDraft()) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("save_settings_button")
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "حفظ الإعدادات",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsSectionCard(
    icon: ImageVector,
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            content()
        }
    }
}
