package com.example

import android.media.AudioManager
import android.media.audiofx.Equalizer
import com.example.audio.BiquadFilter
import com.example.audio.FocusAction
import com.example.audio.FocusPolicy
import com.example.audio.MeterMath
import com.example.audio.SessionEffectChain
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EnginePolicyTest {

    @Test fun `biquad history reset makes a fresh start sound identical`() {
        val filter = BiquadFilter()
        filter.setPeaking(100.0, 44100.0, 2.6, 6.0)
        val impulse = { i: Int -> if (i == 0) 1.0 else 0.0 }
        val first = (0 until 64).map { filter.process(impulse(it)) }
        filter.resetHistory()
        val second = (0 until 64).map { filter.process(impulse(it)) }
        assertEquals(first, second)
    }

    @Test fun `meter rms is computed from written pcm samples not pre scale floats`() {
        val fullScale = ShortArray(64) { if (it % 2 == 0) 32767 else -32767 }
        assertEquals(1f, MeterMath.rmsOfWritten(fullScale, 0), 0.001f)
        assertEquals(1f, MeterMath.rmsOfWritten(fullScale, 1), 0.001f)
        assertEquals(0f, MeterMath.rmsOfWritten(ShortArray(64), 0), 0f)
        val halfScale = ShortArray(64) { 16384 }
        assertEquals(0.5f, MeterMath.rmsOfWritten(halfScale, 0), 0.01f)
    }

    @Test fun `audio focus loss stops test audio and ducking only lowers it`() {
        assertEquals(FocusAction.STOP, FocusPolicy.actionFor(AudioManager.AUDIOFOCUS_LOSS))
        assertEquals(FocusAction.STOP, FocusPolicy.actionFor(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT))
        assertEquals(FocusAction.DUCK, FocusPolicy.actionFor(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK))
        assertEquals(FocusAction.RESTORE, FocusPolicy.actionFor(AudioManager.AUDIOFOCUS_GAIN))
    }

    @Test fun `rms ignores samples beyond the written count`() {
        val buf = ShortArray(8) { 32767 }
        for (i in 4..7) buf[i] = 0
        assertEquals(1f, MeterMath.rmsOfWritten(buf, 0, 4), 0.001f)
        assertEquals(0f, MeterMath.rmsOfWritten(buf, 0, 0), 0.001f)
    }

    @Test fun `claiming 20 band dynamics requires actual control of the session`() {
        val chain = SessionEffectChain(0, "uji")
        try {
            chain.attach()
            if (chain.using20BandDynamics) {
                assertTrue(chain.dynamicsProcessing!!.hasControl())
            } else {
                assertNotNull("fallback Equalizer harus dicoba saat DP tidak mengontrol", chain.equalizer)
                assertNotNull("cabang fallback harus meninggalkan catatan jujur", chain.fallbackNote)
            }
        } finally { chain.release() }
    }

    @Test fun `fallback band count is the native hardware count not a coerced guess`() {
        val chain = SessionEffectChain(0, "uji")
        val probe = Equalizer(0, 0)
        try {
            chain.attach()
            if (!chain.using20BandDynamics) {
                assertEquals(probe.numberOfBands.toInt(), chain.hwBandCount)
            }
        } finally { chain.release(); probe.release() }
    }

    @Test fun `optional effect errors do not mark the whole chain failed`() {
        val chain = SessionEffectChain(0, "uji")
        try {
            chain.attach()
            // Shadow Robolectric membuat hasControl()=false; uji invarian, bukan kesiapan absolut.
            val readyBefore = chain.coreReady
            chain.noteOptionalError("bass", "Bass: tidak didukung sesi ini")
            assertEquals("error opsional tidak mengubah kesiapan inti", readyBefore, chain.coreReady)
            assertNull("error opsional tidak boleh menjadi lastError inti", chain.lastError)
            assertTrue(chain.allErrors.any { it.startsWith("Bass:") })
            chain.clearOptionalError("bass")
            assertEquals(readyBefore, chain.coreReady)
            assertTrue(chain.allErrors.none { it.startsWith("Bass:") })
        } finally { chain.release() }
    }
}