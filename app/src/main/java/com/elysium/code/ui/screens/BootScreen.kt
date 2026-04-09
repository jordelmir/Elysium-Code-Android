package com.elysium.code.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.code.ui.theme.ElysiumTheme
import kotlin.math.*

/**
 * ═══════════════════════════════════════════════════════════════
 * ELYSIUM CODE — Boot Screen
 * ═══════════════════════════════════════════════════════════════
 *
 * Futuristic neon-glow boot sequence with animated particles,
 * pulsing light rings, and scanning effects. Shows model
 * extraction and loading progress.
 */
@Composable
fun BootScreen(
    phase: String,
    message: String,
    progress: Float
) {
    val infiniteTransition = rememberInfiniteTransition(label = "boot")

    // Pulsing glow intensity
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    // Rotating ring angle
    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring"
    )

    // Scanning line position
    val scanLine by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scan"
    )

    // Particle system time
    val particleTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particles"
    )

    // Logo text breathing
    val textGlow by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "textGlow"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // ═══ Background: Animated particle field ═══
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Floating particles
            for (i in 0 until 60) {
                val seed = i * 137.508f
                val x = ((sin(seed + particleTime * 0.02f * (i % 5 + 1)) + 1) / 2 * w)
                val y = ((cos(seed * 0.7f + particleTime * 0.015f * (i % 3 + 1)) + 1) / 2 * h)
                val size = (sin(seed) * 1.5f + 2f).coerceAtLeast(0.5f)
                val alpha = (sin(particleTime * 0.05f + seed) * 0.3f + 0.3f).coerceIn(0.05f, 0.6f)

                val color = when (i % 4) {
                    0 -> Color(0xFF00D4FF) // Cyan
                    1 -> Color(0xFF39FF14) // Green
                    2 -> Color(0xFF7C3AED) // Purple
                    else -> Color(0xFFFF00FF) // Magenta
                }

                drawCircle(
                    color = color.copy(alpha = alpha),
                    radius = size.dp.toPx(),
                    center = Offset(x, y)
                )
            }

            // Scanning horizontal line
            val scanY = scanLine * h
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFF00D4FF).copy(alpha = 0.4f * glowPulse),
                        Color(0xFF39FF14).copy(alpha = 0.6f * glowPulse),
                        Color(0xFF00D4FF).copy(alpha = 0.4f * glowPulse),
                        Color.Transparent
                    )
                ),
                start = Offset(0f, scanY),
                end = Offset(w, scanY),
                strokeWidth = 2.dp.toPx()
            )

            // Center glow rings
            val cx = w / 2
            val cy = h / 2 - 40.dp.toPx()

            for (ring in 0 until 3) {
                val radius = (80 + ring * 40).dp.toPx()
                val ringAlpha = (0.15f - ring * 0.04f) * glowPulse
                val startAngle = ringRotation + ring * 120

                drawArc(
                    color = Color(0xFF00D4FF).copy(alpha = ringAlpha.coerceAtLeast(0.02f)),
                    startAngle = startAngle,
                    sweepAngle = 120f,
                    useCenter = false,
                    topLeft = Offset(cx - radius, cy - radius),
                    size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                    style = Stroke(width = 1.5f.dp.toPx())
                )

                drawArc(
                    color = Color(0xFF7C3AED).copy(alpha = ringAlpha.coerceAtLeast(0.02f)),
                    startAngle = startAngle + 180,
                    sweepAngle = 120f,
                    useCenter = false,
                    topLeft = Offset(cx - radius, cy - radius),
                    size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                    style = Stroke(width = 1.5f.dp.toPx())
                )
            }

            // Center pulsing orb
            val orbRadius = (20 + glowPulse * 8).dp.toPx()
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF00D4FF).copy(alpha = 0.8f * glowPulse),
                        Color(0xFF00D4FF).copy(alpha = 0.3f * glowPulse),
                        Color(0xFF7C3AED).copy(alpha = 0.1f * glowPulse),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = orbRadius * 3
                ),
                center = Offset(cx, cy),
                radius = orbRadius * 3
            )

            drawCircle(
                color = Color(0xFF00D4FF).copy(alpha = 0.9f),
                radius = orbRadius * 0.3f,
                center = Offset(cx, cy)
            )
        }

        // ═══ Content ═══
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Spacer(Modifier.height(120.dp))

            // Logo text with neon glow
            Text(
                text = "ELYSIUM",
                style = ElysiumTheme.typography.displayLarge.copy(
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 8.sp
                ),
                color = Color(0xFF00D4FF).copy(alpha = textGlow)
            )

            Text(
                text = "C  O  D  E",
                style = ElysiumTheme.typography.headlineMedium.copy(
                    letterSpacing = 12.sp,
                    fontWeight = FontWeight.Light
                ),
                color = Color(0xFF39FF14).copy(alpha = textGlow * 0.8f)
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "LOCAL AI • MULTIMODAL • AGENTIC",
                style = ElysiumTheme.typography.labelSmall.copy(
                    letterSpacing = 3.sp
                ),
                color = Color(0xFF7C3AED).copy(alpha = 0.7f)
            )

            Spacer(Modifier.height(80.dp))

            // Progress bar with neon glow
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                // Glow background
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .blur(8.dp),
                    color = Color(0xFF00D4FF).copy(alpha = 0.6f),
                    trackColor = Color.Transparent
                )

                // Actual progress bar
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = Color(0xFF00D4FF),
                    trackColor = Color(0xFF1A1A28)
                )
            }

            Spacer(Modifier.height(16.dp))

            // Status message
            Text(
                text = message,
                style = ElysiumTheme.typography.codeMedium,
                color = Color(0xFF00D4FF).copy(alpha = 0.7f)
            )

            Spacer(Modifier.height(8.dp))

            // Progress percentage
            Text(
                text = "${(progress * 100).toInt()}%",
                style = ElysiumTheme.typography.codeLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = Color(0xFF39FF14).copy(alpha = textGlow)
            )

            Spacer(Modifier.height(48.dp))

            // Model info
            Text(
                text = "GEMMA 4 E4B • Q4_K_M • ON-DEVICE",
                style = ElysiumTheme.typography.labelSmall.copy(
                    letterSpacing = 2.sp
                ),
                color = Color(0xFF585868)
            )
        }

        // ═══ Corner decorations ═══
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cornerLen = 30.dp.toPx()
            val cornerColor = Color(0xFF00D4FF).copy(alpha = 0.3f * glowPulse)
            val strokeW = 1.5f.dp.toPx()

            // Top-left
            drawLine(cornerColor, Offset(0f, cornerLen), Offset(0f, 0f), strokeW)
            drawLine(cornerColor, Offset(0f, 0f), Offset(cornerLen, 0f), strokeW)

            // Top-right
            drawLine(cornerColor, Offset(w - cornerLen, 0f), Offset(w, 0f), strokeW)
            drawLine(cornerColor, Offset(w, 0f), Offset(w, cornerLen), strokeW)

            // Bottom-left
            drawLine(cornerColor, Offset(0f, h - cornerLen), Offset(0f, h), strokeW)
            drawLine(cornerColor, Offset(0f, h), Offset(cornerLen, h), strokeW)

            // Bottom-right
            drawLine(cornerColor, Offset(w - cornerLen, h), Offset(w, h), strokeW)
            drawLine(cornerColor, Offset(w, h - cornerLen), Offset(w, h), strokeW)
        }
    }
}
