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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.domain.model.CallResponderStatus
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun PulsingStatusOrb(
    status: CallResponderStatus,
    modifier: Modifier = Modifier
) {
    val targetColor = when (status) {
        CallResponderStatus.INACTIVE -> MaterialTheme.colorScheme.outline
        CallResponderStatus.LISTENING -> MaterialTheme.colorScheme.primary
        CallResponderStatus.RINGING -> MaterialTheme.colorScheme.tertiary
        CallResponderStatus.ACTIVE_CALL -> MaterialTheme.colorScheme.primary
        CallResponderStatus.PROCESSING_AI -> MaterialTheme.colorScheme.secondary
        CallResponderStatus.SPEAKING -> MaterialTheme.colorScheme.primary
        CallResponderStatus.ERROR -> MaterialTheme.colorScheme.error
    }
    val color by animateColorAsState(targetValue = targetColor, label = "orbColor")

    val shouldPulse = status != CallResponderStatus.INACTIVE && status != CallResponderStatus.ERROR
    val infiniteTransition = rememberInfiniteTransition(label = "orbPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (shouldPulse) 1.55f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = if (shouldPulse) 0.45f else 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = modifier.size(36.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .scale(pulseScale)
                .background(color.copy(alpha = pulseAlpha), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(14.dp)
                .background(color, CircleShape)
        )
    }
}

@Composable
fun AudioWaveformCanvas(
    amplitude: Float,
    status: CallResponderStatus,
    modifier: Modifier = Modifier
) {
    val isActive = status == CallResponderStatus.ACTIVE_CALL ||
        status == CallResponderStatus.SPEAKING ||
        status == CallResponderStatus.PROCESSING_AI ||
        status == CallResponderStatus.RINGING

    val infiniteTransition = rememberInfiniteTransition(label = "wavePhase")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val idleColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)

    val activeBrush = when (status) {
        CallResponderStatus.RINGING -> Brush.verticalGradient(listOf(tertiaryColor, primaryColor))
        CallResponderStatus.PROCESSING_AI -> Brush.verticalGradient(listOf(secondaryColor, primaryColor))
        else -> Brush.verticalGradient(listOf(primaryColor, secondaryColor))
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
    ) {
        val barCount = 28
        val spacing = size.width / (barCount * 1.85f)
        val barWidth = (size.width - spacing * (barCount - 1)) / barCount
        val centerY = size.height / 2f

        for (i in 0 until barCount) {
            val normalizedIndex = i / (barCount - 1f)
            val envelope = sin(normalizedIndex * PI).toFloat()
            val syntheticWave = if (isActive) {
                val baseBoost = when (status) {
                    CallResponderStatus.SPEAKING -> 0.55f
                    CallResponderStatus.PROCESSING_AI -> 0.35f
                    CallResponderStatus.RINGING -> 0.48f
                    else -> 0.18f
                }
                val combined = (amplitude.coerceIn(0f, 1f) * 0.85f + baseBoost)
                val wave = abs(sin(phase + i * 0.45f))
                (0.14f + combined * wave * envelope).coerceIn(0.10f, 1.0f)
            } else {
                0.10f
            }

            val barHeight = (size.height * syntheticWave).coerceAtLeast(6.dp.toPx())
            val x = i * (barWidth + spacing)
            val topY = centerY - barHeight / 2f

            if (isActive) {
                drawRoundRect(
                    brush = activeBrush,
                    topLeft = Offset(x, topY),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                )
            } else {
                drawRoundRect(
                    color = idleColor,
                    topLeft = Offset(x, topY),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                )
            }
        }
    }
}
