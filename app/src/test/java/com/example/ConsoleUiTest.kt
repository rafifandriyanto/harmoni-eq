package com.example

import androidx.compose.ui.test.*
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.EqualizerViewModel
import com.example.ui.HarmoniEqApp
import com.example.ui.theme.HarmoniEqTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConsoleUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun screenshot(name: String) {
        rule.waitForIdle()
        val file = File("build/ui-preview/$name.png")
        file.parentFile?.mkdirs()
        rule.runOnUiThread {
            val view = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
    @Test @Config(qualifiers = "w1024dp-h768dp-land-mdpi")
    fun `tablet keeps EQ beside effects`() {
        rule.onNodeWithTag("nav_effects").performClick()
        rule.onNodeWithTag("output_status").assertIsDisplayed()
        rule.onNodeWithText("Headroom").assertIsDisplayed()
        screenshot("tablet-1024")
    }

    @Test fun `large fonts keep primary controls reachable`() {
        rule.runOnUiThread {
            rule.activity.setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                    HarmoniEqTheme {
                        val vm: EqualizerViewModel = viewModel()
                        HarmoniEqApp(vm)
                    }
                }
            }
        }
        rule.onNodeWithTag("output_status").assertIsDisplayed()
        screenshot("equalizer-font200")
        listOf("HARMONI", "Equalizer", "YouTube", "Efek", "Preset").forEach { label ->
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            val node = rule.onAllNodesWithText(label, useUnmergedTree = true)[0]
            val bounds = node.fetchSemanticsNode().boundsInRoot
            node.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
            // Klip nyata = glif terakhir melewati lebar layout, atau teks wrap/terpotong vertikal.
            // Flag didOverflowWidth dengan softWrap=false ikut menghitung letter-spacing trailing,
            // jadi kriteria visual memakai tepi kanan glif (getLineRight), bukan flag tersebut.
            org.junit.Assert.assertTrue("$label at $bounds must fit without clipped text: ${layouts.map { "lines=${it.lineCount}, size=${it.size}, lineRight=${it.getLineRight(0)}, intrinsic=${it.multiParagraph.width}, overflowH=${it.didOverflowHeight}" }}", layouts.all { it.lineCount == 1 && !it.didOverflowHeight && it.getLineRight(0) <= it.size.width + 0.5f })
        }
        rule.onNodeWithTag("mode_simple").performScrollTo().performClick()
        rule.onNodeWithTag("simple_treble").performScrollTo().assertIsDisplayed()
        screenshot("simple-font200")
        rule.onNodeWithTag("nav_presets").performClick()
        rule.onNodeWithTag("preset_search").performScrollTo().assertIsDisplayed()
        screenshot("presets-font200")
    }

    @Test fun `fader taps align with drawn track endpoints`() {
        var gain by androidx.compose.runtime.mutableStateOf(0f)
        rule.runOnUiThread {
            rule.activity.setContent {
                HarmoniEqTheme {
                    com.example.ui.components.ProEqFader(
                        com.example.model.EqBand(0, 25, "25Hz", -15f, 15f, gain), true, true,
                        com.example.model.StereoChannelFocus.LINKED, { gain = it }, { gain = 0f },
                        androidx.compose.ui.Modifier.height(240.dp))
                }
            }
        }
        rule.onNodeWithTag("eq_fader_0").performTouchInput { click(androidx.compose.ui.geometry.Offset(centerX, 8f)) }
        rule.mainClock.advanceTimeBy(500)
        rule.runOnIdle { org.junit.Assert.assertEquals(15f, gain, 0.01f) }
        rule.onNodeWithTag("eq_fader_0").performTouchInput { click(androidx.compose.ui.geometry.Offset(centerX, height - 8f)) }
        rule.mainClock.advanceTimeBy(500)
        rule.runOnIdle { org.junit.Assert.assertEquals(-15f, gain, 0.01f) }
    }

    @Test fun `fader drag moves gain and reports exactly one gesture`() {
        var gain by androidx.compose.runtime.mutableStateOf(0f)
        var starts = 0
        var ends = 0
        rule.runOnUiThread {
            rule.activity.setContent {
                HarmoniEqTheme {
                    com.example.ui.components.ProEqFader(
                        com.example.model.EqBand(0, 25, "25Hz", -15f, 15f, gain), true, true,
                        com.example.model.StereoChannelFocus.LINKED, { gain = it }, { gain = 0f },
                        androidx.compose.ui.Modifier.height(240.dp), { starts++ }, { ends++ })
                }
            }
        }
        rule.onNodeWithTag("eq_fader_0").performTouchInput {
            down(center)
            advanceEventTime(60)
            moveTo(center - androidx.compose.ui.geometry.Offset(0f, 60f))
            advanceEventTime(60)
            up()
        }
        rule.runOnIdle {
            org.junit.Assert.assertTrue("drag ke atas harus menaikkan gain, dapat $gain", gain > 1f)
            org.junit.Assert.assertEquals(1, starts)
            org.junit.Assert.assertEquals(1, ends)
        }
    }

    @Test fun `hidden youtube webview is excluded from accessibility`() {
        rule.onNodeWithTag("nav_youtube").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("nav_effects").performClick()
        rule.waitForIdle()
        val web = findWebView(rule.activity.window.decorView)
        org.junit.Assert.assertNotNull("WebView YouTube tetap hidup di balik tab lain", web)
        org.junit.Assert.assertEquals(
            android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS, web!!.importantForAccessibility)
    }

    private fun findWebView(view: android.view.View): android.view.View? {
        if (view is android.webkit.WebView) return view
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) findWebView(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    @Test fun `four screens expose new workflow and render`() {
        rule.onNodeWithTag("output_status").assertIsDisplayed()
        screenshot("equalizer-360")
        rule.onNodeWithTag("mode_simple").performScrollTo().performClick()
        rule.onNodeWithTag("simple_bass").performScrollTo().assertIsDisplayed()
        screenshot("simple-360")
        rule.onNodeWithTag("nav_effects").performClick()
        rule.onNodeWithText("Headroom").performScrollTo().assertIsDisplayed()
        screenshot("effects-360")
        rule.onNodeWithTag("nav_presets").performClick()
        rule.onNodeWithTag("preset_categories").performScrollToNode(hasTestTag("preset_category_speaker"))
        rule.onNodeWithTag("preset_category_speaker").performClick()
        rule.onNodeWithTag("preset_card_phone_speaker_boost").assertIsDisplayed()
        screenshot("presets-360")
        rule.onNodeWithTag("nav_youtube").performClick()
        rule.onNodeWithTag("youtube_show_search").performClick()
        rule.onNodeWithTag("youtube_search_input").assertIsDisplayed()
        screenshot("youtube-360")
    }
}
