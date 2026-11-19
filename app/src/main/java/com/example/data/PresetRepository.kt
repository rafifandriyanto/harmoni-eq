package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.EqPreset
import com.example.model.ReverbRoomPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.eqDataStore: DataStore<Preferences> by preferencesDataStore(name = "harmoni_eq_prefs_v3")

data class PersistedEqSettings(
    val eqEnabled: Boolean = true,
    val stereoLinked: Boolean = true,
    val activePresetId: String = "warm_studio",
    val leftBandGainsDb: List<Float> = listOf(
        2.5f, 3.5f, 4.0f, 3.0f, 2.0f,
        1.0f, 0.5f, -0.5f, -1.0f, -0.5f,
        0.5f, 1.0f, 1.5f, 2.2f, 2.8f,
        3.2f, 3.8f, 4.2f, 4.0f, 3.0f
    ),
    val rightBandGainsDb: List<Float> = listOf(
        2.5f, 3.5f, 4.0f, 3.0f, 2.0f,
        1.0f, 0.5f, -0.5f, -1.0f, -0.5f,
        0.5f, 1.0f, 1.5f, 2.2f, 2.8f,
        3.2f, 3.8f, 4.2f, 4.0f, 3.0f
    ),
    val bassBoostEnabled: Boolean = true,
    val bassBoostStrength: Float = 0.30f,
    val virtualizerEnabled: Boolean = true,
    val virtualizerStrength: Float = 0.35f,
    val stereoWidth: Float = 1.15f,
    val stereoBalance: Float = 0f,
    val preampEnabled: Boolean = true,
    val preampGainDb: Float = 0f,
    val limiterEnabled: Boolean = true,
    val reverbPreset: ReverbRoomPreset = ReverbRoomPreset.NONE,
    val customPresets: List<EqPreset> = emptyList(),
    val favoriteIds: Set<String> = emptySet(),
    val outputProfiles: List<com.example.model.OutputProfile> = emptyList(),
    val autoProfiles: Boolean = false,
    val autoHeadroom: Boolean = false
)

class PresetRepository(context: Context, private val store: DataStore<Preferences> = context.eqDataStore) {

    private object Keys {
        val FAVORITES = stringSetPreferencesKey("favorite_ids")
        val PROFILES = stringPreferencesKey("output_profiles_v1")
        val AUTO_PROFILES = booleanPreferencesKey("auto_output_profiles")
        val AUTO_HEADROOM = booleanPreferencesKey("auto_headroom")
        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val STEREO_LINKED = booleanPreferencesKey("stereo_linked")
        val ACTIVE_PRESET_ID = stringPreferencesKey("active_preset_id")
        val LEFT_BAND_GAINS = stringPreferencesKey("left_band_gains")
        val RIGHT_BAND_GAINS = stringPreferencesKey("right_band_gains")
        val BASS_ENABLED = booleanPreferencesKey("bass_enabled")
        val BASS_STRENGTH = floatPreferencesKey("bass_strength")
        val VIRT_ENABLED = booleanPreferencesKey("virt_enabled")
        val VIRT_STRENGTH = floatPreferencesKey("virt_strength")
        val STEREO_WIDTH = floatPreferencesKey("stereo_width")
        val STEREO_BALANCE = floatPreferencesKey("stereo_balance")
        val PREAMP_ENABLED = booleanPreferencesKey("preamp_enabled")
        val PREAMP_GAIN = floatPreferencesKey("preamp_gain")
        val LIMITER_ENABLED = booleanPreferencesKey("limiter_enabled")
        val REVERB_PRESET = stringPreferencesKey("reverb_preset")
        val CUSTOM_PRESETS = stringPreferencesKey("custom_presets_json")
        val COMPLETE_PRESETS = stringPreferencesKey("complete_presets_v2_json")
    }

    val builtInPresets: List<EqPreset> = listOf(
        // Studio & Reference
        EqPreset(
            id = "flat",
            name = "Flat Reference",
            category = "Studio",
            description = "Respons frekuensi datar tanpa pewarnaan sinyal.",
            bandGainsDb = List(20) { 0f },
            bassBoost = 0f,
            virtualizer = 0f,
            stereoWidth = 1.0f
        ),
        EqPreset(
            id = "warm_studio",
            name = "Harmoni Studio",
            category = "Studio",
            description = "Sub-bass solid dengan artikulasi vokal dan detail treble seimbang.",
            bandGainsDb = listOf(
                2.5f, 3.5f, 4.0f, 3.0f, 2.0f,
                1.0f, 0.5f, -0.5f, -1.0f, -0.5f,
                0.5f, 1.0f, 1.5f, 2.2f, 2.8f,
                3.2f, 3.8f, 4.2f, 4.0f, 3.0f
            ),
            bassBoost = 0.30f,
            virtualizer = 0.35f,
            stereoWidth = 1.15f
        ),
        EqPreset(
            id = "broadcast_clean",
            name = "Broadcast Clean",
            category = "Studio",
            description = "Memotong gemuruh rendah di bawah 50Hz agar sinyal bersih.",
            bandGainsDb = listOf(
                -6.0f, -4.5f, -2.5f, -1.0f, 0.0f,
                0.5f, 0.5f, 0.0f, 0.5f, 1.0f,
                1.5f, 2.0f, 2.5f, 2.5f, 2.0f,
                1.5f, 1.5f, 1.0f, 0.5f, 0.0f
            ),
            bassBoost = 0.05f,
            virtualizer = 0.10f,
            stereoWidth = 1.0f
        ),
        EqPreset(
            id = "late_night",
            name = "Late Night (Low Vol)",
            category = "Studio",
            description = "Kompensasi Loudness saat mendengarkan di volume pelan malam hari.",
            bandGainsDb = listOf(
                5.5f, 5.0f, 4.0f, 2.5f, 1.0f,
                0.0f, -0.5f, -1.0f, -1.0f, -0.5f,
                0.5f, 1.0f, 2.0f, 2.5f, 3.0f,
                3.5f, 4.0f, 4.0f, 3.5f, 2.5f
            ),
            bassBoost = 0.40f,
            virtualizer = 0.20f,
            stereoWidth = 1.10f
        ),

        // Musik & Genre
        EqPreset(
            id = "bass_boost",
            name = "Deep Sub Bass",
            category = "Musik",
            description = "Dorongan penuh pada 25Hz sampai 100Hz tanpa menutupi vokal.",
            bandGainsDb = listOf(
                8.5f, 8.0f, 7.0f, 5.5f, 4.0f,
                2.0f, 0.5f, -0.5f, -1.5f, -1.5f,
                -1.0f, 0.0f, 0.5f, 1.0f, 1.5f,
                2.0f, 2.5f, 2.5f, 2.0f, 1.5f
            ),
            bassBoost = 0.78f,
            virtualizer = 0.20f,
            stereoWidth = 1.15f
        ),
        EqPreset(
            id = "edm_club",
            name = "EDM Club",
            category = "Musik",
            description = "Kick drum tajam dengan hi-hat dan synth lebar.",
            bandGainsDb = listOf(
                7.0f, 7.5f, 6.5f, 5.0f, 3.0f,
                1.0f, -1.0f, -2.5f, -3.0f, -2.5f,
                -1.5f, 0.0f, 1.5f, 3.0f, 4.5f,
                5.5f, 6.5f, 7.0f, 6.5f, 5.0f
            ),
            bassBoost = 0.65f,
            virtualizer = 0.50f,
            stereoWidth = 1.40f
        ),
        EqPreset(
            id = "hiphop_trap",
            name = "Hip-Hop & Trap",
            category = "Musik",
            description = "Sub-bass 808 dalam dengan snare crisp dan vokal jelas.",
            bandGainsDb = listOf(
                8.0f, 8.5f, 7.5f, 5.5f, 3.0f,
                0.5f, -1.5f, -2.0f, -1.5f, 0.0f,
                1.5f, 2.0f, 2.5f, 2.0f, 2.5f,
                3.5f, 4.5f, 5.0f, 4.0f, 3.0f
            ),
            bassBoost = 0.80f,
            virtualizer = 0.25f,
            stereoWidth = 1.20f
        ),
        EqPreset(
            id = "house_groove",
            name = "House Groove",
            category = "Musik",
            description = "Mid-bass punchy di 70Hz sampai 140Hz untuk beat elektronik.",
            bandGainsDb = listOf(
                5.0f, 6.0f, 7.0f, 6.0f, 4.0f,
                1.5f, -0.5f, -1.5f, -1.5f, -0.5f,
                0.5f, 1.5f, 2.5f, 3.0f, 3.5f,
                4.5f, 5.0f, 5.5f, 4.5f, 3.5f
            ),
            bassBoost = 0.55f,
            virtualizer = 0.40f,
            stereoWidth = 1.30f
        ),
        EqPreset(
            id = "lofi_chill",
            name = "Lo-Fi Warmth",
            category = "Musik",
            description = "Treble lembut bebas tajam dengan mid hangat khas piringan hitam.",
            bandGainsDb = listOf(
                2.5f, 3.5f, 4.0f, 3.5f, 2.5f,
                2.0f, 1.5f, 1.0f, 0.5f, 0.0f,
                -0.5f, -1.0f, -1.5f, -2.0f, -2.5f,
                -3.5f, -4.5f, -5.5f, -6.5f, -7.0f
            ),
            bassBoost = 0.25f,
            virtualizer = 0.30f,
            stereoWidth = 1.15f
        ),
        EqPreset(
            id = "rock_arena",
            name = "Rock Arena",
            category = "Musik",
            description = "Gitar elektrik bertenaga dengan snare drum yang menghentak.",
            bandGainsDb = listOf(
                4.0f, 4.5f, 4.0f, 3.0f, 2.0f,
                1.0f, 0.0f, -1.5f, -2.0f, -1.0f,
                0.5f, 2.0f, 3.5f, 4.5f, 5.0f,
                5.0f, 4.5f, 4.0f, 3.5f, 2.5f
            ),
            bassBoost = 0.35f,
            virtualizer = 0.35f,
            stereoWidth = 1.25f
        ),
        EqPreset(
            id = "metal_crunch",
            name = "Metal Crunch",
            category = "Musik",
            description = "Double pedal kick presisi dan distorsi gitar agresif.",
            bandGainsDb = listOf(
                3.5f, 5.0f, 5.5f, 4.0f, 1.5f,
                -1.0f, -3.0f, -3.5f, -2.5f, -1.0f,
                1.0f, 2.5f, 4.0f, 5.5f, 6.0f,
                5.5f, 4.5f, 3.5f, 2.5f, 1.5f
            ),
            bassBoost = 0.30f,
            virtualizer = 0.25f,
            stereoWidth = 1.20f
        ),
        EqPreset(
            id = "pop_polish",
            name = "Pop Modern",
            category = "Musik",
            description = "Kurva ceria dengan bass padat dan kilau vokal modern.",
            bandGainsDb = listOf(
                3.0f, 3.5f, 4.0f, 3.0f, 1.5f,
                0.5f, -0.5f, -1.0f, -0.5f, 0.5f,
                1.5f, 2.5f, 3.5f, 4.0f, 4.5f,
                4.5f, 4.0f, 3.5f, 3.0f, 2.0f
            ),
            bassBoost = 0.32f,
            virtualizer = 0.30f,
            stereoWidth = 1.22f
        ),
        EqPreset(
            id = "rnb_smooth",
            name = "R&B Smooth",
            category = "Musik",
            description = "Low-end lembut dan vokal intim penuh kehangatan.",
            bandGainsDb = listOf(
                5.0f, 5.5f, 5.0f, 3.5f, 2.0f,
                1.0f, 0.5f, 0.0f, 0.5f, 1.0f,
                1.5f, 2.0f, 2.5f, 2.0f, 1.5f,
                1.5f, 2.0f, 2.5f, 2.0f, 1.5f
            ),
            bassBoost = 0.45f,
            virtualizer = 0.30f,
            stereoWidth = 1.18f
        ),
        EqPreset(
            id = "funk_slap",
            name = "Funk & Groove",
            category = "Musik",
            description = "Bassline slap yang menonjol dan perkusi ritmis tajam.",
            bandGainsDb = listOf(
                3.5f, 4.5f, 5.0f, 4.0f, 2.5f,
                1.0f, -1.0f, -2.0f, -1.5f, 0.0f,
                2.0f, 3.5f, 4.0f, 3.5f, 3.0f,
                3.5f, 4.5f, 4.5f, 3.5f, 2.5f
            ),
            bassBoost = 0.35f,
            virtualizer = 0.25f,
            stereoWidth = 1.20f
        ),
        EqPreset(
            id = "classical_hall",
            name = "Classical Concert",
            category = "Musik",
            description = "Dinamika orkestra natural dengan panggung stereo luas.",
            bandGainsDb = listOf(
                1.5f, 2.0f, 2.0f, 1.5f, 1.0f,
                0.5f, 0.0f, 0.0f, 0.5f, 1.0f,
                1.5f, 2.0f, 2.5f, 3.0f, 3.5f,
                4.0f, 4.5f, 4.5f, 4.0f, 3.0f
            ),
            bassBoost = 0.10f,
            virtualizer = 0.55f,
            stereoWidth = 1.45f
        ),

        // Vokal & Akustik
        EqPreset(
            id = "vocal_clarity",
            name = "Vocal Presence",
            category = "Vokal",
            description = "Fokus artikulasi 800Hz sampai 4.5kHz untuk vokal dan nyanyian.",
            bandGainsDb = listOf(
                -4.0f, -3.5f, -2.5f, -1.5f, -0.5f,
                0.5f, 1.0f, 1.5f, 2.5f, 3.5f,
                4.5f, 5.5f, 6.0f, 5.5f, 4.5f,
                3.0f, 2.0f, 1.5f, 1.0f, 0.5f
            ),
            bassBoost = 0.05f,
            virtualizer = 0.15f,
            stereoWidth = 1.05f
        ),
        EqPreset(
            id = "podcast_voice",
            name = "Podcast & YouTube Talk",
            category = "Vokal",
            description = "Memperjelas suara bicara pada video YouTube, webinar, dan podcast.",
            bandGainsDb = listOf(
                -8.0f, -6.5f, -4.5f, -2.5f, -1.0f,
                0.0f, 0.5f, 1.0f, 2.0f, 3.0f,
                4.0f, 5.0f, 5.5f, 5.0f, 4.0f,
                2.5f, 1.5f, 0.5f, 0.0f, -1.0f
            ),
            bassBoost = 0.0f,
            virtualizer = 0.05f,
            stereoWidth = 0.95f
        ),
        EqPreset(
            id = "acoustic_guitar",
            name = "Acoustic Strings",
            category = "Vokal",
            description = "Menghidupkan petikan senar gitar dan resonansi bodi kayu.",
            bandGainsDb = listOf(
                -2.0f, -1.0f, 0.5f, 1.5f, 2.0f,
                1.5f, 0.5f, -0.5f, 0.0f, 1.0f,
                2.0f, 3.0f, 3.5f, 4.0f, 4.5f,
                4.5f, 4.0f, 3.5f, 3.0f, 2.0f
            ),
            bassBoost = 0.10f,
            virtualizer = 0.30f,
            stereoWidth = 1.25f
        ),
        EqPreset(
            id = "jazz_lounge",
            name = "Jazz Lounge",
            category = "Vokal",
            description = "Kontrabas hangat dengan simbal lembut dan saksofon natural.",
            bandGainsDb = listOf(
                2.0f, 2.5f, 3.0f, 2.5f, 1.5f,
                1.0f, 0.5f, 0.5f, 1.0f, 1.5f,
                2.0f, 2.5f, 2.5f, 2.0f, 2.0f,
                2.5f, 3.0f, 3.0f, 2.5f, 2.0f
            ),
            bassBoost = 0.20f,
            virtualizer = 0.35f,
            stereoWidth = 1.25f
        ),

        // Headphone & Perangkat (Wavelet-style)
        EqPreset(
            id = "tws_punch",
            name = "TWS / Earbuds Punch",
            category = "Headphone",
            description = "Mengimbangi karakter TWS nirkabel agar lebih dalam dan bertenaga.",
            bandGainsDb = listOf(
                6.0f, 5.5f, 4.5f, 3.0f, 1.5f,
                0.5f, -0.5f, -1.0f, -0.5f, 0.5f,
                1.5f, 2.5f, 3.0f, 3.5f, 3.0f,
                2.5f, 3.5f, 4.0f, 3.5f, 2.5f
            ),
            bassBoost = 0.50f,
            virtualizer = 0.40f,
            stereoWidth = 1.30f
        ),
        EqPreset(
            id = "iem_vshape_fix",
            name = "IEM Vocal Balance",
            category = "Headphone",
            description = "Mengisi midrange IEM V-shape dan meredam sibilance tajam 6kHz.",
            bandGainsDb = listOf(
                1.5f, 1.0f, 0.5f, 0.0f, -0.5f,
                0.0f, 1.0f, 2.0f, 3.0f, 3.5f,
                3.5f, 3.0f, 2.0f, 1.0f, 0.0f,
                -1.5f, -2.5f, -1.0f, 0.5f, 1.5f
            ),
            bassBoost = 0.15f,
            virtualizer = 0.15f,
            stereoWidth = 1.08f
        ),
        EqPreset(
            id = "open_back_warmth",
            name = "Open-Back Sub Fix",
            category = "Headphone",
            description = "Mengangkat roll-off sub-bass pada headphone open-back.",
            bandGainsDb = listOf(
                7.0f, 6.0f, 4.5f, 3.0f, 1.5f,
                0.5f, 0.0f, 0.0f, 0.0f, 0.5f,
                1.0f, 1.5f, 1.5f, 1.0f, 0.5f,
                0.0f, -0.5f, 0.0f, 0.5f, 1.0f
            ),
            bassBoost = 0.35f,
            virtualizer = 0.10f,
            stereoWidth = 1.0f
        ),
        EqPreset(
            id = "phone_speaker_boost",
            name = "Speaker HP Clarity",
            category = "Speaker",
            description = "Mengurangi sub-bass dan menonjolkan kejelasan speaker ponsel. Sesuaikan pada volume nyaman.",
            bandGainsDb = listOf(
                -6.0f, -5.0f, -3.0f, 0.0f, 3.0f,
                4.5f, 4.0f, 2.5f, 1.5f, 2.0f,
                3.0f, 4.0f, 4.5f, 4.0f, 3.5f,
                3.0f, 2.5f, 2.0f, 1.5f, 1.0f
            ),
            bassBoost = 0.10f,
            virtualizer = 0.25f,
            stereoWidth = 1.25f
        ),

        // Gaming & Film
        EqPreset(
            id = "gaming_3d",
            name = "FPS Footsteps 3D",
            category = "Gaming",
            description = "Menonjolkan langkah kaki dan arah tembakan dengan panggung spasial.",
            bandGainsDb = listOf(
                1.0f, 0.5f, -1.0f, -2.0f, -2.5f,
                -2.0f, -1.5f, 0.0f, 1.5f, 3.0f,
                4.5f, 5.5f, 6.5f, 6.5f, 6.0f,
                5.0f, 4.5f, 4.0f, 3.0f, 2.0f
            ),
            bassBoost = 0.10f,
            virtualizer = 0.90f,
            stereoWidth = 1.75f
        ),
        EqPreset(
            id = "cinema_surround",
            name = "Cinema & YouTube Film",
            category = "Gaming",
            description = "Efek ledakan sinematik, dialog tegas, dan ruang audio teater.",
            bandGainsDb = listOf(
                6.5f, 6.0f, 4.5f, 3.0f, 1.5f,
                0.0f, -0.5f, 0.0f, 1.0f, 2.5f,
                3.5f, 4.0f, 4.0f, 3.5f, 3.0f,
                3.5f, 4.0f, 4.5f, 4.0f, 3.0f
            ),
            bassBoost = 0.60f,
            virtualizer = 0.80f,
            stereoWidth = 1.60f
        )
    )

    val settingsFlow: Flow<PersistedEqSettings> = store.data.map { prefs ->
        val default = PersistedEqSettings()
        fun finite(key: Preferences.Key<Float>, fallback: Float, range: ClosedFloatingPointRange<Float>): Float =
            prefs[key]?.takeIf { it.isFinite() }?.coerceIn(range.start, range.endInclusive) ?: fallback
        val leftGains = prefs[Keys.LEFT_BAND_GAINS]?.split(",")
            ?.mapNotNull { it.toFloatOrNull() }
            ?.takeIf { it.size == 20 && it.all { gain -> gain.isFinite() && gain in -15f..15f } }
            ?: default.leftBandGainsDb
        val rightGains = prefs[Keys.RIGHT_BAND_GAINS]?.split(",")
            ?.mapNotNull { it.toFloatOrNull() }
            ?.takeIf { it.size == 20 && it.all { gain -> gain.isFinite() && gain in -15f..15f } }
            ?: leftGains

        val reverbName = prefs[Keys.REVERB_PRESET]
        val parsedReverb = ReverbRoomPreset.entries.firstOrNull { it.name == reverbName }
            ?: default.reverbPreset

        PersistedEqSettings(
            favoriteIds = prefs[Keys.FAVORITES] ?: emptySet(),
            autoProfiles = prefs[Keys.AUTO_PROFILES] ?: false,
            autoHeadroom = prefs[Keys.AUTO_HEADROOM] ?: false,
            outputProfiles = runCatching {
                val a = JSONArray(prefs[Keys.PROFILES] ?: "[]")
                List(a.length()) { i -> a.getJSONObject(i).let { p ->
                    com.example.model.OutputProfile(p.getString("key"), p.getString("label"), p.getString("preset"))
                } }
            }.getOrDefault(emptyList()),
            eqEnabled = prefs[Keys.EQ_ENABLED] ?: default.eqEnabled,
            stereoLinked = prefs[Keys.STEREO_LINKED] ?: default.stereoLinked,
            activePresetId = prefs[Keys.ACTIVE_PRESET_ID] ?: default.activePresetId,
            leftBandGainsDb = leftGains,
            rightBandGainsDb = rightGains,
            bassBoostEnabled = prefs[Keys.BASS_ENABLED] ?: default.bassBoostEnabled,
            bassBoostStrength = finite(Keys.BASS_STRENGTH, default.bassBoostStrength, 0f..1f),
            virtualizerEnabled = prefs[Keys.VIRT_ENABLED] ?: default.virtualizerEnabled,
            virtualizerStrength = finite(Keys.VIRT_STRENGTH, default.virtualizerStrength, 0f..1f),
            stereoWidth = finite(Keys.STEREO_WIDTH, default.stereoWidth, 0f..2f),
            stereoBalance = finite(Keys.STEREO_BALANCE, default.stereoBalance, -1f..1f),
            preampEnabled = prefs[Keys.PREAMP_ENABLED] ?: default.preampEnabled,
            preampGainDb = finite(Keys.PREAMP_GAIN, default.preampGainDb, -12f..12f),
            limiterEnabled = prefs[Keys.LIMITER_ENABLED] ?: default.limiterEnabled,
            reverbPreset = parsedReverb,
            customPresets = (prefs[Keys.COMPLETE_PRESETS] ?: prefs[Keys.CUSTOM_PRESETS])
                ?.takeIf { it.isNotBlank() }?.let { PresetCodec.decode(it, legacyStorage = true) } ?: emptyList()
        )
    }

    suspend fun saveSettings(settings: PersistedEqSettings) {
        store.edit { prefs ->
            prefs[Keys.FAVORITES] = settings.favoriteIds
            prefs[Keys.AUTO_PROFILES] = settings.autoProfiles
            prefs[Keys.AUTO_HEADROOM] = settings.autoHeadroom
            prefs[Keys.PROFILES] = JSONArray().apply { settings.outputProfiles.forEach { p ->
                put(JSONObject().put("key", p.deviceKey).put("label", p.deviceLabel).put("preset", p.presetId))
            } }.toString()
            prefs[Keys.EQ_ENABLED] = settings.eqEnabled
            prefs[Keys.STEREO_LINKED] = settings.stereoLinked
            prefs[Keys.ACTIVE_PRESET_ID] = settings.activePresetId
            prefs[Keys.LEFT_BAND_GAINS] = settings.leftBandGainsDb.joinToString(",")
            prefs[Keys.RIGHT_BAND_GAINS] = settings.rightBandGainsDb.joinToString(",")
            prefs[Keys.BASS_ENABLED] = settings.bassBoostEnabled
            prefs[Keys.BASS_STRENGTH] = settings.bassBoostStrength
            prefs[Keys.VIRT_ENABLED] = settings.virtualizerEnabled
            prefs[Keys.VIRT_STRENGTH] = settings.virtualizerStrength
            prefs[Keys.STEREO_WIDTH] = settings.stereoWidth
            prefs[Keys.STEREO_BALANCE] = settings.stereoBalance
            prefs[Keys.PREAMP_ENABLED] = settings.preampEnabled
            prefs[Keys.PREAMP_GAIN] = settings.preampGainDb
            prefs[Keys.LIMITER_ENABLED] = settings.limiterEnabled
            prefs[Keys.REVERB_PRESET] = settings.reverbPreset.name
            // Leave the original v3 library untouched for safe migration/recovery.
            val previous = prefs[Keys.COMPLETE_PRESETS]
            require(previous == null || runCatching { PresetCodec.decode(previous, legacyStorage = true) }.isSuccess) {
                "Pustaka lama tidak dapat dibaca; penyimpanan dibatalkan agar data asli tetap utuh."
            }
            prefs[Keys.COMPLETE_PRESETS] = encodeCustomPresets(settings.customPresets)
        }
    }

    private fun encodeCustomPresets(presets: List<EqPreset>): String = PresetCodec.encode(presets)

    private fun decodeCustomPresets(json: String?): List<EqPreset> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching { PresetCodec.decode(json, legacyStorage = true) }.getOrDefault(emptyList())
    }
}
