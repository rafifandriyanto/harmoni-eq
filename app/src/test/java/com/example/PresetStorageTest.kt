package com.example

import android.content.Context
import kotlinx.coroutines.flow.first
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.example.data.PresetRepository
import com.example.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PresetStorageTest {
    private val testScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val storage = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(scope = testScope) {
        java.io.File(context.cacheDir, "test-${java.util.UUID.randomUUID()}.preferences_pb")
    }
    private val repo = PresetRepository(context, storage)
    @org.junit.After fun closeStorage() { testScope.coroutineContext[kotlinx.coroutines.Job]?.cancel() }
    private fun encode(presets: List<EqPreset>): String = PresetRepository::class.java
        .getDeclaredMethod("encodeCustomPresets", List::class.java).apply { isAccessible = true }
        .invoke(repo, presets) as String
    @Suppress("UNCHECKED_CAST")
    private fun decode(json: String): List<EqPreset> = PresetRepository::class.java
        .getDeclaredMethod("decodeCustomPresets", String::class.java).apply { isAccessible = true }
        .invoke(repo, json) as List<EqPreset>

    @Test fun `stored preset round trip preserves complete sound`() {
        val sound = SoundSnapshot(left = List(20) { 3f }, right = List(20) { -2f },
            linked = false, bassEnabled = true, bass = 0.4f, surroundEnabled = false,
            surround = 0.7f, width = 1.4f, balance = -0.2f, preampEnabled = false,
            preamp = -4f, limiter = false, reverb = ReverbRoomPreset.PLATE)
        val preset = EqPreset("test", "Stereo", "Kustom", "Test", sound.left, isCustom = true, sound = sound)
        assertEquals(sound, decode(encode(listOf(preset))).single().soundSnapshot())
    }

    @Test fun `corrupt saved library is preserved rather than overwritten`() = kotlinx.coroutines.runBlocking {
        val key = stringPreferencesKey("complete_presets_v2_json")
        storage.edit { it[key] = "{broken}" }
        assertTrue(runCatching { repo.settingsFlow.first() }.isFailure)
        assertTrue(runCatching { repo.saveSettings(com.example.data.PersistedEqSettings()) }.isFailure)
        assertEquals("{broken}", storage.data.first()[key])
    }

    @Test fun `saved smooth gains retain exact precision`() = kotlinx.coroutines.runBlocking {
        val gains = List(20) { 1.234375f }
        repo.saveSettings(com.example.data.PersistedEqSettings(leftBandGainsDb = gains, rightBandGainsDb = gains))
        assertEquals(gains, repo.settingsFlow.first().leftBandGainsDb)
    }

    @Test fun `invalid legacy numeric settings cannot reach DSP`() = kotlinx.coroutines.runBlocking {
        repo.saveSettings(com.example.data.PersistedEqSettings(leftBandGainsDb = List(20) { Float.NaN }, stereoWidth = Float.POSITIVE_INFINITY))
        val saved = repo.settingsFlow.first()
        assertTrue(saved.leftBandGainsDb.all { it.isFinite() && it in -15f..15f })
        assertTrue(saved.stereoWidth.isFinite())
    }

    @Test fun `legacy 5 band preset remains readable as 20 controls`() {
        val json = """[{"id":"old","name":"Lama","gains":[1,2,3,4,5],"bassBoost":0.2}]"""
        val old = decode(json).single()
        assertEquals("Lama", old.name)
        assertEquals(listOf(1f,1f,1f,1f,2f,2f,2f,2f,3f,3f,3f,3f,4f,4f,4f,4f,5f,5f,5f,5f), old.bandGainsDb)
    }
}
