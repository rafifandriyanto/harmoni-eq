package com.example.ui

import android.app.Application
import androidx.compose.runtime.Stable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioForegroundService
import com.example.audio.EqPowerBridge
import com.example.audio.EqualizerService
import com.example.audio.StudioAudioEngine
import com.example.data.PersistedEqSettings
import com.example.data.PresetRepository
import com.example.data.PresetCodec
import com.example.data.autoeq.AutoEqRepository
import com.example.model.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.round

@Stable
class EqualizerViewModel(
    application: Application,
    private val repository: PresetRepository,
    private val autoEqRepository: AutoEqRepository
) : AndroidViewModel(application) {
    // AndroidViewModelFactory merefleksikan konstruktor (Application) tunggal.
    constructor(application: Application) : this(
        application,
        PresetRepository(application.applicationContext),
        AutoEqRepository(application.applicationContext)
    )
    constructor(application: Application, repository: PresetRepository) : this(
        application,
        repository,
        AutoEqRepository(application.applicationContext)
    )
    private val appContext = application.applicationContext
    private val audioEngine = StudioAudioEngine
    private val _uiState = MutableStateFlow(EqualizerUiState(presets = repository.builtInPresets))
    val uiState: StateFlow<EqualizerUiState> = _uiState.asStateFlow()
    val vuLevels = audioEngine.vuLevels
    val stereoScopePoints = audioEngine.stereoScopePoints
    private var customPresets = emptyList<EqPreset>()
    private var persistJob: Job? = null
    private val persistScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate)
    private var userInteracted = false
    private var restored = false
    private var persistRequested = false
    private var autoProfilesTouched = false
    private data class Entry(val sound: SoundSnapshot, val presetId: String)
    private class History {
        val undo = ArrayDeque<Entry>()
        val redo = ArrayDeque<Entry>()
    }
    private val histories = mapOf("A" to History(), "B" to History())
    private val slots = mutableMapOf<String, Entry>()
    private var gestureStart: Entry? = null
    private var audioSyncJob: Job? = null
    private val history get() = histories.getValue(_uiState.value.activeAbSlot)
    private fun entry() = Entry(_uiState.value.soundSnapshot(), _uiState.value.activePresetId)

    private val externalPowerListener: (Boolean) -> Unit = { enabled ->
        viewModelScope.launch {
            if (_uiState.value.eqEnabled != enabled) {
                setMasterPower(enabled, fromExternal = true)
            }
        }
    }

    init {
        audioEngine.bindContext(appContext)
        EqPowerBridge.registerListener(externalPowerListener)
        viewModelScope.launch { AudioForegroundService.isRunning.collect { running ->
            _uiState.update { it.copy(backgroundServiceActive = running) }
        } }
        viewModelScope.launch { AudioForegroundService.error.collect { message -> if (message != null) reportMessage(message) } }
        viewModelScope.launch { audioEngine.detectedSessions.collect { sessions ->
            _uiState.update { it.copy(detectedSessions = sessions) }
        } }
        viewModelScope.launch { audioEngine.detectedOutputDevice.collect { label ->
            _uiState.update { it.copy(detectedOutputDevice = label) }
        } }
        viewModelScope.launch { audioEngine.capabilities.collect { c ->
            val oldSupported = _uiState.value.supportedEffects
            _uiState.update { it.copy(hardwareAttached = c.attached, hardwareBandCount = c.hardwareBandCount,
                hardwareStatusMessage = c.statusMessage, supportedEffects = c.supported, audioErrors = c.errors) }
            if (HeadroomPolicy.resyncNeeded(_uiState.value.autoHeadroom, oldSupported, c.supported)) syncAudioEngine()
        } }
        viewModelScope.launch { audioEngine.playing.collect { play -> _uiState.update { it.copy(isTestAudioPlaying = play) } } }
        viewModelScope.launch { audioEngine.clipping.collect { clip -> _uiState.update { it.copy(measuredClipping = clip) } } }
        viewModelScope.launch { audioEngine.output.collect { output ->
            val oldKey = _uiState.value.output.autoProfileKey
            _uiState.update { it.copy(output = output) }
            if (AutoProfilePolicy.shouldApply(restored, _uiState.value.autoProfiles, oldKey, output.autoProfileKey, initialApply = false)) {
                applyOutputProfile(output.autoProfileKey!!)
            }
        } }
        viewModelScope.launch {
            runCatching {
                val saved = repository.settingsFlow.first()
                val autoEqSaved = runCatching { autoEqRepository.stateFlow.first() }.getOrNull()
                val merge = LibraryMerge.merge(saved.customPresets, customPresets)
                customPresets = merge.presets
                val truncationNotice = "Pustaka melebihi 200 preset; hanya 200 dipulihkan, preset sesi ini didahulukan."
                audioEngine.attachEffects(_uiState.value.audioSourceMode)
                val restoredAutoEqProfile = if (userInteracted) null else autoEqSaved?.activeProfile
                val restoredAutoEqGains = if (restoredAutoEqProfile != null) {
                    autoEqSaved?.activeGainsDb?.takeIf { it.size == 20 }
                } else {
                    null
                }
                _uiState.update { current ->
                    // Baca capabilities saat commit (bukan snapshot pra-suspensi) agar emisi
                    // collector yang lebih baru tidak tertimpa balik oleh restore.
                    val caps = audioEngine.capabilities.value
                    val base = if (userInteracted) current else {
                        val effectiveLeft = restoredAutoEqGains ?: saved.leftBandGainsDb
                        val effectiveRight = restoredAutoEqGains ?: saved.rightBandGainsDb
                        val effectiveLinked = if (restoredAutoEqGains != null) true else saved.stereoLinked
                        current.withSound(SoundSnapshot(
                            left = effectiveLeft, right = effectiveRight,
                            linked = effectiveLinked, bassEnabled = saved.bassBoostEnabled,
                            bass = saved.bassBoostStrength, surroundEnabled = saved.virtualizerEnabled,
                            surround = saved.virtualizerStrength, width = saved.stereoWidth,
                            balance = saved.stereoBalance, preampEnabled = saved.preampEnabled,
                            preamp = saved.preampGainDb, limiter = saved.limiterEnabled, reverb = saved.reverbPreset
                        )).copy(
                            eqEnabled = saved.eqEnabled,
                            activePresetId = saved.activePresetId,
                            activeAutoEqProfile = restoredAutoEqProfile
                        )
                    }
                    base.copy(presets = repository.builtInPresets + customPresets,
                        favoriteIds = saved.favoriteIds + current.favoriteIds,
                        outputProfiles = (current.outputProfiles + saved.outputProfiles).distinctBy { it.deviceKey },
                        autoProfiles = if (autoProfilesTouched) current.autoProfiles else saved.autoProfiles,
                        autoHeadroom = if (userInteracted) current.autoHeadroom else saved.autoHeadroom, hardwareAttached = caps.attached,
                        hardwareBandCount = caps.hardwareBandCount, hardwareStatusMessage = caps.statusMessage,
                        libraryRestored = true, libraryTruncated = merge.truncated,
                        message = if (merge.truncated) truncationNotice else current.message)
                }
                restored = true
                publishPowerBridge()
                ensureBackgroundServiceStarted()
                refreshDerived()
                syncAudioEngine()
                if (restoredAutoEqGains != null) {
                    audioEngine.applyAutoEqCorrection(restoredAutoEqGains)
                }
                if (userInteracted || persistRequested) schedulePersist(true)
                maybeApplyAutoProfile(initialApply = true)
                // Pesan pemangkasan dikirim ulang TERAKHIR agar tidak tertimpa snackbar
                // profil otomatis; banner persisten libraryTruncated tetap ada.
                if (merge.truncated) reportMessage(truncationNotice)
            }.onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                reportMessage("Pengaturan belum dapat dibaca; data lama tidak ditimpa.")
            }
        }
    }

    private fun refreshDerived() {
        val s = _uiState.value
        val preset = s.presets.find { it.id == s.activePresetId }
        _uiState.update { it.copy(presetDirty = preset?.soundSnapshot() != it.soundSnapshot(),
            canUndo = history.undo.isNotEmpty(), canRedo = history.redo.isNotEmpty()) }
    }
    private fun push(before: Entry, after: Entry) {
        if (before == after) return
        history.undo.addLast(before)
        if (history.undo.size > 60) history.undo.removeFirst()
        history.redo.clear()
    }
    private fun change(transform: (EqualizerUiState) -> EqualizerUiState) {
        userInteracted = true
        val before = entry()
        _uiState.update(transform)
        if (gestureStart == null) push(before, entry())
        refreshDerived()
        // Selama gesture, sinkronisasi audio di-throttle ke thread latar agar
        // panggilan binder AudioEffect tidak memblokir main thread per event drag.
        if (gestureStart != null) scheduleAudioSync() else syncAudioEngine()
        schedulePersist()
    }
    private fun scheduleAudioSync() {
        if (audioSyncJob?.isActive == true) return
        audioSyncJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            delay(32)
            syncAudioEngine()
        }
    }
    fun beginGesture() { if (gestureStart == null) gestureStart = entry() }
    fun endGesture() {
        gestureStart?.let { push(it, entry()) }
        gestureStart = null
        audioSyncJob?.cancel()
        refreshDerived(); syncAudioEngine(); schedulePersist()
    }
    fun undo() {
        endGesture()
        if (history.undo.isEmpty()) return
        history.redo.addLast(entry())
        restoreEntry(history.undo.removeLast())
    }
    fun redo() {
        endGesture()
        if (history.redo.isEmpty()) return
        history.undo.addLast(entry())
        restoreEntry(history.redo.removeLast())
    }
    private fun restoreEntry(e: Entry) {
        userInteracted = true
        _uiState.update { it.withSound(e.sound).copy(activePresetId = e.presetId) }
        refreshDerived(); syncAudioEngine(); schedulePersist()
    }
    fun toggleABSlot() {
        endGesture()
        val slot = _uiState.value.activeAbSlot
        slots[slot] = entry()
        val next = if (slot == "A") "B" else "A"
        val target = slots[next] ?: entry()
        _uiState.update { it.copy(activeAbSlot = next) }
        restoreEntry(target)
    }
    fun copyToOtherSlot() {
        endGesture()
        val target = if (_uiState.value.activeAbSlot == "A") "B" else "A"
        slots[target] = entry()
        histories.getValue(target).undo.clear(); histories.getValue(target).redo.clear()
        reportMessage("Seluruh suara disalin ke slot $target.")
    }
    fun setSimpleMode(simple: Boolean) { _uiState.update { it.copy(simpleMode = simple) } }
    fun updateSimpleTone(group: Int, value: Float) {
        if (group !in 0..2 || !value.isFinite()) return
        val wasAutoEqActive = _uiState.value.activeAutoEqProfile != null
        val range = when (group) { 0 -> 0..6; 1 -> 7..13; else -> 14..19 }
        change { s -> s.copy(
            activeAutoEqProfile = null,
            activeQuickTuningId = null,
            bands = s.bands.mapIndexed { i, b ->
                if (i in range) b.copy(levelDb = round(value.coerceIn(-15f, 15f) * 10) / 10,
                    rightLevelDb = round(value.coerceIn(-15f, 15f) * 10) / 10) else b
            }, stereoLinked = false, activeChannelFocus = StereoChannelFocus.LEFT) }
        if (wasAutoEqActive) clearDetachedAutoEqPersistence()
    }
    fun selectChannelFocus(focus: StereoChannelFocus) = change { state ->
        state.copy(stereoLinked = focus == StereoChannelFocus.LINKED, activeChannelFocus = focus,
            bands = if (focus == StereoChannelFocus.LINKED) state.bands.map { it.copy(rightLevelDb = it.levelDb) } else state.bands)
    }
    fun updateBandLevel(bandIndex: Int, levelDb: Float) {
        if (!levelDb.isFinite()) return
        val wasAutoEqActive = _uiState.value.activeAutoEqProfile != null
        change { s -> s.copy(
            activeAutoEqProfile = null,
            activeQuickTuningId = null,
            bands = s.bands.map { b ->
                if (b.index != bandIndex) b else {
                    val gain = round(levelDb.coerceIn(b.minDb, b.maxDb) * 10) / 10
                    when (s.activeChannelFocus) {
                        StereoChannelFocus.LINKED -> b.copy(levelDb = gain, rightLevelDb = gain)
                        StereoChannelFocus.LEFT -> b.copy(levelDb = gain)
                        StereoChannelFocus.RIGHT -> b.copy(rightLevelDb = gain)
                    }
                }
            }) }
        if (wasAutoEqActive) clearDetachedAutoEqPersistence()
    }
    fun resetBandToZero(index: Int) = updateBandLevel(index, 0f)
    fun resetAllFlat() {
        val wasAutoEqActive = _uiState.value.activeAutoEqProfile != null
        change { s ->
            s.copy(
                activeAutoEqProfile = null,
                activeQuickTuningId = null,
                bands = s.bands.map { it.copy(levelDb = 0f, rightLevelDb = 0f) }
            )
        }
        if (wasAutoEqActive) clearDetachedAutoEqPersistence()
    }
    fun applyQuickTuningPreset(preset: QuickTuningPreset) {
        endGesture()
        val wasAutoEqActive = _uiState.value.activeAutoEqProfile != null
        change { s ->
            s.copy(
                activeAutoEqProfile = null,
                activeQuickTuningId = preset.id,
                bands = s.bands.mapIndexed { idx, band ->
                    val target = round(preset.bandGainsDb.getOrElse(idx) { 0f }.coerceIn(band.minDb, band.maxDb) * 10) / 10
                    when (s.activeChannelFocus) {
                        StereoChannelFocus.LINKED -> band.copy(levelDb = target, rightLevelDb = target)
                        StereoChannelFocus.LEFT -> band.copy(levelDb = target)
                        StereoChannelFocus.RIGHT -> band.copy(rightLevelDb = target)
                    }
                }
            )
        }
        if (wasAutoEqActive) clearDetachedAutoEqPersistence()
        publishPowerBridge()
    }

    /**
     * State D helper: when the user manually moves a fader or applies another curve while AutoEQ
     * is active, detach the AutoEQ profile in audio engine metadata and DataStore while keeping
     * the user's new manual fader positions intact.
     */
    private fun clearDetachedAutoEqPersistence() {
        audioEngine.detachAutoEqMetadata()
        publishPowerBridge()
        persistScope.launch {
            runCatching { autoEqRepository.clearActiveProfile() }
        }
    }

    /**
     * Applies a 20-band interpolated AutoEQ headphone correction curve in a thread-safe manner,
     * snapshotting manual EQ settings first so the user can disable AutoEQ and restore them anytime.
     */
    fun applyAutoEqProfile(
        profile: HeadphoneProfile,
        interpolatedGainsDb: List<Float>,
        sendToEngine: Boolean = true
    ) {
        if (interpolatedGainsDb.size != 20) return
        endGesture()
        val current = _uiState.value
        val shouldBackupManual = current.activeAutoEqProfile == null
        val backupLeft = if (shouldBackupManual) current.bands.map { it.levelDb } else null
        val backupRight = if (shouldBackupManual) current.bands.map { it.rightLevelDb } else null
        val safeGains = interpolatedGainsDb.map { g ->
            if (g.isFinite()) round(g.coerceIn(-15f, 15f) * 10f) / 10f else 0f
        }

        // Update UI state (animating faders & Bezier curve smoothly) without snapping audio DSP
        userInteracted = true
        val before = entry()
        _uiState.update { s ->
            s.copy(
                activeAutoEqProfile = profile,
                activeQuickTuningId = null,
                stereoLinked = true,
                activeChannelFocus = StereoChannelFocus.LINKED,
                bands = s.bands.mapIndexed { idx, band ->
                    band.copy(levelDb = safeGains[idx], rightLevelDb = safeGains[idx])
                }
            )
        }
        push(before, entry())
        refreshDerived()
        schedulePersist()

        if (sendToEngine) {
            audioEngine.applyAutoEqGains(safeGains.toFloatArray(), rampTimeMs = 100L)
        }
        publishPowerBridge()
        reportMessage("AutoEQ applied: ${profile.name}")
        persistScope.launch {
            runCatching {
                autoEqRepository.saveActiveProfile(
                    profile = profile,
                    interpolatedGainsDb = safeGains,
                    manualBackupLeftDb = backupLeft,
                    manualBackupRightDb = backupRight
                )
            }
        }
    }

    /**
     * Removes the active AutoEQ correction and smoothly restores the user's previous manual 20-band settings.
     */
    fun disableAutoEq() {
        endGesture()
        persistScope.launch {
            val (backupLeft, backupRight) = runCatching { autoEqRepository.clearActiveProfile() }
                .getOrDefault(null to null)
            val restoreLeft = backupLeft ?: List(20) { 0f }
            val restoreRight = backupRight ?: restoreLeft
            val linked = restoreLeft == restoreRight

            userInteracted = true
            val before = entry()
            _uiState.update { s ->
                s.copy(
                    activeAutoEqProfile = null,
                    activeQuickTuningId = null,
                    stereoLinked = linked,
                    activeChannelFocus = if (linked) StereoChannelFocus.LINKED else StereoChannelFocus.LEFT,
                    bands = s.bands.mapIndexed { idx, band ->
                        band.copy(
                            levelDb = restoreLeft.getOrElse(idx) { 0f },
                            rightLevelDb = restoreRight.getOrElse(idx) { 0f }
                        )
                    }
                )
            }
            push(before, entry())
            refreshDerived()
            schedulePersist()

            audioEngine.clearAutoEqCorrection(restoreLeft, restoreRight, rampTimeMs = 100L)
            publishPowerBridge()
            reportMessage("AutoEQ disabled. Manual EQ settings restored.")
        }
    }
    fun resetAllEffects() = change { s -> s.withSound(SoundSnapshot(
        left = s.bands.map { it.levelDb }, right = s.bands.map { it.rightLevelDb }, linked = s.stereoLinked
    )) }
    fun smoothCurve() {
        val wasAutoEqActive = _uiState.value.activeAutoEqProfile != null
        change { s -> s.copy(activeAutoEqProfile = null, activeQuickTuningId = null, bands = s.bands.mapIndexed { i, b ->
            val prev = s.bands.getOrElse(i - 1) { b }; val next = s.bands.getOrElse(i + 1) { b }
            b.copy(levelDb = prev.levelDb * 0.25f + b.levelDb * 0.5f + next.levelDb * 0.25f,
                rightLevelDb = prev.rightLevelDb * 0.25f + b.rightLevelDb * 0.5f + next.rightLevelDb * 0.25f)
        }) }
        if (wasAutoEqActive) clearDetachedAutoEqPersistence()
    }
    fun mirrorLeftToRight() = change { s -> s.copy(bands = s.bands.map { it.copy(rightLevelDb = it.levelDb) }) }
    fun selectPreset(preset: EqPreset) {
        endGesture()
        val wasAutoEqActive = _uiState.value.activeAutoEqProfile != null
        change { it.withSound(preset.soundSnapshot()).copy(activePresetId = preset.id, activeAutoEqProfile = null, activeQuickTuningId = null) }
        if (wasAutoEqActive) clearDetachedAutoEqPersistence()
        publishPowerBridge()
    }
    fun saveCustomPreset(name: String) {
        if (name.isBlank()) return
        if (customPresets.size >= 200) { reportMessage("Pustaka maksimal 200 preset kustom."); return }
        userInteracted = true
        val s = _uiState.value.soundSnapshot()
        val p = EqPreset("custom_${UUID.randomUUID()}", name.trim().take(60), "Kustom",
            "20 band L/R dan seluruh pengaturan efek.", s.left, s.bass, s.surround, s.width, true, s)
        customPresets = customPresets + p
        _uiState.update { it.copy(presets = repository.builtInPresets + customPresets, activePresetId = p.id) }
        refreshDerived(); schedulePersist(true)
    }
    fun renamePreset(id: String, name: String) {
        if (name.isBlank()) return
        customPresets = customPresets.map { if (it.id == id) it.copy(name = name.trim().take(60)) else it }
        updateLibrary()
    }
    fun duplicatePreset(p: EqPreset) {
        if (customPresets.size >= 200) { reportMessage("Pustaka maksimal 200 preset kustom."); return }
        customPresets = customPresets + p.copy(id = "custom_${UUID.randomUUID()}", name = "${p.name.take(50)} salinan",
            category = "Kustom", isCustom = true, sound = p.soundSnapshot())
        updateLibrary()
    }
    fun deleteCustomPreset(id: String) {
        customPresets = customPresets.filterNot { it.id == id }
        _uiState.update { it.copy(favoriteIds = it.favoriteIds - id) }
        updateLibrary()
    }
    fun toggleFavorite(id: String) {
        userInteracted = true
        _uiState.update { it.copy(favoriteIds = if (id in it.favoriteIds) it.favoriteIds - id else it.favoriteIds + id) }
        schedulePersist(true)
    }
    fun exportPresets(): String {
        check(restored) { "Pustaka belum selesai dipulihkan. Tunggu sebentar lalu coba ekspor lagi." }
        return PresetCodec.encode(customPresets)
    }
    fun importPresets(json: String, copyDuplicates: Boolean): Result<Int> = runCatching {
        check(restored) { "Pustaka belum selesai dipulihkan. Tunggu sebentar lalu coba impor lagi." }
        val parsed = PresetCodec.decode(json)
        val ids = _uiState.value.presets.map { it.id }.toSet()
        val additions = parsed.mapNotNull { p ->
            if (p.id !in ids) p.copy(category = "Kustom")
            else if (copyDuplicates) p.copy(id = "custom_${UUID.randomUUID()}", name = "${p.name.take(50)} salinan", category = "Kustom")
            else null
        }
        require(customPresets.size + additions.size <= 200) { "Pustaka maksimal 200 preset kustom." }
        customPresets = customPresets + additions
        updateLibrary()
        additions.size
    }
    private fun updateLibrary() {
        _uiState.update { it.copy(presets = repository.builtInPresets + customPresets) }
        refreshDerived(); schedulePersist(true)
    }
    fun bindOutputProfile(device: OutputDevice, presetId: String) {
        if (_uiState.value.presets.none { it.id == presetId }) return
        _uiState.update { it.copy(outputProfiles = it.outputProfiles.filterNot { p -> p.deviceKey == device.key } +
            OutputProfile(device.key, device.label, presetId)) }
        schedulePersist(true)
    }
    fun removeOutputProfile(key: String) {
        _uiState.update { it.copy(outputProfiles = it.outputProfiles.filterNot { p -> p.deviceKey == key }) }
        schedulePersist(true)
    }
    fun applyOutputProfile(key: String) {
        val s = _uiState.value
        val p = s.outputProfiles.find { it.deviceKey == key } ?: return
        s.presets.find { it.id == p.presetId }?.let {
            selectPreset(it)
            reportMessage("Profil ${p.deviceLabel} diterapkan. Volume sistem tidak diubah; Undo tersedia.")
        }
    }
    fun toggleAutoProfiles() {
        autoProfilesTouched = true
        _uiState.update { it.copy(autoProfiles = !it.autoProfiles) }
        schedulePersist(true)
        if (_uiState.value.autoProfiles) maybeApplyAutoProfile(initialApply = true)
    }
    private fun maybeApplyAutoProfile(initialApply: Boolean) {
        val key = _uiState.value.output.autoProfileKey ?: return
        if (AutoProfilePolicy.shouldApply(restored, _uiState.value.autoProfiles, null, key, initialApply)) {
            applyOutputProfile(key)
        }
    }
    fun toggleAutoHeadroom() {
        change { it.copy(autoHeadroom = !it.autoHeadroom) }
    }
    fun reportMessage(message: String?) { _uiState.update { it.copy(message = message) } }
    fun toggleBassBoost() = change { it.copy(bassBoostEnabled = !it.bassBoostEnabled) }
    fun updateBassBoost(v: Float) { if (v.isFinite()) change { it.copy(bassBoostStrength = v.coerceIn(0f, 1f)) } }
    fun toggleVirtualizer() = change { it.copy(virtualizerEnabled = !it.virtualizerEnabled) }
    fun updateVirtualizer(v: Float) { if (v.isFinite()) change { it.copy(virtualizerStrength = v.coerceIn(0f, 1f)) } }
    fun updateStereoWidth(v: Float) { if (v.isFinite()) change { it.copy(stereoWidth = v.coerceIn(0f, 2f)) } }
    fun updateStereoBalance(v: Float) { if (v.isFinite()) change { it.copy(stereoBalance = v.coerceIn(-1f, 1f)) } }
    fun togglePreamp() = change { it.copy(preampEnabled = !it.preampEnabled) }
    fun updatePreampGain(v: Float) { if (v.isFinite()) change { it.copy(preampGainDb = v.coerceIn(-12f, 12f)) } }
    fun toggleLimiter() = change { it.copy(limiterEnabled = !it.limiterEnabled) }
    fun selectReverbPreset(p: ReverbRoomPreset) = change { it.copy(reverbPreset = p) }

    fun toggleMasterPower() {
        setMasterPower(!_uiState.value.eqEnabled)
    }
    fun setMasterPower(enabled: Boolean, fromExternal: Boolean = false) {
        userInteracted = true
        _uiState.update { it.copy(eqEnabled = enabled) }
        publishPowerBridge()
        if (!fromExternal) {
            ensureBackgroundServiceStarted()
        }
        AudioForegroundService.notifyStatusChanged(appContext)
        syncAudioEngine()
        schedulePersist()
    }
    private fun publishPowerBridge() {
        val s = _uiState.value
        val activeLabel = s.activeAutoEqProfile?.name
            ?: QuickTuningPresets.defaultItems.find { it.id == s.activeQuickTuningId }?.title
            ?: s.presets.find { it.id == s.activePresetId }?.name
            ?: "Custom"
        EqPowerBridge.publishState(appContext, s.eqEnabled, activeLabel)
    }
    fun toggleBackgroundService() {
        if (AudioForegroundService.isRunning.value) AudioForegroundService.stop(appContext) else AudioForegroundService.start(appContext)
    }
    fun ensureBackgroundServiceStarted() {
        if (!AudioForegroundService.isRunning.value && _uiState.value.eqEnabled) AudioForegroundService.start(appContext)
    }
    fun switchAudioSourceMode(mode: AudioSourceMode) {
        if (_uiState.value.audioSourceMode == mode) return
        audioEngine.stopTestAudio()
        val c = audioEngine.attachEffects(mode)
        _uiState.update { it.copy(audioSourceMode = mode, isTestAudioPlaying = false,
            hardwareAttached = c.attached, hardwareBandCount = c.hardwareBandCount, hardwareStatusMessage = c.statusMessage) }
        syncAudioEngine()
    }
    fun toggleTestAudioPlayback() {
        if (_uiState.value.audioSourceMode != AudioSourceMode.STUDIO_TEST) switchAudioSourceMode(AudioSourceMode.STUDIO_TEST)
        val play = !_uiState.value.isTestAudioPlaying
        if (play) {
            if (!audioEngine.startTestAudio()) reportMessage("Audio uji gagal dimulai. Tutup pemutar lain lalu coba lagi.")
        } else audioEngine.stopTestAudio()
    }
    fun cycleTestTrackStyle() {
        val next = TestTrackStyle.entries[(_uiState.value.testTrackStyle.ordinal + 1) % TestTrackStyle.entries.size]
        audioEngine.setTestTrackStyle(next)
        _uiState.update { it.copy(testTrackStyle = next) }
    }
    private fun syncAudioEngine() {
        val s = _uiState.value
        audioEngine.updateParameters(s.eqEnabled, s.bands, s.bassBoostEnabled, s.bassBoostStrength,
            s.virtualizerEnabled, s.virtualizerStrength, s.stereoWidth, s.stereoBalance,
            s.preampEnabled, (s.preampGainDb + if (s.autoHeadroom && "preamp" in s.supportedEffects) s.soundSnapshot().estimatedCompensationDb() else 0f).coerceIn(-12f, 12f),
            s.limiterEnabled, s.reverbPreset)
    }
    private fun schedulePersist(immediate: Boolean = false) {
        if (!restored) {
            persistRequested = true
            return
        }
        persistJob?.cancel()
        persistJob = persistScope.launch {
            if (!immediate) delay(250)
            val s = _uiState.value
            runCatching { repository.saveSettings(PersistedEqSettings(
                eqEnabled = s.eqEnabled, stereoLinked = s.stereoLinked, activePresetId = s.activePresetId,
                leftBandGainsDb = s.bands.map { it.levelDb }, rightBandGainsDb = s.bands.map { it.rightLevelDb },
                bassBoostEnabled = s.bassBoostEnabled, bassBoostStrength = s.bassBoostStrength,
                virtualizerEnabled = s.virtualizerEnabled, virtualizerStrength = s.virtualizerStrength,
                stereoWidth = s.stereoWidth, stereoBalance = s.stereoBalance, preampEnabled = s.preampEnabled,
                preampGainDb = s.preampGainDb, limiterEnabled = s.limiterEnabled, reverbPreset = s.reverbPreset,
                customPresets = customPresets, favoriteIds = s.favoriteIds,
                outputProfiles = s.outputProfiles, autoProfiles = s.autoProfiles, autoHeadroom = s.autoHeadroom
            )) }.onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                reportMessage("Perubahan belum tersimpan. Coba lagi setelah ruang penyimpanan tersedia.")
            }
        }
    }
    override fun onCleared() {
        EqPowerBridge.unregisterListener(externalPowerListener)
        schedulePersist(true)
        val scopeJob = persistScope.coroutineContext[Job]
        if (persistJob == null) scopeJob?.cancel() else persistJob?.invokeOnCompletion { scopeJob?.cancel() }
        audioEngine.stopTestAudio()
        if (!AudioForegroundService.isRunning.value && !EqualizerService.isRunning.value) audioEngine.releaseGlobalAndExternal()
        super.onCleared()
    }
}

/** Merge pustaka tersimpan + sesi berjalan dengan batas keras 200 preset kustom. */
internal object LibraryMerge {
    data class Result(val presets: List<EqPreset>, val truncated: Boolean)
    fun merge(saved: List<EqPreset>, current: List<EqPreset>, limit: Int = 200): Result {
        // Kerja sesi berjalan didahulukan: preset yang dibuat sebelum restore
        // selesai tidak boleh dipangkas demi data lama.
        val distinct = (current + saved).distinctBy { it.id }
        return Result(distinct.take(limit), distinct.size > limit)
    }
}

/** Profil otomatis diterapkan pada rute awal/enable, bukan hanya saat key rute berubah. */
internal object AutoProfilePolicy {
    fun shouldApply(restored: Boolean, autoProfiles: Boolean, oldKey: String?, newKey: String?, initialApply: Boolean): Boolean =
        restored && autoProfiles && newKey != null && (initialApply || newKey != oldKey)
}

/** Auto-headroom perlu disinkronkan ulang tepat saat kemampuan preamp muncul/hilang. */
internal object HeadroomPolicy {
    fun resyncNeeded(autoHeadroom: Boolean, oldSupported: Set<String>, newSupported: Set<String>): Boolean =
        autoHeadroom && ("preamp" in oldSupported) != ("preamp" in newSupported)
}
