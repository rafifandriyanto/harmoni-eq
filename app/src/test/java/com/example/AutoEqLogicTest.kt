package com.example

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.example.audio.StudioAudioEngine
import com.example.data.PresetRepository
import com.example.data.autoeq.AutoEqCsvParser
import com.example.data.autoeq.AutoEqInterpolator
import com.example.data.autoeq.AutoEqRemoteDataSource
import com.example.data.autoeq.AutoEqRepository
import com.example.model.HeadphoneProfile
import com.example.model.ISO_20_FREQUENCIES_HZ
import com.example.model.ReverbRoomPreset
import com.example.model.StereoChannelFocus
import com.example.model.default20Bands
import com.example.ui.AutoEqUiState
import com.example.ui.AutoEqViewModel
import com.example.ui.EqualizerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.math.abs

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AutoEqLogicTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val testScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun newDataStore(prefix: String) = PreferenceDataStoreFactory.create(scope = testScope) {
        File(app.cacheDir, "$prefix-${UUID.randomUUID()}.preferences_pb")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        testScope.coroutineContext[Job]?.cancel()
    }

    // --- 1. CSV & Index Parser Tests ---

    @Test
    fun `parseCsv extracts equalization column from official AutoEQ multi-column CSV and sorts ascending`() {
        val officialCsv = """
            frequency,raw,error,smoothed,error_smoothed,equalization,parametric_eq,fixed_band_eq,target
            1000.0,-2.0,-1.5,-1.8,-1.4,-1.25,-1.2,-1.1,0.0
            20.0,-6.0,-5.0,-5.8,-4.9,4.50,4.4,4.2,5.0
            100.0,1.0,0.8,0.9,0.7,-0.75,-0.7,-0.6,2.0
            16000.0,-4.0,-3.5,-3.9,-3.4,2.80,2.7,2.5,-2.0
        """.trimIndent()

        val points = AutoEqCsvParser.parseCsv(officialCsv)

        assertEquals(4, points.size)
        assertEquals(listOf(20f, 100f, 1000f, 16000f), points.map { it.first })
        assertEquals(listOf(4.5f, -0.75f, -1.25f, 2.8f), points.map { it.second })
    }

    @Test
    fun `parseCsv supports BOM comments semicolon delimiter and skips malformed rows`() {
        val messyCsv = "\uFEFF# AutoEQ export\n\n" +
            "frequency;gain\n" +
            "32;3.2\n" +
            "invalid;1.0\n" +
            "-10;5.0\n" +
            "0;2.0\n" +
            "64;NaN\n" +
            "250;-1.5\n" +
            "1000;0.4\n"

        val points = AutoEqCsvParser.parseCsv(messyCsv)

        assertEquals(3, points.size)
        assertEquals(32f to 3.2f, points[0])
        assertEquals(250f to -1.5f, points[1])
        assertEquals(1000f to 0.4f, points[2])
    }

    @Test
    fun `parseIndexMarkdown normalizes form factors deduplicates by source priority and sorts A to Z`() {
        val indexMd = """
            # Index
            - [Sony WH-1000XM5](./Rtings/over-ear/Sony%20WH-1000XM5)
            - [Sony WH-1000XM5](./oratory1990/over-ear/Sony%20WH-1000XM5)
            - [Apple AirPods Pro 2](./crinacle/in-ear/Apple%20AirPods%20Pro%202)
            - [Koss Porta Pro](./oratory1990/on-ear/Koss%20Porta%20Pro)
            - [Apple EarPods](./Rtings/earbud/Apple%20EarPods)
        """.trimIndent()

        val profiles = AutoEqCsvParser.parseIndexMarkdown(indexMd, AutoEqRemoteDataSource.CDN_RESULTS_BASE)

        assertEquals(4, profiles.size)
        assertEquals(
            listOf("Apple AirPods Pro 2", "Apple EarPods", "Koss Porta Pro", "Sony WH-1000XM5"),
            profiles.map { it.name }
        )
        assertEquals("In-ear", profiles[0].type)
        assertEquals("Earbud", profiles[1].type)
        assertEquals("On-ear", profiles[2].type)
        assertEquals("Over-ear", profiles[3].type)
        // Deduplication prefers oratory1990 over Rtings for Sony WH-1000XM5
        assertTrue(profiles[3].csvUrl.contains("/oratory1990/"))
        assertTrue(profiles[3].csvUrl.endsWith("/Sony%20WH-1000XM5/Sony%20WH-1000XM5.csv"))
    }

    @Test
    fun `parseGitHubTreeJson parses model CSV paths and ignores non-model files`() {
        val treeJson = """
            {
              "tree": [
                {"path": "results/oratory1990/over-ear/Sennheiser HD 800/Sennheiser HD 800.csv", "type": "blob"},
                {"path": "results/oratory1990/over-ear/Sennheiser HD 800/README.md", "type": "blob"},
                {"path": "results/oratory1990/over-ear/Sennheiser HD 800/other.csv", "type": "blob"},
                {"path": "results/crinacle/in-ear/Moondrop Blessing 2/Moondrop Blessing 2.csv", "type": "blob"}
              ]
            }
        """.trimIndent()

        val profiles = AutoEqCsvParser.parseGitHubTreeJson(treeJson, AutoEqRemoteDataSource.CDN_RESULTS_BASE)

        assertEquals(2, profiles.size)
        assertEquals("Moondrop Blessing 2", profiles[0].name)
        assertEquals("In-ear", profiles[0].type)
        assertEquals("Sennheiser HD 800", profiles[1].name)
        assertEquals("Over-ear", profiles[1].type)
    }

    // --- 2. Frequency Interpolation Tests ---

    @Test
    fun `interpolateCubicSpline reproduces exact values at all 20 ISO frequencies`() {
        val expected = List(ISO_20_FREQUENCIES_HZ.size) { idx ->
            ((idx % 7) - 3) * 1.5f
        }
        val measurement = ISO_20_FREQUENCIES_HZ.mapIndexed { idx, hz -> hz.toFloat() to expected[idx] }

        val result = AutoEqInterpolator.interpolateCubicSpline(measurement)

        assertEquals(20, result.size)
        expected.forEachIndexed { idx, want ->
            assertEquals("Mismatch at band $idx (${ISO_20_FREQUENCIES_HZ[idx]} Hz)", want, result[idx], 0.05f)
        }
    }

    @Test
    fun `interpolateLinear computes logarithmic midpoint accurately`() {
        // Geometric mean of 100 Hz and 400 Hz is 200 Hz -> gain should be exact midpoint (-2 + 6)/2 = 2.0 dB
        val points = listOf(100f to -2.0f, 400f to 6.0f)
        val result = AutoEqInterpolator.interpolateLinear(points, targetFrequenciesHz = listOf(50, 100, 200, 400, 800))

        assertEquals(listOf(-2.0f, -2.0f, 2.0f, 6.0f, 6.0f), result)
    }

    @Test
    fun `interpolateCubicSpline stays monotone without overshoot and clamps extreme dB values`() {
        val stepCurve = listOf(
            20f to -20f,
            100f to 0f,
            500f to 0f,
            1000f to 6f,
            4000f to 6f,
            20000f to 22f
        )

        val result = AutoEqInterpolator.interpolateCubicSpline(stepCurve)

        assertEquals(20, result.size)
        assertTrue("All values must clamp inside [-15, 15]", result.all { it in -15f..15f })
        // Between 100 Hz and 500 Hz, monotone PCHIP must not dip below 0 or overshoot above 0
        val idx140 = ISO_20_FREQUENCIES_HZ.indexOf(140)
        val idx200 = ISO_20_FREQUENCIES_HZ.indexOf(200)
        val idx280 = ISO_20_FREQUENCIES_HZ.indexOf(280)
        assertEquals(0f, result[idx140], 0.01f)
        assertEquals(0f, result[idx200], 0.01f)
        assertEquals(0f, result[idx280], 0.01f)
        // Between 1000 Hz and 4000 Hz, must stay flat at 6.0 dB without ringing
        val idx1600 = ISO_20_FREQUENCIES_HZ.indexOf(1600)
        val idx2200 = ISO_20_FREQUENCIES_HZ.indexOf(2200)
        assertEquals(6.0f, result[idx1600], 0.01f)
        assertEquals(6.0f, result[idx2200], 0.01f)
        // Extreme ends clamped to +-15 dB
        assertEquals(-15f, result.first(), 0.01f)
        assertEquals(15f, result.last(), 0.01f)
    }

    @Test
    fun `interpolator handles empty single-point and non-finite inputs safely`() {
        assertEquals(List(20) { 0f }, AutoEqInterpolator.interpolateCubicSpline(emptyList()))
        assertEquals(List(20) { 3.5f }, AutoEqInterpolator.interpolateCubicSpline(listOf(1000f to 3.5f)))
        val withBad = listOf(Float.NaN to 4f, 100f to Float.POSITIVE_INFINITY, 500f to -2.4f)
        assertEquals(List(20) { -2.4f }, AutoEqInterpolator.interpolateCubicSpline(withBad))
    }

    // --- 3. URL Validation & Offline Caching Repository Tests ---

    @Test
    fun `isTrustedAutoEqUrl allows only official HTTPS hosts`() {
        assertTrue(AutoEqRemoteDataSource.isTrustedAutoEqUrl(AutoEqRemoteDataSource.CDN_INDEX_URL))
        assertTrue(AutoEqRemoteDataSource.isTrustedAutoEqUrl("https://raw.githubusercontent.com/jaakkopasanen/AutoEq/master/results/INDEX.md"))
        assertTrue(AutoEqRemoteDataSource.isTrustedAutoEqUrl(AutoEqRemoteDataSource.GITHUB_TREE_URL))
        assertFalse(AutoEqRemoteDataSource.isTrustedAutoEqUrl("http://cdn.jsdelivr.net/gh/jaakkopasanen/AutoEq@master/results/INDEX.md"))
        assertFalse(AutoEqRemoteDataSource.isTrustedAutoEqUrl("https://evil.example.com/profile.csv"))
        assertFalse(AutoEqRemoteDataSource.isTrustedAutoEqUrl("not a url"))
    }

    @Test
    fun `AutoEqRepository caches index and interpolated curves offline in DataStore`() = runBlocking {
        val networkCalls = AtomicInteger(0)
        var failNetwork = false
        val sampleIndex = "- [Sony WH-1000XM5](./oratory1990/over-ear/Sony%20WH-1000XM5)\n" +
            "- [Apple AirPods Pro 2](./crinacle/in-ear/Apple%20AirPods%20Pro%202)"
        val sampleCsv = """
            frequency,equalization
            20.0,-3.0
            100.0,-2.0
            1000.0,1.5
            4000.0,3.0
            16000.0,0.5
        """.trimIndent()

        val remote = AutoEqRemoteDataSource { url ->
            networkCalls.incrementAndGet()
            if (failNetwork) throw IllegalStateException("Offline")
            if (url.endsWith("INDEX.md")) sampleIndex else sampleCsv
        }
        val repo = AutoEqRepository(app, store = newDataStore("autoeq-cache"), remote = remote)

        // First fetch hits remote and caches in DataStore
        val firstIndex = repo.getHeadphoneIndex().getOrThrow()
        assertEquals(2, firstIndex.size)
        assertEquals(1, networkCalls.get())

        // Offline second fetch serves directly from DataStore cache with 0 network calls
        failNetwork = true
        val offlineIndex = repo.getHeadphoneIndex().getOrThrow()
        assertEquals(firstIndex, offlineIndex)
        assertEquals(1, networkCalls.get())

        // Fetch curve online -> parses, interpolates, and caches raw CSV + 20-band curve
        failNetwork = false
        val sony = firstIndex.first { it.name.contains("Sony") }
        val firstCurve = repo.loadOrFetchCurve(sony).getOrThrow()
        assertEquals(20, firstCurve.interpolatedGainsDb.size)
        assertTrue(firstCurve.rawCsv.contains("frequency,equalization"))
        assertEquals(2, networkCalls.get())

        // Subsequent curve load works completely offline with 0 extra network calls
        failNetwork = true
        val cachedCurve = repo.loadOrFetchCurve(sony).getOrThrow()
        assertEquals(firstCurve.interpolatedGainsDb, cachedCurve.interpolatedGainsDb)
        assertEquals(2, networkCalls.get())
    }

    // --- 4. Thread-Safe Audio Engine & ViewModel Restoration Tests ---

    @Test
    fun `StudioAudioEngine applies AutoEQ and manual updates safely across concurrent threads`() {
        StudioAudioEngine.bindContext(app)
        val latch = CountDownLatch(4)
        val failure = AtomicReference<Throwable?>(null)
        val curveA = List(20) { i -> (i - 10) * 0.4f }
        val curveB = List(20) { i -> (10 - i) * 0.3f }

        repeat(4) { workerId ->
            thread(name = "autoeq-worker-$workerId") {
                try {
                    repeat(25) { step ->
                        when ((workerId + step) % 3) {
                            0 -> StudioAudioEngine.applyAutoEqCorrection(curveA)
                            1 -> StudioAudioEngine.applyAutoEqCorrection(curveB)
                            else -> StudioAudioEngine.updateParameters(
                                eqEnabled = true,
                                bands = default20Bands().mapIndexed { i, b -> b.copy(levelDb = (i % 5) - 2f) },
                                bassBoostEnabled = false,
                                bassBoostStrength = 0f,
                                virtualizerEnabled = false,
                                virtualizerStrength = 0f,
                                stereoWidth = 1f,
                                stereoBalance = 0f,
                                preampEnabled = false,
                                preampGainDb = 0f,
                                limiterEnabled = true,
                                reverbPreset = ReverbRoomPreset.NONE
                            )
                        }
                    }
                } catch (t: Throwable) {
                    failure.compareAndSet(null, t)
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue("Concurrent workers timed out", latch.await(10, TimeUnit.SECONDS))
        assertNull("Concurrent AutoEQ/audio updates threw: ${failure.get()}", failure.get())
        StudioAudioEngine.clearAutoEqCorrection(List(20) { 0f }, List(20) { 0f })
        assertNull(StudioAudioEngine.activeAutoEqGains.value)
    }

    @Test
    fun `EqualizerViewModel applies AutoEQ persists across restart and restores manual gains on disable`() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val presetStore = newDataStore("eq-preset")
        val autoEqStore = newDataStore("eq-autoeq")
        val presetRepo = PresetRepository(app, presetStore)
        val autoEqRepo = AutoEqRepository(app, store = autoEqStore)

        val vm1 = EqualizerViewModel(app, presetRepo, autoEqRepo)
        withTimeout(10_000) { vm1.uiState.first { it.libraryRestored } }

        // User creates distinct manual stereo EQ settings before enabling AutoEQ
        vm1.selectChannelFocus(StereoChannelFocus.LEFT)
        vm1.updateBandLevel(0, 4.5f)
        vm1.selectChannelFocus(StereoChannelFocus.RIGHT)
        vm1.updateBandLevel(0, -2.5f)
        val expectedManualLeft = vm1.uiState.value.bands.map { it.levelDb }
        val expectedManualRight = vm1.uiState.value.bands.map { it.rightLevelDb }

        // Apply AutoEQ profile
        val profile = HeadphoneProfile(
            name = "Sennheiser HD 800",
            type = "Over-ear",
            csvUrl = "${AutoEqRemoteDataSource.CDN_RESULTS_BASE}/oratory1990/over-ear/Sennheiser%20HD%20800/Sennheiser%20HD%20800.csv"
        )
        val curve = List(20) { idx -> ((idx - 10) * 0.5f) }
        vm1.applyAutoEqProfile(profile, curve)

        assertEquals(profile, vm1.uiState.value.activeAutoEqProfile)
        assertEquals(curve, vm1.uiState.value.bands.map { it.levelDb })

        // Wait for AutoEQ DataStore persistence
        withTimeout(10_000) {
            autoEqRepo.stateFlow.first { it.activeProfile?.id == profile.id }
        }

        // Simulate app relaunch with a brand new ViewModel instance backed by same DataStores
        val vm2 = EqualizerViewModel(app, presetRepo, autoEqRepo)
        withTimeout(10_000) { vm2.uiState.first { it.libraryRestored } }

        assertEquals(profile.name, vm2.uiState.value.activeAutoEqProfile?.name)
        assertEquals(curve, vm2.uiState.value.bands.map { it.levelDb })

        // Disable AutoEQ -> restores exact original manual stereo gains
        vm2.disableAutoEq()
        withTimeout(10_000) { vm2.uiState.first { it.activeAutoEqProfile == null } }

        assertNull(vm2.uiState.value.activeAutoEqProfile)
        expectedManualLeft.forEachIndexed { i, v ->
            assertTrue(abs(v - vm2.uiState.value.bands[i].levelDb) < 0.05f)
        }
        expectedManualRight.forEachIndexed { i, v ->
            assertTrue(abs(v - vm2.uiState.value.bands[i].rightLevelDb) < 0.05f)
        }
    }

    // --- 5. Search Filtering & Alphabetical Sorting Tests ---

    @Test
    fun `AutoEqViewModel filterAndSort sorts alphabetically and filters by query and form factor`() {
        val sample = listOf(
            HeadphoneProfile("Sony WH-1000XM5", "Over-ear", "https://cdn.jsdelivr.net/gh/jaakkopasanen/AutoEq@master/results/oratory1990/over-ear/Sony%20WH-1000XM5/Sony%20WH-1000XM5.csv"),
            HeadphoneProfile("Apple AirPods Pro 2", "In-ear", "https://cdn.jsdelivr.net/gh/jaakkopasanen/AutoEq@master/results/crinacle/in-ear/Apple%20AirPods%20Pro%202/Apple%20AirPods%20Pro%202.csv"),
            HeadphoneProfile("Sennheiser HD 650", "Over-ear", "https://cdn.jsdelivr.net/gh/jaakkopasanen/AutoEq@master/results/oratory1990/over-ear/Sennheiser%20HD%20650/Sennheiser%20HD%20650.csv"),
            HeadphoneProfile("Moondrop Chu", "In-ear", "https://cdn.jsdelivr.net/gh/jaakkopasanen/AutoEq@master/results/crinacle/in-ear/Moondrop%20Chu/Moondrop%20Chu.csv")
        )

        val allSorted = AutoEqViewModel.filterAndSort(sample, query = "", typeFilter = "All")
        assertEquals(
            listOf("Apple AirPods Pro 2", "Moondrop Chu", "Sennheiser HD 650", "Sony WH-1000XM5"),
            allSorted.map { it.name }
        )

        val inEarOnly = AutoEqViewModel.filterAndSort(sample, query = "", typeFilter = "In-ear")
        assertEquals(listOf("Apple AirPods Pro 2", "Moondrop Chu"), inEarOnly.map { it.name })

        val multiToken = AutoEqViewModel.filterAndSort(sample, query = "sony xm5", typeFilter = "Over-ear")
        assertEquals(listOf("Sony WH-1000XM5"), multiToken.map { it.name })
    }

    // --- 6. Phase 3: Smooth Gain Ramping & Sealed AutoEqUiState Tests ---

    @Test
    fun `StudioAudioEngine applyAutoEqGains ramps smoothly to target and yields to manual drag`() = runBlocking {
        StudioAudioEngine.bindContext(app)
        StudioAudioEngine.clearAutoEqCorrection(List(20) { 0f }, List(20) { 0f }, rampTimeMs = 0L)?.join()

        val target = FloatArray(20) { idx -> (idx - 10) * 0.6f }
        val rampJob = StudioAudioEngine.applyAutoEqGains(targetGains = target, rampTimeMs = 60L)
        rampJob.join()

        val (leftAfterRamp, rightAfterRamp) = StudioAudioEngine.currentBandGainsSnapshot()
        target.forEachIndexed { idx, want ->
            assertEquals(want, leftAfterRamp[idx], 0.05f)
            assertEquals(want, rightAfterRamp[idx], 0.05f)
        }

        // Start a longer ramp, then preempt it with manual updateParameters (simulating user drag)
        val slowTarget = FloatArray(20) { -12f }
        val preemptedJob = StudioAudioEngine.applyAutoEqGains(targetGains = slowTarget, rampTimeMs = 400L)
        val manualBands = default20Bands().map { it.copy(levelDb = 7.5f, rightLevelDb = 7.5f) }
        StudioAudioEngine.updateParameters(
            eqEnabled = true,
            bands = manualBands,
            bassBoostEnabled = false,
            bassBoostStrength = 0f,
            virtualizerEnabled = false,
            virtualizerStrength = 0f,
            stereoWidth = 1f,
            stereoBalance = 0f,
            preampEnabled = false,
            preampGainDb = 0f,
            limiterEnabled = true,
            reverbPreset = ReverbRoomPreset.NONE
        )
        preemptedJob.join()

        val (finalLeft, _) = StudioAudioEngine.currentBandGainsSnapshot()
        assertEquals(7.5f, finalLeft[0], 0.05f)
        assertEquals(7.5f, finalLeft[19], 0.05f)
    }

    @Test
    fun `AutoEqViewModel sealed uiState loads profiles filters search and ramps audio engine on select`() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val sampleIndex = "- [Sony WH-1000XM5](./oratory1990/over-ear/Sony%20WH-1000XM5)\n" +
            "- [Apple AirPods Pro 2](./crinacle/in-ear/Apple%20AirPods%20Pro%202)"
        val sampleCsv = """
            frequency,equalization
            20.0,-4.0
            200.0,-1.0
            1000.0,1.5
            4000.0,3.0
            18000.0,0.0
        """.trimIndent()

        val remote = AutoEqRemoteDataSource { url ->
            if (url.endsWith("INDEX.md")) sampleIndex else sampleCsv
        }
        val repo = AutoEqRepository(app, store = newDataStore("autoeq-vm-phase3"), remote = remote)

        val engineCalls = AtomicReference<Pair<FloatArray, Long>?>(null)
        val vm = AutoEqViewModel(app, repo) { gains, rampMs ->
            engineCalls.set(gains to rampMs)
        }

        assertEquals(AutoEqUiState.Idle, vm.uiState.value)

        vm.loadProfiles()
        val loaded = withTimeout(10_000) {
            vm.uiState.first { it is AutoEqUiState.Success } as AutoEqUiState.Success
        }
        assertEquals(2, loaded.profiles.size)
        assertNull(loaded.selectedProfile)

        // Debounced search binding filters in-memory (case-insensitive)
        vm.updateSearchQuery("sony")
        val filtered = withTimeout(5_000) {
            vm.uiState.first {
                it is AutoEqUiState.Success && it.profiles.size == 1 && it.profiles.first().name == "Sony WH-1000XM5"
            } as AutoEqUiState.Success
        }
        assertEquals(listOf("Sony WH-1000XM5"), filtered.profiles.map { it.name })

        // Select profile -> fetches 20-band FloatArray, updates selectedProfile, ramps engine
        val sony = filtered.profiles.first()
        var callbackApplied = false
        vm.selectProfile(sony) { applied, gains ->
            callbackApplied = applied == sony && gains.size == 20
        }

        val selectedState = withTimeout(10_000) {
            vm.uiState.first { it is AutoEqUiState.Success && it.selectedProfile?.id == sony.id } as AutoEqUiState.Success
        }
        assertEquals(sony, selectedState.selectedProfile)
        assertTrue(callbackApplied)
        val enginePayload = engineCalls.get()
        assertNotNull(enginePayload)
        assertEquals(20, enginePayload!!.first.size)
        assertEquals(100L, enginePayload.second)

        // Clear/Reset clears selectedProfile
        vm.clearSelection()
        assertNull((vm.uiState.value as AutoEqUiState.Success).selectedProfile)
    }

    // --- 7. Phase 4: Search Debouncing, Error Handling, Offline Fallback & Master Switch State Machine ---

    @Test
    fun `AutoEqViewModel debounces rapid search keystrokes by 300ms in memory without hitting network`() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val networkCalls = AtomicInteger(0)
        val sampleIndex = """
            - [Sony WH-1000XM5](./oratory1990/over-ear/Sony%20WH-1000XM5)
            - [Sennheiser HD 800](./oratory1990/over-ear/Sennheiser%20HD%20800)
            - [Apple AirPods Pro 2](./crinacle/in-ear/Apple%20AirPods%20Pro%202)
        """.trimIndent()
        val remote = AutoEqRemoteDataSource {
            networkCalls.incrementAndGet()
            sampleIndex
        }
        val repo = AutoEqRepository(app, store = newDataStore("autoeq-debounce"), remote = remote)
        val vm = AutoEqViewModel(app, repo, searchDebounceMs = 300L)

        vm.loadProfiles()
        val initial = withTimeout(5_000) {
            vm.uiState.first { it is AutoEqUiState.Success && it.profiles.size == 3 } as AutoEqUiState.Success
        }
        assertEquals(3, initial.profiles.size)
        val baselineFilters = vm.filterExecutionCount.get()

        // Rapid keystrokes within 120ms must NOT trigger intermediate filtering or network hits
        vm.updateSearchQuery("s")
        delay(50)
        vm.updateSearchQuery("so")
        delay(50)
        vm.updateSearchQuery("SONY")

        // Immediately after rapid typing (< 300ms), list has not yet re-filtered for intermediate queries
        assertEquals(3, (vm.uiState.value as AutoEqUiState.Success).profiles.size)
        assertEquals(1, networkCalls.get())

        // After 300ms debounce window elapses, case-insensitive in-memory filter runs once
        val debounced = withTimeout(5_000) {
            vm.uiState.first {
                it is AutoEqUiState.Success && it.profiles.size == 1 && it.profiles.first().name == "Sony WH-1000XM5"
            } as AutoEqUiState.Success
        }
        assertEquals(listOf("Sony WH-1000XM5"), debounced.profiles.map { it.name })
        assertEquals("Search must stay in-memory with zero network hits", 1, networkCalls.get())
        assertTrue(
            "Debounce should coalesce rapid keystrokes into a single filter pass",
            vm.filterExecutionCount.get() - baselineFilters <= 2
        )
    }

    @Test
    fun `AutoEqViewModel handles network failure, corrupted CSV parse error, and offline cache fallback`() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())

        // 1. Network failure with empty cache -> exact network error message
        val failingRemote = AutoEqRemoteDataSource { throw java.io.IOException("No network") }
        val emptyRepo = AutoEqRepository(app, store = newDataStore("autoeq-net-err"), remote = failingRemote)
        val errVm = AutoEqViewModel(app, emptyRepo, searchDebounceMs = 0L)
        errVm.loadProfiles()
        val netErr = withTimeout(5_000) {
            errVm.uiState.first { it is AutoEqUiState.Error } as AutoEqUiState.Error
        }
        assertEquals(AutoEqViewModel.NETWORK_ERROR_MESSAGE, netErr.message)

        // 2. Corrupted CSV -> exact parse error message
        val sampleIndex = "- [Sennheiser HD 800](./oratory1990/over-ear/Sennheiser%20HD%20800)"
        val corruptRemote = AutoEqRemoteDataSource { url ->
            if (url.endsWith("INDEX.md")) sampleIndex else "not,valid,csv\nbad,data,only"
        }
        val corruptRepo = AutoEqRepository(app, store = newDataStore("autoeq-parse-err"), remote = corruptRemote)
        val parseVm = AutoEqViewModel(app, corruptRepo, searchDebounceMs = 0L)
        parseVm.loadProfiles()
        val loaded = withTimeout(5_000) {
            parseVm.uiState.first { it is AutoEqUiState.Success } as AutoEqUiState.Success
        }
        parseVm.selectProfile(loaded.profiles.first())
        val parseErr = withTimeout(5_000) {
            parseVm.uiState.first { it is AutoEqUiState.Error } as AutoEqUiState.Error
        }
        assertEquals(AutoEqViewModel.PARSE_ERROR_MESSAGE, parseErr.message)

        // 3. Offline cache fallback -> loads cached profiles & emits offline-mode snackbar
        var offline = false
        val flakyRemote = AutoEqRemoteDataSource {
            if (offline) throw java.io.IOException("Offline")
            sampleIndex
        }
        val cacheStore = newDataStore("autoeq-offline-fallback")
        val onlineRepo = AutoEqRepository(app, store = cacheStore, remote = flakyRemote)
        assertEquals(1, onlineRepo.getHeadphoneIndex(forceRefresh = true).getOrThrow().size)

        offline = true
        val offlineVm = AutoEqViewModel(app, onlineRepo, searchDebounceMs = 0L)
        offlineVm.loadProfiles(forceRefresh = true)
        val cachedState = withTimeout(5_000) {
            offlineVm.uiState.first { it is AutoEqUiState.Success && it.profiles.size == 1 } as AutoEqUiState.Success
        }
        assertEquals("Sennheiser HD 800", cachedState.profiles.first().name)
        val offlineSnackbar = withTimeout(5_000) {
            offlineVm.snackbarMessage.first { it != null }
        }
        assertEquals(AutoEqViewModel.OFFLINE_CACHE_SNACKBAR, offlineSnackbar)
        offlineVm.consumeSnackbarMessage()
        assertNull(offlineVm.snackbarMessage.value)
    }

    @Test
    fun `Master EQ switch and manual fader drag synchronize AutoEQ across States A, B, C and D`() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val presetStore = newDataStore("eq-sm-preset")
        val autoEqStore = newDataStore("eq-sm-autoeq")
        val presetRepo = PresetRepository(app, presetStore)
        val sampleIndex = "- [Sennheiser HD 800](./oratory1990/over-ear/Sennheiser%20HD%20800)"
        val autoEqRepo = AutoEqRepository(app, store = autoEqStore, remote = AutoEqRemoteDataSource { sampleIndex })

        val eqVm = EqualizerViewModel(app, presetRepo, autoEqRepo)
        val autoEqVm = AutoEqViewModel(app, autoEqRepo, searchDebounceMs = 0L)
        withTimeout(10_000) { eqVm.uiState.first { it.libraryRestored } }
        autoEqVm.loadProfiles()
        withTimeout(5_000) { autoEqVm.uiState.first { it is AutoEqUiState.Success } }

        val hd800 = HeadphoneProfile(
            name = "Sennheiser HD 800",
            type = "Over-ear",
            csvUrl = "${AutoEqRemoteDataSource.CDN_RESULTS_BASE}/oratory1990/over-ear/Sennheiser%20HD%20800/Sennheiser%20HD%20800.csv"
        )
        val curve = List(20) { idx -> ((idx - 10) * 0.4f) }

        // State A: AutoEQ applied + Master Switch ON -> DSP processes AutoEQ curve & success snackbar emitted
        eqVm.setMasterPower(true)
        eqVm.applyAutoEqProfile(hd800, curve)
        assertTrue(eqVm.uiState.value.eqEnabled)
        assertEquals(hd800, eqVm.uiState.value.activeAutoEqProfile)
        assertEquals("AutoEQ applied: Sennheiser HD 800", eqVm.uiState.value.message)
        assertTrue(StudioAudioEngine.isEqEnabledSnapshot())
        curve.forEachIndexed { idx, want ->
            assertEquals(want, eqVm.uiState.value.bands[idx].levelDb, 0.01f)
        }

        // Wait for persistence so AutoEqViewModel also sees selectedProfile
        withTimeout(5_000) {
            autoEqVm.uiState.first { it is AutoEqUiState.Success && it.selectedProfile?.id == hd800.id }
        }

        // State B: Master Switch OFF -> DSP bypasses (flat sound), but AutoEQ profile & UI curve stay preserved
        eqVm.setMasterPower(false)
        assertFalse(eqVm.uiState.value.eqEnabled)
        assertFalse(StudioAudioEngine.isEqEnabledSnapshot())
        assertEquals("AutoEQ profile must stay selected while bypassed", hd800, eqVm.uiState.value.activeAutoEqProfile)
        curve.forEachIndexed { idx, want ->
            assertEquals("UI curve shape must remain visible while dimmed", want, eqVm.uiState.value.bands[idx].levelDb, 0.01f)
        }

        // State C: Master Switch back ON -> DSP immediately re-applies AutoEQ curve
        eqVm.setMasterPower(true)
        assertTrue(eqVm.uiState.value.eqEnabled)
        assertTrue(StudioAudioEngine.isEqEnabledSnapshot())
        assertEquals(hd800, eqVm.uiState.value.activeAutoEqProfile)
        val (dspLeftAfterRestore, _) = StudioAudioEngine.currentBandGainsSnapshot()
        curve.forEachIndexed { idx, want ->
            assertEquals(want, dspLeftAfterRestore[idx], 0.05f)
        }

        // State D: User manually moves a fader while AutoEQ is applied -> auto-detaches AutoEQ & keeps manual edit
        eqVm.updateBandLevel(2, 6.0f)
        assertNull("Manual fader drag must detach AutoEQ in EqualizerViewModel", eqVm.uiState.value.activeAutoEqProfile)
        assertEquals(6.0f, eqVm.uiState.value.bands[2].levelDb, 0.01f)
        assertNull(StudioAudioEngine.activeAutoEqGains.value)

        // Detach syncs to AutoEqViewModel (selectedProfile == null)
        autoEqVm.detachAutoEq()
        val detachedState = withTimeout(5_000) {
            autoEqVm.uiState.first { it is AutoEqUiState.Success && it.selectedProfile == null } as AutoEqUiState.Success
        }
        assertNull(detachedState.selectedProfile)
    }
}
