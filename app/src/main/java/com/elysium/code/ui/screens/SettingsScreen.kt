package com.elysium.code.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.elysium.code.ui.theme.ElysiumTheme

/**
 * ═══════════════════════════════════════════════════════════════
 * ELYSIUM CODE — Settings Screen
 * ═══════════════════════════════════════════════════════════════
 *
 * Configuration hub for:
 * - Model management
 * - Personality selection
 * - Skills management
 * - MCP server configuration
 * - Plugin management
 * - Theme customization
 * - Inference parameters
 * - Memory management
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    var selectedPersonality by remember { mutableStateOf("architect") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ElysiumTheme.colors.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // ═══ Header ═══
        item {
            Text(
                "Settings",
                style = ElysiumTheme.typography.displayMedium,
                color = ElysiumTheme.colors.textPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Configure your AI coding companion",
                style = ElysiumTheme.typography.bodyMedium,
                color = ElysiumTheme.colors.textTertiary
            )
            Spacer(Modifier.height(16.dp))
        }

        // ═══ Model Status ═══
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ElysiumTheme.colors.surfaceCard,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(ElysiumTheme.colors.primary, ElysiumTheme.colors.accent)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Memory, null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Gemma 4 E4B", style = ElysiumTheme.typography.headlineMedium, color = ElysiumTheme.colors.textPrimary)
                            Text("Q4_K_M • Embedded • Ready", style = ElysiumTheme.typography.bodySmall, color = ElysiumTheme.colors.secondary)
                        }
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(ElysiumTheme.colors.secondary)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatChip("Context", "4K tokens")
                        StatChip("Threads", "4")
                        StatChip("RAM", "~1.5 GB")
                        StatChip("Speed", "~10 tok/s")
                    }
                }
            }
        }

        // ═══ Personality ═══
        item {
            SectionHeader("Personality", Icons.Outlined.Face)
        }
        item {
            PersonalitySelector(selectedPersonality) { selectedPersonality = it }
        }

        // ═══ Memory ═══
        item {
            SectionHeader("Memory & Knowledge", Icons.Outlined.Psychology)
        }
        item {
            SettingsCard {
                SettingsRow(Icons.Outlined.Storage, "Knowledge Items", "0 recorded", ElysiumTheme.colors.accent) {}
                HorizontalDivider(color = ElysiumTheme.colors.divider)
                SettingsRow(Icons.Outlined.AccountTree, "Task Patterns", "0 learned", ElysiumTheme.colors.primary) {}
                HorizontalDivider(color = ElysiumTheme.colors.divider)
                SettingsRow(Icons.Outlined.BugReport, "Error Solutions", "0 cataloged", ElysiumTheme.colors.error) {}
                HorizontalDivider(color = ElysiumTheme.colors.divider)
                SettingsRow(Icons.Outlined.Delete, "Clear All Memory", "Reset agent knowledge", ElysiumTheme.colors.textTertiary) {}
            }
        }

        // ═══ Skills ═══
        item {
            SectionHeader("Skills", Icons.Outlined.AutoAwesome)
        }
        item {
            SettingsCard {
                SettingsToggleRow("Code Review", "Thorough code review", true) {}
                HorizontalDivider(color = ElysiumTheme.colors.divider)
                SettingsToggleRow("Refactor", "Intelligent refactoring", true) {}
                HorizontalDivider(color = ElysiumTheme.colors.divider)
                SettingsToggleRow("Test Writer", "Generate test suites", true) {}
                HorizontalDivider(color = ElysiumTheme.colors.divider)
                SettingsToggleRow("Doc Generator", "Auto documentation", true) {}
                HorizontalDivider(color = ElysiumTheme.colors.divider)
                SettingsToggleRow("Performance", "Optimization analysis", true) {}
                HorizontalDivider(color = ElysiumTheme.colors.divider)
                SettingsToggleRow("Android Expert", "Deep Android knowledge", true) {}
                HorizontalDivider(color = ElysiumTheme.colors.divider)
                SettingsRow(Icons.Outlined.Add, "Import Skill (.md)", "Add custom skills", ElysiumTheme.colors.primary) {}
            }
        }

        // ═══ MCP Servers ═══
        item {
            SectionHeader("MCP Servers", Icons.Outlined.Dns)
        }
        item {
            SettingsCard {
                SettingsRow(Icons.Outlined.Add, "Add MCP Server", "Connect external tools", ElysiumTheme.colors.primary) {}
                HorizontalDivider(color = ElysiumTheme.colors.divider)
                SettingsRow(Icons.Outlined.Info, "No servers configured", "Add servers via JSON config", ElysiumTheme.colors.textTertiary) {}
            }
        }

        // ═══ Plugins ═══
        item {
            SectionHeader("Plugins", Icons.Outlined.Extension)
        }
        item {
            SettingsCard {
                SettingsRow(Icons.Outlined.Add, "Install Plugin", "Add from directory", ElysiumTheme.colors.primary) {}
                HorizontalDivider(color = ElysiumTheme.colors.divider)
                SettingsRow(Icons.Outlined.CreateNewFolder, "Create Plugin", "Scaffold new plugin", ElysiumTheme.colors.secondary) {}
            }
        }

        // ═══ Inference ═══
        item {
            SectionHeader("Inference Parameters", Icons.Outlined.Tune)
        }
        item {
            SettingsCard {
                SliderRow("Temperature", 0.7f, 0f, 2f) {}
                HorizontalDivider(color = ElysiumTheme.colors.divider)
                SliderRow("Top P", 0.95f, 0f, 1f) {}
                HorizontalDivider(color = ElysiumTheme.colors.divider)
                SliderRow("Top K", 40f, 1f, 100f) {}
                HorizontalDivider(color = ElysiumTheme.colors.divider)
                SliderRow("Max Tokens", 2048f, 256f, 8192f) {}
                HorizontalDivider(color = ElysiumTheme.colors.divider)
                SliderRow("Context Size", 4096f, 1024f, 131072f) {}
            }
        }

        // ═══ Theme ═══
        item {
            SectionHeader("Appearance", Icons.Outlined.Palette)
        }
        item {
            SettingsCard {
                SettingsRow(Icons.Outlined.DarkMode, "Theme", "Elysium Dark", ElysiumTheme.colors.textSecondary) {}
                HorizontalDivider(color = ElysiumTheme.colors.divider)
                SettingsRow(Icons.Outlined.TextFields, "Editor Font", "Monospace", ElysiumTheme.colors.textSecondary) {}
                HorizontalDivider(color = ElysiumTheme.colors.divider)
                SettingsRow(Icons.Outlined.FormatSize, "Font Size", "13sp", ElysiumTheme.colors.textSecondary) {}
            }
        }

        // ═══ About ═══
        item {
            SectionHeader("About", Icons.Outlined.Info)
        }
        item {
            SettingsCard {
                SettingsRow(Icons.Outlined.Smartphone, "Elysium Code", "v1.0.0-alpha", ElysiumTheme.colors.primary) {}
                HorizontalDivider(color = ElysiumTheme.colors.divider)
                SettingsRow(Icons.Outlined.Memory, "Model", "Gemma 4 E4B (Q4_K_M)", ElysiumTheme.colors.textSecondary) {}
                HorizontalDivider(color = ElysiumTheme.colors.divider)
                SettingsRow(Icons.Outlined.Code, "Engine", "llama.cpp", ElysiumTheme.colors.textSecondary) {}
                HorizontalDivider(color = ElysiumTheme.colors.divider)
                SettingsRow(Icons.Outlined.Shield, "License", "Apache 2.0", ElysiumTheme.colors.textSecondary) {}
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Settings Components
// ═══════════════════════════════════════════════════════════════

@Composable
fun SectionHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    ) {
        Icon(icon, null, tint = ElysiumTheme.colors.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, style = ElysiumTheme.typography.headlineMedium, color = ElysiumTheme.colors.textPrimary)
    }
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = ElysiumTheme.colors.surfaceCard,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(4.dp), content = content)
    }
}

@Composable
fun SettingsRow(icon: ImageVector, title: String, subtitle: String, iconColor: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = ElysiumTheme.typography.bodyMedium, color = ElysiumTheme.colors.textPrimary)
            Text(subtitle, style = ElysiumTheme.typography.bodySmall, color = ElysiumTheme.colors.textTertiary)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = ElysiumTheme.colors.textTertiary, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun SettingsToggleRow(title: String, subtitle: String, isChecked: Boolean, onToggle: (Boolean) -> Unit) {
    var checked by remember { mutableStateOf(isChecked) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { checked = !checked; onToggle(checked) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = ElysiumTheme.typography.bodyMedium, color = ElysiumTheme.colors.textPrimary)
            Text(subtitle, style = ElysiumTheme.typography.bodySmall, color = ElysiumTheme.colors.textTertiary)
        }
        Switch(
            checked = checked,
            onCheckedChange = { checked = it; onToggle(it) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = ElysiumTheme.colors.primary,
                checkedTrackColor = ElysiumTheme.colors.primaryGlow,
                uncheckedThumbColor = ElysiumTheme.colors.textTertiary,
                uncheckedTrackColor = ElysiumTheme.colors.surfaceBright
            )
        )
    }
}

@Composable
fun SliderRow(label: String, initialValue: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    var value by remember { mutableFloatStateOf(initialValue) }
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = ElysiumTheme.typography.bodyMedium, color = ElysiumTheme.colors.textPrimary)
            Text(
                if (value == value.toLong().toFloat()) "${value.toLong()}" else "%.2f".format(value),
                style = ElysiumTheme.typography.codeMedium,
                color = ElysiumTheme.colors.primary
            )
        }
        Slider(
            value = value,
            onValueChange = { value = it; onChange(it) },
            valueRange = min..max,
            colors = SliderDefaults.colors(
                thumbColor = ElysiumTheme.colors.primary,
                activeTrackColor = ElysiumTheme.colors.primary,
                inactiveTrackColor = ElysiumTheme.colors.surfaceBright
            )
        )
    }
}

@Composable
fun StatChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = ElysiumTheme.typography.labelLarge, color = ElysiumTheme.colors.textPrimary)
        Text(label, style = ElysiumTheme.typography.labelSmall, color = ElysiumTheme.colors.textTertiary)
    }
}

@Composable
fun PersonalitySelector(selected: String, onSelect: (String) -> Unit) {
    val personalities = listOf(
        Triple("architect", "🏗️", "Architect"),
        Triple("debugger", "🔍", "Debugger"),
        Triple("mentor", "🎓", "Mentor"),
        Triple("speed_coder", "⚡", "Speed Coder"),
        Triple("security_auditor", "🛡️", "Security"),
        Triple("fullstack", "🌐", "Full-Stack")
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        personalities.forEach { (id, emoji, name) ->
            val isSelected = id == selected
            Surface(
                onClick = { onSelect(id) },
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) ElysiumTheme.colors.primary.copy(alpha = 0.15f)
                        else ElysiumTheme.colors.surfaceCard,
                border = if (isSelected) BorderStroke(1.dp, ElysiumTheme.colors.primary)
                         else null,
                modifier = Modifier.width(90.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(emoji, style = ElysiumTheme.typography.displayMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        name,
                        style = ElysiumTheme.typography.labelSmall,
                        color = if (isSelected) ElysiumTheme.colors.primary
                                else ElysiumTheme.colors.textSecondary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
