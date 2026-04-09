package com.elysium.code.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ═══════════════════════════════════════════════════════════════
// ELYSIUM COLOR SYSTEM — Cyberpunk Dark with Neon Accents
// ═══════════════════════════════════════════════════════════════

data class ElysiumColors(
    // Core
    val background: Color = Color(0xFF000000),
    val surface: Color = Color(0xFF0A0A0F),
    val surfaceElevated: Color = Color(0xFF12121A),
    val surfaceCard: Color = Color(0xFF1A1A25),
    val surfaceBright: Color = Color(0xFF222230),
    
    // Primary — Elysium Cyan
    val primary: Color = Color(0xFF00D4FF),
    val primaryDim: Color = Color(0xFF0099BB),
    val primaryGlow: Color = Color(0x4000D4FF),
    
    // Secondary — Neon Green (success/terminal)
    val secondary: Color = Color(0xFF39FF14),
    val secondaryDim: Color = Color(0xFF2BC20E),
    val secondaryGlow: Color = Color(0x4039FF14),
    
    // Accent — Electric Purple
    val accent: Color = Color(0xFF7C3AED),
    val accentBright: Color = Color(0xFFA855F7),
    val accentGlow: Color = Color(0x407C3AED),
    
    // Semantic
    val error: Color = Color(0xFFFF3B5C),
    val warning: Color = Color(0xFFFFB020),
    val success: Color = Color(0xFF39FF14),
    val info: Color = Color(0xFF00D4FF),
    
    // Text
    val textPrimary: Color = Color(0xFFE8E8F0),
    val textSecondary: Color = Color(0xFF9898A8),
    val textTertiary: Color = Color(0xFF585868),
    val textCode: Color = Color(0xFF39FF14),
    
    // Terminal
    val terminalBg: Color = Color(0xFF050510),
    val terminalFg: Color = Color(0xFF39FF14),
    val terminalCursor: Color = Color(0xFF00D4FF),
    val terminalSelection: Color = Color(0x4000D4FF),
    
    // Editor
    val editorBg: Color = Color(0xFF0D0D18),
    val editorLineNumber: Color = Color(0xFF3A3A4A),
    val editorCurrentLine: Color = Color(0xFF151525),
    val editorSelection: Color = Color(0x407C3AED),
    
    // Borders & Dividers
    val border: Color = Color(0xFF252535),
    val borderFocused: Color = Color(0xFF00D4FF),
    val divider: Color = Color(0xFF1A1A28),
)

// ═══════════════════════════════════════════════════════════════
// TYPOGRAPHY SYSTEM
// ═══════════════════════════════════════════════════════════════

val JetBrainsMono = FontFamily.Monospace // will be replaced with actual font
val InterFont = FontFamily.SansSerif     // will be replaced with actual font

data class ElysiumTypography(
    // Display
    val displayLarge: TextStyle = TextStyle(
        fontFamily = InterFont,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        letterSpacing = (-0.5).sp,
        lineHeight = 40.sp
    ),
    val displayMedium: TextStyle = TextStyle(
        fontFamily = InterFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        letterSpacing = (-0.3).sp,
        lineHeight = 32.sp
    ),
    
    // Headlines
    val headlineLarge: TextStyle = TextStyle(
        fontFamily = InterFont,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    val headlineMedium: TextStyle = TextStyle(
        fontFamily = InterFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    
    // Body
    val bodyLarge: TextStyle = TextStyle(
        fontFamily = InterFont,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    val bodyMedium: TextStyle = TextStyle(
        fontFamily = InterFont,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    val bodySmall: TextStyle = TextStyle(
        fontFamily = InterFont,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    
    // Labels
    val labelLarge: TextStyle = TextStyle(
        fontFamily = InterFont,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        letterSpacing = 0.1.sp,
        lineHeight = 20.sp
    ),
    val labelSmall: TextStyle = TextStyle(
        fontFamily = InterFont,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.5.sp,
        lineHeight = 16.sp
    ),
    
    // Code
    val codeLarge: TextStyle = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    val codeMedium: TextStyle = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    val codeSmall: TextStyle = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp
    ),
    
    // Terminal
    val terminal: TextStyle = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp
    )
)

// ═══════════════════════════════════════════════════════════════
// THEME COMPOSITION
// ═══════════════════════════════════════════════════════════════

val LocalElysiumColors = staticCompositionLocalOf { ElysiumColors() }
val LocalElysiumTypography = staticCompositionLocalOf { ElysiumTypography() }

object ElysiumTheme {
    val colors: ElysiumColors
        @Composable
        @ReadOnlyComposable
        get() = LocalElysiumColors.current

    val typography: ElysiumTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalElysiumTypography.current
}

@Composable
fun ElysiumTheme(
    content: @Composable () -> Unit
) {
    val colors = ElysiumColors()
    val typography = ElysiumTypography()

    CompositionLocalProvider(
        LocalElysiumColors provides colors,
        LocalElysiumTypography provides typography,
    ) {
        content()
    }
}
