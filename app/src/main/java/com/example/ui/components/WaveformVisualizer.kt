package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.NeonRed
import kotlin.random.Random

@Composable
fun WaveformVisualizer(
    isRecording: Boolean,
    amplitude: Float,
    modifier: Modifier = Modifier,
    barColor: Color = CyanAccent,
    barCount: Int = 32
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveAnim")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "phase"
    )

    Box(modifier = modifier.fillMaxWidth().height(64.dp)) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val totalWidth = size.width
            val totalHeight = size.height
            val barWidth = (totalWidth / (barCount * 1.5f)).coerceAtLeast(3f)
            val spacing = (totalWidth - (barWidth * barCount)) / (barCount - 1)

            val centerY = totalHeight / 2f

            for (i in 0 until barCount) {
                val x = i * (barWidth + spacing)
                val normalizedIndex = i.toFloat() / barCount

                val heightFactor = if (isRecording) {
                    val sine = kotlin.math.sin(phase + (normalizedIndex * 3.14f * 2)).toFloat()
                    val dynamicAmp = (amplitude * (0.6f + 0.4f * sine)).coerceIn(0.15f, 1.0f)
                    dynamicAmp
                } else {
                    0.1f + 0.05f * kotlin.math.sin(normalizedIndex * 3.14f).toFloat()
                }

                val barH = (totalHeight * heightFactor).coerceIn(4f, totalHeight)
                val top = centerY - (barH / 2f)

                val gradient = Brush.verticalGradient(
                    colors = if (isRecording) listOf(NeonRed, CyanAccent) else listOf(CyanAccent.copy(alpha = 0.3f), CyanAccent.copy(alpha = 0.6f)),
                    startY = top,
                    endY = top + barH
                )

                drawRoundRect(
                    brush = gradient,
                    topLeft = Offset(x, top),
                    size = Size(barWidth, barH),
                    cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
                )
            }
        }
    }
}
