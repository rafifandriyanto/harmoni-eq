package com.example

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.PersistedEqSettings
import com.example.data.PresetRepository
import com.example.model.EqPreset
import com.example.ui.AutoProfilePolicy
import com.example.ui.EqualizerViewModel
import com.example.ui.HeadroomPolicy
import com.example.ui.LibraryMerge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ViewModelGateTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val testScope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val storage = PreferenceDataStoreFactory.create(scope = testScope) {
        File(app.cacheDir, "vm-test-${UUID.randomUUID()}.preferences_pb")
    }
    private val repo = PresetRepository(app, storage)

    @After fun tearDown() {
        Dispatchers.resetMain()
        testScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    private fun preset(i: Int) = EqPreset("c$i", "Preset $i", "Kustom", "uji", List(20) { 0f }, isCustom = true)

    @Test fun `import before restore finishes is rejected instead of losing saved ids`() {
        Dispatchers.setMain(StandardTestDispatcher())
        val vm = EqualizerViewModel(app, repo)
        val json = com.example.data.PresetCodec.encode(listOf(preset(1)))
        val result = vm.importPresets(json, false)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("pulih"))
    }

    @Test fun `export before restore finishes cannot produce an empty library file`() {
        Dispatchers.setMain(StandardTestDispatcher())
        val vm = EqualizerViewModel(app, repo)
        assertThrows(IllegalStateException::class.java) { vm.exportPresets() }
    }

    @Test fun `library merge never exceeds the 200 preset cap`() {
        val saved = (1..201).map { preset(it) }
        val merged = LibraryMerge.merge(saved, emptyList())
        assertEquals(200, merged.presets.size)
        assertTrue(merged.truncated)
        val exact = LibraryMerge.merge((1..199).map { preset(it) }, listOf(preset(200), preset(201)))
        assertEquals(200, exact.presets.size)
        assertTrue(exact.truncated)
        val dedup = LibraryMerge.merge((1..10).map { preset(it) }, (1..10).map { preset(it) })
        assertEquals(10, dedup.presets.size)
        assertFalse(dedup.truncated)
    }

    @Test fun `auto profile applies on the initial route and on enable not only on key change`() {
        assertTrue(AutoProfilePolicy.shouldApply(restored = true, autoProfiles = true, oldKey = "bt1", newKey = "bt1", initialApply = true))
        assertTrue(AutoProfilePolicy.shouldApply(restored = true, autoProfiles = true, oldKey = "bt1", newKey = "bt2", initialApply = false))
        assertFalse(AutoProfilePolicy.shouldApply(restored = true, autoProfiles = true, oldKey = "bt1", newKey = "bt1", initialApply = false))
        assertFalse(AutoProfilePolicy.shouldApply(restored = true, autoProfiles = false, oldKey = "bt1", newKey = "bt2", initialApply = true))
        assertFalse(AutoProfilePolicy.shouldApply(restored = false, autoProfiles = true, oldKey = null, newKey = "bt1", initialApply = true))
        assertFalse(AutoProfilePolicy.shouldApply(restored = true, autoProfiles = true, oldKey = null, newKey = null, initialApply = true))
    }

    @Test fun `auto headroom resyncs exactly when preamp capability appears or disappears`() {
        assertTrue(HeadroomPolicy.resyncNeeded(true, setOf("eq"), setOf("eq", "preamp")))
        assertTrue(HeadroomPolicy.resyncNeeded(true, setOf("eq", "preamp"), setOf("eq")))
        assertFalse(HeadroomPolicy.resyncNeeded(true, setOf("eq", "preamp"), setOf("eq", "preamp")))
        assertFalse(HeadroomPolicy.resyncNeeded(false, setOf("eq"), setOf("eq", "preamp")))
    }

    @Test fun `metadata changed before restore is persisted once restore completes`() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val vm = EqualizerViewModel(app, repo)
        vm.toggleFavorite("warm_studio")
        withTimeout(10_000) { vm.uiState.first { it.libraryRestored } }
        withTimeout(10_000) {
            val saved = repo.settingsFlow.first { "warm_studio" in it.favoriteIds }
            assertTrue("warm_studio" in saved.favoriteIds)
        }
    }

    @Test fun `oversized saved library is truncated honestly at restore`() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repo.saveSettings(PersistedEqSettings(customPresets = (1..201).map { preset(it) }))
        val vm = EqualizerViewModel(app, repo)
        withTimeout(10_000) { vm.uiState.first { it.libraryRestored } }
        val customs = vm.uiState.value.presets.filter { it.isCustom }
        assertEquals(200, customs.size)
        assertEquals("urutan tersimpan dipertahankan: preset pertama hidup", "c1", customs.first().id)
        assertTrue("preset ke-201 dipangkas, bukan menyelip", customs.none { it.id == "c201" })
        assertTrue(vm.uiState.value.message.orEmpty().contains("200"))
        assertTrue("pemangkasan harus tercatat permanen, bukan hanya snackbar", vm.uiState.value.libraryTruncated)
    }

    @Test fun `session preset created before restore survives the 200 cap`() = runBlocking {
        val main = StandardTestDispatcher()
        Dispatchers.setMain(main)
        repo.saveSettings(PersistedEqSettings(customPresets = (1..200).map { preset(it) }))
        val vm = EqualizerViewModel(app, repo)
        vm.saveCustomPreset("Sesi ini")
        withTimeout(10_000) {
            while (!vm.uiState.value.libraryRestored) { main.scheduler.advanceUntilIdle(); kotlinx.coroutines.delay(20) }
        }
        val customs = vm.uiState.value.presets.filter { it.isCustom }
        assertEquals(200, customs.size)
        assertEquals("kerja sesi ini tidak boleh dipangkas demi data lama", "Sesi ini", customs.first().name)
        assertTrue(vm.uiState.value.libraryTruncated)
    }

    @Test fun `truncation notice survives auto profile application`() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repo.saveSettings(PersistedEqSettings(customPresets = (1..201).map { preset(it) }, autoProfiles = true,
            outputProfiles = listOf(com.example.model.OutputProfile("manual:x", "TWS Uji", "c1"))))
        val vm = EqualizerViewModel(app, repo)
        withTimeout(10_000) { vm.uiState.first { it.libraryRestored } }
        vm.applyOutputProfile("manual:x")
        assertTrue("banner persisten tidak boleh hilang tertimpa snackbar profil", vm.uiState.value.libraryTruncated)
    }
}