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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elysium.code.agent.AgentState
import com.elysium.code.agent.ChatMessage
import com.elysium.code.agent.MessageRole
import com.elysium.code.viewmodel.MainViewModel
import com.elysium.code.ui.theme.ElysiumTheme
import kotlinx.coroutines.launch

/**
 * ═══════════════════════════════════════════════════════════════
 * ELYSIUM CODE — Enhanced Chat Screen (Neon Edition)
 * ═══════════════════════════════════════════════════════════════
 *
 * Premium AI chat interface with:
 * - Gradient-bordered message bubbles
 * - Real-time streaming indicators
 * - Agent state visualization
 * - Neon glow aesthetics
 * - Copy/paste support
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedChatScreen(viewModel: MainViewModel = viewModel()) {
    val scope = rememberCoroutineScope()

    var inputText by remember { mutableStateOf(TextFieldValue("")) }
    val agentMessages by viewModel.agentOrchestrator.messages.collectAsState(initial = emptyList<ChatMessage>())
    val agentState by viewModel.agentOrchestrator.agentState.collectAsState(initial = AgentState.IDLE)
    val currentAction by viewModel.agentOrchestrator.currentAction.collectAsState(initial = null)
    val currentResponse by viewModel.agentOrchestrator.currentResponse.collectAsState(initial = "")
    val pendingToolCall by viewModel.agentOrchestrator.pendingToolCall.collectAsState(initial = null)

    val listState = rememberLazyListState()

    // Auto-scroll to bottom on new messages
    LaunchedEffect(agentMessages.size, currentResponse) {
        if (agentMessages.isNotEmpty()) {
            listState.animateScrollToItem(agentMessages.size - 1)
        }
    }

    // Pulsing animation for status
    val infiniteTransition = rememberInfiniteTransition(label = "chatPulse")
    val statusPulse by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "statusPulse"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ElysiumTheme.colors.background)
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
                                Color(0xFF00D4FF).copy(alpha = 0.4f),
                                Color(0xFF7C3AED).copy(alpha = 0.4f),
                                Color(0xFF00D4FF).copy(alpha = 0.4f)
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
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Pulsing status indicator
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when (agentState) {
                                    AgentState.IDLE -> Color(0xFF39FF14)
                                    AgentState.THINKING -> Color(0xFF00D4FF).copy(alpha = statusPulse)
                                    AgentState.REFLECTING -> Color(0xFFFF00FF).copy(alpha = statusPulse)
                                    AgentState.GENERATING -> Color(0xFFFFB020).copy(alpha = statusPulse)
                                    AgentState.WAITING_FOR_APPROVAL -> Color(0xFF00D4FF)
                                    AgentState.EXECUTING -> Color(0xFF7C3AED).copy(alpha = statusPulse)
                                    AgentState.ERROR -> Color(0xFFFF3B5C)
                                }
                            )
                    )
                    Text(
                        "Elysium Chat",
                        style = ElysiumTheme.typography.headlineMedium,
                        color = Color(0xFF00D4FF)
                    )
                }

                Text(
                    when (agentState) {
                        AgentState.IDLE -> "✅ Ready"
                        AgentState.THINKING -> "🧠 Thinking..."
                        AgentState.REFLECTING -> "🕵️ Reflecting..."
                        AgentState.GENERATING -> "⚡ Generating..."
                        AgentState.WAITING_FOR_APPROVAL -> "⏳ Action requires approval"
                        AgentState.EXECUTING -> "🔧 ${currentAction ?: "Executing..."}"
                        AgentState.ERROR -> "❌ Error"
                    },
                    style = ElysiumTheme.typography.labelSmall,
                    color = when (agentState) {
                        AgentState.IDLE -> Color(0xFF39FF14)
                        AgentState.ERROR -> Color(0xFFFF3B5C)
                        AgentState.WAITING_FOR_APPROVAL -> Color(0xFF00D4FF)
                        AgentState.THINKING, AgentState.REFLECTING, AgentState.GENERATING, AgentState.EXECUTING -> Color(0xFFFFB020)
                    }
                )
            }
        }

        // ═══ Messages Area ═══
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Welcome message if empty
            if (agentMessages.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "⚡",
                            fontSize = 48.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Elysium AI Ready",
                            style = ElysiumTheme.typography.headlineMedium,
                            color = Color(0xFF00D4FF)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Gemma 4 E4B • Local • Private • Agentic",
                            style = ElysiumTheme.typography.labelSmall,
                            color = Color(0xFF585868)
                        )
                    }
                }
            }

            items(agentMessages) { message ->
                val isUser = message.role == MessageRole.USER
                val isTool = message.role == MessageRole.TOOL
                val isSystem = message.role == MessageRole.SYSTEM

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                ) {
                    Surface(
                        color = when {
                            isUser -> Color(0xFF7C3AED).copy(alpha = 0.2f)
                            isTool -> Color(0xFF39FF14).copy(alpha = 0.08f)
                            isSystem -> Color(0xFFFF3B5C).copy(alpha = 0.08f)
                            else -> Color(0xFF00D4FF).copy(alpha = 0.08f)
                        },
                        shape = RoundedCornerShape(
                            topStart = 12.dp,
                            topEnd = 12.dp,
                            bottomStart = if (isUser) 12.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 12.dp
                        ),
                        modifier = Modifier
                            .fillMaxWidth(0.88f)
                            .border(
                                1.dp,
                                when {
                                    isUser -> Color(0xFF7C3AED).copy(alpha = 0.4f)
                                    isTool -> Color(0xFF39FF14).copy(alpha = 0.2f)
                                    isSystem -> Color(0xFFFF3B5C).copy(alpha = 0.2f)
                                    else -> Color(0xFF00D4FF).copy(alpha = 0.2f)
                                },
                                RoundedCornerShape(
                                    topStart = 12.dp,
                                    topEnd = 12.dp,
                                    bottomStart = if (isUser) 12.dp else 4.dp,
                                    bottomEnd = if (isUser) 4.dp else 12.dp
                                )
                            )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // Role label
                            if (!isUser) {
                                Text(
                                    when {
                                        isTool -> "🔧 ${message.toolName ?: "Tool"}"
                                        isSystem -> "⚠️ System"
                                        else -> "🤖 Elysium"
                                    },
                                    style = ElysiumTheme.typography.labelSmall,
                                    color = when {
                                        isTool -> Color(0xFF39FF14)
                                        isSystem -> Color(0xFFFF3B5C)
                                        else -> Color(0xFF00D4FF)
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                            }

                            Text(
                                message.content,
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                ),
                                color = when {
                                    isUser -> Color(0xFFE8E8F0)
                                    isTool -> Color(0xFF39FF14).copy(alpha = 0.9f)
                                    isSystem -> Color(0xFFFF3B5C).copy(alpha = 0.9f)
                                    else -> Color(0xFF00D4FF).copy(alpha = 0.95f)
                                }
                            )
                        }
                    }
                }
            }

            // Streaming response indicator
            if (agentState == AgentState.GENERATING && currentResponse.isNotEmpty()) {
                item {
                    Surface(
                        color = Color(0xFF00D4FF).copy(alpha = 0.08f),
                        shape = RoundedCornerShape(12.dp, 12.dp, 12.dp, 4.dp),
                        modifier = Modifier
                            .fillMaxWidth(0.88f)
                            .border(
                                1.dp,
                                Color(0xFF00D4FF).copy(alpha = 0.3f),
                                RoundedCornerShape(12.dp, 12.dp, 12.dp, 4.dp)
                            )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "🤖 Elysium ⚡",
                                style = ElysiumTheme.typography.labelSmall,
                                color = Color(0xFF00D4FF),
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                currentResponse + "▌",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                ),
                                color = Color(0xFF00D4FF).copy(alpha = 0.95f)
                            )
                        }
                    }
                }
            }

            // Thinking indicator
            if (agentState == AgentState.THINKING || agentState == AgentState.EXECUTING) {
                item {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color(0xFF00D4FF),
                            strokeWidth = 1.5.dp
                        )
                        Text(
                            currentAction ?: "Thinking...",
                            style = ElysiumTheme.typography.labelSmall,
                            color = Color(0xFF00D4FF).copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        // ═══ Input Area ═══
        Surface(
            color = ElysiumTheme.colors.surface.copy(alpha = 0.95f),
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawLine(
                        brush = Brush.horizontalGradient(
                            listOf(
                                Color(0xFF7C3AED).copy(alpha = 0.4f),
                                Color(0xFF00D4FF).copy(alpha = 0.4f),
                                Color(0xFF7C3AED).copy(alpha = 0.4f)
                            )
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 2f
                    )
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BasicTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0A0E27).copy(alpha = 0.8f))
                        .border(
                            1.dp,
                            Color(0xFF00D4FF).copy(alpha = 0.3f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                        .heightIn(min = 40.dp, max = 120.dp),
                    textStyle = TextStyle(
                        color = Color(0xFFE8E8F0),
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    cursorBrush = SolidColor(Color(0xFF00D4FF)),
                    singleLine = false,
                    decorationBox = { innerTextField ->
                        if (inputText.text.isEmpty()) {
                            Text(
                                "Ask Elysium anything...",
                                color = Color(0xFF00D4FF).copy(alpha = 0.4f),
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        innerTextField()
                    }
                )

                // Cancel button when generating
                if (agentState != AgentState.IDLE) {
                    IconButton(
                        onClick = { viewModel.cancelGeneration() },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFF3B5C).copy(alpha = 0.15f))
                    ) {
                        Icon(
                            Icons.Filled.Stop,
                            contentDescription = "Cancel",
                            tint = Color(0xFFFF3B5C)
                        )
                    }
                }

                // Send button
                IconButton(
                    onClick = {
                        val message = inputText.text.trim()
                        if (message.isNotEmpty() && agentState == AgentState.IDLE) {
                            viewModel.sendMessage(message)
                            inputText = TextFieldValue("")
                        }
                    },
                    enabled = inputText.text.isNotEmpty() && agentState == AgentState.IDLE,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (inputText.text.isNotEmpty() && agentState == AgentState.IDLE)
                                Color(0xFF00D4FF).copy(alpha = 0.2f)
                            else
                                Color.Transparent
                        )
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (inputText.text.isNotEmpty() && agentState == AgentState.IDLE)
                            Color(0xFF00D4FF) else Color(0xFF585868)
                    )
                }
            }
        }
    }
        
    // ═══ Intent Approval Overlay ═══
        AnimatedVisibility(
            visible = agentState == AgentState.WAITING_FOR_APPROVAL && pendingToolCall != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            pendingToolCall?.let { tool ->
                IntentApprovalCard(
                    toolName = tool.name,
                    args = tool.args.toString(),
                    onApprove = { viewModel.resolvePendingToolCall(true) },
                    onReject = { viewModel.resolvePendingToolCall(false) }
                )
            }
        }
    }
}

/**
 * ═══════════════════════════════════════════════════════════════
 * IntentApprovalCard — World-Class Zero-Trust UI
 * ═══════════════════════════════════════════════════════════════
 */
@Composable
fun IntentApprovalCard(
    toolName: String,
    args: String,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .heightIn(max = 300.dp),
        color = Color(0xFF0A0E27).copy(alpha = 0.9f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            Brush.linearGradient(listOf(Color(0xFFFF3B5C), Color(0xFF7C3AED)))
        ),
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Security,
                    contentDescription = "Shield",
                    tint = Color(0xFFFF3B5C),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "INTENT VALIDATION REQUIRED",
                    style = ElysiumTheme.typography.labelSmall,
                    color = Color(0xFFFF3B5C),
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
            }
            
            Spacer(Modifier.height(12.dp))
            
            Text(
                "The agent is requesting to execute a sensitive operation:",
                style = ElysiumTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f)
            )
            
            Spacer(Modifier.height(8.dp))
            
            Surface(
                color = Color.Black.copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Tool: $toolName",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00D4FF),
                            fontSize = 12.sp
                        )
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        args,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF39FF14).copy(alpha = 0.8f),
                            fontSize = 11.sp
                        ),
                        maxLines = 5
                    )
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onReject,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF3B5C).copy(alpha = 0.15f),
                        contentColor = Color(0xFFFF3B5C)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFFFF3B5C).copy(alpha = 0.4f))
                ) {
                    Text("DENY", fontWeight = FontWeight.Bold)
                }
                
                Button(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00D4FF).copy(alpha = 0.15f),
                        contentColor = Color(0xFF00D4FF)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF00D4FF).copy(alpha = 0.4f))
                ) {
                    Text("AUTHORIZE", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
