package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.model.AudioSourceMode
import com.example.model.EqFrequencySection
import com.example.model.EqualizerUiState
import com.example.model.QuickTuningIcon
import com.example.model.QuickTuningPreset
import com.example.model.QuickTuningPresets
import com.example.model.StereoChannelFocus
import com.example.model.default20Bands
import com.example.ui.EqualizerViewModel
import com.example.ui.components.ConsolePanel
import com.example.ui.components.EqSliderCurveOverlay
import com.example.ui.components.FrequencyCurveCanvas
import com.example.ui.components.ProEqFader
import com.example.ui.components.ValueSlider
import com.example.ui.components.dbLabel
import com.example.ui.components.glass.GlassAbToolbar
import com.example.ui.components.glass.GlassPlayPauseButton
import com.example.ui.components.glass.GlassPrimaryPillButton
import com.example.ui.theme.GlassTokens
import com.example.ui.theme.HarmoniEqTheme
import com.example.ui.theme.LocalHarmoniPalette
import com.example.ui.theme.LocalThemeController
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.ThemeSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun EqualizerScreen(
    state: EqualizerUiState,
    vm: EqualizerViewModel,
    onPresets: () -> Unit,
    onYouTube: () -> Unit,
    onDevices: () -> Unit,
    onAutoEq: () -> Unit = {},
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    onInteractionChange: (Boolean) -> Unit = {},
    onScrolledUnderChange: (Boolean) -> Unit = {}
) {
    EqualizerScreenContent(
        state = state,
        onToggleMasterPower = vm::setMasterPower,
        onSetSimpleMode = vm::setSimpleMode,
        onUpdateSimpleTone = vm::updateSimpleTone,
        onSelectChannelFocus = vm::selectChannelFocus,
        onUpdateBandLevel = vm::updateBandLevel,
        onResetBandToZero = vm::resetBandToZero,
        onResetAllFlat = {
            vm.resetAllFlat()
            vm.reportMessage("EQ reset to flat (0.0 dB). Undo available.")
        },
        onApplyQuickTuning = { preset ->
            vm.applyQuickTuningPreset(preset)
        },
        onDisableAutoEq = vm::disableAutoEq,
        onBeginGesture = vm::beginGesture,
        onEndGesture = vm::endGesture,
        onUndo = vm::undo,
        onRedo = vm::redo,
        onToggleAbSlot = vm::toggleABSlot,
        onCopyToOtherSlot = vm::copyToOtherSlot,
        onSmoothCurve = vm::smoothCurve,
        onMirrorLeftToRight = vm::mirrorLeftToRight,
        onSwitchAudioSourceMode = vm::switchAudioSourceMode,
        onToggleTestAudioPlayback = vm::toggleTestAudioPlayback,
        onCycleTestTrackStyle = vm::cycleTestTrackStyle,
        onPresets = onPresets,
        onYouTube = onYouTube,
        onDevices = onDevices,
        onAutoEq = onAutoEq,
        onSave = onSave,
        modifier = modifier,
        onInteractionChange = onInteractionChange,
        onScrolledUnderChange = onScrolledUnderChange
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EqualizerScreenContent(
    state: EqualizerUiState,
    onToggleMasterPower: (Boolean) -> Unit,
    onSetSimpleMode: (Boolean) -> Unit,
    onUpdateSimpleTone: (Int, Float) -> Unit,
    onSelectChannelFocus: (StereoChannelFocus) -> Unit,
    onUpdateBandLevel: (Int, Float) -> Unit,
    onResetBandToZero: (Int) -> Unit,
    onResetAllFlat: () -> Unit,
    onApplyQuickTuning: (QuickTuningPreset) -> Unit,
    onDisableAutoEq: () -> Unit = {},
    onBeginGesture: () -> Unit,
    onEndGesture: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onToggleAbSlot: () -> Unit,
    onCopyToOtherSlot: () -> Unit,
    onSmoothCurve: () -> Unit,
    onMirrorLeftToRight: () -> Unit,
    onSwitchAudioSourceMode: (AudioSourceMode) -> Unit,
    onToggleTestAudioPlayback: () -> Unit,
    onCycleTestTrackStyle: () -> Unit,
    onPresets: () -> Unit,
    onYouTube: () -> Unit,
    onDevices: () -> Unit,
    onAutoEq: () -> Unit = {},
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    onInteractionChange: (Boolean) -> Unit = {},
    onScrolledUnderChange: (Boolean) -> Unit = {}
) {
    val palette = LocalHarmoniPalette.current
    val themeController = LocalThemeController.current
    val reduceMotion = themeController.effectiveReduceMotion
    val haptics = LocalHapticFeedback.current

    var showCurve by remember { mutableStateOf(false) }
    var showTools by remember { mutableStateOf(false) }
    var localTipDismissed by remember { mutableStateOf(false) }
    var activeDragBandIndex by remember { mutableStateOf<Int?>(null) }
    var lastSimpleToneGroup by remember { mutableIntStateOf(0) }
    var focusedBandRange by remember { mutableStateOf<IntRange?>(null) }
    var pendingScrollBandIndex by remember { mutableStateOf<Int?>(null) }

    val scrollState = rememberScrollState()
    val faders = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val activePreset = state.presets.find { it.id == state.activePresetId }
    val activeQuickTuning = QuickTuningPresets.defaultItems.find { it.id == state.activeQuickTuningId }

    val activeSection by remember {
        derivedStateOf {
            val firstIdx = faders.firstVisibleItemIndex.coerceIn(0, 19)
            EqFrequencySection.forBandIndex(firstIdx)
        }
    }

    // Automatically scroll to requested frequency section when switching from Simple -> Advanced
    LaunchedEffect(state.simpleMode, pendingScrollBandIndex) {
        val targetIdx = pendingScrollBandIndex
        if (!state.simpleMode && targetIdx != null) {
            if (reduceMotion) {
                faders.scrollToItem(targetIdx)
            } else {
                faders.animateScrollToItem(targetIdx)
            }
            pendingScrollBandIndex = null
        }
    }

    // Gently clear the guided focus highlight after user has oriented themselves
    LaunchedEffect(focusedBandRange) {
        if (focusedBandRange != null) {
            delay(4500L)
            focusedBandRange = null
        }
    }

    val currentScrolledUnderChange by rememberUpdatedState(onScrolledUnderChange)
    val currentInteractionChange by rememberUpdatedState(onInteractionChange)

    val isDraggingFader by remember {
        derivedStateOf { activeDragBandIndex != null }
    }

    // snapshotFlow reads scroll offsets & drag state outside composition to prevent per-pixel recomposition.
    LaunchedEffect(scrollState, faders) {
        launch {
            snapshotFlow { scrollState.value > 8 }
                .distinctUntilChanged()
                .collect { currentScrolledUnderChange(it) }
        }
        launch {
            snapshotFlow { scrollState.isScrollInProgress || faders.isScrollInProgress || isDraggingFader }
                .distinctUntilChanged()
                .collect { currentInteractionChange(it) }
        }
    }

    val showOnboardingTip = !state.simpleMode && !themeController.settings.advancedTipDismissed && !localTipDismissed

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(GlassTokens.Space16),
        verticalArrangement = Arrangement.spacedBy(GlassTokens.Space16)
    ) {
        // 1. Prominent Master EQ Toggle & Output Status Hero Deck (always top of screen)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("output_status"),
            shape = RoundedCornerShape(GlassTokens.CornerRadius),
            color = if (state.eqEnabled) {
                palette.surface
            } else {
                palette.surface.copy(alpha = 0.96f)
            },
            border = BorderStroke(
                width = if (state.eqEnabled) 1.5.dp else GlassTokens.BorderWidth,
                color = if (state.eqEnabled) palette.accent.copy(alpha = 0.55f) else palette.borderSubtle
            )
        ) {
            Column(
                modifier = Modifier.padding(GlassTokens.Space16),
                verticalArrangement = Arrangement.spacedBy(GlassTokens.Space16)
            ) {
                // Unmistakable Master EQ Switch Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(GlassTokens.Space8)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(GlassTokens.Space4)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(GlassTokens.Space8)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (state.eqEnabled) palette.statusSuccess else palette.statusWarning)
                            )
                            Text(
                                text = if (state.eqEnabled) "EQ ACTIVE" else "EQ BYPASSED",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (state.eqEnabled) palette.statusSuccess else palette.statusWarning
                            )
                        }
                        Text(
                            text = when {
                                !state.eqEnabled -> "Bypass active. Audio passes through unprocessed."
                                !state.hardwareAttached -> "Waiting for a compatible audio session"
                                state.audioSourceMode == AudioSourceMode.STUDIO_TEST -> "Studio test DSP active • 20-band stereo"
                                else -> "Processing active • compare with Master Switch"
                            },
                            color = palette.textSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Switch(
                        checked = state.eqEnabled,
                        onCheckedChange = { enabled ->
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleMasterPower(enabled)
                        },
                        thumbContent = {
                            Icon(
                                imageVector = Icons.Rounded.PowerSettingsNew,
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize)
                            )
                        },
                        modifier = Modifier
                            .defaultMinSize(minWidth = 52.dp, minHeight = 48.dp)
                            .testTag("master_eq_switch")
                            .semantics {
                                contentDescription = if (state.eqEnabled) {
                                    "Master Equalizer On. Tap to bypass."
                                } else {
                                    "Master Equalizer Bypassed. Tap to enable."
                                }
                            }
                    )
                }

                HorizontalDivider(color = palette.borderSubtle)

                // Output device routing row
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(GlassTokens.Space8)
                ) {
                    Icon(
                        if (state.output.route?.kind == "Bluetooth") Icons.Rounded.BluetoothAudio else Icons.Rounded.Speaker,
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(Modifier.weight(1f)) {
                        Text("Output audio", color = palette.textMuted, style = MaterialTheme.typography.labelMedium)
                        Text(
                            state.detectedOutputDevice,
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.textPrimary
                        )
                    }
                    TextButton(onClick = onDevices, modifier = Modifier.testTag("output_manage")) {
                        Text("Kelola")
                    }
                }
            }
        }

        // 1b. Headphone Correction (AutoEQ) Entry Card
        val activeAutoEq = state.activeAutoEqProfile
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onAutoEq)
                .testTag("open_autoeq_button"),
            shape = RoundedCornerShape(GlassTokens.CornerRadius),
            color = if (activeAutoEq != null) palette.accentContainer else palette.surface,
            border = BorderStroke(
                width = if (activeAutoEq != null) 1.5.dp else GlassTokens.BorderWidth,
                color = if (activeAutoEq != null) palette.accent.copy(alpha = 0.65f) else palette.borderSubtle
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 56.dp)
                    .padding(horizontal = GlassTokens.Space16, vertical = GlassTokens.Space12),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GlassTokens.Space8)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Headphones,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(22.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            activeAutoEq != null && state.eqEnabled -> "AutoEQ Active"
                            activeAutoEq != null -> "AutoEQ Selected • EQ Off"
                            else -> "Headphone Correction (AutoEQ)"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = when {
                            activeAutoEq != null && state.eqEnabled -> palette.accent
                            activeAutoEq != null -> palette.statusWarning
                            else -> palette.textMuted
                        }
                    )
                    Text(
                        text = activeAutoEq?.let { "${it.name} • ${it.type}" }
                            ?: "Select your headphone model for automatic EQ",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (activeAutoEq != null && !state.eqEnabled) palette.textSecondary else palette.textPrimary,
                        maxLines = 1
                    )
                }
                if (activeAutoEq != null) {
                    TextButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDisableAutoEq()
                        },
                        modifier = Modifier.testTag("disable_autoeq_inline_button")
                    ) {
                        Text("Disable")
                    }
                } else {
                    Text(
                        text = "Browse",
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.accent
                    )
                }
            }
        }

        // 2. Active Sound Character & Preset Shortcut Header
        val presetHeader: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(GlassTokens.Space4)) {
                Text("Karakter suara", style = MaterialTheme.typography.labelMedium, color = palette.textMuted)
                Text(
                    activeAutoEq?.name ?: activeQuickTuning?.title ?: activePreset?.name ?: "Kurva kustom",
                    style = MaterialTheme.typography.titleLarge,
                    color = palette.textPrimary
                )
                if (state.presetDirty && activeQuickTuning == null && activeAutoEq == null) {
                    Text(
                        "Diubah, belum disimpan sebagai preset",
                        color = palette.statusWarning,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        val presetButton: @Composable () -> Unit = {
            Row(
                Modifier
                    .clickable(onClick = onPresets)
                    .defaultMinSize(minHeight = 48.dp)
                    .padding(horizontal = GlassTokens.Space8)
                    .semantics { role = Role.Button },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Preset",
                    maxLines = 1,
                    softWrap = false,
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.accent
                )
            }
        }

        if (LocalDensity.current.fontScale >= 1.5f) {
            Column(Modifier.fillMaxWidth()) {
                presetHeader()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { presetButton() }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { presetHeader() }
                presetButton()
            }
        }

        // 3. Mode Switcher (Simple vs Advanced 20)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GlassTokens.Space8)) {
            FilterChip(
                selected = state.simpleMode,
                onClick = { onSetSimpleMode(true) },
                label = { Text("Simple") },
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("mode_simple")
            )
            FilterChip(
                selected = !state.simpleMode,
                onClick = { onSetSimpleMode(false) },
                label = { Text("Advanced 20") },
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("mode_advanced")
            )
        }

        if (state.simpleMode) {
            // Simple Mode: Bass / Mid / Treble + Guided "Fine-tune this sound" transition
            ConsolePanel {
                val groups = listOf(0..6, 7..13, 14..19)
                val custom = groups.any { range ->
                    state.bands.slice(range).flatMap { listOf(it.levelDb, it.rightLevelDb) }.distinct().size > 1
                }
                if (custom) {
                    Text(
                        "Kurva kustom. Menggeser satu kontrol meratakan kelompok frekuensinya pada L/R. Gunakan Undo untuk kembali.",
                        color = palette.statusWarning,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                listOf("Bass", "Mid", "Treble").forEachIndexed { i, label ->
                    val value = state.bands.slice(groups[i]).map { (it.levelDb + it.rightLevelDb) / 2 }.average().toFloat()
                    ValueSlider(
                        title = label,
                        value = value,
                        range = -15f..15f,
                        label = dbLabel(value),
                        tag = "simple_${label.lowercase()}",
                        enabled = state.eqEnabled,
                        onChange = {
                            lastSimpleToneGroup = i
                            onUpdateSimpleTone(i, it)
                        },
                        onStart = {
                            lastSimpleToneGroup = i
                            onInteractionChange(true)
                            onBeginGesture()
                        },
                        onFinish = { onEndGesture(); onInteractionChange(false) }
                    )
                }

                HorizontalDivider(color = palette.borderSubtle)

                // Guided path from Simple Mode to Advanced 20-band mode focused on relevant bands
                Column(verticalArrangement = Arrangement.spacedBy(GlassTokens.Space8)) {
                    FilledTonalButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val targetRange = EqFrequencySection.simpleGroupRange(lastSimpleToneGroup)
                            focusedBandRange = targetRange
                            pendingScrollBandIndex = targetRange.first
                            onSetSimpleMode(false)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("fine_tune_sound_button")
                    ) {
                        Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(GlassTokens.Space8))
                        Text("Fine-tune this sound")
                        Spacer(Modifier.width(GlassTokens.Space8))
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Text(
                        text = "Opens 20-band view focused on ${EqFrequencySection.simpleGroupName(lastSimpleToneGroup)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary
                    )
                }
            }
        } else {
            // 4. Advanced Mode Guided Onboarding Tooltip (first-open, non-intrusive)
            AnimatedVisibility(
                visible = showOnboardingTip,
                enter = if (reduceMotion) fadeIn() else fadeIn() + expandVertically(),
                exit = if (reduceMotion) fadeOut() else fadeOut() + shrinkVertically()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("advanced_onboarding_tip")
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showCurve = true
                            localTipDismissed = true
                            themeController.dismissAdvancedTip()
                        },
                    shape = RoundedCornerShape(16.dp),
                    color = palette.accentContainer,
                    border = BorderStroke(1.dp, palette.accent.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = GlassTokens.Space16, top = GlassTokens.Space8, bottom = GlassTokens.Space8, end = GlassTokens.Space8),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(GlassTokens.Space8)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoGraph,
                            contentDescription = null,
                            tint = palette.accent,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Drag the sliders to shape your sound. Tap here to see the curve.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                localTipDismissed = true
                                themeController.dismissAdvancedTip()
                            },
                            modifier = Modifier.testTag("dismiss_onboarding_tip")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Dismiss tip",
                                tint = palette.textSecondary
                            )
                        }
                    }
                }
            }

            // 5. Quick Tuning Section (4 friendly animated preset chips)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(GlassTokens.Space8)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Quick Tuning",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.textPrimary
                    )
                    Text(
                        text = "One-tap curve",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.textMuted
                    )
                }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(GlassTokens.Space8),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_tuning_row")
                ) {
                    items(
                        items = QuickTuningPresets.defaultItems,
                        key = { it.id },
                        contentType = { "quick_tuning_chip" }
                    ) { preset ->
                        val isSelected = state.activeQuickTuningId == preset.id
                        val chipIcon = when (preset.icon) {
                            QuickTuningIcon.BASS -> Icons.Rounded.GraphicEq
                            QuickTuningIcon.VOCALS -> Icons.Rounded.RecordVoiceOver
                            QuickTuningIcon.SMOOTH -> Icons.Rounded.Spa
                            QuickTuningIcon.AIR -> Icons.Rounded.Air
                        }
                        Surface(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                focusedBandRange = preset.focusSection.bandRange
                                onApplyQuickTuning(preset)
                                scope.launch {
                                    if (reduceMotion) {
                                        faders.scrollToItem(preset.focusSection.bandRange.first)
                                    } else {
                                        faders.animateScrollToItem(preset.focusSection.bandRange.first)
                                    }
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) palette.accentContainer else palette.surface,
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) palette.accent else palette.borderSubtle
                            ),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 56.dp)
                                .testTag("quick_tuning_${preset.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = GlassTokens.Space16, vertical = GlassTokens.Space8),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(GlassTokens.Space8)
                            ) {
                                Icon(
                                    imageVector = chipIcon,
                                    contentDescription = null,
                                    tint = if (isSelected) palette.accent else palette.textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = preset.title,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSelected) palette.accent else palette.textPrimary,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = preset.subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = palette.textSecondary,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 6. 20-Band Stereo Slider Deck with Functional Sections & Real-Time Bezier Curve Overlay
            ConsolePanel {
                // Channel focus + live dragged band badge
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(GlassTokens.Space8),
                    verticalArrangement = Arrangement.spacedBy(GlassTokens.Space8)
                ) {
                    StereoChannelFocus.entries.forEach { focus ->
                        FilterChip(
                            selected = state.activeChannelFocus == focus,
                            onClick = { onSelectChannelFocus(focus) },
                            label = { Text(focus.shortLabel) },
                            modifier = Modifier.testTag("stereo_channel_${focus.name.lowercase()}")
                        )
                    }
                    val draggedBand = activeDragBandIndex?.let { state.bands.getOrNull(it) }
                    Text(
                        text = if (draggedBand != null) {
                            val sec = EqFrequencySection.forBandIndex(draggedBand.index)
                            val gainVal = if (!state.stereoLinked && state.activeChannelFocus == StereoChannelFocus.RIGHT) {
                                draggedBand.rightLevelDb
                            } else {
                                draggedBand.levelDb
                            }
                            "${sec.label} • ${draggedBand.label} • ${dbLabel(gainVal)}"
                        } else {
                            "20 bands -> ${state.hardwareBandCount.takeIf { it > 0 } ?: "?"} hw"
                        },
                        modifier = Modifier.padding(top = GlassTokens.Space16),
                        color = if (draggedBand != null) palette.accent else palette.textSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (draggedBand != null) FontWeight.SemiBold else FontWeight.Normal
                    )
                }

                // 7 Functional Frequency Section Jump Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(GlassTokens.Space8),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("frequency_section_chips")
                ) {
                    items(
                        items = EqFrequencySection.entries,
                        key = { it.name },
                        contentType = { "frequency_section_chip" }
                    ) { section ->
                        val isCurrentSection = activeSection == section
                        FilterChip(
                            selected = isCurrentSection,
                            onClick = {
                                focusedBandRange = section.bandRange
                                scope.launch {
                                    if (reduceMotion) {
                                        faders.scrollToItem(section.bandRange.first)
                                    } else {
                                        faders.animateScrollToItem(section.bandRange.first)
                                    }
                                }
                            },
                            label = { Text(section.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = palette.accentContainer,
                                selectedLabelColor = palette.accent
                            ),
                            modifier = Modifier.testTag("bank_jump_${section.bandRange.first}")
                        )
                    }
                }

                // 20-Band Slider Bank + Real-Time Cubic Bezier Curve Overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(272.dp)
                ) {
                    LazyRow(
                        state = faders,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("eq_fader_bank"),
                        horizontalArrangement = Arrangement.spacedBy(GlassTokens.Space8)
                    ) {
                        itemsIndexed(
                            items = state.bands,
                            key = { _, band -> band.index },
                            contentType = { _, _ -> "pro_eq_fader" }
                        ) { _, band ->
                            val section = EqFrequencySection.forBandIndex(band.index)
                            val isSectionStart = band.index == section.bandRange.first
                            val isBandHighlighted = focusedBandRange?.contains(band.index) == true

                            Row(
                                modifier = Modifier.fillMaxHeight(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Subtle vertical separator between functional frequency sections
                                if (isSectionStart && band.index > 0) {
                                    Box(
                                        modifier = Modifier
                                            .padding(end = GlassTokens.Space8, top = 24.dp, bottom = GlassTokens.Space8)
                                            .width(1.dp)
                                            .fillMaxHeight()
                                            .background(palette.border)
                                    )
                                }
                                Column(
                                    modifier = Modifier.fillMaxHeight(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    // Uniform 24.dp section header slot so every fader track stays vertically aligned
                                    Box(
                                        modifier = Modifier
                                            .height(24.dp)
                                            .padding(bottom = GlassTokens.Space4),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSectionStart) {
                                            Text(
                                                text = section.label,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isBandHighlighted) palette.accent else palette.textSecondary,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                    ProEqFader(
                                        band = band,
                                        enabled = state.eqEnabled,
                                        stereoLinked = state.stereoLinked,
                                        channelFocus = state.activeChannelFocus,
                                        onValueChange = { onUpdateBandLevel(band.index, it) },
                                        onDoubleTapReset = { onResetBandToZero(band.index) },
                                        modifier = Modifier.weight(1f),
                                        onGestureStart = {
                                            onInteractionChange(true)
                                            onBeginGesture()
                                        },
                                        onGestureEnd = {
                                            onEndGesture()
                                            onInteractionChange(false)
                                        },
                                        isHighlighted = isBandHighlighted,
                                        onDragActiveChange = { dragging ->
                                            activeDragBandIndex = if (dragging) band.index else {
                                                if (activeDragBandIndex == band.index) null else activeDragBandIndex
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Smooth cubic Bezier curve overlay spanning the exact width of the 20-band slider tracks
                    EqSliderCurveOverlay(
                        bands = state.bands,
                        faderListState = faders,
                        eqEnabled = state.eqEnabled,
                        stereoLinked = state.stereoLinked,
                        activeChannelFocus = state.activeChannelFocus,
                        activeDragBandIndex = activeDragBandIndex,
                        topInset = 84.dp,
                        bottomInset = 44.dp,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Text(
                    "Tap dB value for precision entry • double-tap any fader to reset to 0 dB",
                    color = palette.textSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // 7. Quick History, Reset to Flat & Save Actions (8dp spacing)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(GlassTokens.Space8),
            verticalArrangement = Arrangement.spacedBy(GlassTokens.Space8)
        ) {
            OutlinedButton(
                onClick = onUndo,
                enabled = state.canUndo,
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("undo")
            ) {
                Text("Undo")
            }
            OutlinedButton(
                onClick = onRedo,
                enabled = state.canRedo,
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("redo")
            ) {
                Text("Redo")
            }
            OutlinedButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onResetAllFlat()
                },
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("reset_flat_inline_button")
            ) {
                Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(GlassTokens.Space4))
                Text("Reset to Flat")
            }
            GlassPrimaryPillButton(text = "Simpan suara", onClick = onSave)
        }

        // 8. Expandable Full-Spectrum 20-Band Interactive Curve
        TextButton(onClick = { showCurve = !showCurve }, modifier = Modifier.testTag("show_curve")) {
            Icon(Icons.Rounded.AutoGraph, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(GlassTokens.Space8))
            Text(if (showCurve) "Hide full 20-band curve" else "Show full 20-band curve")
        }
        if (showCurve) {
            FrequencyCurveCanvas(
                bands = state.bands,
                eqEnabled = state.eqEnabled,
                stereoLinked = state.stereoLinked,
                activeChannelFocus = state.activeChannelFocus,
                spectrumBars = FloatArray(32),
                onSelectChannelFocus = onSelectChannelFocus,
                onBandLevelChange = onUpdateBandLevel,
                highlightedBandIndex = activeDragBandIndex,
                autoEqActive = state.activeAutoEqProfile != null,
                onGestureStart = { onInteractionChange(true); onBeginGesture() },
                onGestureEnd = { onEndGesture(); onInteractionChange(false) }
            )
        }

        // 9. A/B Comparison & Curve Utilities
        TextButton(onClick = { showTools = !showTools }, modifier = Modifier.testTag("show_tools")) {
            Text(if (showTools) "Tutup alat lanjutan" else "A/B, reset & alat lanjutan")
        }
        if (showTools) {
            GlassAbToolbar(
                activeSlot = state.activeAbSlot,
                onToggleSlot = onToggleAbSlot,
                onCopyToOther = onCopyToOtherSlot
            )
            ConsolePanel {
                Text(
                    "A/B menyimpan EQ dan efek, bukan master power. Tidak ada loudness matching.",
                    color = palette.textSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(GlassTokens.Space8)) {
                    TextButton(onSmoothCurve, Modifier.testTag("smooth_curve_button")) { Text("Haluskan") }
                    TextButton(onMirrorLeftToRight, Modifier.testTag("mirror_lr_button")) { Text("Salin L ke R") }
                    TextButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onResetAllFlat()
                        }
                    ) { Text("Reset EQ saja") }
                }
            }
        }

        // 10. Audio Source & Built-in Studio Test Deck
        ConsolePanel {
            Text("Sumber & uji dengar", color = palette.textMuted, style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(GlassTokens.Space8)) {
                AudioSourceMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.audioSourceMode == mode,
                        onClick = { onSwitchAudioSourceMode(mode) },
                        label = { Text(if (mode == AudioSourceMode.GLOBAL_SYSTEM) "Aplikasi lain" else "Audio uji") }
                    )
                }
            }
            if (state.audioSourceMode == AudioSourceMode.STUDIO_TEST) {
                Text(
                    "Mulai dengan volume nyaman. Meter hanya mengukur sinyal uji internal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(GlassTokens.Space8)) {
                    GlassPlayPauseButton(
                        isPlaying = state.isTestAudioPlaying,
                        onClick = onToggleTestAudioPlayback,
                        modifier = Modifier.testTag("test_audio_play_button")
                    )
                    TextButton(onCycleTestTrackStyle) { Text(state.testTrackStyle.label) }
                }
            } else {
                Text(
                    "Efek aplikasi lain bergantung pada sesi pemutar, Android, dan perangkat.",
                    color = palette.textSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(onYouTube, Modifier.testTag("launch_youtube_button")) { Text("Buka YouTube") }
            }
        }
    }
}

// --- Compose Previews for the improved Equalizer layout ---

@Preview(name = "Equalizer Advanced Dark", showBackground = true, backgroundColor = 0xFF0E1013, widthDp = 390, heightDp = 920)
@Composable
private fun PreviewEqualizerAdvancedDark() {
    val sampleBands = default20Bands().mapIndexed { i, b ->
        b.copy(
            levelDb = QuickTuningPresets.MoreBass.bandGainsDb[i],
            rightLevelDb = QuickTuningPresets.MoreBass.bandGainsDb[i]
        )
    }
    val previewState = EqualizerUiState(
        eqEnabled = true,
        simpleMode = false,
        bands = sampleBands,
        activeQuickTuningId = QuickTuningPresets.MoreBass.id,
        hardwareAttached = true,
        detectedOutputDevice = "Sony WH-1000XM5"
    )
    HarmoniEqTheme(previewSettings = ThemeSettings(mode = ThemeMode.DARK, dynamicColor = false)) {
        EqualizerScreenContent(
            state = previewState,
            onToggleMasterPower = {},
            onSetSimpleMode = {},
            onUpdateSimpleTone = { _, _ -> },
            onSelectChannelFocus = {},
            onUpdateBandLevel = { _, _ -> },
            onResetBandToZero = {},
            onResetAllFlat = {},
            onApplyQuickTuning = {},
            onBeginGesture = {},
            onEndGesture = {},
            onUndo = {},
            onRedo = {},
            onToggleAbSlot = {},
            onCopyToOtherSlot = {},
            onSmoothCurve = {},
            onMirrorLeftToRight = {},
            onSwitchAudioSourceMode = {},
            onToggleTestAudioPlayback = {},
            onCycleTestTrackStyle = {},
            onPresets = {},
            onYouTube = {},
            onDevices = {},
            onSave = {}
        )
    }
}

@Preview(name = "Equalizer Advanced Light", showBackground = true, backgroundColor = 0xFFF5F2EE, widthDp = 390, heightDp = 920)
@Composable
private fun PreviewEqualizerAdvancedLight() {
    val sampleBands = default20Bands().mapIndexed { i, b ->
        b.copy(
            levelDb = QuickTuningPresets.ClearerVocals.bandGainsDb[i],
            rightLevelDb = QuickTuningPresets.ClearerVocals.bandGainsDb[i]
        )
    }
    val previewState = EqualizerUiState(
        eqEnabled = true,
        simpleMode = false,
        bands = sampleBands,
        activeQuickTuningId = QuickTuningPresets.ClearerVocals.id,
        hardwareAttached = true,
        detectedOutputDevice = "Pixel Buds Pro"
    )
    HarmoniEqTheme(previewSettings = ThemeSettings(mode = ThemeMode.LIGHT, dynamicColor = false)) {
        EqualizerScreenContent(
            state = previewState,
            onToggleMasterPower = {},
            onSetSimpleMode = {},
            onUpdateSimpleTone = { _, _ -> },
            onSelectChannelFocus = {},
            onUpdateBandLevel = { _, _ -> },
            onResetBandToZero = {},
            onResetAllFlat = {},
            onApplyQuickTuning = {},
            onBeginGesture = {},
            onEndGesture = {},
            onUndo = {},
            onRedo = {},
            onToggleAbSlot = {},
            onCopyToOtherSlot = {},
            onSmoothCurve = {},
            onMirrorLeftToRight = {},
            onSwitchAudioSourceMode = {},
            onToggleTestAudioPlayback = {},
            onCycleTestTrackStyle = {},
            onPresets = {},
            onYouTube = {},
            onDevices = {},
            onSave = {}
        )
    }
}

@Preview(name = "Equalizer Simple Mode Dark", showBackground = true, backgroundColor = 0xFF0E1013, widthDp = 390, heightDp = 780)
@Composable
private fun PreviewEqualizerSimpleDark() {
    val previewState = EqualizerUiState(
        eqEnabled = true,
        simpleMode = true,
        hardwareAttached = true,
        detectedOutputDevice = "Speaker Perangkat"
    )
    HarmoniEqTheme(previewSettings = ThemeSettings(mode = ThemeMode.DARK, dynamicColor = false)) {
        EqualizerScreenContent(
            state = previewState,
            onToggleMasterPower = {},
            onSetSimpleMode = {},
            onUpdateSimpleTone = { _, _ -> },
            onSelectChannelFocus = {},
            onUpdateBandLevel = { _, _ -> },
            onResetBandToZero = {},
            onResetAllFlat = {},
            onApplyQuickTuning = {},
            onBeginGesture = {},
            onEndGesture = {},
            onUndo = {},
            onRedo = {},
            onToggleAbSlot = {},
            onCopyToOtherSlot = {},
            onSmoothCurve = {},
            onMirrorLeftToRight = {},
            onSwitchAudioSourceMode = {},
            onToggleTestAudioPlayback = {},
            onCycleTestTrackStyle = {},
            onPresets = {},
            onYouTube = {},
            onDevices = {},
            onSave = {}
        )
    }
}
