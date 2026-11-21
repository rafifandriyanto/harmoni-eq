package com.example.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Immutable
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.example.audio.AudioForegroundService
import com.example.audio.MediaSessionNotificationListenerService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.reliabilityDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "harmoni_reliability_prefs_v1"
)

@Immutable
data class OemBatteryGuidance(
    val vendorName: String,
    val title: String,
    val steps: List<String>
)

@Immutable
data class ReliabilityStatus(
    val foregroundServiceRunning: Boolean,
    val batteryOptimizationIgnored: Boolean,
    val notificationPermissionGranted: Boolean,
    val notificationAccessGranted: Boolean,
    val detectedMediaApp: String?,
    val oemGuidance: OemBatteryGuidance
) {
    val completedCount: Int
        get() = listOf(
            foregroundServiceRunning,
            batteryOptimizationIgnored,
            notificationPermissionGranted,
            notificationAccessGranted
        ).count { it }

    val totalCoreChecks: Int = 4

    val allCoreChecksPassed: Boolean
        get() = completedCount == totalCoreChecks
}

/**
 * Centralizes background-survival diagnostics, Android permission checks,
 * OEM battery/autostart instructions, system settings intents, and setup completion flag.
 */
class ReliabilityManager(
    context: Context,
    private val store: DataStore<Preferences> = context.applicationContext.reliabilityDataStore
) {
    private val appContext = context.applicationContext

    private object Keys {
        val SETUP_COMPLETED = booleanPreferencesKey("reliability_setup_completed")
    }

    val setupCompletedFlow: Flow<Boolean> = store.data.map { prefs ->
        prefs[Keys.SETUP_COMPLETED] ?: false
    }

    suspend fun setSetupCompleted(completed: Boolean) {
        store.edit { prefs ->
            prefs[Keys.SETUP_COMPLETED] = completed
        }
    }

    fun inspectStatus(manufacturer: String = Build.MANUFACTURER.orEmpty()): ReliabilityStatus {
        return ReliabilityStatus(
            foregroundServiceRunning = isForegroundServiceRunning(),
            batteryOptimizationIgnored = isIgnoringBatteryOptimizations(appContext),
            notificationPermissionGranted = isNotificationPermissionGranted(appContext),
            notificationAccessGranted = isNotificationListenerEnabled(appContext),
            detectedMediaApp = MediaSessionNotificationListenerService.activeMediaLabel.value
                ?: AudioForegroundService.activeMediaApp.value,
            oemGuidance = detectOemGuidance(manufacturer)
        )
    }

    companion object {
        fun isForegroundServiceRunning(): Boolean = AudioForegroundService.isRunning.value

        fun isIgnoringBatteryOptimizations(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
            return runCatching { pm.isIgnoringBatteryOptimizations(context.packageName) }.getOrDefault(false)
        }

        fun isNotificationPermissionGranted(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }

        fun isNotificationListenerEnabled(context: Context): Boolean {
            val enabledByCompat = runCatching {
                context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
            }.getOrDefault(false)
            if (enabledByCompat) return true

            val rawSetting = runCatching {
                Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            }.getOrNull().orEmpty()
            return rawSetting.split(':').any { component ->
                component.startsWith("${context.packageName}/") || component == context.packageName
            }
        }

        fun createBatteryOptimizationIntent(context: Context): Intent {
            return Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}")
            )
        }

        fun createBatteryOptimizationFallbackIntent(): Intent {
            return Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        }

        fun createNotificationListenerSettingsIntent(): Intent {
            return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }

        fun createAppNotificationSettingsIntent(context: Context): Intent {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
            } else {
                createAppInfoIntent(context)
            }
        }

        fun createAppInfoIntent(context: Context): Intent {
            return Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            )
        }

        fun openBatteryOptimizationSettings(context: Context): Boolean {
            return launchSettingsIntent(
                context = context,
                primary = createBatteryOptimizationIntent(context),
                fallback = createBatteryOptimizationFallbackIntent()
            )
        }

        fun openNotificationListenerSettings(context: Context): Boolean {
            return launchSettingsIntent(
                context = context,
                primary = createNotificationListenerSettingsIntent(),
                fallback = createAppInfoIntent(context)
            )
        }

        fun openAppNotificationSettings(context: Context): Boolean {
            return launchSettingsIntent(
                context = context,
                primary = createAppNotificationSettingsIntent(context),
                fallback = createAppInfoIntent(context)
            )
        }

        fun openAppInfoSettings(context: Context): Boolean {
            return launchSettingsIntent(
                context = context,
                primary = createAppInfoIntent(context),
                fallback = null
            )
        }

        private fun launchSettingsIntent(
            context: Context,
            primary: Intent,
            fallback: Intent?
        ): Boolean {
            val candidates = listOfNotNull(primary, fallback)
            for (candidate in candidates) {
                val prepared = Intent(candidate).apply {
                    if (context !is android.app.Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                val launched = runCatching {
                    context.startActivity(prepared)
                    true
                }.getOrDefault(false)
                if (launched) return true
            }
            return false
        }

        fun detectOemGuidance(manufacturer: String): OemBatteryGuidance {
            val lower = manufacturer.trim().lowercase()
            return when {
                lower.contains("xiaomi") || lower.contains("redmi") || lower.contains("poco") -> OemBatteryGuidance(
                    vendorName = "Xiaomi / MIUI / HyperOS",
                    title = "MIUI & HyperOS Background Survival",
                    steps = listOf(
                        "Open App Info -> Battery saver -> select No restrictions.",
                        "Enable Background autostart (Autostart) in App Info.",
                        "Optional: Lock Harmoni EQ in Recents so MIUI does not clear the DSP process."
                    )
                )
                lower.contains("oppo") || lower.contains("realme") || lower.contains("oneplus") -> OemBatteryGuidance(
                    vendorName = "Oppo / Realme / OnePlus (ColorOS)",
                    title = "ColorOS & OxygenOS Background Survival",
                    steps = listOf(
                        "Open App Info -> Battery usage -> enable Allow foreground activity and Allow background activity.",
                        "Enable Allow auto launch (Autostart) for Harmoni EQ.",
                        "Disable Sleep standby optimization if audio stops while the screen is off."
                    )
                )
                lower.contains("vivo") || lower.contains("iqoo") -> OemBatteryGuidance(
                    vendorName = "Vivo / iQOO (FuntouchOS)",
                    title = "FuntouchOS & OriginOS Background Survival",
                    steps = listOf(
                        "Open App Info -> Battery -> select Unrestricted (High background power consumption).",
                        "Open iManager -> App manager -> Autostart -> enable Harmoni EQ."
                    )
                )
                lower.contains("samsung") -> OemBatteryGuidance(
                    vendorName = "Samsung (One UI)",
                    title = "One UI Background Survival",
                    steps = listOf(
                        "Open App Info -> Battery -> select Unrestricted.",
                        "In Device Care -> Battery -> Background usage limits, add Harmoni EQ to Never auto sleeping apps."
                    )
                )
                lower.contains("huawei") || lower.contains("honor") -> OemBatteryGuidance(
                    vendorName = "Huawei / Honor (EMUI / MagicOS)",
                    title = "EMUI & MagicOS App Launch Survival",
                    steps = listOf(
                        "Open App Info -> Battery -> App launch -> switch from Manage automatically to Manage manually.",
                        "Enable Auto-launch, Secondary launch, and Run in background."
                    )
                )
                else -> OemBatteryGuidance(
                    vendorName = manufacturer.ifBlank { "Android" },
                    title = "Manufacturer Battery & Autostart Guide",
                    steps = listOf(
                        "Open App Info -> Battery -> select Unrestricted / No Restrictions.",
                        "If your device provides an Autostart or Background Launch toggle, enable it for Harmoni EQ."
                    )
                )
            }
        }
    }
}
