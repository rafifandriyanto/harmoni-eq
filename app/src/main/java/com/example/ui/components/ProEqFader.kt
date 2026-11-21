package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.EqBand
import com.example.model.StereoChannelFocus
import com.example.ui.components.glass.GlassDialog
import com.example.ui.theme.GlassTokens
import com.example.ui.theme.LocalHarmoniPalette
import com.example.ui.theme.LocalThemeController
import java.util.Locale

/**
 * Fader EQ performa tinggi (60fps target):
 * - Track SOLID netral (bukan glass).
 * - Thumb SOLID warna aksen tunggal, dengan inner highlight 1.dp saat drag aktif (tanpa glow/neon).
 * - Angka tabular dengan transisi opacity 120ms (menghormati Reduce Motion).
 */
@Composable
fun ProEqFader(
    band: EqBand,
    enabled: Boolean,
    stereoLinked: Boolean,
    channelFocus: StereoChannelFocus,
    onValueChange: (Float) -> Unit,
    onDoubleTapReset: () -> Unit,
    modifier: Modifier = Modifier,
    onGestureStart: () -> Unit = {},
    onGestureEnd: () -> Unit = {}
) {
    val palette = LocalHarmoniPalette.current
    val reduceMotion = LocalThemeController.current.effectiveReduceMotion

    val value = if (!stereoLinked && channelFocus == StereoChannelFocus.RIGHT) band.rightLevelDb else band.levelDb
    val current by rememberUpdatedState(value)
    val update by rememberUpdatedState(onValueChange)
    val start by rememberUpdatedState(onGestureStart)
    val end by rememberUpdatedState(onGestureEnd)
    val reset by rememberUpdatedState(onDoubleTapReset)

    var isDragging by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }

    val isActiveGain = kotlin.math.abs(value) >= 0.05f || isDragging
    val thumbColor = when {
        !stereoLinked && channelFocus == StereoChannelFocus.RIGHT -> palette.channelSecondary
        isActiveGain -> palette.accent
        else -> palette.textMuted
    }

    val valueAlpha by animateFloatAsState(
        targetValue = if (isDragging || isActiveGain) 1f else 0.78f,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else GlassTokens.ValueOpacityDurationMs),
        label = "fader_value_opacity_${band.index}"
    )

    Column(
        modifier = modifier
            .width(64.dp)
            .background(palette.controlWell, RoundedCornerShape(12.dp))
            .padding(GlassTokens.Space4),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(enabled) {
                    input = String.format(Locale.US, "%.1f", value)
                    editing = true
                }
                .testTag("eq_value_${band.index}")
                .semantics { contentDescription = "Masukkan gain ${band.label}, $value dB" },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = String.format(Locale.US, "%+.1f", value),
                color = if (isActiveGain) palette.accent else palette.textSecondary,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.graphicsLayer { alpha = valueAlpha }
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("eq_fader_${band.index}")
                .semantics {
                    contentDescription = "Gain ${band.label}, ${channelFocus.label}, desibel"
                    progressBarRangeInfo = ProgressBarRangeInfo(value, -15f..15f, 299)
                    setProgress {
                        if (enabled && it.isFinite()) {
                            update(it.coerceIn(-15f, 15f))
                            true
                        } else false
                    }
                }
                .pointerInput(enabled) {
                    detectTapGestures(
                        onDoubleTap = { if (enabled) reset() },
                        onTap = {
                            if (enabled) {
                                val inset = 8.dp.toPx()
                                val usable = (size.height - 2 * inset).coerceAtLeast(1f)
                                update((15f - (it.y - inset) / usable * 30f).coerceIn(-15f, 15f))
                            }
                        }
                    )
                }
                .pointerInput(enabled) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            if (enabled) {
                                isDragging = true
                                start()
                            }
                        },
                        onDragEnd = {
                            isDragging = false
                            end()
                        },
                        onDragCancel = {
                            isDragging = false
                            end()
                        }
                    ) { change, amount ->
                        if (enabled) {
                            change.consume()
                            val usable = (size.height - 16.dp.toPx()).coerceAtLeast(1f)
                            update((current - amount / usable * 30f).coerceIn(-15f, 15f))
                        }
                    }
                }
        ) {
            val x = size.width / 2f
            val inset = 8.dp.toPx()
            val usable = (size.height - inset * 2f).coerceAtLeast(1f)

            // Track SOLID netral (tidak pernah glass)
            drawLine(
                color = palette.faderTrack,
                start = Offset(x, inset),
                end = Offset(x, size.height - inset),
                strokeWidth = 4.dp.toPx()
            )

            // Penanda skala dB
            for (n in 0..6) {
                val y = inset + usable * n / 6f
                drawLine(
                    color = if (n == 3) palette.textMuted else palette.borderSubtle,
                    start = Offset(4.dp.toPx(), y),
                    end = Offset(10.dp.toPx(), y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // Fill aktif dari 0 dB ke posisi thumb
            val y = inset + (15f - value) / 30f * usable
            drawLine(
                color = thumbColor,
                start = Offset(x, size.height / 2f),
                end = Offset(x, y),
                strokeWidth = 4.dp.toPx()
            )

            // Thumb SOLID warna aksen (inner highlight 1.dp saat drag aktif, tanpa glow/neon)
            val thumbW = 34.dp.toPx()
            val thumbH = 18.dp.toPx()
            val thumbLeft = x - thumbW / 2f
            val thumbTop = y - thumbH / 2f
            drawRoundRect(
                color = thumbColor,
                topLeft = Offset(thumbLeft, thumbTop),
                size = Size(thumbW, thumbH),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
            )
            if (isDragging) {
                drawLine(
                    color = Color.White.copy(alpha = 0.55f),
                    start = Offset(thumbLeft + 4.dp.toPx(), thumbTop + 1.dp.toPx()),
                    end = Offset(thumbLeft + thumbW - 4.dp.toPx(), thumbTop + 1.dp.toPx()),
                    strokeWidth = 1.dp.toPx()
                )
            }
            drawLine(
                color = palette.onAccent,
                start = Offset(x - 8.dp.toPx(), y),
                end = Offset(x + 8.dp.toPx(), y),
                strokeWidth = 2.dp.toPx()
            )
        }

        Text(
            text = band.label,
            modifier = Modifier.padding(vertical = GlassTokens.Space12),
            style = MaterialTheme.typography.labelMedium,
            color = palette.textSecondary
        )
    }

    if (editing) {
        val parsed = input.replace(',', '.').toFloatOrNull()
        val valid = parsed != null && parsed.isFinite() && parsed in -15f..15f
        GlassDialog(
            title = "Gain ${band.label}",
            onDismissRequest = { editing = false },
            confirmButton = {
                TextButton(onClick = { parsed?.let(update); editing = false }, enabled = valid) {
                    Text("Terapkan")
                }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }) { Text("Batal") }
            }
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                singleLine = true,
                label = { Text("Desibel") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = !valid,
                supportingText = { Text("-15 hingga +15 dB, dibulatkan ke 0,1 dB") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
