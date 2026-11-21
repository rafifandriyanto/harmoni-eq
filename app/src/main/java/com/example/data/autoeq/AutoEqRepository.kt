package com.example.data.autoeq

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.CachedAutoEqCurve
import com.example.model.HeadphoneProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private val Context.autoEqDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "harmoni_autoeq_prefs_v1"
)

class AutoEqParseException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

data class AutoEqIndexResult(
    val profiles: List<HeadphoneProfile>,
    val fromOfflineCacheFallback: Boolean = false
)

data class PersistedAutoEqState(
    val activeProfile: HeadphoneProfile? = null,
    val activeGainsDb: List<Float>? = null,
    val manualBackupLeftDb: List<Float>? = null,
    val manualBackupRightDb: List<Float>? = null,
    val cachedProfileIds: Set<String> = emptySet(),
    val indexUpdatedAtMs: Long = 0L
)

/**
 * Offline-first repository for AutoEQ headphone profiles, raw CSV caching,
 * 20-band interpolated curves, and active profile restoration via DataStore.
 */
class AutoEqRepository(
    context: Context,
    private val store: DataStore<Preferences> = context.applicationContext.autoEqDataStore,
    private val remote: AutoEqRemoteDataSource = AutoEqRemoteDataSource()
) {
    private object Keys {
        val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
        val ACTIVE_PROFILE_NAME = stringPreferencesKey("active_profile_name")
        val ACTIVE_PROFILE_TYPE = stringPreferencesKey("active_profile_type")
        val ACTIVE_PROFILE_URL = stringPreferencesKey("active_profile_url")
        val ACTIVE_GAINS_DB = stringPreferencesKey("active_gains_db")
        val MANUAL_BACKUP_LEFT = stringPreferencesKey("manual_backup_left_db")
        val MANUAL_BACKUP_RIGHT = stringPreferencesKey("manual_backup_right_db")
        val CACHED_INDEX_JSON = stringPreferencesKey("cached_index_json")
        val CACHED_INDEX_TIMESTAMP = longPreferencesKey("cached_index_timestamp")
        val CACHED_CURVES_JSON = stringPreferencesKey("cached_curves_json")
    }

    val stateFlow: Flow<PersistedAutoEqState> = store.data.map { prefs ->
        val id = prefs[Keys.ACTIVE_PROFILE_ID]
        val name = prefs[Keys.ACTIVE_PROFILE_NAME]
        val type = prefs[Keys.ACTIVE_PROFILE_TYPE]
        val url = prefs[Keys.ACTIVE_PROFILE_URL]
        val activeProfile = if (!id.isNullOrBlank() && !name.isNullOrBlank() && !url.isNullOrBlank()) {
            HeadphoneProfile(name = name, type = type.orEmpty().ifBlank { "Over-ear" }, csvUrl = url)
        } else {
            null
        }
        val curves = decodeCachedCurves(prefs[Keys.CACHED_CURVES_JSON])
        PersistedAutoEqState(
            activeProfile = activeProfile,
            activeGainsDb = parseGainList(prefs[Keys.ACTIVE_GAINS_DB]),
            manualBackupLeftDb = parseGainList(prefs[Keys.MANUAL_BACKUP_LEFT]),
            manualBackupRightDb = parseGainList(prefs[Keys.MANUAL_BACKUP_RIGHT]),
            cachedProfileIds = curves.keys,
            indexUpdatedAtMs = prefs[Keys.CACHED_INDEX_TIMESTAMP] ?: 0L
        )
    }

    /**
     * Loads the headphone catalog from local cache if available (unless [forceRefresh] is true),
     * otherwise fetches from jsDelivr/GitHub and caches it in DataStore for offline use.
     */
    suspend fun getHeadphoneIndex(forceRefresh: Boolean = false): Result<List<HeadphoneProfile>> =
        getHeadphoneIndexWithFallback(forceRefresh = forceRefresh, tryNetworkFirst = forceRefresh)
            .map { it.profiles }

    /**
     * Loads the headphone catalog and reports whether an offline local cache fallback was used
     * after a network failure so the UI can inform the user with an offline-mode snackbar.
     */
    suspend fun getHeadphoneIndexWithFallback(
        forceRefresh: Boolean = false,
        tryNetworkFirst: Boolean = true
    ): Result<AutoEqIndexResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val prefs = store.data.first()
                val cached = decodeProfileIndex(prefs[Keys.CACHED_INDEX_JSON])
                if (!forceRefresh && !tryNetworkFirst && cached.isNotEmpty()) {
                    return@runCatching AutoEqIndexResult(
                        profiles = cached,
                        fromOfflineCacheFallback = false
                    )
                }

                val fetched = runCatching { remote.fetchHeadphoneIndex() }.getOrElse { err ->
                    if (cached.isNotEmpty()) {
                        return@runCatching AutoEqIndexResult(
                            profiles = cached,
                            fromOfflineCacheFallback = true
                        )
                    }
                    throw err
                }
                val sorted = fetched.sortedBy { it.name.lowercase() }
                store.edit { mutable ->
                    mutable[Keys.CACHED_INDEX_JSON] = encodeProfileIndex(sorted)
                    mutable[Keys.CACHED_INDEX_TIMESTAMP] = System.currentTimeMillis()
                }
                AutoEqIndexResult(profiles = sorted, fromOfflineCacheFallback = false)
            }
        }

    /**
     * Returns the cached or freshly downloaded & interpolated 20-band EQ curve for [profile].
     * Always caches both the raw CSV and interpolated 20-band gains in DataStore for offline access.
     */
    suspend fun loadOrFetchCurve(profile: HeadphoneProfile): Result<CachedAutoEqCurve> =
        withContext(Dispatchers.IO) {
            runCatching {
                val prefs = store.data.first()
                val existingMap = decodeCachedCurves(prefs[Keys.CACHED_CURVES_JSON]).toMutableMap()
                existingMap[profile.id]?.let { cached ->
                    if (cached.interpolatedGainsDb.size == 20) {
                        return@runCatching cached
                    }
                }

                val rawCsv = remote.fetchProfileCsv(profile)
                val parsedPoints = try {
                    AutoEqCsvParser.parseCsv(rawCsv)
                } catch (err: Exception) {
                    throw AutoEqParseException("Failed to load profile data.", err)
                }
                if (parsedPoints.size < 2) {
                    throw AutoEqParseException("Failed to load profile data.")
                }

                val interpolated = AutoEqInterpolator.interpolateCubicSpline(parsedPoints)
                val entry = CachedAutoEqCurve(
                    profile = profile,
                    rawCsv = rawCsv,
                    interpolatedGainsDb = interpolated,
                    cachedAtMs = System.currentTimeMillis()
                )

                // Keep up to 40 most recently used cached headphone CSVs in DataStore to bound storage size
                existingMap[profile.id] = entry
                val trimmed = existingMap.values
                    .sortedByDescending { it.cachedAtMs }
                    .take(40)
                    .associateBy { it.profile.id }

                store.edit { mutable ->
                    mutable[Keys.CACHED_CURVES_JSON] = encodeCachedCurves(trimmed)
                }
                entry
            }
        }

    /** Persists the currently active AutoEQ profile and optionally snapshots manual EQ gains for restore. */
    suspend fun saveActiveProfile(
        profile: HeadphoneProfile,
        interpolatedGainsDb: List<Float>,
        manualBackupLeftDb: List<Float>? = null,
        manualBackupRightDb: List<Float>? = null
    ) {
        store.edit { prefs ->
            prefs[Keys.ACTIVE_PROFILE_ID] = profile.id
            prefs[Keys.ACTIVE_PROFILE_NAME] = profile.name
            prefs[Keys.ACTIVE_PROFILE_TYPE] = profile.type
            prefs[Keys.ACTIVE_PROFILE_URL] = profile.csvUrl
            prefs[Keys.ACTIVE_GAINS_DB] = interpolatedGainsDb.joinToString(",")
            if (manualBackupLeftDb != null && manualBackupRightDb != null) {
                prefs[Keys.MANUAL_BACKUP_LEFT] = manualBackupLeftDb.joinToString(",")
                prefs[Keys.MANUAL_BACKUP_RIGHT] = manualBackupRightDb.joinToString(",")
            }
        }
    }

    /** Clears the active AutoEQ profile and returns any saved manual backup gains. */
    suspend fun clearActiveProfile(): Pair<List<Float>?, List<Float>?> {
        val current = stateFlow.first()
        val backup = current.manualBackupLeftDb to current.manualBackupRightDb
        store.edit { prefs ->
            prefs.remove(Keys.ACTIVE_PROFILE_ID)
            prefs.remove(Keys.ACTIVE_PROFILE_NAME)
            prefs.remove(Keys.ACTIVE_PROFILE_TYPE)
            prefs.remove(Keys.ACTIVE_PROFILE_URL)
            prefs.remove(Keys.ACTIVE_GAINS_DB)
            prefs.remove(Keys.MANUAL_BACKUP_LEFT)
            prefs.remove(Keys.MANUAL_BACKUP_RIGHT)
        }
        return backup
    }

    private fun parseGainList(raw: String?): List<Float>? {
        if (raw.isNullOrBlank()) return null
        val values = raw.split(",").mapNotNull { it.toFloatOrNull() }
        return values.takeIf { it.size == 20 && it.all { v -> v.isFinite() && v in -15f..15f } }
    }

    private fun encodeProfileIndex(profiles: List<HeadphoneProfile>): String {
        val arr = JSONArray()
        profiles.forEach { p ->
            arr.put(
                JSONObject()
                    .put("n", p.name)
                    .put("t", p.type)
                    .put("u", p.csvUrl)
            )
        }
        return arr.toString()
    }

    private fun decodeProfileIndex(json: String?): List<HeadphoneProfile> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                HeadphoneProfile(
                    name = o.getString("n"),
                    type = o.optString("t", "Over-ear"),
                    csvUrl = o.getString("u")
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeCachedCurves(curves: Map<String, CachedAutoEqCurve>): String {
        val arr = JSONArray()
        curves.values.forEach { c ->
            arr.put(
                JSONObject()
                    .put("n", c.profile.name)
                    .put("t", c.profile.type)
                    .put("u", c.profile.csvUrl)
                    .put("csv", c.rawCsv)
                    .put("g", c.interpolatedGainsDb.joinToString(","))
                    .put("ts", c.cachedAtMs)
            )
        }
        return arr.toString()
    }

    private fun decodeCachedCurves(json: String?): Map<String, CachedAutoEqCurve> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            val arr = JSONArray(json)
            val result = LinkedHashMap<String, CachedAutoEqCurve>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val profile = HeadphoneProfile(
                    name = o.getString("n"),
                    type = o.optString("t", "Over-ear"),
                    csvUrl = o.getString("u")
                )
                val gains = parseGainList(o.optString("g")) ?: continue
                result[profile.id] = CachedAutoEqCurve(
                    profile = profile,
                    rawCsv = o.optString("csv"),
                    interpolatedGainsDb = gains,
                    cachedAtMs = o.optLong("ts", 0L)
                )
            }
            result
        }.getOrDefault(emptyMap())
    }
}
