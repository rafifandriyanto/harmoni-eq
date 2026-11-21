package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.EqBand
import com.example.model.StereoChannelFocus
import com.example.ui.components.glass.GlassDialog
import com.example.ui.theme.GlassTokens
import com.example.ui.theme.HarmoniPalette
import com.example.ui.theme.LocalHarmoniPalette
import com.example.ui.theme.LocalThemeController
import java.util.Locale
import kotlin.math.abs

/**
 * High-performance 120Hz pro studio fader using Compose Phase Skipping:
 * - Continuous gain Float reads during vertical dragging are deferred to the Draw phase
 *   via [Modifier.drawWithCache] (`onDrawBehind`), skipping Composition and Layout for the track.
 * - Value text readout is isolated in a tiny leaf composable backed by [derivedStateOf] so only
 *   the numeric label updates when the rounded 0.1 dB string changes.
 * - [Modifier.graphicsLayer] is scoped strictly to animated opacity elements (not the entire row).
 * - Smooth animated transitions when presets/Quick Tuning/Reset apply (respects Reduce Motion).
 * - Haptic feedback on zero-crossing snap and double-tap reset.
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
    onGestureEnd: () -> Unit = {},
    isHighlighted: Boolean = false,
    onDragActiveChange: (Boolean) -> Unit = {}
) {
    val palette = LocalHarmoniPalette.current
    val reduceMotion = LocalThemeController.current.effectiveReduceMotion
    val haptics = LocalHapticFeedback.current

    val value = if (!stereoLinked && channelFocus == StereoChannelFocus.RIGHT) band.rightLevelDb else band.levelDb
    val update by rememberUpdatedState(onValueChange)
    val start by rememberUpdatedState(onGestureStart)
    val end by rememberUpdatedState(onGestureEnd)
    val reset by rememberUpdatedState(onDoubleTapReset)
    val dragActiveCallback by rememberUpdatedState(onDragActiveChange)

    var isDragging by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }

    // Local float state updated during drag so the draw phase can render immediately at 120Hz
    // without forcing the parent fader column to re-measure or re-layout.
    val dragGainState = remember { mutableFloatStateOf(value) }
    LaunchedEffect(value, isDragging) {
        if (!isDragging) {
            dragGainState.floatValue = value
        }
    }

    val animatedGainState: State<Float> = animateFloatAsState(
        targetValue = value,
        animationSpec = if (isDragging || reduceMotion) snap() else tween(260, easing = FastOutSlowInEasing),
        label = "fader_gain_${band.index}"
    )

    // Deferred state reader — read only inside Draw phase & isolated leaf readout
    val readGain: () -> Float = remember(isDragging) {
        { if (isDragging) dragGainState.floatValue else animatedGainState.value }
    }

    val isActiveGain by remember(isDragging) {
        derivedStateOf { abs(readGain()) >= 0.05f || isDragging }
    }

    val valueAlphaState: State<Float> = animateFloatAsState(
        targetValue = when {
            !enabled -> 0.55f
            isDragging || isActiveGain || isHighlighted -> 1f
            else -> 0.85f
        },
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else GlassTokens.ValueOpacityDurationMs),
        label = "fader_value_opacity_${band.index}"
    )

    val containerAlphaState: State<Float> = animateFloatAsState(
        targetValue = if (enabled) 1f else 0.48f,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 180),
        label = "fader_enabled_opacity_${band.index}"
    )

    val wellShape = remember { RoundedCornerShape(12.dp) }
    val wellBackground = when {
        !enabled -> palette.controlWell
        isDragging -> palette.accent.copy(alpha = if (palette.isDark) 0.14f else 0.10f)
        isHighlighted -> palette.accent.copy(alpha = if (palette.isDark) 0.09f else 0.07f)
        else -> palette.controlWell
    }

    Column(
        modifier = modifier
            .width(64.dp)
            .graphicsLayer { alpha = containerAlphaState.value }
            .background(wellBackground, wellShape)
            .then(
                if (enabled && (isDragging || isHighlighted)) {
                    Modifier.border(
                        width = if (isDragging) 1.5.dp else 1.dp,
                        color = palette.accent.copy(alpha = if (isDragging) 0.85f else 0.45f),
                        shape = wellShape
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = GlassTokens.Space4, vertical = GlassTokens.Space4),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FaderValueReadout(
            bandIndex = band.index,
            bandLabel = band.label,
            enabled = enabled,
            isDragging = isDragging,
            isHighlighted = isHighlighted,
            readGain = readGain,
            valueAlphaState = valueAlphaState,
            palette = palette,
            onClick = {
                input = String.format(Locale.US, "%.1f", readGain())
                editing = true
            }
        )

        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("eq_fader_${band.index}")
                .semantics {
                    val semanticGain = readGain()
                    contentDescription = "Gain ${band.label}, ${channelFocus.label}, desibel"
                    if (!enabled) disabled()
                    progressBarRangeInfo = ProgressBarRangeInfo(semanticGain, -15f..15f, 299)
                    setProgress {
                        if (enabled && it.isFinite()) {
                            val clamped = it.coerceIn(-15f, 15f)
                            dragGainState.floatValue = clamped
                            update(clamped)
                            true
                        } else false
                    }
                }
                .pointerInput(enabled) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (enabled) {
                                dragGainState.floatValue = 0f
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                reset()
                            }
                        },
                        onTap = {
                            if (enabled) {
                                val inset = 8.dp.toPx()
                                val usable = (size.height - 2 * inset).coerceAtLeast(1f)
                                val nextVal = (15f - (it.y - inset) / usable * 30f).coerceIn(-15f, 15f)
                                dragGainState.floatValue = nextVal
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                update(nextVal)
                            }
                        }
                    )
                }
                .pointerInput(enabled) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            if (enabled) {
                                dragGainState.floatValue = animatedGainState.value
                                isDragging = true
                                dragActiveCallback(true)
                                start()
                            }
                        },
                        onDragEnd = {
                            isDragging = false
                            dragActiveCallback(false)
                            end()
                        },
                        onDragCancel = {
                            isDragging = false
                            dragActiveCallback(false)
                            end()
                        }
                    ) { change, amount ->
                        if (enabled) {
                            change.consume()
                            val usable = (size.height - 16.dp.toPx()).coerceAtLeast(1f)
                            val prev = dragGainState.floatValue
                            val nextVal = (prev - amount / usable * 30f).coerceIn(-15f, 15f)
                            // Tactile haptic tick when crossing 0 dB center detent
                            if ((prev > 0.15f && nextVal <= 0.15f) || (prev < -0.15f && nextVal >= -0.15f)) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            dragGainState.floatValue = nextVal
                            update(nextVal)
                        }
                    }
                }
                .drawWithCache {
                    // Cache geometry constants once per layout size change; draw loop allocates zero objects.
                    val x = size.width / 2f
                    val inset = 8.dp.toPx()
                    val usable = (size.height - inset * 2f).coerceAtLeast(1f)
                    val trackWidth = 4.dp.toPx()
                    val activeFillWidth = if (isDragging) 5.dp.toPx() else 4.dp.toPx()
                    val guideWidth = 10.dp.toPx()
                    val tickStart = 4.dp.toPx()
                    val tickMidEnd = 12.dp.toPx()
                    val tickSmallEnd = 9.dp.toPx()
                    val tickMidStroke = 1.5.dp.toPx()
                    val tickSmallStroke = 1.dp.toPx()
                    val thumbW = if (isDragging) 40.dp.toPx() else 34.dp.toPx()
                    val thumbH = if (isDragging) 22.dp.toPx() else 18.dp.toPx()
                    val thumbRadius = CornerRadius(7.dp.toPx(), 7.dp.toPx())
                    val thumbHalfW = thumbW / 2f
                    val thumbHalfH = thumbH / 2f
                    val thumbSize = Size(thumbW, thumbH)
                    val highlightInset = 5.dp.toPx()
                    val highlightOffsetY = 1.5.dp.toPx()
                    val highlightStroke = 1.5.dp.toPx()
                    val centerBarHalf = 9.dp.toPx()
                    val centerBarStroke = 2.dp.toPx()
                    val guideColor = palette.accent.copy(alpha = 0.35f)
                    val topSheenColor = Color.White.copy(alpha = 0.65f)
                    val isRightUnlinked = !stereoLinked && channelFocus == StereoChannelFocus.RIGHT

                    onDrawBehind {
                        // Phase Skipping: read gain strictly inside the Draw phase
                        val currentGain = readGain()
                        val gainActive = abs(currentGain) >= 0.05f || isDragging
                        val thumbColor = when {
                            !enabled -> palette.textMuted
                            isRightUnlinked -> palette.channelSecondary
                            isDragging || gainActive || isHighlighted -> palette.accent
                            else -> palette.textSecondary
                        }

                        if (isDragging) {
                            drawLine(
                                color = guideColor,
                                start = Offset(x, 0f),
                                end = Offset(x, size.height),
                                strokeWidth = guideWidth,
                                cap = StrokeCap.Round
                            )
                        }

                        // Solid neutral track
                        drawLine(
                            color = palette.faderTrack,
                            start = Offset(x, inset),
                            end = Offset(x, size.height - inset),
                            strokeWidth = trackWidth,
                            cap = StrokeCap.Round
                        )

                        // dB scale tick marks (center 0 dB tick emphasized)
                        for (n in 0..6) {
                            val tickY = inset + usable * n / 6f
                            drawLine(
                                color = if (n == 3) palette.textSecondary else palette.borderSubtle,
                                start = Offset(tickStart, tickY),
                                end = Offset(if (n == 3) tickMidEnd else tickSmallEnd, tickY),
                                strokeWidth = if (n == 3) tickMidStroke else tickSmallStroke
                            )
                        }

                        // Active fill from 0 dB center to thumb
                        val y = inset + (15f - currentGain.coerceIn(-15f, 15f)) / 30f * usable
                        drawLine(
                            color = thumbColor,
                            start = Offset(x, size.height / 2f),
                            end = Offset(x, y),
                            strokeWidth = activeFillWidth,
                            cap = StrokeCap.Round
                        )

                        // Tactile thumb cap
                        val thumbLeft = x - thumbHalfW
                        val thumbTop = y - thumbHalfH
                        drawRoundRect(
                            color = thumbColor,
                            topLeft = Offset(thumbLeft, thumbTop),
                            size = thumbSize,
                            cornerRadius = thumbRadius
                        )
                        if (isDragging) {
                            drawLine(
                                color = topSheenColor,
                                start = Offset(thumbLeft + highlightInset, thumbTop + highlightOffsetY),
                                end = Offset(thumbLeft + thumbW - highlightInset, thumbTop + highlightOffsetY),
                                strokeWidth = highlightStroke
                            )
                        }
                        drawLine(
                            color = palette.onAccent,
                            start = Offset(x - centerBarHalf, y),
                            end = Offset(x + centerBarHalf, y),
                            strokeWidth = centerBarStroke,
                            cap = StrokeCap.Round
                        )
                    }
                }
        )

        Text(
            text = band.label,
            modifier = Modifier.padding(vertical = GlassTokens.Space8),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isDragging || isHighlighted) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isDragging || isHighlighted) palette.accent else palette.textSecondary
        )
    }

    if (editing) {
        val parsed = input.replace(',', '.').toFloatOrNull()
        val valid = parsed != null && parsed.isFinite() && parsed in -15f..15f
        GlassDialog(
            title = "Gain ${band.label}",
            onDismissRequest = { editing = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        parsed?.let {
                            dragGainState.floatValue = it
                            update(it)
                        }
                        editing = false
                    },
                    enabled = valid
                ) {
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

/**
 * Isolated leaf composable for the fader's numeric dB readout so only this text recomposes
 * when the rounded 0.1 dB label actually changes during high-frequency drag gestures.
 */
@Composable
private fun FaderValueReadout(
    bandIndex: Int,
    bandLabel: String,
    enabled: Boolean,
    isDragging: Boolean,
    isHighlighted: Boolean,
    readGain: () -> Float,
    valueAlphaState: State<Float>,
    palette: HarmoniPalette,
    onClick: () -> Unit
) {
    val formattedGain by remember(readGain) {
        derivedStateOf { String.format(Locale.US, "%+.1f", readGain()) }
    }
    val activeGain by remember(readGain, isDragging) {
        derivedStateOf { abs(readGain()) >= 0.05f || isDragging }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .testTag("eq_value_$bandIndex")
            .semantics {
                contentDescription = "Masukkan gain $bandLabel, $formattedGain dB"
                if (!enabled) disabled()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = formattedGain,
            color = when {
                !enabled -> palette.textMuted
                activeGain || isHighlighted -> palette.accent
                else -> palette.textPrimary
            },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isDragging) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.graphicsLayer { alpha = valueAlphaState.value }
        )
    }
}
