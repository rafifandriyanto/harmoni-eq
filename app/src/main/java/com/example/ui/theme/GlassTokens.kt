package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Token desain Restrained Glassmorphism untuk Harmoni EQ.
 * Semua angka jarak mengikuti skala 4.dp dan radius seragam 20.dp.
 */
object GlassTokens {
    val CornerRadius: Dp = 20.dp
    val BorderWidth: Dp = 1.dp
    val InnerHighlightHeight: Dp = 1.dp

    // Skala spasi 4.dp
    val Space4: Dp = 4.dp
    val Space8: Dp = 8.dp
    val Space12: Dp = 12.dp
    val Space16: Dp = 16.dp
    val Space24: Dp = 24.dp
    val Space32: Dp = 32.dp

    // Fill transparan (hanya untuk UI chrome)
    const val DarkGlassAlpha: Float = 0.08f        // rentang 0.06..0.10
    const val LightGlassAlpha: Float = 0.62f       // rentang 0.55..0.70

    // Fallback API < 31 tanpa real-time blur
    const val LegacyFallbackAlpha: Float = 0.85f

    // Near-solid untuk permukaan pembawa teks (85..95%)
    const val NearSolidAlpha: Float = 0.90f

    // Reduce transparency (92..96% solid + border 1.dp)
    const val ReduceTransparencyAlpha: Float = 0.95f

    // Border & inner highlight
    const val DarkBorderAlpha: Float = 0.18f       // rentang 0.16..0.20
    const val LightBorderAlpha: Float = 0.20f
    const val InnerHighlightAlpha: Float = 0.10f

    // Pressed delta tombol utama
    const val PressedAlphaDelta: Float = 0.05f
    const val PressedBorderDelta: Float = 0.05f

    // Ambient glow tipis hanya pada tombol utama
    const val PrimaryAmbientGlowAlpha: Float = 0.08f

    // Scrim dialog (solid 40% hitam, bukan blur)
    val DialogScrim: Color = Color.Black.copy(alpha = 0.40f)

    // Blur dinamis untuk API 31+ via RenderEffect, dibatasi ke backdrop panel
    const val BlurRadiusPx: Float = 20f
    val IdleBlurRadius: Dp = 20.dp
    val DragBlurRadius: Dp = 8.dp
    const val BlurTransitionDurationMs: Int = 140
    const val IdleRestoreDelayMs: Long = 150L

    // Durasi motion minimalis (tanpa bounce)
    const val ValueOpacityDurationMs: Int = 120
    const val TopBarTransitionDurationMs: Int = 150
    const val SheetFadeDurationMs: Int = 180
}

@Immutable
data class HarmoniPalette(
    val isDark: Boolean,
    val canvas: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val controlWell: Color,
    val faderTrack: Color,
    val nearSolidChrome: Color,
    val border: Color,
    val borderSubtle: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val onAccent: Color,
    val accentContainer: Color,
    val radialSpot: Color,
    val channelSecondary: Color,
    val statusSuccess: Color,
    val statusWarning: Color,
    val statusDanger: Color
)

val LocalHarmoniPalette = staticCompositionLocalOf<HarmoniPalette> {
    buildHarmoniPalette(isDark = true, accentPreset = AccentPreset.AMBER, customHex = "#C77D4A")
}

/**
 * True saat pengguna sedang drag fader atau scroll list.
 * Menurunkan beban GPU blur sementara agar animasi tetap 120fps.
 */
val LocalGlassBlurSuppressed = compositionLocalOf { false }

/**
 * Radius blur dinamis untuk surface kaca (20.dp saat idle -> 8.dp saat drag/interaksi aktif).
 */
val LocalGlassBlurRadius = compositionLocalOf { GlassTokens.IdleBlurRadius }
