package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.os.Build
import com.example.model.AudioSourceMode
import com.example.model.EqBand
import com.example.model.ISO_20_FREQUENCIES_HZ
import com.example.model.ReverbRoomPreset
import com.example.model.TestTrackStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

data class HardwareCapabilities(
    val attached: Boolean,
    val hardwareBandCount: Int,
    val statusMessage: String,
    val supported: Set<String> = emptySet(),
    val errors: List<String> = emptyList()
)

internal class BiquadFilter {
    private data class Coefficients(val b0: Double = 1.0, val b1: Double = 0.0, val b2: Double = 0.0, val a1: Double = 0.0, val a2: Double = 0.0)
    @Volatile private var coefficients = Coefficients()

    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    fun resetHistory() {
        x1 = 0.0
        x2 = 0.0
        y1 = 0.0
        y2 = 0.0
    }

    fun setPeaking(freqHz: Double, sampleRate: Double, q: Double, gainDb: Double) {
        if (abs(gainDb) < 0.05) {
            coefficients = Coefficients()
            return
        }
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * (freqHz.coerceIn(20.0, sampleRate * 0.45) / sampleRate)
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / (2.0 * q)

        val a0 = 1.0 + alpha / a
        coefficients = Coefficients((1.0 + alpha * a) / a0, (-2.0 * cosW0) / a0,
            (1.0 - alpha * a) / a0, (-2.0 * cosW0) / a0, (1.0 - alpha / a) / a0)
    }

    fun process(x: Double): Double {
        val c = coefficients
        val y = c.b0 * x + c.b1 * x1 + c.b2 * x2 - c.a1 * y1 - c.a2 * y2
        x2 = x1
        x1 = x
        y2 = y1
        y1 = y
        return y
    }
}

/** RMS dari sampel PCM yang benar-benar ditulis ke AudioTrack, bukan float pra-skala. */
internal object MeterMath {
    fun rmsOfWritten(samples: ShortArray, channelOffset: Int, writtenShorts: Int = samples.size): Float {
        var sum = 0.0
        var count = 0
        for (i in channelOffset until minOf(writtenShorts, samples.size) step 2) {
            val v = samples[i] / 32767.0
            sum += v * v
            count++
        }
        return if (count == 0) 0f else sqrt(sum / count).toFloat().coerceIn(0f, 1f)
    }
}

internal enum class FocusAction { STOP, DUCK, RESTORE }

/** Kebijakan audio focus untuk audio uji: kehilangan fokus menghentikan, ducking menurunkan level. */
internal object FocusPolicy {
    fun actionFor(change: Int): FocusAction = when (change) {
        AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> FocusAction.STOP
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> FocusAction.DUCK
        else -> FocusAction.RESTORE
    }
}

internal class SessionEffectChain(
    val sessionId: Int,
    val packageLabel: String
) {
    var dynamicsProcessing: DynamicsProcessing? = null
    var equalizer: Equalizer? = null
    var bassBoost: BassBoost? = null
    var virtualizer: Virtualizer? = null
    var loudnessEnhancer: LoudnessEnhancer? = null
    var presetReverb: PresetReverb? = null
    var using20BandDynamics: Boolean = false
    var hwBandCount: Int = 0
    var lastError: String? = null
    var fallbackNote: String? = null
    private var dpFallbackReason: String? = null
    private val optionalErrors = LinkedHashMap<String, String>()
    /** Chain inti siap walau efek opsional (bass/surround) gagal; error opsional tidak menyandera status. */
    val coreReady: Boolean get() = attached && lastError == null
    val allErrors: List<String> get() = listOfNotNull(lastError) + optionalErrors.values.toList()
    fun noteOptionalError(key: String, message: String) { optionalErrors[key] = message }
    fun clearOptionalError(key: String) { optionalErrors.remove(key) }
    val attached: Boolean get() = runCatching {
        if (Build.VERSION.SDK_INT >= 28 && dynamicsProcessing != null) dynamicsProcessing!!.hasControl()
        else equalizer?.hasControl() == true
    }.getOrDefault(false)
    val supported: Set<String> get() = buildSet {
        if (attached) add("eq")
        if (using20BandDynamics && attached) addAll(listOf("stereo", "preamp", "limiter"))
        if (runCatching { bassBoost?.hasControl() == true && bassBoost?.strengthSupported == true }.getOrDefault(false)) add("bass")
        if (runCatching { virtualizer?.hasControl() == true && virtualizer?.strengthSupported == true }.getOrDefault(false)) add("surround")
    }

    fun attach(): Boolean {
        var attachedAny = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                val config = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2,
                    false, 0,
                    false, 0,
                    true, 20,
                    true
                ).build()
                val dp = DynamicsProcessing(0, sessionId, config)
                if (dp.hasControl()) {
                    dynamicsProcessing = dp
                    using20BandDynamics = true
                    hwBandCount = 20
                    attachedAny = true
                } else {
                    runCatching { dp.release() }
                    dpFallbackReason = "DynamicsProcessing tidak mengontrol sesi ini"
                }
            }.onFailure {
                lastError = it.message ?: it.javaClass.simpleName
                dpFallbackReason = "DynamicsProcessing gagal dibuat"
            }
        }

        if (!using20BandDynamics) {
            runCatching {
                val eq = Equalizer(0, sessionId)
                equalizer = eq
                hwBandCount = eq.numberOfBands.toInt().coerceAtLeast(0)
                attachedAny = attached
            }.onFailure { lastError = it.message ?: it.javaClass.simpleName }
            // Catatan fallback baru jujur setelah nasib Equalizer diketahui.
            fallbackNote = dpFallbackReason?.let {
                if (equalizer != null) "$it; memakai Equalizer hardware" else "$it; Equalizer hardware juga gagal"
            }
        }

        runCatching { bassBoost = BassBoost(0, sessionId) }
        runCatching { virtualizer = Virtualizer(0, sessionId) }
        // Aux reverb cannot be promised on other applications' tracks; no fake attachment.
        if (attachedAny) lastError = null
        return attachedAny
    }

    fun apply(
        enabled: Boolean,
        leftGainsDb: List<Float>,
        rightGainsDb: List<Float>,
        bassEnabled: Boolean,
        bassStrength: Float,
        virtEnabled: Boolean,
        virtStrength: Float,
        stereoWidth: Float,
        stereoBalance: Float,
        preampEnabled: Boolean,
        preampGainDb: Float,
        limiterEnabled: Boolean,
        reverbPreset: ReverbRoomPreset
    ) {
        val effPreamp = if (preampEnabled) preampGainDb.coerceIn(-12f, 12f) else 0f

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                dynamicsProcessing?.let { dp ->
                    dp.enabled = enabled
                    if (enabled) {
                        for (ch in 0..1) {
                            val amplitude = if (ch == 0) (1f - stereoBalance.coerceAtLeast(0f)) else (1f + stereoBalance.coerceAtMost(0f))
                            val balanceDb = (20 * kotlin.math.log10(amplitude.coerceAtLeast(0.00001f))).coerceAtLeast(-100f)
                            dp.setInputGainbyChannel(ch, effPreamp + balanceDb)
                            val gains = if (ch == 0) leftGainsDb else rightGainsDb
                            val eq = DynamicsProcessing.Eq(true, true, 20)
                            for (b in 0 until 20) {
                                val freq = ISO_20_FREQUENCIES_HZ.getOrElse(b) { 1000 }.toFloat()
                                val gain = gains.getOrElse(b) { 0f }.coerceIn(-15f, 15f)
                                eq.setBand(b, DynamicsProcessing.EqBand(true, freq, gain))
                            }
                            dp.setPostEqByChannelIndex(ch, eq)

                            val limiter = DynamicsProcessing.Limiter(
                                true,
                                limiterEnabled,
                                0,
                                1f,
                                60f,
                                4f,
                                -1.0f,
                                0f
                            )
                            dp.setLimiterByChannelIndex(ch, limiter)
                        }
                    }
                }
            }.onFailure { lastError = "DynamicsProcessing: ${it.message}" }
        }

        runCatching {
            equalizer?.let { eq ->
                eq.enabled = enabled
                val range = eq.bandLevelRange
                val minMb = range.getOrNull(0)?.toInt() ?: -1500
                val maxMb = range.getOrNull(1)?.toInt() ?: 1500
                val hwCount = eq.numberOfBands.toInt().coerceAtLeast(1)
                for (hwIdx in 0 until hwCount) {
                    val hz = eq.getCenterFreq(hwIdx.toShort()) / 1000f
                    val lo = ISO_20_FREQUENCIES_HZ.indexOfLast { it <= hz }.coerceIn(0, 19)
                    val hi = (lo + 1).coerceAtMost(19)
                    val frac = if (lo == hi) 0f else (kotlin.math.ln(hz / ISO_20_FREQUENCIES_HZ[lo]) /
                        kotlin.math.ln(ISO_20_FREQUENCIES_HZ[hi].toFloat() / ISO_20_FREQUENCIES_HZ[lo])).coerceIn(0f, 1f)
                    val avgLo = (leftGainsDb.getOrElse(lo) { 0f } + rightGainsDb.getOrElse(lo) { 0f }) * 0.5f
                    val avgHi = (leftGainsDb.getOrElse(hi) { 0f } + rightGainsDb.getOrElse(hi) { 0f }) * 0.5f
                    val db = avgLo * (1f - frac) + avgHi * frac
                    val mb = (db * 100f).roundToInt().coerceIn(minMb, maxMb).toShort()
                    eq.setBandLevel(hwIdx.toShort(), mb)
                }
            }
        }.onFailure { lastError = "Equalizer: ${it.message}" }

        runCatching {
            bassBoost?.let { bb ->
                bb.enabled = enabled && bassEnabled
                if (bb.strengthSupported) {
                    bb.setStrength((bassStrength.coerceIn(0f, 1f) * 1000f).roundToInt().toShort())
                }
            }
            clearOptionalError("bass")
        }.onFailure { noteOptionalError("bass", "Bass: ${it.message}") }

        runCatching {
            virtualizer?.let { virt ->
                virt.enabled = enabled && virtEnabled
                if (virt.strengthSupported) {
                    val effectiveVirt = virtStrength.coerceIn(0f, 1f)
                    virt.setStrength((effectiveVirt * 1000f).roundToInt().toShort())
                }
            }
            clearOptionalError("surround")
        }.onFailure { noteOptionalError("surround", "Surround: ${it.message}") }

        runCatching {
            loudnessEnhancer?.let { le ->
                val boostOnlyDb = if (!using20BandDynamics && preampEnabled && effPreamp > 0f) effPreamp else 0f
                le.enabled = enabled && boostOnlyDb > 0.1f
                le.setTargetGain((boostOnlyDb * 100f).roundToInt())
            }
        }

        runCatching {
            presetReverb?.let { rev ->
                rev.enabled = enabled && reverbPreset != ReverbRoomPreset.NONE
                rev.preset = reverbPreset.androidPreset
            }
        }
    }

    fun release() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { dynamicsProcessing?.release() }
        }
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        runCatching { loudnessEnhancer?.release() }
        runCatching { presetReverb?.release() }
        dynamicsProcessing = null
        equalizer = null
        bassBoost = null
        virtualizer = null
        loudnessEnhancer = null
        presetReverb = null
    }
}

object StudioAudioEngine {

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var globalSessionChain: SessionEffectChain? = null
    private val externalSessionChains = ConcurrentHashMap<Int, SessionEffectChain>()

    private var audioTrack: AudioTrack? = null
    private var synthJob: Job? = null
    private val playbackLock = Any()
    private var idleMeterJob: Job? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: Any? = null
    @Volatile private var focusDuck: Float = 1f
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (FocusPolicy.actionFor(change)) {
            FocusAction.STOP -> stopTestAudio()
            FocusAction.DUCK -> focusDuck = 0.25f
            FocusAction.RESTORE -> focusDuck = 1f
        }
    }

    private var currentSourceMode: AudioSourceMode = AudioSourceMode.GLOBAL_SYSTEM
    private var currentTrackStyle: TestTrackStyle = TestTrackStyle.GROOVE_BASS

    private val filtersLeft = Array(20) { BiquadFilter() }
    private val filtersRight = Array(20) { BiquadFilter() }

    private val paramLock = Any()
    private val rampMutex = Mutex()
    private val rampGeneration = AtomicLong(0L)
    @Volatile private var autoEqRampJob: Job? = null

    @Volatile private var eqEnabled: Boolean = true
    @Volatile private var leftGainsDb: List<Float> = List(20) { 0f }
    @Volatile private var rightGainsDb: List<Float> = List(20) { 0f }
    @Volatile private var bassEnabled: Boolean = true
    @Volatile private var bassStrength: Float = 0.30f
    @Volatile private var virtEnabled: Boolean = true
    @Volatile private var virtStrength: Float = 0.35f
    @Volatile private var stereoWidth: Float = 1.15f
    @Volatile private var stereoBalance: Float = 0f
    @Volatile private var preampEnabled: Boolean = true
    @Volatile private var preampGainDb: Float = 0f
    @Volatile private var limiterEnabled: Boolean = true
    @Volatile private var reverbPreset: ReverbRoomPreset = ReverbRoomPreset.NONE

    private val _activeAutoEqGains = MutableStateFlow<List<Float>?>(null)
    val activeAutoEqGains: StateFlow<List<Float>?> = _activeAutoEqGains.asStateFlow()

    private var outputMonitor: AudioOutputMonitor? = null
    private val _output = MutableStateFlow(com.example.model.OutputState())
    val output = _output.asStateFlow()
    private val _capabilities = MutableStateFlow(HardwareCapabilities(false, 0, "Efek belum diperiksa"))
    val capabilities = _capabilities.asStateFlow()
    private val _playing = MutableStateFlow(false)
    val playing = _playing.asStateFlow()
    private val _clipping = MutableStateFlow(false)
    val clipping = _clipping.asStateFlow()

    private val _vuLevels = MutableStateFlow(0f to 0f)
    val vuLevels: StateFlow<Pair<Float, Float>> = _vuLevels.asStateFlow()

    private val _stereoScopePoints = MutableStateFlow(FloatArray(96) { 0f })
    val stereoScopePoints: StateFlow<FloatArray> = _stereoScopePoints.asStateFlow()

    private val _detectedSessions = MutableStateFlow<List<String>>(emptyList())
    val detectedSessions: StateFlow<List<String>> = _detectedSessions.asStateFlow()

    private val _detectedOutputDevice = MutableStateFlow("Speaker Perangkat")
    val detectedOutputDevice: StateFlow<String> = _detectedOutputDevice.asStateFlow()

    init {
        startIdleVisualizer()
    }

    fun bindContext(context: Context) {
        if (audioManager == null) {
            audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        }
        if (outputMonitor == null) outputMonitor = audioManager?.let { AudioOutputMonitor(it) }
        if (idleMeterJob?.isActive != true) startIdleVisualizer()
        refreshOutputDeviceLabel()
    }

    fun refreshOutputDeviceLabel() {
        outputMonitor?.refresh(audioTrack)
        outputMonitor?.state?.value?.let { state ->
            _output.value = state
            _detectedOutputDevice.value = state.route?.label ?: "Output belum diketahui"
        }
    }

    fun reattachCurrentEffects() = attachEffects(currentSourceMode)

    fun attachEffects(sourceMode: AudioSourceMode): HardwareCapabilities {
        currentSourceMode = sourceMode
        if (sourceMode == AudioSourceMode.STUDIO_TEST) {
            ensureAudioTrackSession()
            applyAllParameters()
            return publishCapabilities()
        }
        return ensureGlobalMixAttached()
    }

    private fun ensureGlobalMixAttached(): HardwareCapabilities {
        if (globalSessionChain == null) {
            val chain = SessionEffectChain(sessionId = 0, packageLabel = "Global Mix")
            chain.attach()
            globalSessionChain = chain
        }
        applyAllParameters()

        return publishCapabilities()
    }

    private fun publishCapabilities(): HardwareCapabilities {
        val c = if (currentSourceMode == AudioSourceMode.STUDIO_TEST) {
            val ready = audioTrack?.state == AudioTrack.STATE_INITIALIZED
            HardwareCapabilities(ready, if (ready) 20 else 0,
                if (ready) "DSP audio uji • 20 band stereo" else "Audio uji belum tersedia",
                if (ready) setOf("eq", "stereo", "width", "preamp", "limiter") else emptySet())
        } else {
            val chains = if (externalSessionChains.isNotEmpty()) externalSessionChains.values.toList() else listOfNotNull(globalSessionChain)
            val ready = chains.filter { it.coreReady }
            val errors = chains.flatMap { it.allErrors }.distinct()
            val bands = ready.minOfOrNull { it.hwBandCount } ?: 0
            val note = chains.firstNotNullOfOrNull { it.fallbackNote }
            HardwareCapabilities(ready.isNotEmpty(), bands,
                (if (ready.isEmpty()) "Efek belum terpasang • coba pemutar lain atau audio uji"
                else if (externalSessionChains.isEmpty()) "Efek global terpasang • kompatibilitas pemutar belum terverifikasi"
                else "Efek terpasang pada ${ready.size} sesi • band hardware $bands") +
                    (note?.let { " • $it" } ?: ""),
                ready.map { it.supported }.reduceOrNull { a, b -> a intersect b } ?: emptySet(), errors)
        }
        _capabilities.value = c
        return c
    }

    fun registerExternalSession(sessionId: Int, packageName: String?) {
        if (sessionId <= 0) return
        if (audioTrack?.audioSessionId == sessionId) return
        val label = friendlyAppName(packageName)
        externalSessionChains[sessionId]?.release()
        val chain = SessionEffectChain(sessionId = sessionId, packageLabel = label)
        chain.attach()
        externalSessionChains[sessionId] = chain
        applyAllParameters()
        publishDetectedSessions()
    }

    fun unregisterExternalSession(sessionId: Int) {
        externalSessionChains.remove(sessionId)?.release()
        applyAllParameters()
        publishDetectedSessions()
    }

    private fun publishDetectedSessions() {
        _detectedSessions.value = externalSessionChains.values
            .map { it.packageLabel }
            .distinct()
    }

    private fun friendlyAppName(packageName: String?): String {
        val pkg = packageName?.lowercase().orEmpty()
        return when {
            pkg.contains("youtube") && pkg.contains("music") -> "YouTube Music"
            pkg.contains("youtube") -> "YouTube"
            pkg.contains("spotify") -> "Spotify"
            pkg.contains("tiktok") || pkg.contains("musically") -> "TikTok"
            pkg.contains("chrome") -> "Chrome Audio"
            pkg.contains("vlc") -> "VLC Player"
            pkg.isNotBlank() -> pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
            else -> "Pemutar Audio"
        }
    }

    private fun ensureAudioTrackSession(): Int {
        val existing = audioTrack
        if (existing != null && existing.state == AudioTrack.STATE_INITIALIZED) {
            return existing.audioSessionId
        }
        return runCatching {
            val sampleRate = 44100
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack = track
            track.audioSessionId
        }.getOrDefault(0)
    }

    fun updateParameters(
        eqEnabled: Boolean,
        bands: List<EqBand>,
        bassBoostEnabled: Boolean,
        bassBoostStrength: Float,
        virtualizerEnabled: Boolean,
        virtualizerStrength: Float,
        stereoWidth: Float,
        stereoBalance: Float,
        preampEnabled: Boolean,
        preampGainDb: Float,
        limiterEnabled: Boolean,
        reverbPreset: ReverbRoomPreset
    ) = synchronized(paramLock) {
        // Manual fader/parameter edits preempt any in-flight AutoEQ gain ramp cleanly.
        rampGeneration.incrementAndGet()
        autoEqRampJob?.cancel()
        autoEqRampJob = null

        this.eqEnabled = eqEnabled
        this.leftGainsDb = bands.map { it.levelDb }
        this.rightGainsDb = bands.map { it.rightLevelDb }
        this.bassEnabled = bassBoostEnabled
        this.bassStrength = bassBoostStrength
        this.virtEnabled = virtualizerEnabled
        this.virtStrength = virtualizerStrength
        this.stereoWidth = stereoWidth
        this.stereoBalance = stereoBalance
        this.preampEnabled = preampEnabled
        this.preampGainDb = preampGainDb
        this.limiterEnabled = limiterEnabled
        this.reverbPreset = reverbPreset

        updateBiquadCoefficients()
        applyAllParameters()
    }

    /**
     * Smoothly ramps the active 20-band stereo DSP gains from their current levels to
     * [targetGains] over [rampTimeMs] on [Dispatchers.Default] to prevent audio pops/clicks.
     *
     * Thread-safe with [rampMutex] + [rampGeneration] so manual fader drags preempt an
     * active ramp cleanly without races or stutter.
     */
    fun applyAutoEqGains(
        targetGains: FloatArray,
        rampTimeMs: Long = 100L
    ): Job {
        require(targetGains.size == 20) { "AutoEQ target gains must contain exactly 20 bands." }
        val safeTarget = List(20) { idx ->
            val v = targetGains[idx]
            if (v.isFinite()) v.coerceIn(-15f, 15f) else 0f
        }
        _activeAutoEqGains.value = safeTarget
        return startSmoothGainRamp(
            targetLeftDb = safeTarget,
            targetRightDb = safeTarget,
            rampTimeMs = rampTimeMs
        )
    }

    /**
     * Convenience wrapper accepting a 20-band [List] and delegating to [applyAutoEqGains]
     * with smooth pop-free ramping.
     */
    fun applyAutoEqCorrection(gainsDb: List<Float>, rampTimeMs: Long = 100L): Job {
        require(gainsDb.size == 20) { "AutoEQ curve must contain exactly 20 band gains." }
        return applyAutoEqGains(gainsDb.toFloatArray(), rampTimeMs = rampTimeMs)
    }

    /**
     * Thread-safely removes active AutoEQ correction and smoothly ramps back to the user's
     * manual L/R 20-band gains over [rampTimeMs].
     */
    fun clearAutoEqCorrection(
        restoreLeftDb: List<Float>? = null,
        restoreRightDb: List<Float>? = null,
        rampTimeMs: Long = 100L
    ): Job? {
        _activeAutoEqGains.value = null
        val targetLeft = restoreLeftDb?.takeIf { it.size == 20 }
            ?.map { if (it.isFinite()) it.coerceIn(-15f, 15f) else 0f }
        val targetRight = restoreRightDb?.takeIf { it.size == 20 }
            ?.map { if (it.isFinite()) it.coerceIn(-15f, 15f) else 0f }
            ?: targetLeft

        if (targetLeft == null || targetRight == null) {
            synchronized(paramLock) {
                rampGeneration.incrementAndGet()
                autoEqRampJob?.cancel()
                autoEqRampJob = null
                updateBiquadCoefficients()
                applyAllParameters()
            }
            return null
        }

        return startSmoothGainRamp(
            targetLeftDb = targetLeft,
            targetRightDb = targetRight,
            rampTimeMs = rampTimeMs
        )
    }

    /**
     * Detaches active AutoEQ profile metadata when the user manually moves a fader,
     * preserving the user's current band gains without snapping or resetting them.
     */
    fun detachAutoEqMetadata() {
        _activeAutoEqGains.value = null
    }

    internal fun currentBandGainsSnapshot(): Pair<List<Float>, List<Float>> = synchronized(paramLock) {
        leftGainsDb.toList() to rightGainsDb.toList()
    }

    internal fun isEqEnabledSnapshot(): Boolean = synchronized(paramLock) {
        eqEnabled
    }

    private fun startSmoothGainRamp(
        targetLeftDb: List<Float>,
        targetRightDb: List<Float>,
        rampTimeMs: Long
    ): Job {
        val token = rampGeneration.incrementAndGet()
        autoEqRampJob?.cancel()

        val job = engineScope.launch(Dispatchers.Default) {
            rampMutex.withLock {
                if (!isActive || rampGeneration.get() != token) return@withLock

                val (startLeft, startRight) = synchronized(paramLock) {
                    leftGainsDb.toList() to rightGainsDb.toList()
                }

                if (rampTimeMs <= 0L) {
                    commitRampStep(token, targetLeftDb, targetRightDb)
                    return@withLock
                }

                val stepIntervalMs = 10L
                val totalSteps = (rampTimeMs / stepIntervalMs).toInt().coerceIn(4, 30)
                val delayPerStepMs = (rampTimeMs / totalSteps).coerceAtLeast(1L)

                for (step in 1..totalSteps) {
                    if (!isActive || rampGeneration.get() != token) return@withLock
                    val rawProgress = step.toDouble() / totalSteps.toDouble()
                    // Raised-cosine S-curve avoids discontinuities at ramp start and finish
                    val eased = (0.5 * (1.0 - cos(PI * rawProgress))).toFloat()

                    val nextLeft = List(20) { i ->
                        val s = startLeft.getOrElse(i) { 0f }
                        val t = targetLeftDb[i]
                        (s + (t - s) * eased).coerceIn(-15f, 15f)
                    }
                    val nextRight = List(20) { i ->
                        val s = startRight.getOrElse(i) { 0f }
                        val t = targetRightDb[i]
                        (s + (t - s) * eased).coerceIn(-15f, 15f)
                    }

                    if (!commitRampStep(token, nextLeft, nextRight)) return@withLock
                    if (step < totalSteps) {
                        delay(delayPerStepMs)
                    }
                }
            }
        }
        autoEqRampJob = job
        return job
    }

    private fun commitRampStep(
        token: Long,
        nextLeft: List<Float>,
        nextRight: List<Float>
    ): Boolean = synchronized(paramLock) {
        if (rampGeneration.get() != token) return false
        this.leftGainsDb = nextLeft
        this.rightGainsDb = nextRight
        updateBiquadCoefficients()
        applyAllParameters()
        true
    }

    private fun updateBiquadCoefficients() {
        val sampleRate = 44100.0
        val q = 2.6
        for (i in 0 until 20) {
            val freq = ISO_20_FREQUENCIES_HZ.getOrElse(i) { 1000 }.toDouble()
            val gL = if (eqEnabled) leftGainsDb.getOrElse(i) { 0f }.toDouble() else 0.0
            val gR = if (eqEnabled) rightGainsDb.getOrElse(i) { 0f }.toDouble() else 0.0
            filtersLeft[i].setPeaking(freq, sampleRate, q, gL)
            filtersRight[i].setPeaking(freq, sampleRate, q, gR)
        }
    }

    private fun applyAllParameters() = synchronized(paramLock) {
        globalSessionChain?.let { applyToChain(it) }
        externalSessionChains.values.forEach { applyToChain(it) }
        publishCapabilities()
    }

    private fun applyToChain(chain: SessionEffectChain) {
        chain.apply(
            enabled = eqEnabled && currentSourceMode == AudioSourceMode.GLOBAL_SYSTEM &&
                (chain.sessionId != 0 || externalSessionChains.isEmpty()),
            leftGainsDb = leftGainsDb,
            rightGainsDb = rightGainsDb,
            bassEnabled = bassEnabled,
            bassStrength = bassStrength,
            virtEnabled = virtEnabled,
            virtStrength = virtStrength,
            stereoWidth = stereoWidth,
            stereoBalance = stereoBalance,
            preampEnabled = preampEnabled,
            preampGainDb = preampGainDb,
            limiterEnabled = limiterEnabled,
            reverbPreset = reverbPreset
        )
    }

    fun setTestTrackStyle(style: TestTrackStyle) {
        currentTrackStyle = style
    }

    fun startTestAudio(): Boolean {
        if (synthJob?.isActive == true) return true
        ensureAudioTrackSession()
        val track = audioTrack ?: return false
        if (!requestTestFocus()) return false
        if (runCatching { track.play() }.isFailure) {
            abandonTestFocus()
            return false
        }
        _playing.value = true

        val previousJob = synthJob
        synthJob = engineScope.launch(Dispatchers.Default) {
            previousJob?.join()
                // Reset history setelah job lama benar-benar selesai, bukan sebelum join:
                // job lama yang cancelled masih bisa memproses satu frame lagi.
                filtersLeft.forEach { it.resetHistory() }
                filtersRight.forEach { it.resetHistory() }
            val sampleRate = 44100
            val frames = 1024
            val buffer = ShortArray(frames * 2)
            var sampleIndex = 0L

            val bassNotes = doubleArrayOf(55.0, 55.0, 65.41, 73.42, 49.0, 49.0, 61.74, 65.41)
            val chordRoots = doubleArrayOf(220.0, 261.63, 293.66, 196.0)
            val scopePoints = FloatArray(96)

            while (isActive) {
                val style = currentTrackStyle
                val width = if (eqEnabled) stereoWidth.coerceIn(0f, 2f) else 1f
                val bal = if (eqEnabled) stereoBalance.coerceIn(-1f, 1f) else 0f
                val leftBal = if (bal > 0f) 1f - bal else 1f
                val rightBal = if (bal < 0f) 1f + bal else 1f
                val preampMult = if (eqEnabled && preampEnabled) {
                    10.0.pow(preampGainDb.coerceIn(-12f, 12f) / 20.0)
                } else 1.0

                var clipped = false
                val duck = focusDuck

                for (i in 0 until frames) {
                    val t = sampleIndex / sampleRate.toDouble()
                    val beatTime = (t * 2.0) % 1.0
                    val step = ((t * 2.0).toInt()) % 8
                    val bar = ((t * 0.5).toInt()) % 4

                    var rawL = 0.0
                    var rawR = 0.0

                    when (style) {
                        TestTrackStyle.GROOVE_BASS -> {
                            val kickFreq = 45.0 + 95.0 * exp(-beatTime * 22.0)
                            val kick = sin(2.0 * PI * kickFreq * beatTime) * exp(-beatTime * 6.5) * 0.34
                            val subFreq = bassNotes[step]
                            val sub = (sin(2.0 * PI * subFreq * t) * 0.24 +
                                sin(2.0 * PI * subFreq * 2.0 * t) * 0.08) *
                                (0.4 + 0.6 * exp(-beatTime * 3.0))

                            val root = chordRoots[bar]
                            val padL = (sin(2.0 * PI * root * t) +
                                0.7 * sin(2.0 * PI * (root * 1.1892 + 0.8) * t) +
                                0.6 * sin(2.0 * PI * (root * 1.4983 - 0.6) * t)) * 0.085
                            val padR = (sin(2.0 * PI * (root + 1.1) * t) +
                                0.7 * sin(2.0 * PI * (root * 1.1892 - 0.7) * t) +
                                0.6 * sin(2.0 * PI * (root * 1.4983 + 0.9) * t)) * 0.085

                            val hatPhase = (t * 4.0) % 1.0
                            val hatNoise = sin(t * 28000.0 + sin(t * 13000.0) * 3.0)
                            val hatPan = 0.5 + 0.45 * sin(t * 1.5)
                            val hat = hatNoise * exp(-hatPhase * 28.0) * 0.08

                            rawL = kick + sub + padL + hat * (1.0 - hatPan * 0.6)
                            rawR = kick + sub + padR + hat * (0.4 + hatPan * 0.6)
                        }

                        TestTrackStyle.SYNTH_PAD -> {
                            val root = chordRoots[bar] * 0.5
                            val swell = 0.65 + 0.35 * sin(2.0 * PI * 0.25 * t)
                            val f1 = sin(2.0 * PI * root * t) * 0.22
                            val f2L = sin(2.0 * PI * (root * 1.5 + 1.4) * t) * 0.16
                            val f2R = sin(2.0 * PI * (root * 1.5 - 1.4) * t) * 0.16
                            val f3L = sin(2.0 * PI * (root * 2.0 - 0.9) * t) * 0.13
                            val f3R = sin(2.0 * PI * (root * 2.0 + 0.9) * t) * 0.13
                            val shimmer = sin(2.0 * PI * (root * 6.0) * t) * 0.05 * swell
                            rawL = (f1 + f2L + f3L + shimmer) * swell
                            rawR = (f1 + f2R + f3R - shimmer * 0.6) * swell
                        }

                        TestTrackStyle.ACOUSTIC_SWEEP -> {
                            val arpNotes = doubleArrayOf(110.0, 164.81, 220.0, 277.18, 329.63, 440.0, 659.25, 880.0)
                            val noteIdx = ((t * 4.0).toInt()) % arpNotes.size
                            val noteEnv = exp(-((t * 4.0) % 1.0) * 5.0)
                            val freq = arpNotes[noteIdx]
                            val pan = if (noteIdx % 2 == 0) 0.25 else 0.75
                            val tone = (sin(2.0 * PI * freq * t) * 0.24 +
                                sin(2.0 * PI * freq * 2.0 * t) * 0.11 +
                                sin(2.0 * PI * freq * 3.0 * t) * 0.06) * noteEnv
                            val warmSub = sin(2.0 * PI * 55.0 * t) * 0.16
                            rawL = warmSub + tone * (1.2 - pan * 0.7)
                            rawR = warmSub + tone * (0.5 + pan * 0.7)
                        }
                    }

                    var eqL = rawL
                    var eqR = rawR
                    if (eqEnabled) {
                        for (b in 0 until 20) {
                            eqL = filtersLeft[b].process(eqL)
                            eqR = filtersRight[b].process(eqR)
                        }
                    }

                    val mid = (eqL + eqR) * 0.5
                    val side = (eqL - eqR) * 0.5 * width
                    val preL = (mid + side) * preampMult * leftBal * duck
                    val preR = (mid - side) * preampMult * rightBal * duck
                    clipped = clipped || abs(preL) > 1.0 || abs(preR) > 1.0
                    val shapedL = if (eqEnabled && limiterEnabled) tanh(preL) else preL.coerceIn(-1.0, 1.0)
                    val shapedR = if (eqEnabled && limiterEnabled) tanh(preR) else preR.coerceIn(-1.0, 1.0)

                    val sL = (shapedL * 29000.0).roundToInt().coerceIn(-32767, 32767).toShort()
                    val sR = (shapedR * 29000.0).roundToInt().coerceIn(-32767, 32767).toShort()
                    buffer[i * 2] = sL
                    buffer[i * 2 + 1] = sR

                    if (i % 21 == 0 && (i / 21) < 48) {
                        val ptIdx = (i / 21) * 2
                        scopePoints[ptIdx] = ((shapedL - shapedR) * 0.85).toFloat().coerceIn(-1f, 1f)
                        scopePoints[ptIdx + 1] = ((shapedL + shapedR) * 0.65).toFloat().coerceIn(-1f, 1f)
                    }

                    sampleIndex++
                }

                val wrote = synchronized(playbackLock) {
                    if (!isActive) false else {
                        val written = runCatching { track.write(buffer, 0, buffer.size) }.getOrDefault(-1)
                        if (written <= 0) {
                            _playing.value = false
                            _vuLevels.value = 0f to 0f
                            _stereoScopePoints.value = FloatArray(96)
                            _clipping.value = false
                            false
                        } else {
                            _clipping.value = clipped
                            _vuLevels.value = MeterMath.rmsOfWritten(buffer, 0, written) to MeterMath.rmsOfWritten(buffer, 1, written)
                            _stereoScopePoints.value = scopePoints.copyOf()
                            true
                        }
                    }
                }
                if (!wrote) break

                // No FFT capture is implemented. Never present synthetic bars as measured audio.
            }
        }
        return true
    }

    fun stopTestAudio() {
        synthJob?.cancel()
        abandonTestFocus()
        // Pause/flush DI LUAR lock: synth memegang playbackLock selama track.write
        // yang bisa memblokir (perangkat putus). Pause dulu agar write lepas,
        // baru ambil lock untuk reset state —hindari inversi lock → ANR.
        runCatching { audioTrack?.pause(); audioTrack?.flush() }
        synchronized(playbackLock) {
            _playing.value = false
            _vuLevels.value = 0f to 0f
            _stereoScopePoints.value = FloatArray(96)
            _clipping.value = false
        }
    }

    private fun requestTestFocus(): Boolean {
        val am = audioManager ?: return true
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        return if (Build.VERSION.SDK_INT >= 26) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(focusListener)
                .build()
            focusRequest = request
            am.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) ==
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonTestFocus() {
        focusDuck = 1f
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            (focusRequest as? AudioFocusRequest)?.let { am.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(focusListener)
        }
        focusRequest = null
    }

    private fun startIdleVisualizer() {
        idleMeterJob?.cancel()
        idleMeterJob = engineScope.launch {
            while (isActive) {
                refreshOutputDeviceLabel()
                publishCapabilities()
                if (!_playing.value) {
                    if (_vuLevels.value != (0f to 0f)) _vuLevels.value = 0f to 0f
                }
                delay(1500L)
            }
        }
    }

    fun releaseGlobalAndExternal() {
        stopTestAudio()
        globalSessionChain?.release()
        globalSessionChain = null
        externalSessionChains.values.forEach { it.release() }
        externalSessionChains.clear()
        _detectedSessions.value = emptyList()
        runCatching { audioTrack?.release() }
        audioTrack = null
        outputMonitor?.close()
        outputMonitor = null
        idleMeterJob?.cancel()
        publishCapabilities()
    }
}
