package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.model.EqBand
import com.example.model.StereoChannelFocus
import com.example.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun FrequencyCurveCanvas(bands: List<EqBand>, eqEnabled: Boolean, stereoLinked: Boolean,
    activeChannelFocus: StereoChannelFocus,
    onSelectChannelFocus: (StereoChannelFocus) -> Unit,
    onBandLevelChange: (Int, Float) -> Unit, modifier: Modifier = Modifier,
    onGestureStart: () -> Unit = {}, onGestureEnd: () -> Unit = {}) {
    val update by rememberUpdatedState(onBandLevelChange)
    val start by rememberUpdatedState(onGestureStart)
    val end by rememberUpdatedState(onGestureEnd)
    ConsolePanel(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Kurva EQ", style = MaterialTheme.typography.titleSmall)
            Text(if (stereoLinked) "L + R" else "Kiri steel · kanan clay", color = BoneMuted, style = MaterialTheme.typography.labelMedium)
        }
        Canvas(Modifier.fillMaxWidth().height(116.dp).testTag("frequency_curve_canvas")
            .semantics { contentDescription = "Kurva pengaturan 20 band, bukan spektrum audio terukur. Gain juga dapat diedit lewat fader." }
            .pointerInput(Unit) { detectTapGestures { p ->
                val index = (p.x / size.width * 19).roundToInt().coerceIn(0, 19)
                update(index, (15f - p.y / size.height * 30f).coerceIn(-15f, 15f))
            } }
            .pointerInput(Unit) { detectDragGestures(onDragStart = { start() }, onDragEnd = { end() }, onDragCancel = { end() }) { change, _ ->
                change.consume()
                val index = (change.position.x / size.width * 19).roundToInt().coerceIn(0, 19)
                update(index, (15f - change.position.y / size.height * 30f).coerceIn(-15f, 15f))
            } }) {
            for (i in 0..4) {
                val y = size.height * i / 4
                drawLine(if (i == 2) SeamLine else GridLine, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            for (i in 0..5) {
                val x = size.width * i / 5
                drawLine(GridLine, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
            }
            fun curve(right: Boolean): Path = Path().apply {
                bands.forEachIndexed { i, b ->
                    val x = size.width * i / (bands.size - 1)
                    val gain = if (right) b.rightLevelDb else b.levelDb
                    val y = size.height * (15f - gain) / 30f
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
            }
            drawPath(curve(false), if (stereoLinked) SageSignal else SteelChannel, style = Stroke(2.dp.toPx()))
            if (!stereoLinked) drawPath(curve(true), ClayChannel, style = Stroke(2.dp.toPx()))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("25 Hz", "200", "1k", "6k", "18k").forEach { Text(it, style = MaterialTheme.typography.labelMedium, color = BoneMuted) }
        }
        Text(if (eqEnabled) "Pengaturan tersimpan • seret kurva untuk mengedit" else "Bypass • kurva tetap tersimpan, tidak diterapkan",
            style = MaterialTheme.typography.bodySmall, color = BoneMuted)
    }
}
