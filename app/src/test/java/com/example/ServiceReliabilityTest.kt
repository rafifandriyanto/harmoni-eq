package com.example

import com.example.audio.EqualizerService
import com.example.audio.StudioAudioEngine
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ServiceReliabilityTest {
    @Test fun `creating service alone does not claim foreground is running`() {
        val controller = Robolectric.buildService(EqualizerService::class.java).create()
        try { assertFalse(EqualizerService.isRunning.value) } finally { controller.destroy() }
    }
    @Test fun `service destruction releases effect session registrations`() {
        val controller = Robolectric.buildService(EqualizerService::class.java).create()
        StudioAudioEngine.registerExternalSession(42, "test.player")
        controller.destroy()
        assertTrue(StudioAudioEngine.detectedSessions.value.isEmpty())
        assertFalse(EqualizerService.isRunning.value)
    }
}
