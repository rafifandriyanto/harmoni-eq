package com.example.ui.theme

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.max
import kotlin.math.min

private val Context.harmoniThemeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "harmoni_theme_prefs_v1"
)

enum class ThemeMode(val label: String) {
    SYSTEM("Sistem"),
    DARK("Gelap"),
    LIGHT("Terang")
}

enum class AccentPreset(
    val label: String,
    val darkColor: Color,
    val lightColor: Color,
    val spotColor: Color
) {
    AMBER(
        label = "Amber",
        darkColor = Color(0xFFD48B57),
        lightColor = Color(0xFF9A5728),
        spotColor = Color(0xFFC77D4A)
    ),
    SAGE(
        label = "Sage",
        darkColor = Color(0xFF7FA385),
        lightColor = Color(0xFF436649),
        spotColor = Color(0xFF6B8F71)
    ),
    SLATE(
        label = "Slate",
        darkColor = Color(0xFF8AA9C7),
        lightColor = Color(0xFF335B7D),
        spotColor = Color(0xFF6E8FAF)
    ),
    CLAY(
        label = "Clay",
        darkColor = Color(0xFFD18677),
        lightColor = Color(0xFF914437),
        spotColor = Color(0xFFC27263)
    ),
    CUSTOM(
        label = "Kustom",
        darkColor = Color(0xFFD48B57),
        lightColor = Color(0xFF9A5728),
        spotColor = Color(0xFFC77D4A)
    )
}

@Immutable
data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val accent: AccentPreset = AccentPreset.AMBER,
    val customHex: String = "#C77D4A",
    val reduceTransparency: Boolean = false,
    val reduceMotion: Boolean = false
)

@Immutable
data class ThemeController(
    val settings: ThemeSettings = ThemeSettings(),
    val effectiveReduceMotion: Boolean = false,
    val setThemeMode: (ThemeMode) -> Unit = {},
    val setAccentPreset: (AccentPreset) -> Unit = {},
    val setCustomHex: (String) -> Unit = {},
    val setReduceTransparency: (Boolean) -> Unit = {},
    val setReduceMotion: (Boolean) -> Unit = {}
)

val LocalThemeController = staticCompositionLocalOf { ThemeController() }

class ThemePreferencesRepository(context: Context) {
    private val store = context.applicationContext.harmoniThemeDataStore

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT_PRESET = stringPreferencesKey("accent_preset")
        val CUSTOM_HEX = stringPreferencesKey("custom_hex")
        val REDUCE_TRANSPARENCY = booleanPreferencesKey("reduce_transparency")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
    }

    val settingsFlow: Flow<ThemeSettings> = store.data.map { prefs ->
        val mode = prefs[Keys.THEME_MODE]
            ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
            ?: ThemeMode.SYSTEM
        val accent = prefs[Keys.ACCENT_PRESET]
            ?.let { name -> AccentPreset.entries.firstOrNull { it.name == name } }
            ?: AccentPreset.AMBER
        val hex = prefs[Keys.CUSTOM_HEX]?.takeIf(::isValidHexColor) ?: "#C77D4A"
        ThemeSettings(
            mode = mode,
            accent = accent,
            customHex = hex,
            reduceTransparency = prefs[Keys.REDUCE_TRANSPARENCY] ?: false,
            reduceMotion = prefs[Keys.REDUCE_MOTION] ?: false
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setAccentPreset(preset: AccentPreset) {
        store.edit { it[Keys.ACCENT_PRESET] = preset.name }
    }

    suspend fun setCustomHex(hex: String) {
        val normalized = normalizeHexColor(hex) ?: return
        store.edit {
            it[Keys.CUSTOM_HEX] = normalized
            it[Keys.ACCENT_PRESET] = AccentPreset.CUSTOM.name
        }
    }

    suspend fun setReduceTransparency(enabled: Boolean) {
        store.edit { it[Keys.REDUCE_TRANSPARENCY] = enabled }
    }

    suspend fun setReduceMotion(enabled: Boolean) {
        store.edit { it[Keys.REDUCE_MOTION] = enabled }
    }
}

fun normalizeHexColor(raw: String): String? {
    val clean = raw.trim().removePrefix("#").uppercase()
    if (clean.length != 6 || !clean.all { it in '0'..'9' || it in 'A'..'F' }) return null
    return "#$clean"
}

fun isValidHexColor(raw: String): Boolean = normalizeHexColor(raw) != null

/**
 * Mengonversi hex kustom menjadi warna aksen yang aman dari neon (saturasi dibatasi)
 * dan memiliki kontras memadai untuk mode gelap maupun terang.
 */
fun resolveSafeCustomAccent(hex: String, isDark: Boolean): Color {
    val normalized = normalizeHexColor(hex) ?: return if (isDark) AccentPreset.AMBER.darkColor else AccentPreset.AMBER.lightColor
    val argb = normalized.removePrefix("#").toLong(16)
    val r = ((argb shr 16) and 0xFF) / 255f
    val g = ((argb shr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f

    val maxC = max(r, max(g, b))
    val minC = min(r, min(g, b))
    val delta = maxC - minC

    val h = when {
        delta == 0f -> 0f
        maxC == r -> 60f * (((g - b) / delta) % 6f)
        maxC == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }

    val lOrig = (maxC + minC) / 2f
    val sOrig = if (delta == 0f) 0f else delta / (1f - kotlin.math.abs(2f * lOrig - 1f))

    // Batasi saturasi agar bebas efek neon, lalu sesuaikan lightness untuk kontras AA
    val safeS = sOrig.coerceIn(0.22f, if (isDark) 0.54f else 0.56f)
    val safeL = if (isDark) 0.62f else 0.36f

    return hslToColor(h, safeS, safeL)
}

private fun hslToColor(h: Float, s: Float, l: Float): Color {
    val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
    val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
    val m = l - c / 2f
    val (r1, g1, b1) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(
        red = (r1 + m).coerceIn(0f, 1f),
        green = (g1 + m).coerceIn(0f, 1f),
        blue = (b1 + m).coerceIn(0f, 1f),
        alpha = 1f
    )
}
