package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.EqualizerUiState
import com.example.ui.EqualizerViewModel
import com.example.ui.ReliabilityStatus
import com.example.ui.components.ConsolePanel
import com.example.ui.components.SectionHeading
import com.example.ui.components.ToggleRow
import com.example.ui.theme.GlassTokens
import com.example.ui.theme.LocalHarmoniPalette
import com.example.ui.theme.LocalThemeController

/**
 * Reusable System Reliability ("Keep EQ Alive") entry card shown inside SettingsScreen
 * and OutputSettingsSheet so users can immediately inspect and open the Reliability checklist.
 */
@Composable
fun SystemReliabilityLinkCard(
    status: ReliabilityStatus,
    setupCompleted: Boolean,
    onOpenReliability: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalHarmoniPalette.current
    val isHealthy = status.allCoreChecksPassed
    val showAlert = !isHealthy && !setupCompleted
    val accentTint = when {
        isHealthy -> palette.statusSuccess
        showAlert -> palette.statusDanger
        else -> palette.accent
    }

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onOpenReliability)
            .testTag("open_reliability_screen_button"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = palette.surfaceElevated
        ),
        border = BorderStroke(1.dp, accentTint.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accentTint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = accentTint,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "System Reliability",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.textPrimary
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = accentTint.copy(alpha = 0.16f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (isHealthy) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
                                contentDescription = null,
                                tint = accentTint,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = if (isHealthy) {
                                    "OK (${status.completedCount}/${status.totalCoreChecks})"
                                } else if (setupCompleted) {
                                    "${status.completedCount}/${status.totalCoreChecks} Active"
                                } else {
                                    "Action Required"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = accentTint
                            )
                        }
                    }
                }
                Text(
                    text = "Keep EQ Alive • Foreground service, battery exemption, notification access & OEM autostart.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "Open System Reliability",
                tint = palette.textSecondary
            )
        }
    }
}

/**
 * Dedicated Settings screen linking to System Reliability ("Keep EQ Alive")
 * alongside theme, accessibility, and background service controls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: EqualizerUiState,
    vm: EqualizerViewModel,
    reliabilityStatus: ReliabilityStatus,
    reliabilitySetupCompleted: Boolean,
    onOpenReliability: () -> Unit,
    onToggleService: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalHarmoniPalette.current
    val themeController = LocalThemeController.current
    val themeSettings = themeController.settings

    BackHandler(onBack = onBack)

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        containerColor = palette.canvas,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Navigate back",
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SectionHeading(
                    title = "Background Survival",
                    subtitle = "Prevent Android OEM battery savers from stopping the 20-band DSP engine."
                )
                SystemReliabilityLinkCard(
                    status = reliabilityStatus,
                    setupCompleted = reliabilitySetupCompleted,
                    onOpenReliability = onOpenReliability
                )

                SectionHeading(
                    title = "Audio Engine & Appearance",
                    subtitle = "Quick toggles for background service, Material You colors, and visual accessibility."
                )
                ConsolePanel {
                    ToggleRow(
                        title = "Foreground Audio Keep-Alive",
                        description = "Run Harmoni EQ in a persistent low-priority notification.",
                        checked = state.backgroundServiceActive,
                        onToggle = onToggleService,
                        tag = "settings_toggle_service"
                    )
                    ToggleRow(
                        title = "Material You (Dynamic Color)",
                        description = "Match system wallpaper colors on Android 12+ with 4.5:1 contrast.",
                        checked = themeSettings.dynamicColor,
                        onToggle = { themeController.setDynamicColor(!themeSettings.dynamicColor) },
                        tag = "settings_toggle_dynamic_color"
                    )
                    ToggleRow(
                        title = "Reduce transparency",
                        description = "Use high-opacity surfaces for improved readability.",
                        checked = themeSettings.reduceTransparency,
                        onToggle = { themeController.setReduceTransparency(!themeSettings.reduceTransparency) },
                        tag = "settings_toggle_reduce_transparency"
                    )
                    ToggleRow(
                        title = "Auto output profiles",
                        description = "Switch EQ presets automatically when a paired audio device connects.",
                        checked = state.autoProfiles,
                        onToggle = vm::toggleAutoProfiles,
                        tag = "settings_toggle_auto_profiles"
                    )
                }

                Spacer(Modifier.height(GlassTokens.Space16))
            }
        }
    }
}
