package com.example.ui.components.glass

import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.GlassTokens
import com.example.ui.theme.HarmoniEqTheme
import com.example.ui.theme.LocalGlassBlurRadius
import com.example.ui.theme.LocalGlassBlurSuppressed
import com.example.ui.theme.LocalHarmoniPalette
import com.example.ui.theme.LocalThemeController
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.ThemeSettings
import kotlin.math.roundToInt

enum class GlassVariant {
    CHROME,
    NEAR_SOLID
}

/**
 * Thick Glassmorphism Surface dengan Dynamic Blur Radius (120Hz-aware):
 * - Pada Android 12+ (API 31+), efek `RenderEffect.createBlurEffect` diterapkan hanya pada
 *   lapisan backdrop terpisah (`matchParentSize()`) di belakang konten sehingga teks dan kontrol
 *   tetap 100% tajam (WCAG >= 4.5:1) dan tidak ikut kabur.
 * - Radius blur dinamis (`20.dp` saat idle -> `8.dp` saat pengguna drag fader / interaksi aktif)
 *   di-cache menggunakan `remember(quantizedRadiusPx)` agar objek RenderEffect tidak dibuat ulang
 *   di setiap frame.
 * - Pada API < 31 atau saat Reduce Transparency aktif, langsung jatuh ke surface near-solid tanpa
 *   overhead GPU blur.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    variant: GlassVariant = GlassVariant.CHROME,
    reduceTransparency: Boolean = false,
    blurSuppressed: Boolean = false,
    cornerRadius: Dp = GlassTokens.CornerRadius,
    accentBorder: Boolean = false,
    alphaBoost: Float = 0f,
    borderBoost: Float = 0f,
    content: @Composable BoxScope.() -> Unit
) {
    val palette = LocalHarmoniPalette.current
    val themeController = LocalThemeController.current
    val isInspection = LocalInspectionMode.current
    val density = LocalDensity.current

    val effectiveReduceTransparency = reduceTransparency || themeController.settings.reduceTransparency
    val globalSuppressed = LocalGlassBlurSuppressed.current
    val dynamicBlurRadius = LocalGlassBlurRadius.current

    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }

    val baseFillColor = if (variant == GlassVariant.CHROME) {
        if (palette.isDark) palette.surfaceElevated else palette.surface
    } else {
        palette.surface
    }
    val fillAlpha = when {
        effectiveReduceTransparency -> GlassTokens.ReduceTransparencyAlpha
        variant == GlassVariant.NEAR_SOLID -> (GlassTokens.NearSolidAlpha + alphaBoost).coerceIn(0.85f, 0.98f)
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> (GlassTokens.LegacyFallbackAlpha + alphaBoost).coerceIn(0.80f, 0.96f)
        else -> ((if (palette.isDark) 0.88f else 0.86f) + alphaBoost).coerceIn(0.75f, 0.96f)
    }
    val fillColor = baseFillColor.copy(alpha = fillAlpha)

    val borderColor = if (accentBorder) {
        palette.accent
    } else if (borderBoost > 0f) {
        palette.border.copy(alpha = (palette.border.alpha + borderBoost).coerceIn(0f, 1f))
    } else {
        palette.borderSubtle
    }

    val useRenderEffect = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        !isInspection &&
        !effectiveReduceTransparency &&
        variant == GlassVariant.CHROME &&
        dynamicBlurRadius > 0.dp

    val effectiveBlurDp = if (blurSuppressed || globalSuppressed) {
        minOf(dynamicBlurRadius, GlassTokens.DragBlurRadius)
    } else {
        dynamicBlurRadius
    }
    // Quantize to integer px to avoid churning native RenderEffect allocations during animation
    val quantizedBlurPx = with(density) { effectiveBlurDp.toPx() }.roundToInt().coerceAtLeast(1)

    val cachedRenderEffect: RenderEffect? = remember(useRenderEffect, quantizedBlurPx) {
        if (!useRenderEffect || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            null
        } else {
            runCatching {
                val radius = quantizedBlurPx.toFloat()
                AndroidRenderEffect
                    .createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
                    .asComposeRenderEffect()
            }.getOrNull()
        }
    }

    val sheenBrush = remember(palette.isDark, palette.accent, accentBorder) {
        val topHighlight = if (palette.isDark) {
            Color.White.copy(alpha = GlassTokens.InnerHighlightAlpha)
        } else {
            Color.White.copy(alpha = 0.28f)
        }
        val midTint = if (accentBorder) palette.accent.copy(alpha = 0.08f) else Color.Transparent
        Brush.verticalGradient(listOf(topHighlight, midTint, Color.Transparent))
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(fillColor, shape)
            .border(width = GlassTokens.BorderWidth, color = borderColor, shape = shape)
    ) {
        if (cachedRenderEffect != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { renderEffect = cachedRenderEffect }
                    .background(sheenBrush, shape)
            )
        }
        content()
    }
}

@Preview(name = "Thick Glass Surface Dark", showBackground = true, backgroundColor = 0xFF0E1013)
@Composable
private fun PreviewGlassSurfaceDark() {
    HarmoniEqTheme(previewSettings = ThemeSettings(mode = ThemeMode.DARK)) {
        Box(Modifier.padding(GlassTokens.Space16)) {
            GlassSurface(Modifier.fillMaxWidth(), variant = GlassVariant.CHROME) {
                Column(Modifier.padding(GlassTokens.Space16)) {
                    Text("Thick Glassmorphism Surface", style = MaterialTheme.typography.titleMedium)
                    Text("Dynamic blur 20.dp idle -> 8.dp saat drag aktif.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Preview(name = "Thick Glass Surface Light", showBackground = true, backgroundColor = 0xFFF5F2EE)
@Composable
private fun PreviewGlassSurfaceLight() {
    HarmoniEqTheme(previewSettings = ThemeSettings(mode = ThemeMode.LIGHT)) {
        Box(Modifier.padding(GlassTokens.Space16)) {
            GlassSurface(Modifier.fillMaxWidth(), variant = GlassVariant.CHROME) {
                Column(Modifier.padding(GlassTokens.Space16)) {
                    Text("Thick Glassmorphism Surface", style = MaterialTheme.typography.titleMedium)
                    Text("Dynamic blur 20.dp idle -> 8.dp saat drag aktif.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
