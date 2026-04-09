package com.elysium.code.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.elysium.code.ui.components.NeonPulse
import com.elysium.code.ui.theme.ElysiumTheme

/**
 * ═══════════════════════════════════════════════════════════════
 * ELYSIUM CODE — Chat Screen (Neon Edition)
 * ═══════════════════════════════════════════════════════════════
 *
 * Futuristic AI chat with neon glow effects, pulsing avatar,
 * glowing message bubbles, and animated streaming indicators.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen() {
    var inputText by remember { mutableStateOf(TextFieldValue("")) }
    var isRecording by remember { mutableStateOf(false) }
    val messages = remember {
        mutableStateListOf(
            UiMessage(
                role = "assistant",
                content = "⚡ **Elysium Code** initialized.\n\nI'm your local AI coding companion powered by Gemma 4 E4B — running 100% on this device.\n\n" +
                        "I can:\n" +
                        "• 💻 Write, debug, and refactor code\n" +
                        "• 📁 Create files and execute commands\n" +
                        "• 📷 Analyze images and screenshots\n" +
                        "• 🎤 Process audio input\n" +
                        "• 🎥 Understand video content\n" +
                        "• 🧠 Learn and remember your preferences\n\n" +
                        "All processing stays on your device. No internet required.\n\n" +
                        "Ask me anything or use `/` commands.",
                timestamp = System.currentTimeMillis()
            )
        )
    }
    val listState = rememberLazyListState()
    var showAttachMenu by remember { mutableStateOf(false) }

    // Ambient glow animation
    val infiniteTransition = rememberInfiniteTransition(label = "chatGlow")
    val avatarGlow by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "avatarGlow"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ElysiumTheme.colors.background)
    ) {
        // ═══ Top Bar with neon border ═══
        Surface(
            color = ElysiumTheme.colors.surface,
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    // Bottom neon line
                    drawLine(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFF00D4FF).copy(alpha = 0.4f),
                                Color(0xFF7C3AED).copy(alpha = 0.4f),
                                Color.Transparent
                            )
                        ),
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.5f
                    )
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // AI Avatar with glow
                Box(contentAlignment = Alignment.Center) {
                    // Outer glow
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .blur(8.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF00D4FF).copy(alpha = 0.4f * avatarGlow),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF00D4FF),
                                        Color(0xFF7C3AED)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("E", style = ElysiumTheme.typography.headlineMedium, color = Color.White)
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Elysium Code",
                        style = ElysiumTheme.typography.headlineMedium,
                        color = ElysiumTheme.colors.textPrimary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NeonPulse(
                            color = Color(0xFF39FF14),
                            size = 4.dp,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Gemma 4 E4B • Local • Ready",
                            style = ElysiumTheme.typography.bodySmall,
                            color = Color(0xFF39FF14).copy(alpha = 0.8f)
                        )
                    }
                }

                // Memory indicator with glow
                IconButton(onClick = { }) {
                    Icon(
                        Icons.Outlined.Psychology,
                        "Memory",
                        tint = Color(0xFF7C3AED).copy(alpha = avatarGlow),
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(onClick = { messages.clear() }) {
                    Icon(
                        Icons.Outlined.AddComment,
                        "New Chat",
                        tint = ElysiumTheme.colors.textSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // ═══ Messages ═══
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(messages) { message ->
                ChatBubble(message = message, glowAlpha = avatarGlow)
            }
        }

        // ═══ Attach Menu ═══
        AnimatedVisibility(visible = showAttachMenu) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ElysiumTheme.colors.surfaceElevated)
                    .drawBehind {
                        drawLine(
                            color = Color(0xFF00D4FF).copy(alpha = 0.2f),
                            start = Offset(0f, 0f),
                            end = Offset(size.width, 0f),
                            strokeWidth = 1f
                        )
                    }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AttachButton(Icons.Outlined.Image, "Image", Color(0xFF00D4FF)) { }
                AttachButton(Icons.Outlined.CameraAlt, "Camera", Color(0xFF39FF14)) { }
                AttachButton(Icons.Outlined.Mic, "Audio", Color(0xFFFFB020)) { }
                AttachButton(Icons.Outlined.Videocam, "Video", Color(0xFFFF3B5C)) { }
                AttachButton(Icons.Outlined.AttachFile, "File", Color(0xFF7C3AED)) { }
            }
        }

        // ═══ Input Bar with neon border ═══
        Surface(
            color = ElysiumTheme.colors.surface,
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawLine(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFF00D4FF).copy(alpha = 0.25f),
                                Color(0xFF7C3AED).copy(alpha = 0.25f),
                                Color.Transparent
                            )
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 1f
                    )
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                IconButton(
                    onClick = { showAttachMenu = !showAttachMenu },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Filled.Add,
                        "Attach",
                        tint = ElysiumTheme.colors.textSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                BasicTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    textStyle = ElysiumTheme.typography.bodyMedium.copy(
                        color = ElysiumTheme.colors.textPrimary
                    ),
                    cursorBrush = SolidColor(Color(0xFF00D4FF)),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 36.dp, max = 120.dp)
                        .background(
                            ElysiumTheme.colors.surfaceElevated,
                            RoundedCornerShape(18.dp)
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF00D4FF).copy(alpha = 0.15f),
                                    Color(0xFF7C3AED).copy(alpha = 0.15f)
                                )
                            ),
                            shape = RoundedCornerShape(18.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    decorationBox = { innerTextField ->
                        Box {
                            if (inputText.text.isEmpty()) {
                                Text(
                                    text = "Ask Elysium anything...",
                                    style = ElysiumTheme.typography.bodyMedium,
                                    color = ElysiumTheme.colors.textTertiary
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                Spacer(Modifier.width(4.dp))

                // Neon Send / Mic button
                if (inputText.text.isEmpty()) {
                    IconButton(
                        onClick = { isRecording = !isRecording },
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                if (isRecording)
                                    Brush.linearGradient(listOf(Color(0xFFFF3B5C), Color(0xFFFF006E)))
                                else
                                    Brush.linearGradient(listOf(Color(0xFF00D4FF), Color(0xFF7C3AED))),
                                CircleShape
                            )
                    ) {
                        Icon(
                            if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                            "Record",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = {
                            val text = inputText.text.trim()
                            if (text.isNotEmpty()) {
                                messages.add(UiMessage("user", text, System.currentTimeMillis()))
                                messages.add(UiMessage("assistant", "Thinking...", System.currentTimeMillis(), isStreaming = true))
                                inputText = TextFieldValue("")
                                showAttachMenu = false
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                Brush.linearGradient(listOf(Color(0xFF00D4FF), Color(0xFF39FF14))),
                                CircleShape
                            )
                    ) {
                        Icon(
                            Icons.Filled.ArrowUpward,
                            "Send",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Chat Components
// ═══════════════════════════════════════════════════════════════

data class UiMessage(
    val role: String,
    val content: String,
    val timestamp: Long,
    val isStreaming: Boolean = false
)

@Composable
fun ChatBubble(message: UiMessage, glowAlpha: Float = 0.5f) {
    val isUser = message.role == "user"
    val isAssistant = message.role == "assistant"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            // AI avatar with glow
            Box(contentAlignment = Alignment.Center) {
                if (message.isStreaming) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .blur(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00D4FF).copy(alpha = 0.3f * glowAlpha))
                    )
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF00D4FF), Color(0xFF7C3AED))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("E", style = ElysiumTheme.typography.labelSmall, color = Color.White)
                }
            }
            Spacer(Modifier.width(8.dp))
        }

        Surface(
            shape = RoundedCornerShape(
                topStart = if (isUser) 16.dp else 4.dp,
                topEnd = if (isUser) 4.dp else 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            ),
            color = when {
                isUser -> Color(0xFF00D4FF).copy(alpha = 0.1f)
                else -> ElysiumTheme.colors.surfaceCard
            },
            border = when {
                isUser -> BorderStroke(1.dp, Color(0xFF00D4FF).copy(alpha = 0.15f))
                message.isStreaming -> BorderStroke(1.dp, Color(0xFF00D4FF).copy(alpha = 0.2f * glowAlpha))
                else -> null
            },
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = message.content,
                    style = ElysiumTheme.typography.bodyMedium,
                    color = ElysiumTheme.colors.textPrimary
                )

                if (message.isStreaming) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        color = Color(0xFF00D4FF),
                        trackColor = ElysiumTheme.colors.surfaceBright,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp))
                    )
                }
            }
        }
    }
}

@Composable
fun AttachButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.12f))
                .border(1.dp, color.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, label, tint = color, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = ElysiumTheme.typography.labelSmall, color = ElysiumTheme.colors.textSecondary)
    }
}
