package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

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

val EmeraldMint = Color(0xFF8AC492)
val DeepEmeraldContainer = Color(0xFF1B271D)

val ElectricCyan = Color(0xFF8AA9C7)
val CyanGlow = Color(0xFFA8C2DA)
val DeepCyanContainer = Color(0xFF192430)

val CoralPeak = Color(0xFFEC8C80)
val CobaltCut = Color(0xFF8AA9C7)

val TextPrimary = Color(0xFFF2EFEA)
val TextSecondary = Color(0xFFCEC9C2)
val TextMuted = Color(0xFFA8A49D)

/** Computes WCAG 2.1 contrast ratio between two opaque colors. */
fun wcagContrastRatio(foreground: Color, background: Color): Float {
    val l1 = foreground.luminance()
    val l2 = background.luminance()
    val lighter = max(l1, l2)
    val darker = min(l1, l2)
    return (lighter + 0.05f) / (darker + 0.05f)
}

/**
 * Guarantees at least [minRatio] (default 4.5:1 WCAG AA) contrast for text/icon
 * foreground against [background], gently shifting toward white/black if needed.
 */
fun ensureAccessibleContrast(
    foreground: Color,
    background: Color,
    isDark: Boolean,
    minRatio: Float = 4.5f
): Color {
    if (wcagContrastRatio(foreground, background) >= minRatio) return foreground
    val target = if (isDark) Color(0xFFF7F5F2) else Color(0xFF141311)
    var low = 0f
    var high = 1f
    var best = target
    repeat(8) {
        val mid = (low + high) / 2f
        val candidate = lerp(foreground, target, mid)
        if (wcagContrastRatio(candidate, background) >= minRatio) {
            best = candidate
            high = mid
        } else {
            low = mid
        }
    }
    return best
}

fun buildDynamicHarmoniPalette(
    isDark: Boolean,
    scheme: ColorScheme
): HarmoniPalette {
    val canvas = scheme.background
    val surface = scheme.surface
    val surfaceElevated = scheme.surfaceContainerHigh
    val controlWell = scheme.surfaceContainerLowest
    val faderTrack = scheme.surfaceVariant
    val primaryText = ensureAccessibleContrast(scheme.onSurface, surfaceElevated, isDark, 7.0f)
    val secondaryText = ensureAccessibleContrast(scheme.onSurfaceVariant, surfaceElevated, isDark, 5.2f)
    val mutedText = ensureAccessibleContrast(
        lerp(scheme.onSurfaceVariant, surface, 0.14f),
        surfaceElevated,
        isDark,
        4.6f
    )
    val accent = ensureAccessibleContrast(scheme.primary, surfaceElevated, isDark, 4.6f)
    val onAccent = ensureAccessibleContrast(scheme.onPrimary, accent, !isDark, 4.6f)
    val secondaryAccent = ensureAccessibleContrast(scheme.tertiary, surfaceElevated, isDark, 4.6f)
    val success = ensureAccessibleContrast(
        if (isDark) Color(0xFF8AC492) else Color(0xFF275D32),
        surfaceElevated,
        isDark,
        4.6f
    )
    val warning = ensureAccessibleContrast(
        if (isDark) Color(0xFFE2B264) else Color(0xFF7A4D09),
        surfaceElevated,
        isDark,
        4.6f
    )
    val danger = ensureAccessibleContrast(scheme.error, surfaceElevated, isDark, 4.6f)

    return HarmoniPalette(
        isDark = isDark,
        canvas = canvas,
        surface = surface,
        surfaceElevated = surfaceElevated,
        controlWell = controlWell,
        faderTrack = faderTrack,
        nearSolidChrome = scheme.surfaceContainer,
        border = scheme.outline.copy(alpha = 0.36f),
        borderSubtle = scheme.outlineVariant.copy(alpha = 0.55f),
        textPrimary = primaryText,
        textSecondary = secondaryText,
        textMuted = mutedText,
        accent = accent,
        onAccent = onAccent,
        accentContainer = scheme.primaryContainer,
        radialSpot = accent,
        channelSecondary = secondaryAccent,
        statusSuccess = success,
        statusWarning = warning,
        statusDanger = danger
    )
}

fun buildHarmoniPalette(
    isDark: Boolean,
    accentPreset: AccentPreset,
    customHex: String
): HarmoniPalette {
    val rawAccent = if (accentPreset == AccentPreset.CUSTOM) {
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
        val surfaceElevated = Color(0xFF1F232A)
        val accent = ensureAccessibleContrast(rawAccent, surfaceElevated, isDark = true, minRatio = 4.6f)
        HarmoniPalette(
            isDark = true,
            canvas = Color(0xFF0E1013),
            surface = Color(0xFF16191E),
            surfaceElevated = surfaceElevated,
            controlWell = Color(0xFF12151A),
            faderTrack = Color(0xFF2A303A),
            nearSolidChrome = Color(0xFF171A1F),
            border = Color.White.copy(alpha = 0.16f),
            borderSubtle = Color.White.copy(alpha = 0.09f),
            textPrimary = Color(0xFFF2EFEA),
            textSecondary = Color(0xFFCEC9C2),
            textMuted = Color(0xFFA8A49D),
            accent = accent,
            onAccent = Color(0xFF14110E),
            accentContainer = accent.copy(alpha = 0.18f),
            radialSpot = spot,
            channelSecondary = Color(0xFF9BB4C7),
            statusSuccess = Color(0xFF8AC492),
            statusWarning = Color(0xFFE2B264),
            statusDanger = Color(0xFFEC8C80)
        )
    } else {
        val controlWell = Color(0xFFECE8E2)
        val accent = ensureAccessibleContrast(rawAccent, controlWell, isDark = false, minRatio = 4.6f)
        HarmoniPalette(
            isDark = false,
            canvas = Color(0xFFF5F2EE),
            surface = Color(0xFFFAF8F5),
            surfaceElevated = Color(0xFFFFFFFF),
            controlWell = controlWell,
            faderTrack = Color(0xFFD7D0C7),
            nearSolidChrome = Color(0xFFFAF8F5),
            border = Color(0xFF1A1917).copy(alpha = 0.18f),
            borderSubtle = Color(0xFF1A1917).copy(alpha = 0.10f),
            textPrimary = Color(0xFF1A1917),
            textSecondary = Color(0xFF423F3A),
            textMuted = Color(0xFF57534D),
            accent = accent,
            onAccent = Color(0xFFFAF8F5),
            accentContainer = accent.copy(alpha = 0.14f),
            radialSpot = spot,
            channelSecondary = Color(0xFF3A5770),
            statusSuccess = Color(0xFF275D32),
            statusWarning = Color(0xFF7A4D09),
            statusDanger = Color(0xFF9A2D22)
        )
    }
}
