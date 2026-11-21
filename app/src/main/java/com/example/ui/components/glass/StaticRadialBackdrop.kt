package com.example.ui.components.glass

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import com.example.ui.theme.LocalHarmoniPalette

/**
 * Latar belakang statis dengan SATU titik cahaya radial low-saturation.
 * Di-pre-render sekali ke Bitmap agar zero real-time blur dan stabil 60fps.
 */
@Composable
fun StaticRadialBackdrop(modifier: Modifier = Modifier) {
    val palette = LocalHarmoniPalette.current
    val bitmap = remember(palette.isDark, palette.canvas, palette.radialSpot) {
        renderStaticBackdropBitmap(
            canvasColor = palette.canvas,
            spotColor = palette.radialSpot,
            isDark = palette.isDark
        )
    }
    Image(
        bitmap = bitmap,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxSize()
    )
}

private fun renderStaticBackdropBitmap(
    canvasColor: Color,
    spotColor: Color,
    isDark: Boolean
): ImageBitmap {
    // Resolusi kecil (256x256) cukup untuk gradasi radial halus dan sangat hemat memori
    val size = 256
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(canvasColor.toArgb())

    val spotAlpha = if (isDark) 0.11f else 0.09f
    val centerColor = spotColor.copy(alpha = spotAlpha).toArgb()
    val edgeColor = spotColor.copy(alpha = 0f).toArgb()

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            size * 0.78f,
            size * 0.16f,
            size * 0.75f,
            intArrayOf(centerColor, edgeColor),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
    }
    canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
    return bmp.asImageBitmap()
}
