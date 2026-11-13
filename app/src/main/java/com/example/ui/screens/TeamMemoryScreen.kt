package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AlarmAdd
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.CallerCategory
import com.example.domain.model.CallerProfile
import com.example.domain.model.HandlingStrategy
import com.example.domain.model.LearningInsight
import com.example.domain.model.PhoneActionType
import com.example.domain.model.ScheduledReminder
import com.example.domain.model.TrainedQaRule
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TeamMemoryScreen(
    profiles: List<CallerProfile>,
    insights: List<LearningInsight>,
    trainedRules: List<TrainedQaRule>,
    scheduledReminders: List<ScheduledReminder>,
    onSaveProfile: (String, String, CallerCategory, String, Boolean) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onAddManualInsight: (String) -> Unit,
    onDeleteInsight: (Long) -> Unit,
    onAddTrainedRule: (String, String, CallerCategory, HandlingStrategy, Boolean) -> Unit,
    onDeleteTrainedRule: (Long) -> Unit,
    onCreatePhoneAction: (PhoneActionType, String, Int?, Int?, Int?) -> Unit,
    onDeleteReminder: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showProfileDialog by remember { mutableStateOf(false) }
    var showTrainDialog by remember { mutableStateOf(false) }
    var showReminderDialog by remember { mutableStateOf(false) }
    var newInsightText by remember { mutableStateOf("") }

    if (showProfileDialog) {
        AddCallerProfileDialog(
            onDismiss = { showProfileDialog = false },
            onConfirm = { phone, name, category, notes, deflect ->
                onSaveProfile(phone, name, category, notes, deflect)
                showProfileDialog = false
            }
        )
    }

    if (showTrainDialog) {
        AddTrainedRuleDialog(
            onDismiss = { showTrainDialog = false },
            onConfirm = { keywords, reply, category, shouldEnd ->
                val strategy = when (category) {
                    CallerCategory.FRIEND -> HandlingStrategy.WARM_FRIENDLY
                    CallerCategory.SPAM_SALES -> HandlingStrategy.DIPLOMATIC_DEFLECTION
                    CallerCategory.VIP -> HandlingStrategy.URGENT_ESCALATION
                    else -> HandlingStrategy.PROFESSIONAL_CLIENT
                }
                onAddTrainedRule(keywords, reply, category, strategy, shouldEnd)
                showTrainDialog = false
            }
        )
    }

    if (showReminderDialog) {
        AddPhoneActionDialog(
            onDismiss = { showReminderDialog = false },
            onConfirm = { type, title, hour, minute, delayMins ->
                onCreatePhoneAction(type, title, hour, minute, delayMins)
                showReminderDialog = false
            }
        )
    }

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
                    text = "الفريق والتدريب أوفلاين",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // 1. Visual AI Team Banner + 4 Specialists
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_ai_team_banner),
                                contentDescription = "صورة فريق الرد الذكي",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.scrim.copy(alpha = 0.25f),
                                                MaterialTheme.colorScheme.surface
                                            )
                                        )
                                    )
                            )
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Groups,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "فريق الرد الذكي المتكامل (أونلاين + أوفلاين)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TeamRoleRow(
                                icon = Icons.Default.Psychology,
                                role = "١. المُحلِّل والمُصنِّف",
                                desc = "يميّز تلقائياً بين العميل، الصديق، السائل، ومكالمات التسويق والإزعاج."
                            )
                            TeamRoleRow(
                                icon = Icons.Default.Shield,
                                role = "٢. المُهندِل والمُراوغ الدبلوماسي",
                                desc = "يتعامل بلباقة ومراوغة ذكية مع المتصلين المزعجين وينهِي المكالمة بأدب."
                            )
                            TeamRoleRow(
                                icon = Icons.Default.RecordVoiceOver,
                                role = "٣. المتحدث الطبيعي",
                                desc = "يتحدث بعربية بشرية سلسة وودودة تناسب طبيعة كل متصل دون تكلف."
                            )
                            TeamRoleRow(
                                icon = Icons.Default.AutoAwesome,
                                role = "٤. مُطوّر الذاكرة والتحكم بالهاتف",
                                desc = "يتدرّب من مكالمات الأونلاين ليعمل أوفلاين، ويضبط المنبهات والتذكيرات."
                            )
                        }
                    }
                }
            }

            // 2. Offline Q&A Training Studio
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                                Text(
                                    text = "استوديو التدريب الأوفلاين (${trainedRules.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            FilledTonalButton(
                                onClick = { showTrainDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("train_offline_rule_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("درّب رداً")
                            }
                        }

                        Text(
                            text = "درّب التطبيق على أسئلة وكلمات مفتاحية ليرد بها فوراً بدون إنترنت، كما يحفظ تلقائياً ردود Gemini الناجحة هنا.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (trainedRules.isEmpty()) {
                            Text(
                                text = "لا توجد قواعد مدربة يدوياً بعد. أضف مثالاً مثل: (الأسعار، الموقع، الدوام).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            trainedRules.take(8).forEach { rule ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = "الكلمات: ${rule.triggerKeywords}",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = rule.replyText,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                text = "${rule.category.arabicLabel} • استُخدمت ${rule.hitCount} مرة",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(onClick = { onDeleteTrainedRule(rule.id) }) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "حذف القاعدة المدربة",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Phone Actions: Alarms, Timers, & Reminders Hub
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Alarm,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = "المنبهات والتذكيرات (${scheduledReminders.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            FilledTonalButton(
                                onClick = { showReminderDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("add_phone_action_button")
                            ) {
                                Icon(Icons.Default.AlarmAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("إجراء جديد")
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    onCreatePhoneAction(PhoneActionType.CREATE_REMINDER, "تذكير متابعة مكالمة", null, null, 15)
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("تذكير ١٥ د")
                            }
                            OutlinedButton(
                                onClick = {
                                    onCreatePhoneAction(PhoneActionType.SET_TIMER, "مؤقت سريع", null, null, 5)
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("مؤقت ٥ د")
                            }
                        }

                        if (scheduledReminders.isEmpty()) {
                            Text(
                                text = "أي منبه أو مؤقت أو تذكير يُطلب أثناء المكالمة أو يُنشأ هنا يظهر في هذه القائمة.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            scheduledReminders.take(8).forEach { rem ->
                                val timeText = remember(rem.triggerAtMillis) {
                                    SimpleDateFormat("MM/dd hh:mm a", Locale("ar")).format(Date(rem.triggerAtMillis))
                                }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${rem.type.arabicLabel}: ${rem.title}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "$timeText • ${rem.sourceCaller} • ${if (rem.isCompleted) "مكتمل" else "مجدول"}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(onClick = { onDeleteReminder(rem.id) }) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "حذف التذكير",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Caller Profiles Directory
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "تصنيف الأرقام والعملاء (${profiles.size})",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    FilledTonalButton(
                        onClick = { showProfileDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("add_caller_profile_button")
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تصنيف رقم")
                    }
                }
            }

            if (profiles.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "لم يتم تصنيف أرقام بعد. سيقوم الفريق بتصنيف المتصلين تلقائياً بعد كل مكالمة أو يمكنك إضافة رقم يدوياً.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(profiles, key = { it.phoneNumber }) { profile ->
                    CallerProfileCard(
                        profile = profile,
                        onDelete = { onDeleteProfile(profile.phoneNumber) }
                    )
                }
            }

            // 5. Self-Learning Memory & Insights
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "ذاكرة التطوير الذاتي (${insights.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newInsightText,
                                onValueChange = { newInsightText = it },
                                placeholder = { Text("توجيه دائم للفريق...") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("add_insight_input")
                            )
                            Button(
                                onClick = {
                                    if (newInsightText.isNotBlank()) {
                                        onAddManualInsight(newInsightText)
                                        newInsightText = ""
                                    }
                                },
                                enabled = newInsightText.isNotBlank(),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .height(52.dp)
                                    .testTag("add_insight_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "إضافة قاعدة")
                            }
                        }

                        insights.take(8).forEach { item ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        if (item.callerNumber.isNotBlank()) {
                                            Text(
                                                text = item.callerNumber,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Text(
                                            text = item.insightText,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    IconButton(onClick = { onDeleteInsight(item.id) }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "حذف الملاحظة",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamRoleRow(
    icon: ImageVector,
    role: String,
    desc: String
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = role,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = role,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CallerProfileCard(
    profile: CallerProfile,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = profile.displayName.ifBlank { profile.phoneNumber },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = profile.category.arabicLabel,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    if (profile.isDeflectMode) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = "مراوغة",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
                if (profile.displayName.isNotBlank()) {
                    Text(
                        text = profile.phoneNumber,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                if (profile.notes.isNotBlank()) {
                    Text(
                        text = profile.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "عدد المكالمات المسجلة: ${profile.callCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "حذف التصنيف",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddCallerProfileDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, CallerCategory, String, Boolean) -> Unit
) {
    var phone by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(CallerCategory.CLIENT) }
    var notes by remember { mutableStateOf("") }
    var deflectMode by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تصنيف متصل جديد") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("رقم الهاتف") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("الاسم (اختياري)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("التصنيف:", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CallerCategory.entries.filter { it != CallerCategory.UNKNOWN }.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = {
                                category = cat
                                if (cat == CallerCategory.SPAM_SALES) deflectMode = true
                            },
                            label = { Text(cat.arabicLabel) }
                        )
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات للفريق الذكي") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("تفعيل الهندلة والمراوغة والإغلاق", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = deflectMode, onCheckedChange = { deflectMode = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(phone, name, category, notes, deflectMode) },
                enabled = phone.isNotBlank()
            ) {
                Text("حفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddTrainedRuleDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, CallerCategory, Boolean) -> Unit
) {
    var keywords by remember { mutableStateOf("") }
    var replyText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(CallerCategory.CLIENT) }
    var shouldEndCall by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تدريب الرد الأوفلاين") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = keywords,
                    onValueChange = { keywords = it },
                    label = { Text("الكلمات المفتاحية (مفصولة بفاصلة)") },
                    placeholder = { Text("السعر، التكلفة، كم") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = replyText,
                    onValueChange = { replyText = it },
                    label = { Text("الرد الصوتي المعتمد") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(CallerCategory.CLIENT, CallerCategory.FRIEND, CallerCategory.INQUIRER, CallerCategory.SPAM_SALES)
                        .forEach { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = { category = cat },
                                label = { Text(cat.arabicLabel) }
                            )
                        }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("إنهاء المكالمة بعد هذا الرد", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = shouldEndCall, onCheckedChange = { shouldEndCall = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(keywords, replyText, category, shouldEndCall) },
                enabled = keywords.isNotBlank() && replyText.isNotBlank()
            ) {
                Text("حفظ التدريب")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddPhoneActionDialog(
    onDismiss: () -> Unit,
    onConfirm: (PhoneActionType, String, Int?, Int?, Int?) -> Unit
) {
    var actionType by remember { mutableStateOf(PhoneActionType.CREATE_REMINDER) }
    var title by remember { mutableStateOf("متابعة مكالمة مهمة") }
    var numberInput by remember { mutableStateOf("15") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ضبط منبه أو تذكير") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        PhoneActionType.CREATE_REMINDER,
                        PhoneActionType.SET_TIMER,
                        PhoneActionType.SET_ALARM
                    ).forEach { type ->
                        FilterChip(
                            selected = actionType == type,
                            onClick = {
                                actionType = type
                                numberInput = if (type == PhoneActionType.SET_ALARM) "8" else "15"
                            },
                            label = { Text(type.arabicLabel) }
                        )
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("العنوان") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = numberInput,
                    onValueChange = { numberInput = it.filter { ch -> ch.isDigit() } },
                    label = {
                        Text(
                            if (actionType == PhoneActionType.SET_ALARM) {
                                "الساعة (0 إلى 23)"
                            } else {
                                "المدة بالدقائق"
                            }
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val value = numberInput.toIntOrNull()
                    if (actionType == PhoneActionType.SET_ALARM) {
                        onConfirm(actionType, title, (value ?: 8).coerceIn(0, 23), 0, null)
                    } else {
                        onConfirm(actionType, title, null, null, (value ?: 15).coerceIn(1, 1440))
                    }
                },
                enabled = title.isNotBlank()
            ) {
                Text("تنفيذ الآن")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
