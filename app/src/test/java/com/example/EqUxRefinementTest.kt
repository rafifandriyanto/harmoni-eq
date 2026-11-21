package com.example

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.model.EqFrequencySection
import com.example.model.QuickTuningPresets
import com.example.ui.EqualizerViewModel
import com.example.ui.theme.AccentPreset
import com.example.ui.theme.buildHarmoniPalette
import com.example.ui.theme.wcagContrastRatio
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w390dp-h844dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EqUxRefinementTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val store = ViewModelStore()

    @After
    fun cleanup() {
        store.clear()
    }

    @Test
    fun `functional frequency sections cover all 20 bands without gaps or overlaps`() {
        val covered = EqFrequencySection.entries.flatMap { it.bandRange.toList() }
        assertEquals(7, EqFrequencySection.entries.size)
        assertEquals((0..19).toList(), covered)
    }

    @Test
    fun `quick tuning presets provide valid 20-band curves and support undo`() {
        val vm = EqualizerViewModel(ApplicationProvider.getApplicationContext<Application>())
            .also { store.put("qt", it) }

        QuickTuningPresets.defaultItems.forEach { preset ->
            assertEquals(20, preset.bandGainsDb.size)
            assertTrue(preset.bandGainsDb.all { it in -15f..15f })
        }

        vm.resetAllFlat()
        vm.applyQuickTuningPreset(QuickTuningPresets.MoreBass)
        assertEquals(QuickTuningPresets.MoreBass.id, vm.uiState.value.activeQuickTuningId)
        assertEquals(QuickTuningPresets.MoreBass.bandGainsDb[0], vm.uiState.value.bands[0].levelDb, 0.05f)

        vm.undo()
        assertEquals(0f, vm.uiState.value.bands[0].levelDb, 0.01f)
    }

    @Test
    fun `palette text tokens meet WCAG 4_5 to 1 contrast in dark and light modes`() {
        listOf(true, false).forEach { isDark ->
            AccentPreset.entries.forEach { accent ->
                val p = buildHarmoniPalette(isDark, accent, "#C77D4A")
                val bg = if (isDark) p.surfaceElevated else p.controlWell
                assertTrue("textPrimary contrast >= 4.5 ($isDark/$accent)", wcagContrastRatio(p.textPrimary, bg) >= 4.5f)
                assertTrue("textSecondary contrast >= 4.5 ($isDark/$accent)", wcagContrastRatio(p.textSecondary, bg) >= 4.5f)
                assertTrue("textMuted contrast >= 4.5 ($isDark/$accent)", wcagContrastRatio(p.textMuted, bg) >= 4.5f)
                assertTrue("accent contrast >= 4.5 ($isDark/$accent)", wcagContrastRatio(p.accent, bg) >= 4.5f)
                assertTrue("statusSuccess contrast >= 4.5 ($isDark/$accent)", wcagContrastRatio(p.statusSuccess, bg) >= 4.5f)
                assertTrue("statusWarning contrast >= 4.5 ($isDark/$accent)", wcagContrastRatio(p.statusWarning, bg) >= 4.5f)
                assertTrue("statusDanger contrast >= 4.5 ($isDark/$accent)", wcagContrastRatio(p.statusDanger, bg) >= 4.5f)
            }
        }
    }

    @Test
    fun `master switch, uncluttered top bar, contextual controls, and fine tune flow work`() {
        // Top Bar stays uncluttered: full HARMONI title + Settings + Master Power only
        rule.onNodeWithTag("top_bar_title").assertIsDisplayed()
        rule.onNodeWithText("HARMONI").assertIsDisplayed()
        rule.onNodeWithTag("theme_settings_button").assertIsDisplayed()
        rule.onNodeWithTag("master_power_button").assertIsDisplayed()
        rule.onNodeWithTag("eq_active_indicator_dot").assertIsDisplayed()
        rule.onNodeWithTag("top_bar_autoeq_button").assertDoesNotExist()
        rule.onNodeWithTag("reset_flat_top_button").assertDoesNotExist()

        // Master switch, AutoEQ card & contextual Reset to Flat are reachable in the Equalizer screen
        rule.onNodeWithTag("master_eq_switch").assertIsDisplayed()
        rule.onNodeWithTag("open_autoeq_button").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("reset_flat_inline_button").performScrollTo().assertIsDisplayed()

        // Onboarding tooltip appears on first Advanced view and opens full EQ curve on tap
        rule.onNodeWithTag("advanced_onboarding_tip").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Drag the sliders to shape your sound. Tap here to see the curve.").assertIsDisplayed()
        rule.onNodeWithTag("advanced_onboarding_tip").performClick()
        rule.onNodeWithTag("frequency_curve_canvas").performScrollTo().assertIsDisplayed()

        // Real-time curve overlay & Quick Tuning chips are present in Advanced mode
        rule.onNodeWithTag("quick_tuning_qt_more_bass").performScrollTo().performClick()
        rule.onNodeWithTag("eq_slider_curve_overlay").performScrollTo().assertIsDisplayed()

        // Simple mode Fine-tune this sound button smoothly transitions back to Advanced 20-band mode
        rule.onNodeWithTag("mode_simple").performScrollTo().performClick()
        rule.onNodeWithTag("fine_tune_sound_button").performScrollTo().assertIsDisplayed().performClick()
        rule.onNodeWithTag("eq_fader_bank").performScrollTo().assertIsDisplayed()

        // Help & sound check dialog is reachable inside Settings sheet
        rule.onNodeWithTag("theme_settings_button").performClick()
        rule.onNodeWithTag("settings_help_button").performScrollTo().assertIsDisplayed().performClick()
        rule.onNodeWithText("Mulai & cek suara").assertIsDisplayed()
    }

    @Test
    fun `master eq switch disables and re-enables faders while keeping curve visible`() {
        // Initially EQ ON: faders enabled, curve overlay visible
        rule.onNodeWithTag("eq_fader_bank").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("eq_fader_0").assertIsDisplayed().assertIsEnabled()
        rule.onNodeWithTag("eq_slider_curve_overlay").assertIsDisplayed()

        // Turn Master EQ OFF -> faders disabled, curve remains visible (dimmed)
        rule.onNodeWithTag("master_power_button").performClick()
        rule.onNodeWithTag("eq_fader_0").assertIsNotEnabled()
        rule.onNodeWithTag("eq_slider_curve_overlay").assertIsDisplayed()

        // Turn Master EQ back ON -> faders re-enabled
        rule.onNodeWithTag("master_power_button").performClick()
        rule.onNodeWithTag("eq_fader_0").assertIsEnabled()
    }
}
