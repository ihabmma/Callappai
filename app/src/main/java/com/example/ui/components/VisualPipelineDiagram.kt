package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.domain.model.CallLogItem
import com.example.domain.model.CallResponderStatus
import com.example.domain.model.CallerCategory
import com.example.domain.model.ConnectivityPolicy
import com.example.domain.model.LiveCallState
import com.example.domain.model.ResponderSettings

@Composable
fun VisualCallPipelineCard(
    liveState: LiveCallState,
    settings: ResponderSettings,
    modifier: Modifier = Modifier
) {
    val activeStageIndex = when (liveState.status) {
        CallResponderStatus.INACTIVE -> -1
        CallResponderStatus.LISTENING,
        CallResponderStatus.RINGING -> 0
        CallResponderStatus.ACTIVE_CALL -> 1
        CallResponderStatus.PROCESSING_AI -> 2
        CallResponderStatus.SPEAKING -> 3
        CallResponderStatus.ERROR -> 2
    }

    val isOfflineBadge = liveState.isOfflineActive ||
        settings.connectivityPolicy == ConnectivityPolicy.OFFLINE_ONLY

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "مسار المعالجة البصري المباشر",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isOfflineBadge) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = if (isOfflineBadge) Icons.Default.CloudOff else Icons.Default.CloudQueue,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = if (isOfflineBadge) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        )
                        Text(
                            text = if (isOfflineBadge) "أوفلاين محلي" else "هجين ذكي",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PipelineStageNode(
                    icon = Icons.Default.PhoneInTalk,
                    label = "المتصل",
                    isActive = activeStageIndex == 0,
                    isPassed = activeStageIndex > 0,
                    modifier = Modifier.weight(1f)
                )
                PipelineConnectorLine(isActive = activeStageIndex >= 1)
                PipelineStageNode(
                    icon = Icons.Default.GraphicEq,
                    label = "الصوت",
                    isActive = activeStageIndex == 1,
                    isPassed = activeStageIndex > 1,
                    modifier = Modifier.weight(1f)
                )
                PipelineConnectorLine(isActive = activeStageIndex >= 2)
                PipelineStageNode(
                    icon = Icons.Default.Psychology,
                    label = if (isOfflineBadge) "أوفلاين" else "العقل",
                    isActive = activeStageIndex == 2,
                    isPassed = activeStageIndex > 2,
                    modifier = Modifier.weight(1f)
                )
                PipelineConnectorLine(isActive = activeStageIndex >= 3)
                PipelineStageNode(
                    icon = Icons.Default.RecordVoiceOver,
                    label = "الرد والفعل",
                    isActive = activeStageIndex == 3,
                    isPassed = false,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PipelineStageNode(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    isPassed: Boolean,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "stagePulse")
    val scale by infinite.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "nodeScale"
    )

    val bgColor by animateColorAsState(
        targetValue = when {
            isActive -> MaterialTheme.colorScheme.primary
            isPassed -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        label = "nodeBg"
    )
    val iconColor by animateColorAsState(
        targetValue = when {
            isActive -> MaterialTheme.colorScheme.onPrimary
            isPassed -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "nodeIcon"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(46.dp)
                .scale(if (isActive) scale else 1f)
                .clip(CircleShape)
                .background(bgColor)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun PipelineConnectorLine(isActive: Boolean) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier = Modifier
            .width(20.dp)
            .height(24.dp)
            .padding(bottom = 16.dp)
    ) {
        drawLine(
            color = if (isActive) activeColor else inactiveColor,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = 6f,
            cap = StrokeCap.Round
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CallerDistributionVisualCard(
    logs: List<CallLogItem>,
    modifier: Modifier = Modifier
) {
    val total = logs.size.coerceAtLeast(1)
    val clientsCount = logs.count { it.callerCategory == CallerCategory.CLIENT }
    val friendsCount = logs.count { it.callerCategory == CallerCategory.FRIEND }
    val spamCount = logs.count { it.callerCategory == CallerCategory.SPAM_SALES }
    val offlineCount = logs.count { it.handledOffline }
    val otherCount = (logs.size - clientsCount - friendsCount - spamCount).coerceAtLeast(0)

    val clientColor = MaterialTheme.colorScheme.primary
    val friendColor = MaterialTheme.colorScheme.secondary
    val spamColor = MaterialTheme.colorScheme.error
    val otherColor = MaterialTheme.colorScheme.tertiary

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.fillMaxWidth()
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
                Text(
                    text = "تحليل المتصلين البصري",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "أوفلاين: $offlineCount / ${logs.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Text(
                    text = "سيظهر هنا الرسم البياني لتوزيع العملاء والأصدقاء ومكالمات التسويق بعد أول مكالمة.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    if (clientsCount > 0) {
                        Box(
                            modifier = Modifier
                                .weight(clientsCount.toFloat() / total.toFloat())
                                .height(16.dp)
                                .background(clientColor)
                        )
                    }
                    if (friendsCount > 0) {
                        Box(
                            modifier = Modifier
                                .weight(friendsCount.toFloat() / total.toFloat())
                                .height(16.dp)
                                .background(friendColor)
                        )
                    }
                    if (spamCount > 0) {
                        Box(
                            modifier = Modifier
                                .weight(spamCount.toFloat() / total.toFloat())
                                .height(16.dp)
                                .background(spamColor)
                        )
                    }
                    if (otherCount > 0) {
                        Box(
                            modifier = Modifier
                                .weight(otherCount.toFloat() / total.toFloat())
                                .height(16.dp)
                                .background(otherColor)
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LegendChip(color = clientColor, label = "عملاء ($clientsCount)")
                    LegendChip(color = friendColor, label = "أصدقاء ($friendsCount)")
                    LegendChip(color = spamColor, label = "تسويق/مراوغة ($spamCount)")
                    LegendChip(color = otherColor, label = "استفسارات ($otherCount)")
                }
            }
        }
    }
}

@Composable
private fun LegendChip(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
