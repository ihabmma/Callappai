package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsPhone
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.CallLogItem
import com.example.domain.model.CallResponderStatus
import com.example.domain.model.CallerCategory
import com.example.domain.model.ConnectivityPolicy
import com.example.domain.model.ConversationTurn
import com.example.domain.model.LiveCallState
import com.example.domain.model.PermissionSummary
import com.example.domain.model.ResponderSettings
import com.example.domain.model.Speaker
import com.example.ui.components.AudioWaveformCanvas
import com.example.ui.components.CallerDistributionVisualCard
import com.example.ui.components.PulsingStatusOrb
import com.example.ui.components.VisualCallPipelineCard

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    settings: ResponderSettings,
    liveState: LiveCallState,
    permissions: PermissionSummary,
    callLogs: List<CallLogItem>,
    trainedRulesCount: Int,
    remindersCount: Int,
    onToggleService: (Boolean) -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onStartLiveTest: () -> Unit,
    onStopLiveTest: () -> Unit,
    onSendSimulatedUtterance: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var simulatedInput by remember { mutableStateOf("") }

    val isSessionActive = liveState.status != CallResponderStatus.INACTIVE &&
        liveState.status != CallResponderStatus.LISTENING

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
                HeroControlCard(
                    settings = settings,
                    liveState = liveState,
                    onToggleService = onToggleService
                )
            }

            item {
                VisualCallPipelineCard(
                    liveState = liveState,
                    settings = settings
                )
            }

            if (!permissions.allGranted || !permissions.ignoringBatteryOptimizations || !permissions.canDrawOverlays) {
                item {
                    PermissionsCard(
                        permissions = permissions,
                        onRequestPermissions = onRequestPermissions,
                        onOpenOverlaySettings = onOpenOverlaySettings,
                        onRequestBatteryExemption = onRequestBatteryExemption
                    )
                }
            }

            item {
                QuickTelemetryRow(
                    totalCallsCount = callLogs.size,
                    trainedRulesCount = trainedRulesCount,
                    remindersCount = remindersCount,
                    settings = settings
                )
            }

            item {
                CallerDistributionVisualCard(logs = callLogs)
            }

            item {
                LivePipelineCard(
                    liveState = liveState,
                    isSessionActive = isSessionActive,
                    simulatedInput = simulatedInput,
                    onSimulatedInputChange = { simulatedInput = it },
                    onStartLiveTest = onStartLiveTest,
                    onStopLiveTest = onStopLiveTest,
                    onSendUtterance = { text ->
                        if (text.isNotBlank()) {
                            onSendSimulatedUtterance(text)
                            simulatedInput = ""
                        }
                    }
                )
            }

            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SettingsPhone,
                            contentDescription = "معلومة توجيه الصوت",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "يعمل المجيب بخفية واستهلاك طاقة شبه صفري في الخلفية، ويدعم العمل بدون إنترنت عبر المحرك المحلي المدرب، وضبط المنبهات والتذكيرات، مع تنظيف السجلات تلقائياً كل ${settings.retentionDays} أيام.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroControlCard(
    settings: ResponderSettings,
    liveState: LiveCallState,
    onToggleService: (Boolean) -> Unit
) {
    val isEnabled = settings.isServiceEnabled
    val buttonColor by animateColorAsState(
        targetValue = if (isEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        label = "powerButtonColor"
    )

    val statusTitle = when (liveState.status) {
        CallResponderStatus.INACTIVE -> "المجيب الآلي متوقف"
        CallResponderStatus.LISTENING -> if (settings.stealthZeroDrainMode) {
            "يعمل بخفية • استهلاك صفري"
        } else {
            "فريق المجيب نشط ومستعد"
        }
        CallResponderStatus.RINGING -> "رنين مكالمة واردة..."
        CallResponderStatus.ACTIVE_CALL -> "مكالمة جارية — استماع وفهم"
        CallResponderStatus.PROCESSING_AI -> "الفريق يحلل المتصل ويصيغ الرد"
        CallResponderStatus.SPEAKING -> "جاري بث الرد الصوتي الطبيعي"
        CallResponderStatus.ERROR -> "تنبيه في جلسة الرد"
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.img_hero_telecom),
                contentDescription = "خلفية وحدة التحكم الصوتية",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(24.dp))
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.scrim.copy(alpha = 0.75f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.90f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        PulsingStatusOrb(status = liveState.status)
                        Column {
                            Text(
                                text = statusTitle,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = liveState.statusNote.ifBlank {
                                    if (isEnabled) "الخدمة الخلفية تراقب المكالمات الواردة"
                                    else "اضغط لتفعيل الرد الذكي على مكالماتك"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    val isOfflineMode = settings.connectivityPolicy == ConnectivityPolicy.OFFLINE_ONLY || liveState.isOfflineActive
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isOfflineMode) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        }
                    ) {
                        Icon(
                            imageVector = if (isOfflineMode) Icons.Default.CloudOff else Icons.Default.CloudQueue,
                            contentDescription = if (isOfflineMode) "وضع أوفلاين" else "وضع هجين أونلاين وأوفلاين",
                            tint = if (isOfflineMode) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                Button(
                    onClick = { onToggleService(!isEnabled) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonColor,
                        contentColor = if (isEnabled) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("toggle_service_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isEnabled) "إيقاف المجيب الآلي" else "تفعيل المجيب الآلي الآن",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionsCard(
    permissions: PermissionSummary,
    onRequestPermissions: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onRequestBatteryExemption: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "صلاحيات النظام",
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    text = "صلاحيات التشغيل والعمل في الخلفية",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (!permissions.allGranted) {
                PermissionRow("حالة الهاتف والمكالمات (READ_PHONE_STATE)", permissions.readPhoneState)
                PermissionRow("التقاط الصوت والميكروفون (RECORD_AUDIO)", permissions.recordAudio)
                PermissionRow("الرد والإغلاق التلقائي (ANSWER_PHONE_CALLS)", permissions.answerPhoneCalls)
                PermissionRow("قراءة رقم المتصل (READ_CALL_LOG)", permissions.readCallLog)
                PermissionRow("تمييز الأصدقاء والعملاء (READ_CONTACTS)", permissions.readContacts)
                PermissionRow("إشعار الخدمة المستمرة (POST_NOTIFICATIONS)", permissions.postNotifications)

                FilledTonalButton(
                    onClick = onRequestPermissions,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("request_permissions_button")
                ) {
                    Icon(imageVector = Icons.Default.Security, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "منح الصلاحيات الأساسية",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRequestBatteryExemption,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("battery_exemption_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.BatteryChargingFull,
                        contentDescription = null,
                        tint = if (permissions.ignoringBatteryOptimizations) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.tertiary
                        },
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (permissions.ignoringBatteryOptimizations) "البطارية: مستثنى" else "استثناء البطارية",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = onOpenOverlaySettings,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("overlay_permission_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = null,
                        tint = if (permissions.canDrawOverlays) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.tertiary
                        },
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (permissions.canDrawOverlays) "فوق التطبيقات: نشط" else "فوق التطبيقات",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
            contentDescription = if (granted) "ممنوحة" else "غير ممنوحة",
            tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun QuickTelemetryRow(
    totalCallsCount: Int,
    trainedRulesCount: Int,
    remindersCount: Int,
    settings: ResponderSettings
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TelemetryMiniCard(
            icon = Icons.Default.PhoneInTalk,
            label = "المكالمات",
            value = "$totalCallsCount",
            modifier = Modifier.weight(1f)
        )
        TelemetryMiniCard(
            icon = Icons.Default.CloudOff,
            label = "تدريب أوفلاين",
            value = "$trainedRulesCount قاعدة",
            modifier = Modifier.weight(1f)
        )
        TelemetryMiniCard(
            icon = Icons.Default.Alarm,
            label = "منبهات وتذكير",
            value = "$remindersCount",
            modifier = Modifier.weight(1f)
        )
        TelemetryMiniCard(
            icon = Icons.Default.NotificationsActive,
            label = "الرد التلقائي",
            value = if (settings.autoAnswerEnabled) "${settings.autoAnswerDelaySeconds}ث" else "يدوي",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TelemetryMiniCard(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LivePipelineCard(
    liveState: LiveCallState,
    isSessionActive: Boolean,
    simulatedInput: String,
    onSimulatedInputChange: (String) -> Unit,
    onStartLiveTest: () -> Unit,
    onStopLiveTest: () -> Unit,
    onSendUtterance: (String) -> Unit
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
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "مراقب الصوت وفريق الرد المباشر",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (isSessionActive) {
                            val title = buildString {
                                if (liveState.callerName.isNotBlank()) append("${liveState.callerName} • ")
                                append(liveState.callerNumber)
                            }
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }

                if (isSessionActive) {
                    FilledTonalButton(
                        onClick = onStopLiveTest,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("test_session_button")
                    ) {
                        Icon(Icons.Default.CallEnd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إنهاء")
                    }
                } else {
                    OutlinedButton(
                        onClick = onStartLiveTest,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("test_session_button")
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("اختبار صوتي")
                    }
                }
            }

            AnimatedVisibility(visible = isSessionActive || liveState.detectedCategory != CallerCategory.UNKNOWN) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "المتصل: ${liveState.detectedCategory.arabicLabel}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "الأسلوب: ${liveState.activeStrategy.arabicLabel}",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                    if (liveState.isOfflineActive) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = "استجابة أوفلاين",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            AudioWaveformCanvas(
                amplitude = liveState.audioAmplitude,
                status = liveState.status
            )

            AnimatedVisibility(visible = liveState.partialCallerSpeech.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "المتصل يتحدث الآن: ${liveState.partialCallerSpeech}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(10.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            AnimatedVisibility(visible = !liveState.errorMessage.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = liveState.errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            if (liveState.turns.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "جرّب السيناريوهات السريعة لاختبار التمييز، المراوغة، الوضع الأوفلاين، وضبط المنبهات:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    liveState.turns.takeLast(6).forEach { turn ->
                        LiveTranscriptBubble(turn = turn)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "عميل يطلب عرض سعر" to "السلام عليكم، أنا عميل جديد وأريد الاستفسار عن أسعار خدماتكم وموعد التسليم.",
                    "صديق مقرب" to "أهلاً يا صديقي، أنا خالد، حبيت أطمن عليك وأشوفك فاضي بالمساء؟",
                    "مندوب تسويق (مراوغة)" to "مرحباً معك شركة عروض التمويل، عندنا عرض بطاقة ائتمان ممتاز لازم تاخذه اليوم!",
                    "تذكير وموعد" to "ذكرني بعد 15 دقيقة أتصل بخصوص العقد واضبط منبه الساعة 7 مساء.",
                    "إنهاء ووداع" to "تمام شكراً جزيلاً لك، يعطيك العافية، مع السلامة."
                ).forEach { (label, prompt) ->
                    AssistChip(
                        onClick = { onSendUtterance(prompt) },
                        label = { Text(label) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = simulatedInput,
                    onValueChange = onSimulatedInputChange,
                    placeholder = { Text("جرّب إرسال كلام المتصل نصياً...") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("simulated_caller_input")
                )
                IconButton(
                    onClick = { onSendUtterance(simulatedInput) },
                    enabled = simulatedInput.isNotBlank(),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .testTag("send_simulated_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "إرسال محاكاة كلام المتصل"
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveTranscriptBubble(turn: ConversationTurn) {
    val isCaller = turn.speaker == Speaker.CALLER
    val isSystem = turn.speaker == Speaker.SYSTEM

    val containerColor = when {
        isSystem -> MaterialTheme.colorScheme.surfaceVariant
        isCaller -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
    }

    val label = when (turn.speaker) {
        Speaker.CALLER -> "المتصل"
        Speaker.ASSISTANT -> "المجيب الذكي"
        Speaker.SYSTEM -> "النظام"
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = turn.text,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
