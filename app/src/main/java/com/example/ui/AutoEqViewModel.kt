package com.example.ui

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.StudioAudioEngine
import com.example.data.autoeq.AutoEqParseException
import com.example.data.autoeq.AutoEqRepository
import com.example.model.HeadphoneProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * Sealed UI state for the AutoEQ Headphone Correction bottom sheet.
 */
@Immutable
sealed interface AutoEqUiState {
    data object Idle : AutoEqUiState
    data object Loading : AutoEqUiState
    data class Success(
        val profiles: List<HeadphoneProfile>,
        val selectedProfile: HeadphoneProfile?
    ) : AutoEqUiState
    data class Error(val message: String) : AutoEqUiState
}

/**
 * ViewModel managing AutoEQ headphone profile loading on [Dispatchers.IO],
 * 300ms debounced in-memory case-insensitive search filtering on [Dispatchers.Default],
 * robust network/parse error handling with offline cache fallback, smooth gain handoff
 * to [StudioAudioEngine], and manual-fader detachment via [detachAutoEq].
 */
@OptIn(FlowPreview::class)
@Stable
class AutoEqViewModel(
    application: Application,
    private val repository: AutoEqRepository,
    private val searchDebounceMs: Long = DEFAULT_SEARCH_DEBOUNCE_MS,
    private val applyToAudioEngine: (FloatArray, Long) -> Unit = { gains, rampMs ->
        StudioAudioEngine.applyAutoEqGains(targetGains = gains, rampTimeMs = rampMs)
    }
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application = application,
        repository = AutoEqRepository(application.applicationContext)
    )

    constructor(
        application: Application,
        repository: AutoEqRepository,
        applyToAudioEngine: (FloatArray, Long) -> Unit
    ) : this(
        application = application,
        repository = repository,
        searchDebounceMs = DEFAULT_SEARCH_DEBOUNCE_MS,
        applyToAudioEngine = applyToAudioEngine
    )

    private val _uiState = MutableStateFlow<AutoEqUiState>(AutoEqUiState.Idle)
    val uiState: StateFlow<AutoEqUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedTypeFilter = MutableStateFlow("All")
    val selectedTypeFilter: StateFlow<String> = _selectedTypeFilter.asStateFlow()

    private val _cachedProfileIds = MutableStateFlow<Set<String>>(emptySet())
    val cachedProfileIds: StateFlow<Set<String>> = _cachedProfileIds.asStateFlow()

    private val _activeCurveGainsDb = MutableStateFlow<List<Float>?>(null)
    val activeCurveGainsDb: StateFlow<List<Float>?> = _activeCurveGainsDb.asStateFlow()

    private val _applyingProfileId = MutableStateFlow<String?>(null)
    val applyingProfileId: StateFlow<String?> = _applyingProfileId.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _allProfiles = MutableStateFlow<List<HeadphoneProfile>>(emptyList())
    private val _selectedProfile = MutableStateFlow<HeadphoneProfile?>(null)
    private val _catalogLoaded = MutableStateFlow(false)

    internal val filterExecutionCount = AtomicInteger(0)

    init {
        viewModelScope.launch {
            repository.stateFlow.collect { persisted ->
                _selectedProfile.value = persisted.activeProfile
                _cachedProfileIds.value = persisted.cachedProfileIds
                _activeCurveGainsDb.value = persisted.activeGainsDb
            }
        }

        // Debounced (300ms) in-memory case-insensitive filtering executed off the Main thread.
        val debouncedQuery = _searchQuery
            .debounce(searchDebounceMs)
            .onStart { emit(_searchQuery.value) }
            .distinctUntilChanged()

        viewModelScope.launch {
            combine(
                _catalogLoaded,
                _allProfiles,
                debouncedQuery,
                _selectedTypeFilter,
                _selectedProfile
            ) { loaded, profiles, query, typeFilter, selected ->
                if (!loaded) {
                    null
                } else {
                    filterExecutionCount.incrementAndGet()
                    AutoEqUiState.Success(
                        profiles = filterAndSort(
                            profiles = profiles,
                            query = query,
                            typeFilter = typeFilter
                        ),
                        selectedProfile = selected
                    )
                }
            }
                .flowOn(Dispatchers.Default)
                .collect { successState ->
                    if (successState != null && _uiState.value !is AutoEqUiState.Loading && _uiState.value !is AutoEqUiState.Error) {
                        _uiState.value = successState
                    }
                }
        }
    }

    /**
     * Triggers Phase 2 headphone catalog loading on [Dispatchers.IO].
     * Falls back to cached profiles on network failure with an offline-mode snackbar,
     * or emits [AutoEqUiState.Error] if no cache is available.
     */
    fun loadProfiles(forceRefresh: Boolean = false) {
        if (_uiState.value is AutoEqUiState.Loading) return
        if (!forceRefresh && _catalogLoaded.value && _uiState.value is AutoEqUiState.Success) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = AutoEqUiState.Loading
            repository.getHeadphoneIndexWithFallback(
                forceRefresh = forceRefresh,
                tryNetworkFirst = true
            )
                .onSuccess { result ->
                    val sorted = result.profiles.sortedBy { it.name.lowercase() }
                    _allProfiles.value = sorted
                    _catalogLoaded.value = true
                    val filtered = filterAndSort(
                        profiles = sorted,
                        query = _searchQuery.value,
                        typeFilter = _selectedTypeFilter.value
                    )
                    _uiState.value = AutoEqUiState.Success(
                        profiles = filtered,
                        selectedProfile = _selectedProfile.value
                    )
                    if (result.fromOfflineCacheFallback) {
                        _snackbarMessage.value = OFFLINE_CACHE_SNACKBAR
                    }
                }
                .onFailure { err ->
                    _uiState.value = AutoEqUiState.Error(mapErrorMessage(err))
                }
        }
    }

    fun ensureIndexLoaded(forceRefresh: Boolean = false) = loadProfiles(forceRefresh)

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectTypeFilter(type: String) {
        _selectedTypeFilter.value = type
    }

    fun consumeSnackbarMessage() {
        _snackbarMessage.value = null
    }

    /**
     * Fetches the interpolated 20-band [FloatArray] for [profile], updates [_selectedProfile],
     * smoothly ramps [StudioAudioEngine] to the target gains, and notifies [onApplied].
     */
    fun selectProfile(
        profile: HeadphoneProfile,
        onApplied: (HeadphoneProfile, FloatArray) -> Unit = { _, _ -> }
    ) {
        if (_applyingProfileId.value != null) return
        viewModelScope.launch {
            _applyingProfileId.value = profile.id
            repository.loadOrFetchCurve(profile)
                .onSuccess { cached ->
                    val gainsArray = cached.interpolatedGainsDb.toFloatArray()
                    _selectedProfile.value = profile
                    _activeCurveGainsDb.value = cached.interpolatedGainsDb
                    _applyingProfileId.value = null
                    if (_catalogLoaded.value) {
                        _uiState.value = AutoEqUiState.Success(
                            profiles = filterAndSort(
                                profiles = _allProfiles.value,
                                query = _searchQuery.value,
                                typeFilter = _selectedTypeFilter.value
                            ),
                            selectedProfile = profile
                        )
                    }

                    // Smoothly ramp DSP gains over 100ms to avoid audio pops/clicks
                    applyToAudioEngine(gainsArray, 100L)
                    onApplied(profile, gainsArray)
                }
                .onFailure { err ->
                    _applyingProfileId.value = null
                    _uiState.value = AutoEqUiState.Error(mapErrorMessage(err))
                }
        }
    }

    /**
     * Detaches the active AutoEQ profile (for example when the user manually drags a fader),
     * clearing [selectedProfile] in StateFlow and persisted storage so the user has manual control.
     */
    fun detachAutoEq() {
        _selectedProfile.value = null
        _activeCurveGainsDb.value = null
        val current = _uiState.value
        if (current is AutoEqUiState.Success && current.selectedProfile != null) {
            _uiState.value = current.copy(selectedProfile = null)
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.clearActiveProfile() }
        }
    }

    fun clearSelection() {
        detachAutoEq()
    }

    private fun mapErrorMessage(err: Throwable): String = when (err) {
        is AutoEqParseException -> PARSE_ERROR_MESSAGE
        else -> NETWORK_ERROR_MESSAGE
    }

    companion object {
        const val DEFAULT_SEARCH_DEBOUNCE_MS = 300L
        const val NETWORK_ERROR_MESSAGE = "No internet connection. Please try again."
        const val PARSE_ERROR_MESSAGE = "Failed to load profile data."
        const val OFFLINE_CACHE_SNACKBAR = "Offline mode: Using cached profiles."

        internal fun filterAndSort(
            profiles: List<HeadphoneProfile>,
            query: String,
            typeFilter: String
        ): List<HeadphoneProfile> {
            val trimmed = query.trim()
            val tokens = if (trimmed.isEmpty()) emptyList() else trimmed.split(Regex("\\s+"))
            return profiles
                .asSequence()
                .filter { profile ->
                    (typeFilter == "All" || profile.type.equals(typeFilter, ignoreCase = true)) &&
                        (tokens.isEmpty() || tokens.all { token ->
                            profile.name.contains(token, ignoreCase = true) ||
                                profile.type.contains(token, ignoreCase = true) ||
                                profile.sourceLabel.contains(token, ignoreCase = true)
                        })
                }
                .sortedBy { it.name.lowercase() }
                .toList()
        }
    }
}
