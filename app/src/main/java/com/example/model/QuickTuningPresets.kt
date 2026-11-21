package com.example.model

import androidx.compose.runtime.Immutable

/**
 * Functional grouping for the 20 ISO equalizer frequency bands.
 * Helps users build a clear mental map from Sub-bass (25 Hz) to Air (18 kHz).
 */
enum class EqFrequencySection(
    val label: String,
    val shortLabel: String,
    val bandRange: IntRange,
    val rangeCaption: String
) {
    SUB_BASS("Sub-bass", "Sub", 0..2, "25–50 Hz"),
    BASS("Bass", "Bass", 3..5, "70–140 Hz"),
    LOW_MID("Low-mid", "Low-mid", 6..8, "200–400 Hz"),
    MID("Mid", "Mid", 9..11, "560–1.1k"),
    HIGH_MID("High-mid", "High-mid", 12..14, "1.6k–3.1k"),
    TREBLE("Treble", "Treble", 15..17, "4.5k–9k"),
    AIR("Air", "Air", 18..19, "12.5k–18k");

    companion object {
        fun forBandIndex(index: Int): EqFrequencySection =
            entries.firstOrNull { index in it.bandRange } ?: MID

        fun simpleGroupRange(group: Int): IntRange = when (group) {
            0 -> 0..6
            1 -> 7..13
            else -> 14..19
        }

        fun simpleGroupName(group: Int): String = when (group) {
            0 -> "Bass (25Hz – 200Hz)"
            1 -> "Mid (280Hz – 2.2kHz)"
            else -> "Treble (3.1kHz – 18kHz)"
        }
    }
}

enum class QuickTuningIcon {
    BASS,
    VOCALS,
    SMOOTH,
    AIR
}

/**
 * Friendly one-tap sound tuning curves for the Advanced 20-band mode.
 * Each preset provides a smooth 20-band gain curve (dB) designed to animate
 * all sliders into musical shapes without harsh spikes.
 */
@Immutable
data class QuickTuningPreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: QuickTuningIcon,
    val focusSection: EqFrequencySection,
    val bandGainsDb: List<Float>
)

@Immutable
data class QuickTuningPresets(
    val items: List<QuickTuningPreset> = defaultItems
) {
    companion object {
        val MoreBass = QuickTuningPreset(
            id = "qt_more_bass",
            title = "More Bass",
            subtitle = "Deep punch & warm low-end",
            icon = QuickTuningIcon.BASS,
            focusSection = EqFrequencySection.BASS,
            bandGainsDb = listOf(
                5.5f, 6.0f, 5.5f, 4.5f, 3.2f,
                1.8f, 0.5f, -0.5f, -1.0f, -0.5f,
                0.0f, 0.5f, 0.8f, 1.0f, 1.2f,
                1.5f, 1.5f, 1.2f, 0.8f, 0.5f
            )
        )

        val ClearerVocals = QuickTuningPreset(
            id = "qt_clearer_vocals",
            title = "Clearer Vocals",
            subtitle = "Crisp voice & clean low-mid",
            icon = QuickTuningIcon.VOCALS,
            focusSection = EqFrequencySection.HIGH_MID,
            bandGainsDb = listOf(
                -2.5f, -2.0f, -1.5f, -0.8f, -0.2f,
                0.0f, 0.2f, 0.8f, 1.6f, 2.5f,
                3.5f, 4.2f, 4.8f, 4.5f, 3.6f,
                2.2f, 1.2f, 0.6f, 0.4f, 0.0f
            )
        )

        val ReduceHarshness = QuickTuningPreset(
            id = "qt_reduce_harshness",
            title = "Reduce Harshness",
            subtitle = "Tame sibilance & ear fatigue",
            icon = QuickTuningIcon.SMOOTH,
            focusSection = EqFrequencySection.TREBLE,
            bandGainsDb = listOf(
                1.5f, 1.8f, 1.5f, 1.0f, 0.6f,
                0.4f, 0.2f, 0.0f, 0.0f, 0.0f,
                -0.2f, -0.6f, -1.2f, -2.2f, -3.4f,
                -4.2f, -4.5f, -3.2f, -1.8f, -1.0f
            )
        )

        val AiryDetail = QuickTuningPreset(
            id = "qt_airy_detail",
            title = "Airy & Detailed",
            subtitle = "Open sparkle & wide clarity",
            icon = QuickTuningIcon.AIR,
            focusSection = EqFrequencySection.AIR,
            bandGainsDb = listOf(
                1.5f, 2.0f, 1.8f, 1.2f, 0.5f,
                0.0f, -0.4f, -0.6f, -0.4f, 0.0f,
                0.5f, 1.0f, 1.5f, 2.0f, 2.5f,
                3.2f, 4.0f, 4.8f, 5.2f, 4.5f
            )
        )

        val defaultItems: List<QuickTuningPreset> = listOf(
            MoreBass,
            ClearerVocals,
            ReduceHarshness,
            AiryDetail
        )
    }
}
