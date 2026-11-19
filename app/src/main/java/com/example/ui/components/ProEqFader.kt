package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.*
import com.example.ui.theme.*
import java.util.Locale

@Composable
fun ProEqFader(band: EqBand, enabled: Boolean, stereoLinked: Boolean, channelFocus: StereoChannelFocus,
    onValueChange: (Float) -> Unit, onDoubleTapReset: () -> Unit, modifier: Modifier = Modifier,
    onGestureStart: () -> Unit = {}, onGestureEnd: () -> Unit = {}) {
    val value = if (!stereoLinked && channelFocus == StereoChannelFocus.RIGHT) band.rightLevelDb else band.levelDb
    val current by rememberUpdatedState(value)
    val update by rememberUpdatedState(onValueChange)
    val start by rememberUpdatedState(onGestureStart)
    val end by rememberUpdatedState(onGestureEnd)
    val reset by rememberUpdatedState(onDoubleTapReset)
    val accent = if (channelFocus == StereoChannelFocus.RIGHT) ClayChannel else if (band.index < 7) ClayChannel else if (band.index < 14) SageSignal else SteelChannel
    var editing by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    Column(modifier.width(64.dp).background(GraphiteWell, RoundedCornerShape(12.dp)).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled) {
            input = String.format(Locale.US, "%.1f", value); editing = true
        }.testTag("eq_value_${band.index}").semantics { contentDescription = "Masukkan gain ${band.label}, $value dB" }, contentAlignment = Alignment.Center) {
            Text(String.format(Locale.US, "%+.1f", value), color = accent, style = MaterialTheme.typography.labelLarge)
        }
        Canvas(Modifier.fillMaxWidth().weight(1f).testTag("eq_fader_${band.index}").semantics {
            contentDescription = "Gain ${band.label}, ${channelFocus.label}, desibel"
            progressBarRangeInfo = ProgressBarRangeInfo(value, -15f..15f, 299)
            setProgress { if (enabled && it.isFinite()) { update(it.coerceIn(-15f, 15f)); true } else false }
        }.pointerInput(enabled) { detectTapGestures(onDoubleTap = { if (enabled) reset() }, onTap = {
            if (enabled) {
                val inset = 8.dp.toPx()
                val usable = (size.height - 2 * inset).coerceAtLeast(1f)
                update((15f - (it.y - inset) / usable * 30f).coerceIn(-15f, 15f))
            }
        }) }.pointerInput(enabled) { detectVerticalDragGestures(onDragStart = { if (enabled) start() },
            onDragEnd = { end() }, onDragCancel = { end() }) { change, amount ->
            if (enabled) {
                change.consume()
                val usable = (size.height - 16.dp.toPx()).coerceAtLeast(1f)
                update((current - amount / usable * 30f).coerceIn(-15f, 15f))
            }
        } }) {
            val x = size.width / 2
            drawLine(SeamLine, Offset(x, 8.dp.toPx()), Offset(x, size.height - 8.dp.toPx()), 4.dp.toPx())
            for (n in 0..6) {
                val y = 8.dp.toPx() + (size.height - 16.dp.toPx()) * n / 6
                drawLine(if (n == 3) BoneSecondary else SeamLine, Offset(3.dp.toPx(), y), Offset(10.dp.toPx(), y), 1.dp.toPx())
            }
            val y = 8.dp.toPx() + (15f - value) / 30f * (size.height - 16.dp.toPx())
            drawLine(accent, Offset(x, size.height / 2), Offset(x, y), 4.dp.toPx())
            drawRoundRect(GraphiteRaised, Offset(x - 17.dp.toPx(), y - 9.dp.toPx()), Size(34.dp.toPx(), 18.dp.toPx()), CornerRadius(4.dp.toPx()))
            drawLine(accent, Offset(x - 11.dp.toPx(), y), Offset(x + 11.dp.toPx(), y), 2.dp.toPx())
        }
        Text(band.label, Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.labelMedium, color = BoneSecondary)
    }
    if (editing) {
        val parsed = input.replace(',', '.').toFloatOrNull()
        val valid = parsed != null && parsed.isFinite() && parsed in -15f..15f
        AlertDialog(onDismissRequest = { editing = false }, title = { Text("Gain ${band.label}") }, text = {
            OutlinedTextField(input, { input = it }, singleLine = true, label = { Text("Desibel") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = !valid,
                supportingText = { Text("−15 hingga +15 dB • dibulatkan ke 0,1 dB") })
        }, confirmButton = { TextButton(onClick = { parsed?.let(update); editing = false }, enabled = valid) { Text("Terapkan") } },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Batal") } })
    }
}
