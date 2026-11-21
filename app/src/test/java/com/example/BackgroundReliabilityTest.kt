package com.example

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.example.audio.AudioForegroundService
import com.example.audio.EqPowerBridge
import com.example.audio.StudioAudioEngine
import com.example.ui.ReliabilityManager
import com.example.ui.ReliabilityStatus
import com.example.ui.screens.ReliabilityDashboardContent
import com.example.ui.theme.HarmoniEqTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackgroundReliabilityTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val testScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() {
        testScope.coroutineContext[Job]?.cancel()
    }

    @Test
    fun `AudioForegroundService creates IMPORTANCE_LOW channel and shows Harmoni EQ is running notification`() {
        EqPowerBridge.publishState(app, enabled = true, presetName = "Studio Flat")
        val controller = Robolectric.buildService(AudioForegroundService::class.java).create()
        try {
            assertFalse(AudioForegroundService.isRunning.value)

            controller.startCommand(0, 0)
            assertTrue(AudioForegroundService.isRunning.value)

            val nm = app.getSystemService(NotificationManager::class.java)
            val channel = nm.getNotificationChannel(AudioForegroundService.CHANNEL_ID)
            assertNotNull(channel)
            assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)

            val shadowService = Shadows.shadowOf(controller.get())
            val notification = shadowService.lastForegroundNotification
            assertNotNull("Service must invoke startForeground()", notification)
            assertEquals(
                "Harmoni EQ is running",
                notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            )
            val actionTitles = notification.actions?.mapNotNull { it.title?.toString() }.orEmpty()
            assertTrue("Notification must include Pause action", "Pause" in actionTitles)
            assertTrue("Notification must include Stop action", "Stop" in actionTitles)
        } finally {
            controller.destroy()
        }
        assertFalse(AudioForegroundService.isRunning.value)
        assertTrue(StudioAudioEngine.detectedSessions.value.isEmpty())
    }

    @Test
    fun `ReliabilityManager creates valid system settings intents and detects OEM battery guides`() {
        val batteryIntent = ReliabilityManager.createBatteryOptimizationIntent(app)
        assertEquals(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, batteryIntent.action)
        assertEquals("package:${app.packageName}", batteryIntent.data?.toString())

        val listenerIntent = ReliabilityManager.createNotificationListenerSettingsIntent()
        assertEquals(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, listenerIntent.action)

        val appInfoIntent = ReliabilityManager.createAppInfoIntent(app)
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appInfoIntent.action)
        assertEquals("package:${app.packageName}", appInfoIntent.data?.toString())

        val miui = ReliabilityManager.detectOemGuidance("Xiaomi")
        assertTrue(miui.vendorName.contains("Xiaomi"))
        assertTrue(miui.steps.any { it.contains("No restrictions", ignoreCase = true) })
        assertTrue(miui.steps.any { it.contains("Autostart", ignoreCase = true) })

        val colorOs = ReliabilityManager.detectOemGuidance("OPPO")
        assertTrue(colorOs.vendorName.contains("Oppo"))

        val oneUi = ReliabilityManager.detectOemGuidance("samsung")
        assertTrue(oneUi.vendorName.contains("Samsung"))

        val emui = ReliabilityManager.detectOemGuidance("HUAWEI")
        assertTrue(emui.vendorName.contains("Huawei"))

        val generic = ReliabilityManager.detectOemGuidance("GenericVendor")
        assertTrue(generic.steps.any { it.contains("No Restrictions", ignoreCase = true) })
    }

    @Test
    fun `ReliabilityManager detects NotificationListenerService enablement and persists setupCompleted`() = runBlocking {
        Settings.Secure.putString(app.contentResolver, "enabled_notification_listeners", "")
        assertFalse(ReliabilityManager.isNotificationListenerEnabled(app))

        Settings.Secure.putString(
            app.contentResolver,
            "enabled_notification_listeners",
            "${app.packageName}/com.example.audio.MediaSessionNotificationListenerService"
        )
        assertTrue(ReliabilityManager.isNotificationListenerEnabled(app))

        val store = PreferenceDataStoreFactory.create(scope = testScope) {
            File(app.cacheDir, "reliability-test-${UUID.randomUUID()}.preferences_pb")
        }
        val manager = ReliabilityManager(app, store)
        assertFalse(manager.setupCompletedFlow.first())

        manager.setSetupCompleted(true)
        assertTrue(manager.setupCompletedFlow.first())

        manager.setSetupCompleted(false)
        assertFalse(manager.setupCompletedFlow.first())
    }

    @Test
    fun `ReliabilityDashboardContent displays all five checklist items and triggers actions`() {
        var batteryFixClicks = 0
        var notifPermClicks = 0
        var notifAccessClicks = 0
        var appInfoClicks = 0
        var setupDoneClicks = 0

        val status = ReliabilityStatus(
            foregroundServiceRunning = true,
            batteryOptimizationIgnored = false,
            notificationPermissionGranted = false,
            notificationAccessGranted = false,
            detectedMediaApp = "Spotify",
            oemGuidance = ReliabilityManager.detectOemGuidance("Xiaomi")
        )

        composeRule.setContent {
            HarmoniEqTheme {
                ReliabilityDashboardContent(
                    status = status,
                    setupCompleted = false,
                    onBack = {},
                    onRefresh = {},
                    onToggleForegroundService = {},
                    onFixBatteryOptimization = { batteryFixClicks++ },
                    onFixNotificationPermission = { notifPermClicks++ },
                    onFixNotificationAccess = { notifAccessClicks++ },
                    onOpenAppInfo = { appInfoClicks++ },
                    onToggleSetupCompleted = { if (it) setupDoneClicks++ }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("System Reliability").assertIsDisplayed()
        val list = composeRule.onNodeWithTag("reliability_checklist")

        list.performScrollToNode(hasTestTag("reliability_item_service"))
        composeRule.onNodeWithText("Foreground Service").assertIsDisplayed()

        list.performScrollToNode(hasTestTag("reliability_item_battery"))
        composeRule.onNodeWithText("Battery Optimization").assertIsDisplayed()
        composeRule.onNodeWithTag("reliability_fix_battery").performClick()

        list.performScrollToNode(hasTestTag("reliability_item_notification_permission"))
        composeRule.onNodeWithTag("reliability_fix_notification_permission").performClick()

        list.performScrollToNode(hasTestTag("reliability_item_notification_access"))
        composeRule.onNodeWithTag("reliability_fix_notification_access").performClick()

        list.performScrollToNode(hasTestTag("reliability_item_autostart"))
        composeRule.onNodeWithTag("reliability_open_app_info").performClick()

        list.performScrollToNode(hasTestTag("reliability_setup_complete_button"))
        composeRule.onNodeWithTag("reliability_setup_complete_button").performClick()

        composeRule.waitForIdle()
        assertEquals(1, batteryFixClicks)
        assertEquals(1, notifPermClicks)
        assertEquals(1, notifAccessClicks)
        assertEquals(1, appInfoClicks)
        assertEquals(1, setupDoneClicks)
    }
}
