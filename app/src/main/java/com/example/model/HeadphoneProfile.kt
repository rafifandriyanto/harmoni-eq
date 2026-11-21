package com.example.model

import androidx.compose.runtime.Immutable

/**
 * Represents a headphone correction entry from the official AutoEQ database
 * (jaakkopasanen/AutoEq).
 */
@Immutable
data class HeadphoneProfile(
    val name: String,
    val type: String,
    val csvUrl: String
) {
    /** Stable unique identifier derived from the canonical CSV path/URL. */
    val id: String
        get() = csvUrl

    /** Measurement source (e.g., oratory1990, crinacle, Rtings) extracted from the AutoEQ URL if available. */
    val sourceLabel: String
        get() {
            val marker = "/results/"
            val after = csvUrl.substringAfter(marker, "")
            val rawSource = after.substringBefore("/", "")
            return rawSource.replace("%20", " ").ifBlank { "AutoEQ" }
        }
}

/**
 * Locally cached AutoEQ curve including raw CSV text and 20-band interpolated gains (dB)
 * for offline operation without re-downloading.
 */
@Immutable
data class CachedAutoEqCurve(
    val profile: HeadphoneProfile,
    val rawCsv: String,
    val interpolatedGainsDb: List<Float>,
    val cachedAtMs: Long
)
