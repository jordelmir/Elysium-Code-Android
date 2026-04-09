package com.elysium.code.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.elysium.code.ui.components.NeonActivityBar
import com.elysium.code.ui.screens.*
import com.elysium.code.ui.theme.ElysiumTheme
import kotlin.math.sin

/**
 * ═══════════════════════════════════════════════════════════════
 * ELYSIUM CODE — Navigation Host (Neon Edition)
 * ═══════════════════════════════════════════════════════════════
 *
 * Futuristic bottom navigation with neon glow indicators,
 * animated icon transitions, and living light effects.
 */

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val iconSelected: ImageVector,
    val glowColor: Color
) {
    data object Terminal : Screen("terminal", "Terminal", Icons.Outlined.Terminal, Icons.Filled.Terminal, Color(0xFF39FF14))
    data object Editor : Screen("editor", "Editor", Icons.Outlined.Code, Icons.Filled.Code, Color(0xFF7C3AED))
    data object Chat : Screen("chat", "AI Chat", Icons.Outlined.SmartToy, Icons.Filled.SmartToy, Color(0xFF00D4FF))
    data object Settings : Screen("settings", "Settings", Icons.Outlined.Settings, Icons.Filled.Settings, Color(0xFFFF00FF))
}

val screens = listOf(Screen.Terminal, Screen.Editor, Screen.Chat, Screen.Settings)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElysiumNavHost(viewModel: com.elysium.code.viewmodel.MainViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Ambient glow animation
    val infiniteTransition = rememberInfiniteTransition(label = "navGlow")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "navPulse"
    )

    Scaffold(
        containerColor = ElysiumTheme.colors.background,
        bottomBar = {
            Column {
                // Neon activity line on top of nav bar
                NeonActivityBar(
                    isActive = true,
                    color = screens.find { currentDestination?.hierarchy?.any { d -> d.route == it.route } == true }?.glowColor
                        ?: ElysiumTheme.colors.primary
                )

                val themePrimary = ElysiumTheme.colors.primary
                val themeAccent = ElysiumTheme.colors.accent
                Box {
                    // Glow canvas behind nav bar
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                    ) {
                        val selectedScreen = screens.find { s ->
                            currentDestination?.hierarchy?.any { it.route == s.route } == true
                        }

                        if (selectedScreen != null) {
                            val selectedIndex = screens.indexOf(selectedScreen)
                            val segmentWidth = size.width / screens.size
                            val centerX = segmentWidth * selectedIndex + segmentWidth / 2

                            // Glow behind selected icon
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        selectedScreen.glowColor.copy(alpha = 0.2f * glowPulse),
                                        selectedScreen.glowColor.copy(alpha = 0.05f * glowPulse),
                                        Color.Transparent
                                    ),
                                    center = Offset(centerX, 0f),
                                    radius = segmentWidth * 0.8f
                                ),
                                center = Offset(centerX, 0f),
                                radius = segmentWidth * 0.8f
                            )
                        }

                        // Subtle top edge glow
                        drawLine(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    themePrimary.copy(alpha = 0.15f),
                                    themeAccent.copy(alpha = 0.15f),
                                    Color.Transparent
                                )
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(size.width, 0f),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    // Actual navigation bar
                    NavigationBar(
                        containerColor = ElysiumTheme.colors.surface.copy(alpha = 0.95f),
                        contentColor = ElysiumTheme.colors.textPrimary,
                        tonalElevation = 0.dp,
                        modifier = Modifier.height(64.dp)
                    ) {
                        screens.forEach { screen ->
                            val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Box(contentAlignment = Alignment.Center) {
                                        // Glow behind selected icon
                                        if (selected) {
                                            Canvas(modifier = Modifier.size(36.dp)) {
                                                drawCircle(
                                                    color = screen.glowColor.copy(alpha = 0.3f * glowPulse),
                                                    radius = size.minDimension / 2
                                                )
                                            }
                                        }
                                        Icon(
                                            imageVector = if (selected) screen.iconSelected else screen.icon,
                                            contentDescription = screen.title,
                                            modifier = Modifier.size(22.dp),
                                            tint = if (selected) screen.glowColor else ElysiumTheme.colors.textTertiary
                                        )
                                    }
                                },
                                label = {
                                    Text(
                                        text = screen.title,
                                        style = ElysiumTheme.typography.labelSmall,
                                        color = if (selected) screen.glowColor else ElysiumTheme.colors.textTertiary
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = screen.glowColor,
                                    selectedTextColor = screen.glowColor,
                                    unselectedIconColor = ElysiumTheme.colors.textTertiary,
                                    unselectedTextColor = ElysiumTheme.colors.textTertiary,
                                    indicatorColor = screen.glowColor.copy(alpha = 0.1f)
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Chat.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(ElysiumTheme.colors.background),
            enterTransition = { fadeIn(animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) }
        ) {
            composable(Screen.Terminal.route) {
                EnhancedTerminalScreen(viewModel = viewModel)
            }
            composable(Screen.Editor.route) {
                EnhancedEditorScreen()
            }
            composable(Screen.Chat.route) {
                EnhancedChatScreen(viewModel = viewModel)
            }
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
        }
    }
}
