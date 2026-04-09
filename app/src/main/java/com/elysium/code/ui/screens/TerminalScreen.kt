package com.elysium.code.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import com.elysium.code.utils.MediaUtils
import com.elysium.code.utils.MediaType
import androidx.compose.foundation.text.selection.SelectionContainer
import java.util.UUID
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.code.ui.theme.ElysiumTheme

enum class MessageType { SYSTEM, USER, AI_TEXT, AI_MEDIA }
data class TerminalMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val type: MessageType = MessageType.SYSTEM,
    val mediaType: MediaType? = null
)

/**
 * ═══════════════════════════════════════════════════════════════
 * ELYSIUM CODE — Terminal Screen (Neon Edition)
 * ═══════════════════════════════════════════════════════════════
 *
 * Futuristic terminal with:
 * - Neon green text on AMOLED black
 * - Glowing cursor & prompt
 * - Scanline overlay effect
 * - Neon-bordered keyboard keys
 * - Pulsing modifier indicators
 * - Complete 7-row PC keyboard
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(viewModel: com.elysium.code.viewmodel.MainViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val context = LocalContext.current
    
    val shellOutput by viewModel.enhancedShellManager.terminalOutput.collectAsState()
    val isExecuting by viewModel.enhancedShellManager.isExecuting.collectAsState()

    val outputLines = remember(shellOutput, isExecuting) {
        val lines = mutableListOf<TerminalMessage>()
        lines.add(TerminalMessage(text = "╔══════════════════════════════════════════════╗"))
        lines.add(TerminalMessage(text = "║  ELYSIUM TERMINAL v1.0 — LOCAL SHELL        ║"))
        lines.add(TerminalMessage(text = "║  Android OS Environment                     ║"))
        lines.add(TerminalMessage(text = "╚══════════════════════════════════════════════╝"))
        lines.add(TerminalMessage(text = ""))
        lines.add(TerminalMessage(text = "$ Type any bash/sh command"))
        lines.add(TerminalMessage(text = ""))

        shellOutput.forEach { line ->
            lines.add(TerminalMessage(text = line.trimEnd(), type = MessageType.SYSTEM))
        }

        if (isExecuting) {
            lines.add(TerminalMessage(text = "[_] Running...", type = MessageType.SYSTEM))
        }
        
        lines
    }
    var inputText by remember { mutableStateOf("") }
    var isCtrlActive by remember { mutableStateOf(false) }
    var isAltActive by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val sessions = remember { mutableStateListOf("Session 1") }
    var activeSession by remember { mutableIntStateOf(0) }
    
    // PDF Picker Launcher
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            outputLines.add(TerminalMessage(text = "[Analyzer] Archivo PDF montado: ${uri.lastPathSegment}", type = MessageType.SYSTEM))
            outputLines.add(TerminalMessage(text = "Leyendo y vectorizando bloques del PDF (simulado)...", type = MessageType.SYSTEM))
            // TODO: Here you plug in a real PDF Text Extractor like PdfBox-Android or PDFRenderer
            inputText += " [Analiza este documento PDF: ${uri.path}]"
        }
    }

    // Animations
    val infiniteTransition = rememberInfiniteTransition(label = "terminal")
    val cursorBlink by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "cursor"
    )
    val scanlineY by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "scanline"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF020208))
    ) {
        // ═══ Tab Bar with neon underline ═══
        Box {
            ScrollableTabRow(
                selectedTabIndex = activeSession,
                containerColor = ElysiumTheme.colors.surface,
                contentColor = Color(0xFF39FF14),
                edgePadding = 8.dp,
                divider = {},
                indicator = {},
                modifier = Modifier.height(36.dp)
            ) {
                sessions.forEachIndexed { index, name ->
                    Tab(
                        selected = activeSession == index,
                        onClick = { activeSession = index },
                        modifier = Modifier
                            .height(36.dp)
                            .then(
                                if (activeSession == index) {
                                    Modifier.drawBehind {
                                        drawLine(
                                            color = Color(0xFF39FF14),
                                            start = Offset(0f, size.height),
                                            end = Offset(size.width, size.height),
                                            strokeWidth = 2.dp.toPx()
                                        )
                                    }
                                } else Modifier
                            )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) {
                            // Neon dot for active tab
                            if (activeSession == index) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .background(Color(0xFF39FF14), RoundedCornerShape(2.5.dp))
                                )
                                Spacer(Modifier.width(2.dp))
                            }
                            Text(
                                text = name,
                                style = ElysiumTheme.typography.codeSmall,
                                color = if (activeSession == index) Color(0xFF39FF14)
                                        else ElysiumTheme.colors.textTertiary
                            )
                            if (sessions.size > 1) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Close",
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clickable {
                                            if (sessions.size > 1) {
                                                sessions.removeAt(index)
                                                if (activeSession >= sessions.size) activeSession = sessions.size - 1
                                            }
                                        },
                                    tint = ElysiumTheme.colors.textTertiary
                                )
                            }
                        }
                    }
                }
                Tab(selected = false, onClick = {
                    sessions.add("Session ${sessions.size + 1}")
                    activeSession = sessions.size - 1
                }, modifier = Modifier.height(36.dp)) {
                    Icon(Icons.Filled.Add, "New Tab", tint = ElysiumTheme.colors.textTertiary, modifier = Modifier.size(16.dp))
                }
            }
        }

        // ═══ Terminal Output with scanline overlay ═══
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                items(outputLines) { msg ->
                    val line = msg.text
                    val isAi = msg.type == MessageType.AI_TEXT || msg.type == MessageType.AI_MEDIA
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        SelectionContainer(modifier = Modifier.weight(1f)) {
                            Text(
                                text = line,
                                style = ElysiumTheme.typography.terminal,
                                color = when {
                                    msg.type == MessageType.USER -> Color(0xFF39FF14)
                                    msg.type == MessageType.AI_TEXT -> Color(0xFF00D4FF)
                                    msg.type == MessageType.AI_MEDIA -> Color(0xFFFFB020)
                                    line.startsWith("$") -> Color(0xFF39FF14)
                                    line.startsWith("╔") || line.startsWith("║") || line.startsWith("╚") -> Color(0xFF00D4FF).copy(alpha = 0.7f)
                                    line.startsWith("Error") || line.startsWith("error") -> Color(0xFFFF3B5C)
                                    line.startsWith("Warning") -> Color(0xFFFFB020)
                                    line.startsWith("[AI]") -> Color(0xFF00D4FF)
                                    line.contains("success", ignoreCase = true) -> Color(0xFF39FF14)
                                    else -> Color(0xFF39FF14).copy(alpha = 0.85f)
                                }
                            )
                        }
                        
                        if (isAi) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                // Default Copy Text button for all AI generated output
                                IconButton(
                                    onClick = { MediaUtils.copyToClipboard(context, line) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.ContentCopy, 
                                        contentDescription = "Copiar",
                                        tint = Color(0xFF00D4FF).copy(alpha = 0.8f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                
                                // Download Media button for AI Media output
                                if (msg.type == MessageType.AI_MEDIA) {
                                    IconButton(
                                        onClick = { MediaUtils.saveMediaToDevice(context, line, msg.mediaType ?: MediaType.IMAGE) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Download, 
                                            contentDescription = "Descargar Media",
                                            tint = Color(0xFF39FF14).copy(alpha = 0.8f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
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

        // ═══ Input Line with neon prompt ═══
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF080810))
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
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Glowing prompt
            Text(
                "$",
                style = ElysiumTheme.typography.terminal.copy(fontSize = 15.sp),
                color = Color(0xFF39FF14).copy(alpha = 0.7f + cursorBlink * 0.3f)
            )
            Spacer(Modifier.width(6.dp))
            BasicTextField(
                value = inputText,
                onValueChange = { inputText = it },
                textStyle = ElysiumTheme.typography.terminal.copy(color = Color(0xFF39FF14)),
                cursorBrush = SolidColor(Color(0xFF00D4FF).copy(alpha = cursorBlink)),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp),
                decorationBox = { innerTextField ->
                    Box {
                        if (inputText.isEmpty()) {
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
            // Paste Icon
            IconButton(
                onClick = { 
                    val cbText = MediaUtils.getFromClipboard(context)
                    if (cbText.isNotEmpty()) {
                        inputText += cbText
                    }
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Filled.ContentPaste, "Pegar",
                    tint = Color(0xFF39FF14).copy(alpha = 0.8f),
                    modifier = Modifier.size(16.dp)
                )
            }
            
            // Attach PDF / File Icon
            IconButton(
                onClick = { 
                    pdfPickerLauncher.launch(arrayOf("application/pdf"))
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Filled.AttachFile, "Attach PDF",
                    tint = Color(0xFF00D4FF).copy(alpha = 0.8f),
                    modifier = Modifier.size(16.dp)
                )
            }
            IconButton(
                onClick = {
                    val cmd = inputText.trim()
                    if (cmd.isNotEmpty()) {
                        viewModel.enhancedShellManager.executeCommand(cmd)
                        inputText = ""
                    }
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Filled.Send, "Execute",
                    tint = Color(0xFF00D4FF),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // ═══ PC KEYBOARD — Neon Glass Keys ═══
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF08080F), Color(0xFF0A0A15))
                    )
                )
                .drawBehind {
                    drawLine(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFF39FF14).copy(alpha = 0.15f),
                                Color(0xFF00D4FF).copy(alpha = 0.15f),
                                Color.Transparent
                            )
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 1f
                    )
                }
                .padding(vertical = 3.dp)
        ) {
            val sendKey = { key: ExtraKey ->
                when {
                    key.label == "CTRL" -> isCtrlActive = !isCtrlActive
                    key.label == "ALT" -> isAltActive = !isAltActive
                    else -> {
                        isCtrlActive = false
                        isAltActive = false
                    }
                }
            }

            // Row 1: Modifier keys
            ExtraKeysRow(
                keys = listOf(
                    ExtraKey("ESC", "\u001B"),
                    ExtraKey("CTRL", "", isModifier = true, isToggle = true),
                    ExtraKey("ALT", "", isModifier = true, isToggle = true),
                    ExtraKey("TAB", "\t"),
                    ExtraKey("|", "|"),
                    ExtraKey("/", "/"),
                    ExtraKey("-", "-"),
                    ExtraKey("~", "~"),
                ),
                isCtrlActive = isCtrlActive, isAltActive = isAltActive, onKeyPress = sendKey
            )
            // Row 2: Navigation
            ExtraKeysRow(
                keys = listOf(
                    ExtraKey("↑", "\u001B[A"), ExtraKey("↓", "\u001B[B"),
                    ExtraKey("←", "\u001B[D"), ExtraKey("→", "\u001B[C"),
                    ExtraKey("HOME", "\u001B[H"), ExtraKey("END", "\u001B[F"),
                    ExtraKey("PGUP", "\u001B[5~"), ExtraKey("PGDN", "\u001B[6~"),
                ),
                isCtrlActive = isCtrlActive, isAltActive = isAltActive, onKeyPress = sendKey
            )
            // Row 3: F1-F8
            ExtraKeysRow(
                keys = listOf(
                    ExtraKey("F1", "\u001BOP"), ExtraKey("F2", "\u001BOQ"),
                    ExtraKey("F3", "\u001BOR"), ExtraKey("F4", "\u001BOS"),
                    ExtraKey("F5", "\u001B[15~"), ExtraKey("F6", "\u001B[17~"),
                    ExtraKey("F7", "\u001B[18~"), ExtraKey("F8", "\u001B[19~"),
                ),
                isCtrlActive = isCtrlActive, isAltActive = isAltActive, onKeyPress = sendKey
            )
            // Row 4: F9-F12 + special
            ExtraKeysRow(
                keys = listOf(
                    ExtraKey("F9", "\u001B[20~"), ExtraKey("F10", "\u001B[21~"),
                    ExtraKey("F11", "\u001B[23~"), ExtraKey("F12", "\u001B[24~"),
                    ExtraKey("INS", "\u001B[2~"), ExtraKey("DEL", "\u001B[3~"),
                    ExtraKey("\\", "\\"), ExtraKey("`", "`"),
                ),
                isCtrlActive = isCtrlActive, isAltActive = isAltActive, onKeyPress = sendKey
            )
            // Row 5: Brackets & symbols
            ExtraKeysRow(
                keys = listOf(
                    ExtraKey("{", "{"), ExtraKey("}", "}"), ExtraKey("[", "["), ExtraKey("]", "]"),
                    ExtraKey("(", "("), ExtraKey(")", ")"), ExtraKey("<", "<"), ExtraKey(">", ">"),
                    ExtraKey("&", "&"), ExtraKey(";", ";"),
                ),
                isCtrlActive = isCtrlActive, isAltActive = isAltActive, onKeyPress = sendKey
            )
            // Row 6: Operators
            ExtraKeysRow(
                keys = listOf(
                    ExtraKey("=", "="), ExtraKey("+", "+"), ExtraKey("*", "*"), ExtraKey("#", "#"),
                    ExtraKey("@", "@"), ExtraKey("$", "$"), ExtraKey("%", "%"), ExtraKey("^", "^"),
                    ExtraKey("!", "!"), ExtraKey("?", "?"),
                ),
                isCtrlActive = isCtrlActive, isAltActive = isAltActive, onKeyPress = sendKey
            )
            // Row 7: Ctrl combos
            ExtraKeysRow(
                keys = listOf(
                    ExtraKey("C-c", "\u0003", isCtrlCombo = true),
                    ExtraKey("C-d", "\u0004", isCtrlCombo = true),
                    ExtraKey("C-z", "\u001A", isCtrlCombo = true),
                    ExtraKey("C-l", "\u000C", isCtrlCombo = true),
                    ExtraKey("C-a", "\u0001", isCtrlCombo = true),
                    ExtraKey("C-e", "\u0005", isCtrlCombo = true),
                    ExtraKey("C-r", "\u0012", isCtrlCombo = true),
                    ExtraKey("C-w", "\u0017", isCtrlCombo = true),
                ),
                isCtrlActive = isCtrlActive, isAltActive = isAltActive, onKeyPress = sendKey
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Extra Keys Components — Neon Glass Edition
// ═══════════════════════════════════════════════════════════════

data class ExtraKey(
    val label: String,
    val value: String,
    val isModifier: Boolean = false,
    val isToggle: Boolean = false,
    val isCtrlCombo: Boolean = false
)

@Composable
fun ExtraKeysRow(
    keys: List<ExtraKey>,
    isCtrlActive: Boolean,
    isAltActive: Boolean,
    onKeyPress: (ExtraKey) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 3.dp, vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        items(keys) { key ->
            val isActive = (key.label == "CTRL" && isCtrlActive) ||
                          (key.label == "ALT" && isAltActive)

            val bgColor = when {
                isActive -> Color(0xFF00D4FF).copy(alpha = 0.25f)
                key.isModifier -> Color(0xFF151520)
                key.isCtrlCombo -> Color(0xFF1A1015)
                else -> Color(0xFF10101A)
            }

            val borderColor = when {
                isActive -> Color(0xFF00D4FF).copy(alpha = 0.6f)
                key.isModifier -> Color(0xFF39FF14).copy(alpha = 0.2f)
                key.isCtrlCombo -> Color(0xFFFF3B5C).copy(alpha = 0.2f)
                else -> Color(0xFF39FF14).copy(alpha = 0.08f)
            }

            val textColor = when {
                isActive -> Color(0xFF00D4FF)
                key.isCtrlCombo -> Color(0xFFFF3B5C).copy(alpha = 0.9f)
                key.isModifier -> Color(0xFF39FF14).copy(alpha = 0.8f)
                else -> Color(0xFF39FF14).copy(alpha = 0.65f)
            }

            Surface(
                onClick = { onKeyPress(key) },
                shape = RoundedCornerShape(5.dp),
                color = bgColor,
                border = BorderStroke(0.5.dp, borderColor),
                modifier = Modifier
                    .height(30.dp)
                    .widthIn(min = 34.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 6.dp)
                ) {
                    Text(
                        text = key.label,
                        style = ElysiumTheme.typography.codeSmall.copy(fontSize = 10.sp),
                        color = textColor,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
