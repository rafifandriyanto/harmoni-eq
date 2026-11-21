package com.example.ui.components.glass

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.GlassTokens
import com.example.ui.theme.HarmoniEqTheme
import com.example.ui.theme.LocalHarmoniPalette
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.ThemeSettings

enum class GlassVariant {
    CHROME,
    NEAR_SOLID
}

/**
 * High-Performance Clean Surface:
 * Tanpa RenderEffect/real-time blur, tanpa shader overlay berat.
 * Menggunakan solid/near-solid background dan crisp border GPU-accelerated 60fps.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    variant: GlassVariant = GlassVariant.CHROME,
    reduceTransparency: Boolean = true,
    blurSuppressed: Boolean = true,
    cornerRadius: Dp = GlassTokens.CornerRadius,
    accentBorder: Boolean = false,
    alphaBoost: Float = 0f,
    borderBoost: Float = 0f,
    content: @Composable BoxScope.() -> Unit
) {
    val palette = LocalHarmoniPalette.current
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }

    // Bersih, solid dan sangat cepat: tanpa beban blur GPU
    val fillColor = if (variant == GlassVariant.CHROME) {
        if (palette.isDark) palette.surfaceElevated else palette.surface
    } else {
        palette.surface
    }

    val borderColor = if (accentBorder) {
        palette.accent
    } else {
        palette.borderSubtle
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(fillColor, shape)
            .border(width = GlassTokens.BorderWidth, color = borderColor, shape = shape),
        content = content
    )
}

@Preview(name = "Clean Surface Dark", showBackground = true, backgroundColor = 0xFF0E1013)
@Composable
private fun PreviewGlassSurfaceDark() {
    HarmoniEqTheme(previewSettings = ThemeSettings(mode = ThemeMode.DARK)) {
        Box(Modifier.padding(GlassTokens.Space16)) {
            GlassSurface(Modifier.fillMaxWidth(), variant = GlassVariant.CHROME) {
                Column(Modifier.padding(GlassTokens.Space16)) {
                    Text("High-Performance Surface", style = MaterialTheme.typography.titleMedium)
                    Text("Performa 60fps maksimal tanpa overhead GPU blur.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Preview(name = "Clean Surface Light", showBackground = true, backgroundColor = 0xFFF5F2EE)
@Composable
private fun PreviewGlassSurfaceLight() {
    HarmoniEqTheme(previewSettings = ThemeSettings(mode = ThemeMode.LIGHT)) {
        Box(Modifier.padding(GlassTokens.Space16)) {
            GlassSurface(Modifier.fillMaxWidth(), variant = GlassVariant.CHROME) {
                Column(Modifier.padding(GlassTokens.Space16)) {
                    Text("High-Performance Surface", style = MaterialTheme.typography.titleMedium)
                    Text("Performa 60fps maksimal tanpa overhead GPU blur.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
