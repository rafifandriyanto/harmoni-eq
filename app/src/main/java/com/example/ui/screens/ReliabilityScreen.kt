package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SettingsApplications
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.audio.AudioForegroundService
import com.example.audio.MediaSessionNotificationListenerService
import com.example.ui.OemBatteryGuidance
import com.example.ui.ReliabilityManager
import com.example.ui.ReliabilityStatus
import com.example.ui.theme.HarmoniEqTheme
import com.example.ui.theme.LocalHarmoniPalette
import kotlinx.coroutines.launch

/**
 * Stateful "System Reliability" ("Keep EQ Alive") dashboard screen.
 * Automatically re-checks permissions whenever the user returns from system settings.
 */
@Composable
fun ReliabilityScreen(
    reliabilityManager: ReliabilityManager,
    onBack: () -> Unit,
    onReportMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val serviceRunning by AudioForegroundService.isRunning.collectAsStateWithLifecycle()
    val detectedMedia by MediaSessionNotificationListenerService.activeMediaLabel.collectAsStateWithLifecycle()
    val setupCompleted by reliabilityManager.setupCompletedFlow.collectAsStateWithLifecycle(initialValue = false)

    var status by remember { mutableStateOf(reliabilityManager.inspectStatus()) }
    var notificationRequestAttempted by remember { mutableStateOf(false) }

    fun refreshStatus() {
        status = reliabilityManager.inspectStatus()
    }

    LaunchedEffect(serviceRunning, detectedMedia) {
        refreshStatus()
    }

    // Re-check all permissions when returning from Android System Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        refreshStatus()
        if (granted) {
            onReportMessage("Notification permission granted.")
            if (!AudioForegroundService.isRunning.value) {
                AudioForegroundService.start(context)
            }
        } else {
            onReportMessage("Notification permission denied. Opening App Notification Settings allows enabling it.")
            if (notificationRequestAttempted) {
                ReliabilityManager.openAppNotificationSettings(context)
            }
        }
    }

    BackHandler(onBack = onBack)

    ReliabilityDashboardContent(
        status = status,
        setupCompleted = setupCompleted,
        onBack = onBack,
        onRefresh = { refreshStatus() },
        onToggleForegroundService = {
            if (status.foregroundServiceRunning) {
                AudioForegroundService.stop(context)
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !ReliabilityManager.isNotificationPermissionGranted(context)
                ) {
                    notificationRequestAttempted = true
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    AudioForegroundService.start(context)
                }
            }
        },
        onFixBatteryOptimization = {
            if (!ReliabilityManager.openBatteryOptimizationSettings(context)) {
                onReportMessage("Battery optimization settings could not be opened on this device.")
            }
        },
        onFixNotificationPermission = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (notificationRequestAttempted && !ReliabilityManager.isNotificationPermissionGranted(context)) {
                    if (!ReliabilityManager.openAppNotificationSettings(context)) {
                        onReportMessage("Notification settings could not be opened.")
                    }
                } else {
                    notificationRequestAttempted = true
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                refreshStatus()
            }
        },
        onFixNotificationAccess = {
            if (!ReliabilityManager.openNotificationListenerSettings(context)) {
                onReportMessage("Notification Access settings could not be opened on this device.")
            }
        },
        onOpenAppInfo = {
            if (!ReliabilityManager.openAppInfoSettings(context)) {
                onReportMessage("App Info page could not be opened on this device.")
            }
        },
        onToggleSetupCompleted = { completed ->
            scope.launch {
                reliabilityManager.setSetupCompleted(completed)
                onReportMessage(
                    if (completed) "Reliability setup marked complete."
                    else "Reliability checklist reminders re-enabled."
                )
            }
        },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReliabilityDashboardContent(
    status: ReliabilityStatus,
    setupCompleted: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onToggleForegroundService: () -> Unit,
    onFixBatteryOptimization: () -> Unit,
    onFixNotificationPermission: () -> Unit,
    onFixNotificationAccess: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onToggleSetupCompleted: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalHarmoniPalette.current
    val okColor = palette.statusSuccess
    val alertColor = palette.statusDanger
    val progress = status.completedCount.toFloat() / status.totalCoreChecks.toFloat()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("reliability_screen"),
        containerColor = palette.canvas,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.reliability_screen_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textPrimary
                        )
                        Text(
                            text = stringResource(R.string.reliability_screen_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("reliability_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Navigate back",
                            tint = palette.textPrimary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.testTag("reliability_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Re-check system reliability permissions",
                            tint = palette.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.nearSolidChrome,
                    titleContentColor = palette.textPrimary
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp)
                    .testTag("reliability_checklist"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Overall summary header
                item(key = "summary") {
                    val allReady = status.allCoreChecksPassed
                    val badgeColor = if (allReady) okColor else alertColor
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reliability_summary_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = palette.surfaceElevated
                        ),
                        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.55f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(badgeColor.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Shield,
                                        contentDescription = null,
                                        tint = badgeColor,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (allReady) {
                                            "Background Survival Ready"
                                        } else {
                                            "Action Required to Keep EQ Alive"
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = palette.textPrimary
                                    )
                                    Text(
                                        text = "${status.completedCount} of ${status.totalCoreChecks} core protections active",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = palette.textSecondary
                                    )
                                }
                                StatusPill(
                                    label = if (allReady) "OK" else "Action Needed",
                                    isOk = allReady,
                                    okColor = okColor,
                                    alertColor = alertColor
                                )
                            }

                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = badgeColor,
                                trackColor = palette.faderTrack
                            )

                            Text(
                                text = "Aggressive Android battery savers can terminate background DSP processes. Complete each item below so Harmoni EQ stays active during screen-off playback.",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textSecondary
                            )
                        }
                    }
                }

                // 1. Foreground Service
                item(key = "foreground_service") {
                    ReliabilityChecklistCard(
                        icon = Icons.Rounded.GraphicEq,
                        title = "Foreground Service",
                        statusLabel = if (status.foregroundServiceRunning) "Running" else "Stopped",
                        isOk = status.foregroundServiceRunning,
                        description = "Runs the audio engine inside a low-priority persistent notification ('Harmoni EQ is running') so the OS does not kill DSP processing.",
                        actionLabel = if (status.foregroundServiceRunning) "Stop" else "Start",
                        onAction = onToggleForegroundService,
                        cardTag = "reliability_item_service",
                        buttonTag = "reliability_fix_service",
                        useTonalWhenOk = true
                    )
                }

                // 2. Battery Optimization
                item(key = "battery_optimization") {
                    ReliabilityChecklistCard(
                        icon = Icons.Rounded.BatteryChargingFull,
                        title = "Battery Optimization",
                        statusLabel = if (status.batteryOptimizationIgnored) "Disabled" else "Enabled",
                        isOk = status.batteryOptimizationIgnored,
                        description = if (status.batteryOptimizationIgnored) {
                            "Harmoni EQ is exempted from Android Doze and standard battery optimization."
                        } else {
                            "Battery optimization is currently restricting Harmoni EQ. Exempt the app so audio effects are not suspended in the background."
                        },
                        actionLabel = if (status.batteryOptimizationIgnored) "Review" else "Fix",
                        onAction = onFixBatteryOptimization,
                        cardTag = "reliability_item_battery",
                        buttonTag = "reliability_fix_battery",
                        useTonalWhenOk = true
                    )
                }

                // 3. Notification Permission (Android 13+)
                item(key = "notification_permission") {
                    ReliabilityChecklistCard(
                        icon = Icons.Rounded.NotificationsActive,
                        title = "Notification Permission",
                        statusLabel = if (status.notificationPermissionGranted) "Granted" else "Denied",
                        isOk = status.notificationPermissionGranted,
                        description = if (status.notificationPermissionGranted) {
                            "Required on Android 13+ so the foreground keep-alive notification remains visible."
                        } else {
                            "Without notification permission on Android 13+, the foreground service notification cannot be shown and Android may stop the EQ."
                        },
                        actionLabel = if (status.notificationPermissionGranted) "OK" else "Fix",
                        onAction = onFixNotificationPermission,
                        actionEnabled = !status.notificationPermissionGranted,
                        cardTag = "reliability_item_notification_permission",
                        buttonTag = "reliability_fix_notification_permission",
                        useTonalWhenOk = true
                    )
                }

                // 4. Notification Access (Media Session Detection)
                item(key = "notification_access") {
                    val mediaSuffix = status.detectedMediaApp?.let { " Active player detected: $it." }.orEmpty()
                    ReliabilityChecklistCard(
                        icon = Icons.Rounded.Headphones,
                        title = "Notification Access",
                        statusLabel = if (status.notificationAccessGranted) "Granted" else "Denied",
                        isOk = status.notificationAccessGranted,
                        description = "Allows Harmoni EQ to detect when Spotify, YouTube Music, or other media players start playing so the audio session stays attached.$mediaSuffix",
                        actionLabel = if (status.notificationAccessGranted) "Settings" else "Fix",
                        onAction = onFixNotificationAccess,
                        cardTag = "reliability_item_notification_access",
                        buttonTag = "reliability_fix_notification_access",
                        useTonalWhenOk = true
                    )
                }

                // 5. Autostart (OEM Specific)
                item(key = "oem_autostart") {
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reliability_item_autostart"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = palette.surfaceElevated
                        ),
                        border = BorderStroke(1.dp, palette.border)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(palette.accent.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SettingsApplications,
                                        contentDescription = null,
                                        tint = palette.accent,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Autostart (OEM Specific)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = palette.textPrimary
                                    )
                                    Text(
                                        text = status.oemGuidance.vendorName,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = palette.accent
                                    )
                                }
                            }

                            Text(
                                text = status.oemGuidance.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = palette.textPrimary
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                status.oemGuidance.steps.forEachIndexed { idx, step ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "${idx + 1}.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = palette.accent
                                        )
                                        Text(
                                            text = step,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = palette.textSecondary,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = palette.borderSubtle)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                FilledTonalButton(
                                    onClick = onOpenAppInfo,
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("reliability_open_app_info")
                                ) {
                                    Text(stringResource(R.string.reliability_open_app_info))
                                }
                            }
                        }
                    }
                }

                // Setup completion flag stored in DataStore
                item(key = "setup_complete_footer") {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = palette.surface
                        ),
                        border = BorderStroke(1.dp, palette.borderSubtle)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (setupCompleted) "Reliability Setup Completed" else "Finished configuring your device?",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = palette.textPrimary
                                )
                                Text(
                                    text = if (setupCompleted) {
                                        "Warning banners are hidden. You can return here anytime."
                                    } else {
                                        "Mark setup complete to hide repeated reliability warning banners."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.textSecondary
                                )
                            }
                            if (setupCompleted) {
                                OutlinedButton(
                                    onClick = { onToggleSetupCompleted(false) },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("reliability_setup_complete_button")
                                ) {
                                    Text("Reset")
                                }
                            } else {
                                Button(
                                    onClick = { onToggleSetupCompleted(true) },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("reliability_setup_complete_button")
                                ) {
                                    Text("Done")
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ReliabilityChecklistCard(
    icon: ImageVector,
    title: String,
    statusLabel: String,
    isOk: Boolean,
    description: String,
    actionLabel: String,
    onAction: () -> Unit,
    cardTag: String,
    buttonTag: String,
    actionEnabled: Boolean = true,
    useTonalWhenOk: Boolean = false
) {
    val palette = LocalHarmoniPalette.current
    val okColor = palette.statusSuccess
    val alertColor = palette.statusDanger
    val statusColor = if (isOk) okColor else alertColor

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(cardTag)
            .semantics(mergeDescendants = false) {},
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = palette.surfaceElevated
        ),
        border = BorderStroke(1.dp, statusColor.copy(alpha = if (isOk) 0.42f else 0.65f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.textPrimary
                    )
                    Spacer(Modifier.height(2.dp))
                    StatusPill(
                        label = statusLabel,
                        isOk = isOk,
                        okColor = okColor,
                        alertColor = alertColor
                    )
                }
                if (isOk && useTonalWhenOk) {
                    OutlinedButton(
                        onClick = onAction,
                        enabled = actionEnabled,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag(buttonTag)
                    ) {
                        Text(actionLabel)
                    }
                } else {
                    Button(
                        onClick = onAction,
                        enabled = actionEnabled,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.accent,
                            contentColor = palette.onAccent
                        ),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag(buttonTag)
                    ) {
                        Text(actionLabel)
                    }
                }
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary
            )
        }
    }
}

@Composable
private fun StatusPill(
    label: String,
    isOk: Boolean,
    okColor: Color,
    alertColor: Color
) {
    val tint = if (isOk) okColor else alertColor
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = tint.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (isOk) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
                contentDescription = if (isOk) "OK" else "Action required",
                tint = tint,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = tint
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 900)
@Composable
private fun PreviewReliabilityDashboard() {
    HarmoniEqTheme {
        ReliabilityDashboardContent(
            status = ReliabilityStatus(
                foregroundServiceRunning = true,
                batteryOptimizationIgnored = false,
                notificationPermissionGranted = true,
                notificationAccessGranted = false,
                detectedMediaApp = "Spotify",
                oemGuidance = OemBatteryGuidance(
                    vendorName = "Xiaomi / MIUI / HyperOS",
                    title = "MIUI & HyperOS Background Survival",
                    steps = listOf(
                        "Open App Info -> Battery saver -> select No restrictions.",
                        "Enable Background autostart (Autostart) in App Info."
                    )
                )
            ),
            setupCompleted = false,
            onBack = {},
            onRefresh = {},
            onToggleForegroundService = {},
            onFixBatteryOptimization = {},
            onFixNotificationPermission = {},
            onFixNotificationAccess = {},
            onOpenAppInfo = {},
            onToggleSetupCompleted = {}
        )
    }
}
