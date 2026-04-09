package com.elysium.code.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/**
 * ═══════════════════════════════════════════════════════════════
 * ELYSIUM CODE — Neon Effects Library
 * ═══════════════════════════════════════════════════════════════
 *
 * Reusable neon glow effects, animated borders, pulsing
 * backgrounds, and particle systems for the futuristic UI.
 */

/**
 * Animated neon border that pulses and shifts colors
 */
@Composable
fun NeonBorder(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    borderWidth: Dp = 1.5.dp,
    primaryColor: Color = Color(0xFF00D4FF),
    secondaryColor: Color = Color(0xFF7C3AED),
    glowRadius: Dp = 8.dp,
    animationDurationMs: Int = 3000
) {
    val infiniteTransition = rememberInfiniteTransition(label = "neonBorder")

    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(animationDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "borderPhase"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cr = cornerRadius.toPx()
        val bw = borderWidth.toPx()

        // Glow layer (blurred)
        drawRoundRect(
            brush = Brush.sweepGradient(
                0f to primaryColor.copy(alpha = glowAlpha),
                0.25f to secondaryColor.copy(alpha = glowAlpha * 0.5f),
                0.5f to primaryColor.copy(alpha = glowAlpha),
                0.75f to secondaryColor.copy(alpha = glowAlpha * 0.5f),
                1f to primaryColor.copy(alpha = glowAlpha),
                center = Offset(w / 2, h / 2)
            ),
            cornerRadius = CornerRadius(cr),
            style = Stroke(width = bw + glowRadius.toPx()),
            size = Size(w, h)
        )

        // Sharp border
        drawRoundRect(
            brush = Brush.sweepGradient(
                // Rotate the gradient based on phase
                ((0f + phase) % 1f) to primaryColor,
                ((0.25f + phase) % 1f) to secondaryColor,
                ((0.5f + phase) % 1f) to primaryColor,
                ((0.75f + phase) % 1f) to secondaryColor,
                center = Offset(w / 2, h / 2)
            ),
            cornerRadius = CornerRadius(cr),
            style = Stroke(width = bw),
            size = Size(w, h)
        )
    }
}

/**
 * Pulsing neon dot indicator
 */
@Composable
fun NeonPulse(
    color: Color = Color(0xFF39FF14),
    size: Dp = 8.dp,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Canvas(modifier = modifier.size(size * 3)) {
        val center = Offset(this.size.width / 2, this.size.height / 2)
        val radius = size.toPx() / 2

        // Outer glow
        drawCircle(
            color = color.copy(alpha = alpha * 0.3f),
            radius = radius * scale * 2.5f,
            center = center
        )

        // Inner glow
        drawCircle(
            color = color.copy(alpha = alpha * 0.6f),
            radius = radius * scale * 1.5f,
            center = center
        )

        // Core
        drawCircle(
            color = color.copy(alpha = alpha),
            radius = radius * scale,
            center = center
        )

        // Bright center
        drawCircle(
            color = Color.White.copy(alpha = alpha * 0.8f),
            radius = radius * 0.3f,
            center = center
        )
    }
}

/**
 * Animated data stream lines (Matrix-style but with neon colors)
 */
@Composable
fun NeonDataStream(
    modifier: Modifier = Modifier,
    lineCount: Int = 15,
    color: Color = Color(0xFF00D4FF)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "dataStream")

    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "streamTime"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        for (i in 0 until lineCount) {
            val x = (i.toFloat() / lineCount) * w
            val speed = 0.5f + (i % 3) * 0.3f
            val offset = (time * speed + i * 17f) % h

            // Each line segment
            val segmentLength = 30.dp.toPx() + (i % 5) * 10.dp.toPx()
            val alpha = 0.1f + (sin(time * 0.1f + i) * 0.15f).coerceAtLeast(0f)

            drawLine(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        color.copy(alpha = alpha),
                        color.copy(alpha = alpha * 2),
                        color.copy(alpha = alpha),
                        Color.Transparent
                    ),
                    startY = offset - segmentLength,
                    endY = offset
                ),
                start = Offset(x, (offset - segmentLength).coerceAtLeast(0f)),
                end = Offset(x, offset.coerceAtMost(h)),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}

/**
 * Glowing card background with animated gradient
 */
@Composable
fun NeonGlowCard(
    modifier: Modifier = Modifier,
    glowColor: Color = Color(0xFF00D4FF),
    cornerRadius: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glowCard")

    val glowIntensity by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cardGlow"
    )

    Box(modifier = modifier) {
        // Glow layer behind the card
        Box(
            modifier = Modifier
                .matchParentSize()
                .blur(16.dp)
                .clip(RoundedCornerShape(cornerRadius))
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            glowColor.copy(alpha = glowIntensity),
                            Color.Transparent
                        )
                    )
                )
        )

        // Card content
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(cornerRadius))
                .background(Color(0xFF0A0A15).copy(alpha = 0.9f)),
            content = content
        )
    }
}

/**
 * Status bar line that shows activity with racing neon light
 */
@Composable
fun NeonActivityBar(
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
    color: Color = Color(0xFF00D4FF)
) {
    if (!isActive) return

    val infiniteTransition = rememberInfiniteTransition(label = "activityBar")

    val position by infiniteTransition.animateFloat(
        initialValue = -0.2f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "barPosition"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(2.dp)
    ) {
        val w = size.width
        val segmentWidth = w * 0.3f
        val x = position * w

        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    color.copy(alpha = 0.8f),
                    color,
                    color.copy(alpha = 0.8f),
                    Color.Transparent
                ),
                startX = x - segmentWidth / 2,
                endX = x + segmentWidth / 2
            ),
            start = Offset((x - segmentWidth / 2).coerceAtLeast(0f), size.height / 2),
            end = Offset((x + segmentWidth / 2).coerceAtMost(w), size.height / 2),
            strokeWidth = size.height
        )
    }
}
