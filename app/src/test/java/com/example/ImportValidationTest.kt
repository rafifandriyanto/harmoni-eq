package com.example

import com.example.data.PresetCodec
import com.example.model.*
import com.example.ui.screens.YouTubeNavigation
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ImportValidationTest {
    private fun document() = JSONObject(PresetCodec.encode(listOf(EqPreset("one", "Test", "Kustom", "", List(20) { 0f }, sound = SoundSnapshot()))))
    @Test fun `fractional schema version is rejected`() {
        assertTrue(runCatching { PresetCodec.decode(document().put("schemaVersion", 2.5).toString()) }.isFailure)
    }
    @Test fun `string masquerading as boolean is rejected`() {
        val doc = document()
        doc.getJSONArray("presets").getJSONObject(0).getJSONObject("sound").put("linked", "true")
        assertTrue(runCatching { PresetCodec.decode(doc.toString()) }.isFailure)
    }
    @Test fun `invalid gains and duplicate IDs are rejected`() {
        val doc = document()
        val preset = doc.getJSONArray("presets").getJSONObject(0)
        preset.getJSONObject("sound").getJSONArray("left").put(0, 100)
        assertTrue(runCatching { PresetCodec.decode(doc.toString()) }.isFailure)
        val duplicate = document()
        duplicate.getJSONArray("presets").put(duplicate.getJSONArray("presets").getJSONObject(0))
        assertTrue(runCatching { PresetCodec.decode(duplicate.toString()) }.isFailure)
    }
    @Test fun `youtube accepts search and trusted HTTPS only`() {
        assertEquals("https://m.youtube.com/results?search_query=lofi+music", YouTubeNavigation.resolve("lofi music").getOrThrow())
        assertTrue(YouTubeNavigation.resolve("https://youtu.be/example").isSuccess)
        listOf("javascript:alert(1)", "file:///test", "http://youtube.com", "https://youtube.com.evil.com", "https://user@youtube.com").forEach {
            assertTrue(it, YouTubeNavigation.resolve(it).isFailure)
        }
    }
}
