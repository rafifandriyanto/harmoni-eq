package com.example.model

enum class AudioSourceMode(val label: String, val subtitle: String) {
    GLOBAL_SYSTEM("YouTube & Semua App", "Global Mix + Sesi Audio"),
    STUDIO_TEST("Audio Uji Studio", "20-Band Synth DSP")
}

enum class TestTrackStyle(val label: String) {
    GROOVE_BASS("Groove Stereo"),
    SYNTH_PAD("Wide Pad"),
    ACOUSTIC_SWEEP("Crystal Arp")
}

enum class StereoChannelFocus(val label: String, val shortLabel: String) {
    LINKED("Stereo L+R", "L+R"),
    LEFT("Kiri (L)", "L"),
    RIGHT("Kanan (R)", "R")
}

enum class ReverbRoomPreset(val label: String, val androidPreset: Short) {
    NONE("Mati", 0),
    SMALL_ROOM("Studio Kecil", 1),
    MEDIUM_HALL("Ruang Sedang", 3),
    LARGE_HALL("Aula Konser", 4),
    PLATE("Plate Reverb", 6)
}

enum class ConsoleTab {
    EQUALIZER,
    YOUTUBE,
    EFFECTS,
    PRESETS
}

data class EqBand(
    val index: Int,
    val centerFreqHz: Int,
    val label: String,
    val minDb: Float,
    val maxDb: Float,
    val levelDb: Float,
    val rightLevelDb: Float = levelDb
)

data class EqPreset(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val bandGainsDb: List<Float>,
    val bassBoost: Float = 0f,
    val virtualizer: Float = 0f,
    val stereoWidth: Float = 1.0f,
    val isCustom: Boolean = false,
    val sound: SoundSnapshot? = null
)

data class EqualizerUiState(
    val eqEnabled: Boolean = true,
    val backgroundServiceActive: Boolean = false,
    val simpleMode: Boolean = false,
    val presetDirty: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val favoriteIds: Set<String> = emptySet(),
    val message: String? = null,
    val output: OutputState = OutputState(),
    val outputProfiles: List<OutputProfile> = emptyList(),
    val autoProfiles: Boolean = false,
    val autoHeadroom: Boolean = false,
    val supportedEffects: Set<String> = emptySet(),
    val audioErrors: List<String> = emptyList(),
    val measuredClipping: Boolean = false,
    val stereoLinked: Boolean = true,
    val activeChannelFocus: StereoChannelFocus = StereoChannelFocus.LINKED,
    val activeAbSlot: String = "A",
    val bands: List<EqBand> = default20Bands(),
    val presets: List<EqPreset> = emptyList(),
    val activePresetId: String = "warm_studio",
    val activeQuickTuningId: String? = null,
    val activeAutoEqProfile: HeadphoneProfile? = null,
    val bassBoostEnabled: Boolean = true,
    val bassBoostStrength: Float = 0.30f,
    val virtualizerEnabled: Boolean = true,
    val virtualizerStrength: Float = 0.35f,
    val stereoWidth: Float = 1.15f,
    val stereoBalance: Float = 0f,
    val preampEnabled: Boolean = true,
    val preampGainDb: Float = 0f, // -12f..+12f
    val limiterEnabled: Boolean = true,
    val reverbPreset: ReverbRoomPreset = ReverbRoomPreset.NONE,
    val audioSourceMode: AudioSourceMode = AudioSourceMode.GLOBAL_SYSTEM,
    val isTestAudioPlaying: Boolean = false,
    val testTrackStyle: TestTrackStyle = TestTrackStyle.GROOVE_BASS,
    val hardwareAttached: Boolean = false,
    val hardwareBandCount: Int = 20,
    val detectedOutputDevice: String = "Speaker Perangkat",
    val detectedSessions: List<String> = emptyList(),
    val libraryRestored: Boolean = false,
    val libraryTruncated: Boolean = false,
    val hardwareStatusMessage: String = "Siap untuk YouTube, Spotify & pemutar audio"
)

val ISO_20_FREQUENCIES_HZ = listOf(
    25, 36, 50, 70, 100,
    140, 200, 280, 400, 560,
    800, 1100, 1600, 2200, 3100,
    4500, 6300, 9000, 12500, 18000
)

fun default20Bands(): List<EqBand> = ISO_20_FREQUENCIES_HZ.mapIndexed { idx, freqHz ->
    EqBand(
        index = idx,
        centerFreqHz = freqHz,
        label = formatFrequencyLabel(freqHz),
        minDb = -15f,
        maxDb = 15f,
        levelDb = 0f,
        rightLevelDb = 0f
    )
}

fun formatFrequencyLabel(freqHz: Int): String {
    return if (freqHz >= 1000) {
        val khz = freqHz / 1000f
        if (khz % 1f == 0f) "${khz.toInt()}k" else String.format(java.util.Locale.US, "%.1fk", khz)
    } else {
        "${freqHz}Hz"
    }
}
