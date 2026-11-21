package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.model.EqBand
import com.example.model.StereoChannelFocus
import com.example.ui.theme.LocalHarmoniPalette
import kotlin.math.roundToInt

@Composable
fun FrequencyCurveCanvas(
    bands: List<EqBand>,
    eqEnabled: Boolean,
    stereoLinked: Boolean,
    activeChannelFocus: StereoChannelFocus,
    spectrumBars: FloatArray,
    onSelectChannelFocus: (StereoChannelFocus) -> Unit,
    onBandLevelChange: (Int, Float) -> Unit,
    modifier: Modifier = Modifier,
    onGestureStart: () -> Unit = {},
    onGestureEnd: () -> Unit = {}
) {
    val palette = LocalHarmoniPalette.current
    val update by rememberUpdatedState(onBandLevelChange)
    val start by rememberUpdatedState(onGestureStart)
    val end by rememberUpdatedState(onGestureEnd)

    // Path & Stroke dipakai ulang antar frame agar tidak ada alokasi saat drag.
    val density = LocalDensity.current
    val leftPath = remember { Path() }
    val rightPath = remember { Path() }
    val curveStroke = remember(density) { with(density) { Stroke(2.dp.toPx()) } }

    ConsolePanel(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Kurva EQ", style = MaterialTheme.typography.titleSmall, color = palette.textPrimary)
            Text(
                if (stereoLinked) "L + R" else "L aksen / R sekunder",
                color = palette.textMuted,
                style = MaterialTheme.typography.labelMedium
            )
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(116.dp)
                .testTag("frequency_curve_canvas")
                .semantics {
                    contentDescription = "Kurva pengaturan 20 band, bukan spektrum audio terukur. Gain juga dapat diedit lewat fader."
                }
                .pointerInput(Unit) {
                    detectTapGestures { p ->
                        val index = (p.x / size.width * 19).roundToInt().coerceIn(0, 19)
                        update(index, (15f - p.y / size.height * 30f).coerceIn(-15f, 15f))
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { start() },
                        onDragEnd = { end() },
                        onDragCancel = { end() }
                    ) { change, _ ->
                        change.consume()
                        val index = (change.position.x / size.width * 19).roundToInt().coerceIn(0, 19)
                        update(index, (15f - change.position.y / size.height * 30f).coerceIn(-15f, 15f))
                    }
                }
        ) {
            for (i in 0..4) {
                val y = size.height * i / 4f
                drawLine(
                    if (i == 2) palette.border else palette.borderSubtle,
                    Offset(0f, y),
                    Offset(size.width, y),
                    1.dp.toPx()
                )
            }
            for (i in 0..5) {
                val x = size.width * i / 5f
                drawLine(palette.borderSubtle, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
            }
            fun fill(path: Path, right: Boolean) {
                path.rewind()
                bands.forEachIndexed { i, b ->
                    val x = size.width * i / (bands.size - 1).coerceAtLeast(1)
                    val gain = if (right) b.rightLevelDb else b.levelDb
                    val y = size.height * (15f - gain) / 30f
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
            }
            fill(leftPath, false)
            drawPath(leftPath, palette.accent, style = curveStroke)
            if (!stereoLinked) {
                fill(rightPath, true)
                drawPath(rightPath, palette.channelSecondary, style = curveStroke)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("25 Hz", "200", "1k", "6k", "18k").forEach {
                Text(it, style = MaterialTheme.typography.labelMedium, color = palette.textMuted)
            }
        }
        Text(
            if (eqEnabled) "Pengaturan tersimpan. Seret kurva untuk mengedit."
            else "Bypass aktif. Kurva tetap tersimpan tanpa diterapkan.",
            style = MaterialTheme.typography.bodySmall,
            color = palette.textMuted
        )
    }
}
