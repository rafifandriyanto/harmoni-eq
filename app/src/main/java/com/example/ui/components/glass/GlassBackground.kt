package com.example.ui.components.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.ui.theme.LocalHarmoniPalette

/**
 * Pure Solid Canvas Background:
 * Tanpa perhitungan gradien radial berat saat frame render.
 * Langsung mengisi layar dengan warna dasar canvas yang cepat dan efisien.
 */
@Composable
fun GlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {}
) {
    val palette = LocalHarmoniPalette.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.canvas)
    ) {
        content()
    }
}
