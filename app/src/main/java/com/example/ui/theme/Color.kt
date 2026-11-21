package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Palet netral hangat/dingin (zero purple-to-blue gradient, bebas neon)
val ObsidianBg = Color(0xFF0E1013)
val CarbonDeck = Color(0xFF14171C)
val ConsoleSurface = Color(0xFF16191E)
val ElevatedModule = Color(0xFF1F232A)
val ControlWell = Color(0xFF111418)
val BezelBorder = Color(0xFF2B3038)
val SubtleGridLine = Color(0xFF22272F)

// Aksen tunggal default (Amber hangat low-saturation) & kompatibilitas nama lama
val AmberGold = Color(0xFFD48B57)
val DeepAmberContainer = Color(0xFF2C2017)

val EmeraldMint = Color(0xFF7FA385)      // Sage low-saturation
val DeepEmeraldContainer = Color(0xFF1B271D)

val ElectricCyan = Color(0xFF8AA9C7)     // Slate low-saturation untuk kanal R saat unlinked
val CyanGlow = Color(0xFFA8C2DA)
val DeepCyanContainer = Color(0xFF192430)

val CoralPeak = Color(0xFFD9786C)        // Bahaya / clipping muted
val CobaltCut = Color(0xFF8AA9C7)

val TextPrimary = Color(0xFFF2EFEA)
val TextSecondary = Color(0xFFC4C0B9)
val TextMuted = Color(0xFF96928B)

fun buildHarmoniPalette(
    isDark: Boolean,
    accentPreset: AccentPreset,
    customHex: String
): HarmoniPalette {
    val accent = if (accentPreset == AccentPreset.CUSTOM) {
        resolveSafeCustomAccent(customHex, isDark)
    } else {
        if (isDark) accentPreset.darkColor else accentPreset.lightColor
    }
    val spot = if (accentPreset == AccentPreset.CUSTOM) {
        resolveSafeCustomAccent(customHex, isDark = true)
    } else {
        accentPreset.spotColor
    }

    return if (isDark) {
        HarmoniPalette(
            isDark = true,
            canvas = Color(0xFF0E1013),
            surface = Color(0xFF16191E),
            surfaceElevated = Color(0xFF1F232A),
            controlWell = Color(0xFF12151A),
            faderTrack = Color(0xFF262B33),
            nearSolidChrome = Color(0xFF171A1F),
            border = Color.White.copy(alpha = 0.14f),
            borderSubtle = Color.White.copy(alpha = 0.08f),
            textPrimary = Color(0xFFF2EFEA),
            textSecondary = Color(0xFFC4C0B9),
            textMuted = Color(0xFF96928B),
            accent = accent,
            onAccent = Color(0xFF14110E),
            accentContainer = accent.copy(alpha = 0.16f),
            radialSpot = spot,
            channelSecondary = Color(0xFF94A8B8),
            statusSuccess = Color(0xFF7FA385),
            statusWarning = Color(0xFFD4A257),
            statusDanger = Color(0xFFD9786C)
        )
    } else {
        HarmoniPalette(
            isDark = false,
            canvas = Color(0xFFF5F2EE),
            surface = Color(0xFFFAF8F5),
            surfaceElevated = Color(0xFFFFFFFF),
            controlWell = Color(0xFFECE8E2),
            faderTrack = Color(0xFFDCD6CE),
            nearSolidChrome = Color(0xFFFAF8F5),
            border = Color(0xFF1A1917).copy(alpha = 0.14f),
            borderSubtle = Color(0xFF1A1917).copy(alpha = 0.08f),
            textPrimary = Color(0xFF1A1917),
            textSecondary = Color(0xFF4A4742),
            textMuted = Color(0xFF66625C),
            accent = accent,
            onAccent = Color(0xFFFAF8F5),
            accentContainer = accent.copy(alpha = 0.12f),
            radialSpot = spot,
            channelSecondary = Color(0xFF49647A),
            statusSuccess = Color(0xFF3E6B47),
            statusWarning = Color(0xFF8C5B12),
            statusDanger = Color(0xFFA33A2E)
        )
    }
}
