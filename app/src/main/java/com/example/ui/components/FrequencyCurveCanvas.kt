package com.example.ui.components

import android.os.SystemClock
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.model.EqBand
import com.example.model.EqFrequencySection
import com.example.model.QuickTuningPresets
import com.example.model.StereoChannelFocus
import com.example.model.default20Bands
import com.example.ui.theme.GlassTokens
import com.example.ui.theme.HarmoniEqTheme
import com.example.ui.theme.LocalHarmoniPalette
import com.example.ui.theme.LocalThemeController
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.ThemeSettings
import kotlin.math.roundToInt

private val SECTION_BOUNDARY_BANDS = intArrayOf(3, 6, 9, 12, 15, 18)

/**
 * Interactive full-spectrum 20-band EQ curve canvas rendered with smooth Catmull-Rom
 * cubic Bezier spline interpolation, cached [Modifier.drawWithCache] gradient & strokes,
 * [derivedStateOf] gain snapshots, and [Path.rewind] for zero per-frame allocations at 120Hz.
 */
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
    highlightedBandIndex: Int? = null,
    autoEqActive: Boolean = false,
    onGestureStart: () -> Unit = {},
    onGestureEnd: () -> Unit = {}
) {
    val palette = LocalHarmoniPalette.current
    val reduceMotion = LocalThemeController.current.effectiveReduceMotion
    val haptics = LocalHapticFeedback.current
    val update by rememberUpdatedState(onBandLevelChange)
    val start by rememberUpdatedState(onGestureStart)
    val end by rememberUpdatedState(onGestureEnd)

    var draggingIndex by remember { mutableIntStateOf(-1) }
    var lastDispatchMs by remember { mutableLongStateOf(0L) }

    val activeIndex = if (draggingIndex in bands.indices) draggingIndex else highlightedBandIndex
    val isDragging = draggingIndex >= 0

    val leftAnimated = rememberAnimatedBandGains(
        bands = bands,
        useRightChannel = false,
        isDragging = isDragging,
        reduceMotion = reduceMotion
    )
    val rightAnimated = rememberAnimatedBandGains(
        bands = bands,
        useRightChannel = true,
        isDragging = isDragging,
        reduceMotion = reduceMotion
    )

    // Pre-allocated Path & coordinate buffers reused via Path.rewind()
    val leftPath = remember { Path() }
    val leftFillPath = remember { Path() }
    val rightPath = remember { Path() }
    val xsBuffer = remember { FloatArray(20) }
    val leftYsBuffer = remember { FloatArray(20) }
    val rightYsBuffer = remember { FloatArray(20) }

    val bandCount = bands.size.coerceAtMost(20)
    val leftGainSnapshot by remember(leftAnimated, bandCount) {
        derivedStateOf { FloatArray(bandCount) { i -> leftAnimated[i].value.coerceIn(-15f, 15f) } }
    }
    val rightGainSnapshot by remember(rightAnimated, bandCount, stereoLinked) {
        derivedStateOf {
            if (stereoLinked) FloatArray(0) else FloatArray(bandCount) { i -> rightAnimated[i].value.coerceIn(-15f, 15f) }
        }
    }

    val lineColor = if (eqEnabled) palette.accent else palette.textMuted
    val secondaryColor = if (eqEnabled) palette.channelSecondary else palette.textMuted.copy(alpha = 0.6f)

    ConsolePanel(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = if (activeIndex != null && activeIndex in bands.indices) {
                    val b = bands[activeIndex]
                    val section = EqFrequencySection.forBandIndex(activeIndex)
                    "EQ Curve • ${section.label} (${b.label}: ${dbLabel(b.levelDb)})"
                } else {
                    "20-Band EQ Curve"
                },
                style = MaterialTheme.typography.titleSmall,
                color = palette.textPrimary
            )
            Text(
                if (stereoLinked) "L + R" else "L / R",
                color = palette.textSecondary,
                style = MaterialTheme.typography.labelMedium
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(144.dp)
                .graphicsLayer { alpha = if (eqEnabled) 1f else 0.5f }
                .testTag("frequency_curve_canvas")
                .semantics {
                    contentDescription = if (eqEnabled) {
                        "Interactive 20-band smooth frequency response curve. Drag to shape EQ gain."
                    } else {
                        "20-band frequency response curve dimmed while Master EQ is off."
                    }
                }
                .pointerInput(eqEnabled) {
                    if (!eqEnabled) return@pointerInput
                    detectTapGestures { p ->
                        val index = (p.x / size.width * 19).roundToInt().coerceIn(0, 19)
                        val gain = (15f - p.y / size.height * 30f).coerceIn(-15f, 15f)
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        update(index, gain)
                    }
                }
                .pointerInput(eqEnabled) {
                    if (!eqEnabled) return@pointerInput
                    detectDragGestures(
                        onDragStart = { p ->
                            val index = (p.x / size.width * 19).roundToInt().coerceIn(0, 19)
                            draggingIndex = index
                            lastDispatchMs = 0L
                            start()
                        },
                        onDragEnd = {
                            draggingIndex = -1
                            end()
                        },
                        onDragCancel = {
                            draggingIndex = -1
                            end()
                        }
                    ) { change, _ ->
                        change.consume()
                        val now = SystemClock.uptimeMillis()
                        if (now - lastDispatchMs < 8L) return@detectDragGestures
                        lastDispatchMs = now
                        val index = (change.position.x / size.width * 19).roundToInt().coerceIn(0, 19)
                        draggingIndex = index
                        val gain = (15f - change.position.y / size.height * 30f).coerceIn(-15f, 15f)
                        update(index, gain)
                    }
                }
                .drawWithCache {
                    val padY = 8.dp.toPx()
                    val usableH = (size.height - padY * 2f).coerceAtLeast(1f)
                    val zeroY = padY + usableH / 2f
                    val centerGridStroke = 1.25.dp.toPx()
                    val thinGridStroke = 1.dp.toPx()
                    val guideStroke = 1.5.dp.toPx()
                    val activeHaloRadius = 10.dp.toPx()
                    val activeDotRadius = 4.5.dp.toPx()
                    val passiveDotRadius = 2.dp.toPx()
                    val curveStroke = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    val secondaryStroke = Stroke(1.75.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    val guideColor = lineColor.copy(alpha = 0.65f)
                    val haloColor = lineColor.copy(alpha = 0.24f)
                    val passiveDotColor = lineColor.copy(alpha = 0.7f)
                    val fillBrush = Brush.verticalGradient(
                        colors = listOf(
                            lineColor.copy(alpha = if (eqEnabled) 0.26f else 0.10f),
                            Color.Transparent,
                            lineColor.copy(alpha = if (eqEnabled) 0.18f else 0.08f)
                        ),
                        startY = padY,
                        endY = size.height - padY
                    )

                    onDrawBehind {
                        // Horizontal dB grid lines (+15, +7.5, 0, -7.5, -15)
                        for (i in 0..4) {
                            val y = padY + usableH * i / 4f
                            drawLine(
                                color = if (i == 2) palette.border else palette.borderSubtle,
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = if (i == 2) centerGridStroke else thinGridStroke
                            )
                        }

                        // Functional frequency section boundaries
                        for (i in SECTION_BOUNDARY_BANDS.indices) {
                            val x = size.width * SECTION_BOUNDARY_BANDS[i] / 19f
                            drawLine(palette.borderSubtle, Offset(x, padY), Offset(x, size.height - padY), thinGridStroke)
                        }

                        val leftGains = leftGainSnapshot
                        val count = leftGains.size
                        if (count >= 2) {
                            val rightGains = rightGainSnapshot
                            val denom = (count - 1).coerceAtLeast(1)
                            for (i in 0 until count) {
                                xsBuffer[i] = size.width * i / denom
                                leftYsBuffer[i] = padY + ((15f - leftGains[i]) / 30f) * usableH
                                if (!stereoLinked && i < rightGains.size) {
                                    rightYsBuffer[i] = padY + ((15f - rightGains[i]) / 30f) * usableH
                                }
                            }

                            if (!stereoLinked) {
                                buildSmoothBezierCurvePath(
                                    path = rightPath,
                                    fillPath = null,
                                    xs = xsBuffer,
                                    ys = rightYsBuffer,
                                    count = count,
                                    baselineY = zeroY,
                                    minY = padY,
                                    maxY = size.height - padY
                                )
                                drawPath(rightPath, secondaryColor, style = secondaryStroke)
                            }

                            buildSmoothBezierCurvePath(
                                path = leftPath,
                                fillPath = leftFillPath,
                                xs = xsBuffer,
                                ys = leftYsBuffer,
                                count = count,
                                baselineY = zeroY,
                                minY = padY,
                                maxY = size.height - padY
                            )

                            drawPath(path = leftFillPath, brush = fillBrush)
                            drawPath(leftPath, lineColor, style = curveStroke)

                            for (i in 0 until count) {
                                val x = xsBuffer[i]
                                val y = leftYsBuffer[i]
                                if (i == activeIndex) {
                                    drawLine(
                                        color = guideColor,
                                        start = Offset(x, padY),
                                        end = Offset(x, size.height - padY),
                                        strokeWidth = guideStroke
                                    )
                                    drawCircle(haloColor, activeHaloRadius, Offset(x, y))
                                    drawCircle(lineColor, activeDotRadius, Offset(x, y))
                                } else {
                                    drawCircle(passiveDotColor, passiveDotRadius, Offset(x, y))
                                }
                            }
                        }
                    }
                }
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("25Hz", "100Hz", "400Hz", "1.6k", "6.3k", "18k").forEach {
                Text(it, style = MaterialTheme.typography.labelMedium, color = palette.textMuted)
            }
        }
        Text(
            text = when {
                eqEnabled -> "Drag directly on the curve or use the 20-band sliders above."
                autoEqActive -> "AutoEQ is selected, but EQ is currently off."
                else -> "Bypass active. Turn Master EQ on to process or adjust bands."
            },
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary
        )
    }
}

@Preview(name = "FrequencyCurveCanvas Dark", showBackground = true, backgroundColor = 0xFF0E1013)
@Composable
private fun PreviewFrequencyCurveDark() {
    val bands = default20Bands().mapIndexed { i, b ->
        b.copy(levelDb = QuickTuningPresets.AiryDetail.bandGainsDb[i])
    }
    HarmoniEqTheme(previewSettings = ThemeSettings(mode = ThemeMode.DARK, dynamicColor = false)) {
        Box(Modifier.padding(GlassTokens.Space16)) {
            FrequencyCurveCanvas(
                bands = bands,
                eqEnabled = true,
                stereoLinked = true,
                activeChannelFocus = StereoChannelFocus.LINKED,
                spectrumBars = FloatArray(32),
                onSelectChannelFocus = {},
                onBandLevelChange = { _, _ -> },
                highlightedBandIndex = 16
            )
        }
    }
}

@Preview(name = "FrequencyCurveCanvas Light", showBackground = true, backgroundColor = 0xFFF5F2EE)
@Composable
private fun PreviewFrequencyCurveLight() {
    val bands = default20Bands().mapIndexed { i, b ->
        b.copy(levelDb = QuickTuningPresets.ReduceHarshness.bandGainsDb[i])
    }
    HarmoniEqTheme(previewSettings = ThemeSettings(mode = ThemeMode.LIGHT, dynamicColor = false)) {
        Box(Modifier.padding(GlassTokens.Space16)) {
            FrequencyCurveCanvas(
                bands = bands,
                eqEnabled = true,
                stereoLinked = true,
                activeChannelFocus = StereoChannelFocus.LINKED,
                spectrumBars = FloatArray(32),
                onSelectChannelFocus = {},
                onBandLevelChange = { _, _ -> },
                highlightedBandIndex = 15
            )
        }
    }
}
