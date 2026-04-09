package com.elysium.code.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elysium.code.viewmodel.MainViewModel
import com.elysium.code.ui.theme.ElysiumTheme
import kotlinx.coroutines.launch

/**
 * ═══════════════════════════════════════════════════════════════
 * ENHANCED Terminal Screen — Functional Neon Terminal
 * ═══════════════════════════════════════════════════════════════
 *
 * Features:
 * - Real command execution via EnhancedShellManager
 * - Live output streaming
 * - Terminal history with command recall
 * - Neon aesthetic with scanline effects
 * - CRT-style glow
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedTerminalScreen(viewModel: MainViewModel = viewModel()) {
    val scope = rememberCoroutineScope()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    var inputText by remember { mutableStateOf(TextFieldValue("")) }
    val terminalOutput by viewModel.enhancedShellManager.terminalOutput.collectAsState()
    val isExecuting by viewModel.enhancedShellManager.isExecuting.collectAsState()
    val commandHistory by viewModel.enhancedShellManager.commandHistory.collectAsState()
    var historyIndex by remember { mutableIntStateOf(-1) }

    val listState = rememberLazyListState()

    // Auto-scroll on new output
    LaunchedEffect(terminalOutput.size) {
        if (terminalOutput.isNotEmpty()) {
            listState.animateScrollToItem(terminalOutput.size - 1)
        }
    }

    // Scanline animation
    val infiniteTransition = rememberInfiniteTransition(label = "terminal")
    val scanlineY by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "scanline"
    )
    val cursorBlink by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "cursor"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF020208))
    ) {
        // ═══ Header ═══
        Surface(
            color = ElysiumTheme.colors.surface,
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawLine(
                        brush = Brush.horizontalGradient(
                            listOf(
                                Color(0xFF39FF14).copy(alpha = 0.4f),
                                Color(0xFFFF6B00).copy(alpha = 0.4f),
                                Color(0xFF39FF14).copy(alpha = 0.4f)
                            )
                        ),
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 2f
                    )
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "⚡ Elysium Terminal",
                    style = ElysiumTheme.typography.headlineMedium,
                    color = Color(0xFF39FF14)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isExecuting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = Color(0xFFFFB020),
                            strokeWidth = 1.5.dp
                        )
                    }
                    Text(
                        if (isExecuting) "Executing..." else "Ready",
                        style = ElysiumTheme.typography.labelSmall,
                        color = if (isExecuting) Color(0xFFFFB020) else Color(0xFF39FF14)
                    )
                }
            }
        }

        // ═══ Output Area with scanline ═══
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                if (terminalOutput.isEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                "╔══════════════════════════════════════════════╗",
                                style = ElysiumTheme.typography.terminal,
                                color = Color(0xFF00D4FF).copy(alpha = 0.7f)
                            )
                            Text(
                                "║  ELYSIUM TERMINAL v1.0 — LOCAL AI SHELL     ║",
                                style = ElysiumTheme.typography.terminal,
                                color = Color(0xFF00D4FF).copy(alpha = 0.7f)
                            )
                            Text(
                                "║  Gemma 4 E4B • On-Device • Private          ║",
                                style = ElysiumTheme.typography.terminal,
                                color = Color(0xFF00D4FF).copy(alpha = 0.7f)
                            )
                            Text(
                                "╚══════════════════════════════════════════════╝",
                                style = ElysiumTheme.typography.terminal,
                                color = Color(0xFF00D4FF).copy(alpha = 0.7f)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "$ Type commands below. Use ↑/↓ for history.",
                                style = ElysiumTheme.typography.terminal,
                                color = Color(0xFF39FF14).copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                items(terminalOutput) { line ->
                    Text(
                        text = line,
                        style = ElysiumTheme.typography.terminal,
                        color = when {
                            line.startsWith("$ ") -> Color(0xFF39FF14)
                            line.startsWith("[ERROR]") || line.contains("error", ignoreCase = true) -> Color(0xFFFF3B5C)
                            line.startsWith("[Exit code:") -> Color(0xFFFFB020)
                            else -> Color(0xFF39FF14).copy(alpha = 0.85f)
                        },
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }

            // Scanline overlay
            Canvas(modifier = Modifier.fillMaxSize()) {
                val y = scanlineY * size.height
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFF39FF14).copy(alpha = 0.04f),
                            Color(0xFF39FF14).copy(alpha = 0.08f),
                            Color(0xFF39FF14).copy(alpha = 0.04f),
                            Color.Transparent
                        )
                    ),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 2.dp.toPx()
                )

                // CRT-style faint horizontal lines
                for (i in 0 until (size.height / 3.dp.toPx()).toInt()) {
                    val lineY = i * 3.dp.toPx()
                    drawLine(
                        color = Color(0xFF39FF14).copy(alpha = 0.015f),
                        start = Offset(0f, lineY),
                        end = Offset(size.width, lineY),
                        strokeWidth = 0.5f
                    )
                }
            }
        }

        // ═══ Special Keys Bar ═══
        Surface(
            color = Color(0xFF0F0F1A),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val specialKeys = listOf(
                    "ESC", "TAB", "CTRL", "ALT", "|", "/", "\\", "—", "_", "!", "@", "#", "$", "%", "^", "&", "*", "(", ")", "{", "}", "[", "]", "<", ">", "?", "!", ":", ";", "\"", "'", ",", "."
                )
                specialKeys.forEach { key ->
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            // For now, these append to input as we are in a stateless shell model
                            // A future update will integrate these with a PTY for raw control signals
                            val appendChar = when(key) {
                                "TAB" -> "  "
                                "ESC" -> "" 
                                "CTRL", "ALT" -> "" // Modifiers need PTY support
                                else -> key
                            }
                            inputText = TextFieldValue(
                                text = inputText.text + appendChar,
                                selection = androidx.compose.ui.text.TextRange(inputText.text.length + appendChar.length)
                            )
                        },
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF39FF14).copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, Color(0xFF39FF14).copy(alpha = 0.2f)),
                        modifier = Modifier.padding(2.dp)
                    ) {
                        Text(
                            text = key,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = ElysiumTheme.typography.terminal.copy(fontSize = 12.sp),
                            color = Color(0xFF39FF14).copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // ═══ Input Area ═══
        Surface(
            color = Color(0xFF080810),
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawLine(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF39FF14).copy(alpha = 0.3f),
                                Color(0xFF00D4FF).copy(alpha = 0.15f),
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Neon prompt
                Text(
                    "❯",
                    style = ElysiumTheme.typography.terminal.copy(fontSize = 15.sp),
                    color = Color(0xFF39FF14).copy(alpha = 0.7f + cursorBlink * 0.3f)
                )

                // Input field
                BasicTextField(
                    value = inputText,
                    onValueChange = { inputText = it; historyIndex = -1 },
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 4.dp),
                    textStyle = ElysiumTheme.typography.terminal.copy(color = Color(0xFF39FF14)),
                    cursorBrush = SolidColor(Color(0xFF00D4FF).copy(alpha = cursorBlink)),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        Box {
                            if (inputText.text.isEmpty()) {
                                Text(
                                    "enter command...",
                                    style = ElysiumTheme.typography.terminal,
                                    color = Color(0xFF39FF14).copy(alpha = 0.25f)
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                // History up
                IconButton(
                    onClick = {
                        if (commandHistory.isNotEmpty()) {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            historyIndex = (historyIndex + 1).coerceAtMost(commandHistory.size - 1)
                            val cmd = commandHistory.reversed().getOrNull(historyIndex)
                            if (cmd != null) inputText = TextFieldValue(cmd)
                        }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowUp, "History Up",
                        tint = Color(0xFF39FF14).copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Execute button
                IconButton(
                    onClick = {
                        val cmd = inputText.text.trim()
                        if (cmd.isNotEmpty()) {
                            viewModel.enhancedShellManager.executeCommand(cmd)
                            inputText = TextFieldValue("")
                            historyIndex = -1
                        }
                    },
                    enabled = inputText.text.isNotEmpty() && !isExecuting,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (inputText.text.isNotEmpty())
                                Color(0xFF39FF14).copy(alpha = 0.15f)
                            else Color.Transparent
                        )
                ) {
                    Icon(
                        Icons.Default.PlayArrow, "Execute",
                        tint = if (inputText.text.isNotEmpty()) Color(0xFF39FF14) else Color(0xFF585868),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Clear button
                IconButton(
                    onClick = { viewModel.enhancedShellManager.clearTerminal() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Delete, "Clear",
                        tint = Color(0xFF39FF14).copy(alpha = 0.4f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
