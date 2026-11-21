package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.EqBand
import com.example.model.QuickTuningPresets
import com.example.model.StereoChannelFocus
import com.example.model.default20Bands
import com.example.ui.theme.GlassTokens
import com.example.ui.theme.HarmoniEqTheme
import com.example.ui.theme.LocalHarmoniPalette
import com.example.ui.theme.LocalThemeController
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.ThemeSettings

/**
 * Builds a smooth Catmull-Rom cubic Bezier spline through [count] points stored in
 * pre-allocated [xs] and [ys] arrays. Reuses [path] and [fillPath] via [Path.rewind]
 * to guarantee zero per-frame allocations during 120Hz drags and scrolls.
 */
internal fun buildSmoothBezierCurvePath(
    path: Path,
    fillPath: Path?,
    xs: FloatArray,
    ys: FloatArray,
    count: Int,
    baselineY: Float,
    minY: Float,
    maxY: Float
) {
    path.rewind()
    fillPath?.rewind()
    if (count <= 0) return

    path.moveTo(xs[0], ys[0])
    fillPath?.moveTo(xs[0], baselineY)
    fillPath?.lineTo(xs[0], ys[0])

    if (count == 1) {
        fillPath?.lineTo(xs[0], baselineY)
        fillPath?.close()
        return
    }

    val tension = 6f
    for (i in 0 until count - 1) {
        val x0 = xs[(i - 1).coerceAtLeast(0)]
        val y0 = ys[(i - 1).coerceAtLeast(0)]
        val x1 = xs[i]
        val y1 = ys[i]
        val x2 = xs[i + 1]
        val y2 = ys[i + 1]
        val x3 = xs[(i + 2).coerceAtMost(count - 1)]
        val y3 = ys[(i + 2).coerceAtMost(count - 1)]

        val cp1x = x1 + (x2 - x0) / tension
        val cp1y = (y1 + (y2 - y0) / tension).coerceIn(minY, maxY)
        val cp2x = x2 - (x3 - x1) / tension
        val cp2y = (y2 - (y3 - y1) / tension).coerceIn(minY, maxY)

        path.cubicTo(cp1x, cp1y, cp2x, cp2y, x2, y2)
        fillPath?.cubicTo(cp1x, cp1y, cp2x, cp2y, x2, y2)
    }

    fillPath?.lineTo(xs[count - 1], baselineY)
    fillPath?.close()
}

/**
 * Animates a 20-band gain array smoothly when presets or Reset to Flat are applied,
 * while snapping instantaneously during direct user dragging or when Reduce Motion is active.
 */
@Composable
fun rememberAnimatedBandGains(
    bands: List<EqBand>,
    useRightChannel: Boolean,
    isDragging: Boolean,
    reduceMotion: Boolean
): List<State<Float>> {
    val spec = remember(isDragging, reduceMotion) {
        if (isDragging || reduceMotion) {
            snap<Float>()
        } else {
            tween<Float>(durationMillis = 260, easing = FastOutSlowInEasing)
        }
    }
    return bands.map { band ->
        val target = if (useRightChannel) band.rightLevelDb else band.levelDb
        animateFloatAsState(
            targetValue = target,
            animationSpec = spec,
            label = "eq_band_gain_${band.index}_${if (useRightChannel) "r" else "l"}"
        )
    }
}

/**
 * Real-time smooth cubic Bezier curve overlay spanning the exact width of the 20-band
 * horizontal slider bank.
 *
 * 120Hz Optimizations:
 * - Uses [Modifier.drawWithCache] to pre-allocate reusable [Path], [FloatArray], [Stroke],
 *   and gradient [Brush] instances once per layout/theme change.
 * - Uses [derivedStateOf] to snapshot active gain arrays only when band gain values change.
 * - Calls [Path.rewind] inside `onDrawBehind` and reads [LazyListState.layoutInfo] strictly
 *   in the Draw phase for zero per-frame object allocations and zero scroll recompositions.
 */
@Composable
fun EqSliderCurveOverlay(
    bands: List<EqBand>,
    faderListState: LazyListState,
    eqEnabled: Boolean,
    stereoLinked: Boolean,
    activeChannelFocus: StereoChannelFocus,
    activeDragBandIndex: Int?,
    topInset: Dp,
    bottomInset: Dp,
    modifier: Modifier = Modifier
) {
    val palette = LocalHarmoniPalette.current
    val reduceMotion = LocalThemeController.current.effectiveReduceMotion

    val isDraggingAny = activeDragBandIndex != null
    val leftAnimated = rememberAnimatedBandGains(
        bands = bands,
        useRightChannel = false,
        isDragging = isDraggingAny,
        reduceMotion = reduceMotion
    )
    val rightAnimated = rememberAnimatedBandGains(
        bands = bands,
        useRightChannel = true,
        isDragging = isDraggingAny,
        reduceMotion = reduceMotion
    )

    // Reusable pre-allocated path & coordinate buffers
    val strokePath = remember { Path() }
    val fillPath = remember { Path() }
    val secondaryPath = remember { Path() }
    val xsBuffer = remember { FloatArray(24) }
    val ysBuffer = remember { FloatArray(24) }
    val rightYsBuffer = remember { FloatArray(24) }

    val showRightAsPrimary = !stereoLinked && activeChannelFocus == StereoChannelFocus.RIGHT
    val bandCount = bands.size

    // Derived state snapshots so path math only recomputes when actual gain values change
    val primaryGainSnapshot by remember(leftAnimated, rightAnimated, showRightAsPrimary, bandCount) {
        derivedStateOf {
            FloatArray(bandCount) { idx ->
                if (showRightAsPrimary) rightAnimated[idx].value else leftAnimated[idx].value
            }
        }
    }
    val secondaryGainSnapshot by remember(leftAnimated, rightAnimated, showRightAsPrimary, bandCount, stereoLinked) {
        derivedStateOf {
            if (stereoLinked) {
                FloatArray(0)
            } else {
                FloatArray(bandCount) { idx ->
                    if (showRightAsPrimary) leftAnimated[idx].value else rightAnimated[idx].value
                }
            }
        }
    }

    val overlayAlphaState: State<Float> = animateFloatAsState(
        targetValue = if (eqEnabled) 1f else 0.5f,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 180),
        label = "eq_curve_overlay_alpha"
    )
    val curveColor = if (eqEnabled) palette.accent else palette.accent.copy(alpha = 0.72f)
    val secondaryColor = if (eqEnabled) palette.channelSecondary else palette.textMuted.copy(alpha = 0.55f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = overlayAlphaState.value }
            .testTag("eq_slider_curve_overlay")
            .drawWithCache {
                val trackTop = topInset.toPx()
                val trackBottom = (size.height - bottomInset.toPx()).coerceAtLeast(trackTop + 1f)
                val usableHeight = (trackBottom - trackTop).coerceAtLeast(1f)
                val zeroY = trackTop + usableHeight / 2f
                val defaultPitch = 64.dp.toPx()
                val horizonStrokeWidth = 1.dp.toPx()
                val activeGuideWidth = 1.5.dp.toPx()
                val activeHaloRadius = 12.dp.toPx()
                val activeDotRadius = 5.dp.toPx()

                val primaryStroke = Stroke(
                    width = 2.75.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
                val secondaryStroke = Stroke(
                    width = 1.75.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
                val horizonColor = palette.border.copy(alpha = 0.45f)
                val secondaryDrawColor = secondaryColor.copy(alpha = 0.65f)
                val activeGuideColor = curveColor.copy(alpha = 0.55f)
                val activeHaloColor = curveColor.copy(alpha = 0.24f)
                val fillBrush = Brush.verticalGradient(
                    colors = listOf(
                        curveColor.copy(alpha = if (eqEnabled) 0.20f else 0.08f),
                        curveColor.copy(alpha = 0.03f),
                        curveColor.copy(alpha = if (eqEnabled) 0.16f else 0.06f)
                    ),
                    startY = trackTop,
                    endY = trackBottom
                )

                fun gainToY(gainDb: Float): Float =
                    trackTop + ((15f - gainDb.coerceIn(-15f, 15f)) / 30f) * usableHeight

                onDrawBehind {
                    val visibleItems = faderListState.layoutInfo.visibleItemsInfo
                    val primaryGains = primaryGainSnapshot
                    if (visibleItems.isEmpty() || primaryGains.isEmpty()) return@onDrawBehind
                    val secondaryGains = secondaryGainSnapshot

                    // Subtle 0 dB horizon guide spanning the full slider bank
                    drawLine(
                        color = horizonColor,
                        start = Offset(0f, zeroY),
                        end = Offset(size.width, zeroY),
                        strokeWidth = horizonStrokeWidth
                    )

                    val firstVisible = visibleItems.first()
                    val lastVisible = visibleItems.last()
                    val pitch = if (visibleItems.size >= 2) {
                        ((lastVisible.offset - firstVisible.offset).toFloat() / (visibleItems.size - 1).coerceAtLeast(1))
                            .coerceAtLeast(1f)
                    } else {
                        firstVisible.size.toFloat().coerceAtLeast(defaultPitch)
                    }

                    var count = 0

                    // Left virtual anchor (one band before first visible) for seamless incoming tangent
                    val leftNeighborIdx = firstVisible.index - 1
                    if (leftNeighborIdx in primaryGains.indices && count < xsBuffer.size) {
                        xsBuffer[count] = (firstVisible.offset + firstVisible.size / 2f) - pitch
                        ysBuffer[count] = gainToY(primaryGains[leftNeighborIdx])
                        if (!stereoLinked && leftNeighborIdx in secondaryGains.indices) {
                            rightYsBuffer[count] = gainToY(secondaryGains[leftNeighborIdx])
                        }
                        count++
                    }

                    // All visible band centers
                    var activeNodeX = Float.NaN
                    var activeNodeY = Float.NaN
                    for (i in visibleItems.indices) {
                        val item = visibleItems[i]
                        val idx = item.index
                        if (idx !in primaryGains.indices || count >= xsBuffer.size - 1) continue
                        val centerX = item.offset + item.size / 2f
                        val y = gainToY(primaryGains[idx])
                        xsBuffer[count] = centerX
                        ysBuffer[count] = y
                        if (!stereoLinked && idx in secondaryGains.indices) {
                            rightYsBuffer[count] = gainToY(secondaryGains[idx])
                        }
                        if (idx == activeDragBandIndex) {
                            activeNodeX = centerX
                            activeNodeY = y
                        }
                        count++
                    }

                    // Right virtual anchor (one band after last visible) for seamless outgoing tangent
                    val rightNeighborIdx = lastVisible.index + 1
                    if (rightNeighborIdx in primaryGains.indices && count < xsBuffer.size) {
                        xsBuffer[count] = (lastVisible.offset + lastVisible.size / 2f) + pitch
                        ysBuffer[count] = gainToY(primaryGains[rightNeighborIdx])
                        if (!stereoLinked && rightNeighborIdx in secondaryGains.indices) {
                            rightYsBuffer[count] = gainToY(secondaryGains[rightNeighborIdx])
                        }
                        count++
                    }

                    if (count < 2) return@onDrawBehind

                    if (!stereoLinked) {
                        buildSmoothBezierCurvePath(
                            path = secondaryPath,
                            fillPath = null,
                            xs = xsBuffer,
                            ys = rightYsBuffer,
                            count = count,
                            baselineY = zeroY,
                            minY = trackTop,
                            maxY = trackBottom
                        )
                        drawPath(
                            path = secondaryPath,
                            color = secondaryDrawColor,
                            style = secondaryStroke
                        )
                    }

                    buildSmoothBezierCurvePath(
                        path = strokePath,
                        fillPath = fillPath,
                        xs = xsBuffer,
                        ys = ysBuffer,
                        count = count,
                        baselineY = zeroY,
                        minY = trackTop,
                        maxY = trackBottom
                    )

                    drawPath(path = fillPath, brush = fillBrush)
                    drawPath(path = strokePath, color = curveColor, style = primaryStroke)

                    if (!activeNodeX.isNaN() && !activeNodeY.isNaN()) {
                        drawLine(
                            color = activeGuideColor,
                            start = Offset(activeNodeX, trackTop),
                            end = Offset(activeNodeX, trackBottom),
                            strokeWidth = activeGuideWidth
                        )
                        drawCircle(
                            color = activeHaloColor,
                            radius = activeHaloRadius,
                            center = Offset(activeNodeX, activeNodeY)
                        )
                        drawCircle(
                            color = curveColor,
                            radius = activeDotRadius,
                            center = Offset(activeNodeX, activeNodeY)
                        )
                    }
                }
            }
    )
}

/**
 * Standalone smooth 20-band curve preview/overview canvas using [Modifier.drawWithCache]
 * and [Path.rewind] for zero per-frame allocations.
 */
@Composable
fun StaticFullCurvePreviewCanvas(
    bands: List<EqBand>,
    eqEnabled: Boolean,
    highlightedBandIndex: Int? = null,
    modifier: Modifier = Modifier
) {
    val palette = LocalHarmoniPalette.current
    val strokePath = remember { Path() }
    val fillPath = remember { Path() }
    val xs = remember { FloatArray(20) }
    val ys = remember { FloatArray(20) }
    val lineColor = if (eqEnabled) palette.accent else palette.textMuted

    val gainSnapshot by remember(bands) {
        derivedStateOf {
            val count = bands.size.coerceAtMost(20)
            FloatArray(count) { i -> bands[i].levelDb.coerceIn(-15f, 15f) }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(palette.controlWell, RoundedCornerShape(16.dp))
            .drawWithCache {
                val pad = 12.dp.toPx()
                val usableW = (size.width - pad * 2f).coerceAtLeast(1f)
                val usableH = (size.height - pad * 2f).coerceAtLeast(1f)
                val zeroY = pad + usableH / 2f
                val stroke = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val gridStroke = 1.dp.toPx()
                val highlightLineStroke = 1.5.dp.toPx()
                val highlightRadius = 5.dp.toPx()
                val highlightLineColor = lineColor.copy(alpha = 0.6f)
                val fillBrush = Brush.verticalGradient(
                    colors = listOf(
                        lineColor.copy(alpha = 0.24f),
                        Color.Transparent,
                        lineColor.copy(alpha = 0.18f)
                    ),
                    startY = pad,
                    endY = size.height - pad
                )

                onDrawBehind {
                    drawLine(palette.border, Offset(pad, zeroY), Offset(size.width - pad, zeroY), gridStroke)

                    val gains = gainSnapshot
                    val count = gains.size
                    if (count >= 2) {
                        val denom = (count - 1).coerceAtLeast(1)
                        for (i in 0 until count) {
                            xs[i] = pad + usableW * i / denom
                            ys[i] = pad + ((15f - gains[i]) / 30f) * usableH
                        }
                        buildSmoothBezierCurvePath(strokePath, fillPath, xs, ys, count, zeroY, pad, size.height - pad)
                        drawPath(fillPath, fillBrush)
                        drawPath(strokePath, lineColor, style = stroke)

                        if (highlightedBandIndex != null && highlightedBandIndex in 0 until count) {
                            val hx = xs[highlightedBandIndex]
                            val hy = ys[highlightedBandIndex]
                            drawLine(highlightLineColor, Offset(hx, pad), Offset(hx, size.height - pad), highlightLineStroke)
                            drawCircle(lineColor, highlightRadius, Offset(hx, hy))
                        }
                    }
                }
            }
    )
}

@Preview(name = "EQ Curve Overlay Dark", showBackground = true, backgroundColor = 0xFF0E1013)
@Composable
private fun PreviewEqCurveOverlayDark() {
    val sampleBands = default20Bands().mapIndexed { i, b ->
        b.copy(levelDb = QuickTuningPresets.MoreBass.bandGainsDb[i])
    }
    HarmoniEqTheme(previewSettings = ThemeSettings(mode = ThemeMode.DARK, dynamicColor = false)) {
        Box(Modifier.padding(GlassTokens.Space16)) {
            StaticFullCurvePreviewCanvas(
                bands = sampleBands,
                eqEnabled = true,
                highlightedBandIndex = 2
            )
        }
    }
}

@Preview(name = "EQ Curve Overlay Light", showBackground = true, backgroundColor = 0xFFF5F2EE)
@Composable
private fun PreviewEqCurveOverlayLight() {
    val sampleBands = default20Bands().mapIndexed { i, b ->
        b.copy(levelDb = QuickTuningPresets.ClearerVocals.bandGainsDb[i])
    }
    HarmoniEqTheme(previewSettings = ThemeSettings(mode = ThemeMode.LIGHT, dynamicColor = false)) {
        Box(Modifier.padding(GlassTokens.Space16)) {
            StaticFullCurvePreviewCanvas(
                bands = sampleBands,
                eqEnabled = true,
                highlightedBandIndex = 12
            )
        }
    }
}
