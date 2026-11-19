package com.example

import com.example.audio.StudioAudioEngine
import com.example.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AudioReliabilityTest {
    @Test fun `stopped audio has zero unavailable meters not a simulated signal`() {
        StudioAudioEngine.stopTestAudio()
        assertEquals(0f, StudioAudioEngine.vuLevels.value.first, 0f)
        assertTrue(StudioAudioEngine.stereoScopePoints.value.all { it == 0f })
    }
    @Test fun `connected Bluetooth is not automatically the media route`() {
        val bt = OutputDevice("bt1", "TWS", "Bluetooth", true)
        val output = OutputState(available = listOf(bt))
        assertNull(output.route)
        assertNull(output.autoProfileKey)
    }
    @Test fun `ambiguous device identity cannot auto switch a profile`() {
        val bt = OutputDevice("generic", "Bluetooth", "Bluetooth", false)
        assertNull(OutputState(route = bt).autoProfileKey)
    }
    @Test fun `headroom estimate attenuates and never boosts`() {
        val sound = SoundSnapshot(left = List(20) { 5f }, right = List(20) { 2f })
        assertEquals(-5f, sound.estimatedCompensationDb(), 0f)
        assertEquals(0f, SoundSnapshot(left = List(20) { -4f }).estimatedCompensationDb(), 0f)
    }
}
