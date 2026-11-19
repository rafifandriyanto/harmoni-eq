package com.example

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {
    @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test fun `read app name from context`() {
        assertEquals("Harmoni EQ", ApplicationProvider.getApplicationContext<Context>().getString(R.string.app_name))
    }

    @Test fun `20 band stereo controls remain available through progressive disclosure`() {
        with(composeTestRule) {
            onNodeWithText("HARMONI").assertIsDisplayed()
            onNodeWithTag("frequency_curve_canvas").assertDoesNotExist()
            onNodeWithTag("stereo_channel_left").performScrollTo().performClick()
            onNodeWithTag("eq_fader_bank").performScrollTo()
            onNodeWithTag("eq_value_0").assertIsDisplayed().performClick()
            onNode(hasSetTextAction()).performTextReplacement("-3.2")
            onNodeWithText("Terapkan").performClick()
            onNodeWithTag("undo").performScrollTo().assertIsEnabled()
            onNodeWithTag("show_curve").performScrollTo().performClick()
            onNodeWithTag("frequency_curve_canvas").performScrollTo().assertIsDisplayed()
            onNodeWithTag("show_tools").performScrollTo().performClick()
            onNodeWithTag("mirror_lr_button").performScrollTo().performClick()
            onNodeWithTag("ab_compare_button").performScrollTo().performClick()
            onNodeWithTag("ab_compare_button").performClick()
            onNodeWithTag("smooth_curve_button").performScrollTo().performClick()
            onNodeWithTag("stereo_channel_right").performScrollTo().performClick()
            onNodeWithTag("stereo_channel_linked").performClick()
            onNodeWithTag("eq_fader_bank").performScrollTo().performScrollToIndex(19)
            onNodeWithTag("eq_fader_19").assertIsDisplayed()
            onNodeWithTag("launch_youtube_button").performScrollTo().performClick()
            onNodeWithTag("youtube_webview_container").assertIsDisplayed()
            onNodeWithTag("youtube_search_input").assertDoesNotExist()
            onNodeWithTag("youtube_show_search").performClick()
            onNodeWithTag("youtube_search_input").assertIsDisplayed()
            onNodeWithTag("youtube_open_eq_button").performClick()
            onNodeWithTag("output_status").assertIsDisplayed()
            onNodeWithTag("nav_effects").performClick()
            onNodeWithTag("stereo_width_knob").performScrollTo().assertIsDisplayed()
            onNodeWithTag("bass_boost_knob").performScrollTo().assertIsDisplayed()
            onNodeWithTag("reverb_chip_medium_hall").performScrollTo().assertIsNotEnabled()
            onNodeWithTag("nav_presets").performClick()
            onNodeWithTag("preset_categories").performScrollTo().performScrollToNode(hasTestTag("preset_category_headphone"))
            onNodeWithTag("preset_category_headphone").performClick()
            onNodeWithTag("preset_list").performScrollToNode(hasTestTag("preset_card_tws_punch"))
            onNodeWithTag("preset_card_tws_punch").assertIsDisplayed()
            onNodeWithTag("preset_list").performScrollToIndex(2)
            onNodeWithTag("preset_categories").performScrollToNode(hasTestTag("preset_category_semua"))
            onNodeWithTag("preset_category_semua").performClick()
            onNodeWithTag("preset_list").performScrollToNode(hasTestTag("preset_card_flat"))
            onNodeWithTag("preset_card_flat").assertIsDisplayed()
        }
    }
}
