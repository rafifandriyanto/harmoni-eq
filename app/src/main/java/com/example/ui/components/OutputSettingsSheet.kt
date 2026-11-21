package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.EqualizerUiState
import com.example.model.OutputDevice
import com.example.ui.EqualizerViewModel
import com.example.ui.ReliabilityStatus
import com.example.ui.components.glass.GlassBottomSheet
import com.example.ui.screens.SystemReliabilityLinkCard
import com.example.ui.theme.AccentPreset
import com.example.ui.theme.GlassTokens
import com.example.ui.theme.LocalHarmoniPalette
import com.example.ui.theme.LocalThemeController
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.isValidHexColor

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OutputSettingsSheet(
    state: EqualizerUiState,
    vm: EqualizerViewModel,
    reliabilityStatus: ReliabilityStatus,
    reliabilitySetupCompleted: Boolean,
    onOpenReliability: () -> Unit,
    onOpenHelp: () -> Unit = {},
    onDismiss: () -> Unit,
    onBluetoothSettings: () -> Unit,
    onService: () -> Unit
) {
    val palette = LocalHarmoniPalette.current
    val themeController = LocalThemeController.current
    val themeSettings = themeController.settings

    var manualName by remember { mutableStateOf("") }
    var hexInput by remember(themeSettings.customHex) { mutableStateOf(themeSettings.customHex) }

    GlassBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(GlassTokens.Space24),
            verticalArrangement = Arrangement.spacedBy(GlassTokens.Space16)
        ) {
            // Bagian 0: System Reliability (Keep EQ Alive)
            SectionHeading(
                "System Reliability",
                "Ensure foreground service, battery exemption, notification access, and OEM autostart stay configured."
            )
            SystemReliabilityLinkCard(
                status = reliabilityStatus,
                setupCompleted = reliabilitySetupCompleted,
                onOpenReliability = onOpenReliability
            )

            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(role = Role.Button, onClick = onOpenHelp)
                    .testTag("settings_help_button"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = palette.surfaceElevated
                ),
                border = BorderStroke(1.dp, palette.borderSubtle)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = GlassTokens.Space16, vertical = GlassTokens.Space12),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(GlassTokens.Space12)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(palette.accent.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.HelpOutline,
                            contentDescription = null,
                            tint = palette.accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Panduan & cek suara",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textPrimary
                        )
                        Text(
                            text = "Langkah awal, audio uji, dan solusi jika efek EQ tidak terdengar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Buka panduan dan cek suara",
                        tint = palette.textSecondary
                    )
                }
            }

            HorizontalDivider(color = palette.borderSubtle)

            // Bagian 1: Tampilan, Tema Kustom & Aksesibilitas
            SectionHeading(
                "Tampilan & tema",
                "Ubah mode terang/gelap, pilih warna aksen, atau kurangi efek visual untuk performa dan aksesibilitas."
            )
            ConsolePanel {
                Text("Mode tema", style = MaterialTheme.typography.titleSmall, color = palette.textPrimary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(GlassTokens.Space8)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = themeSettings.mode == mode,
                            onClick = { themeController.setThemeMode(mode) },
                            label = { Text(mode.label) },
                            modifier = Modifier.testTag("theme_mode_${mode.name.lowercase()}")
                        )
                    }
                }

                HorizontalDivider(color = palette.borderSubtle)

                ToggleRow(
                    title = "Material You (Dynamic Color)",
                    description = "Match system wallpaper colors on Android 12+ with 4.5:1 text contrast.",
                    checked = themeSettings.dynamicColor,
                    onToggle = { themeController.setDynamicColor(!themeSettings.dynamicColor) },
                    tag = "toggle_dynamic_color"
                )

                Text("Warna aksen tunggal", style = MaterialTheme.typography.titleSmall, color = palette.textPrimary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(GlassTokens.Space8)) {
                    AccentPreset.entries.forEach { preset ->
                        val previewColor = if (palette.isDark) preset.darkColor else preset.lightColor
                        FilterChip(
                            selected = !themeSettings.dynamicColor && themeSettings.accent == preset,
                            onClick = { themeController.setAccentPreset(preset) },
                            leadingIcon = {
                                Box(
                                    Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(previewColor)
                                        .border(1.dp, palette.border, CircleShape)
                                )
                            },
                            label = { Text(preset.label) },
                            modifier = Modifier.testTag("accent_preset_${preset.name.lowercase()}")
                        )
                    }
                }

                if (themeSettings.accent == AccentPreset.CUSTOM) {
                    val validHex = isValidHexColor(hexInput)
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(GlassTokens.Space8)
                    ) {
                        OutlinedTextField(
                            value = hexInput,
                            onValueChange = { hexInput = it.take(7) },
                            label = { Text("Kode hex (#RRGGBB)") },
                            singleLine = true,
                            isError = !validHex,
                            supportingText = {
                                Text(
                                    if (validHex) "Saturasi otomatis disesuaikan agar kontras tetap aman."
                                    else "Gunakan format 6 digit hex, misalnya #C77D4A."
                                )
                            },
                            modifier = Modifier.weight(1f).testTag("custom_accent_hex_input")
                        )
                        TextButton(
                            onClick = { themeController.setCustomHex(hexInput) },
                            enabled = validHex
                        ) {
                            Text("Pakai")
                        }
                    }
                }

                HorizontalDivider(color = palette.borderSubtle)

                ToggleRow(
                    title = "Reduce transparency",
                    description = "Ganti semua permukaan glass menjadi 95% solid dengan border 1.dp.",
                    checked = themeSettings.reduceTransparency,
                    onToggle = { themeController.setReduceTransparency(!themeSettings.reduceTransparency) },
                    tag = "toggle_reduce_transparency"
                )
                ToggleRow(
                    title = "Reduce motion",
                    description = "Matikan animasi non-esensial pada fader dan bar navigasi.",
                    checked = themeSettings.reduceMotion,
                    onToggle = { themeController.setReduceMotion(!themeSettings.reduceMotion) },
                    tag = "toggle_reduce_motion"
                )
            }

            HorizontalDivider(color = palette.borderSubtle)

            // Bagian 2: Output & Kompatibilitas Audio (tetap utuh)
            SectionHeading(
                "Output & kompatibilitas",
                "Pasangkan TWS, headphone, atau speaker lewat Android. Profil di sini mengubah EQ, bukan rute audio."
            )
            OutlinedButton(onBluetoothSettings) { Text("Buka pengaturan Bluetooth") }
            Text(state.output.routeDescription, color = palette.statusWarning, style = MaterialTheme.typography.bodyMedium)
            Text(state.hardwareStatusMessage, style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary)
            Text(
                "Sesi terdeteksi: ${state.detectedSessions.joinToString().ifBlank { "belum ada" }}",
                color = palette.textMuted,
                style = MaterialTheme.typography.bodyMedium
            )
            ToggleRow(
                "Layanan latar belakang",
                "Layanan berjalan tidak berarti semua aplikasi bisa diproses.",
                state.backgroundServiceActive,
                onService
            )
            ConsolePanel {
                SectionHeading("Kemampuan jalur saat ini")
                listOf(
                    "eq" to "Equalizer",
                    "stereo" to "Kanal L/R & balance",
                    "preamp" to "Preamp",
                    "limiter" to "Limiter",
                    "bass" to "Bass boost",
                    "surround" to "Surround",
                    "width" to "Mid/Side",
                    "reverb" to "Reverb"
                ).forEach { (key, label) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary)
                        Text(
                            if (key in state.supportedEffects) "Tersedia" else "Tidak tersedia",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (key in state.supportedEffects) palette.statusSuccess else palette.textMuted
                        )
                    }
                }
                if (state.hardwareBandCount in 1..19) {
                    Text(
                        "20 kontrol dipetakan ke ${state.hardwareBandCount} band hardware; L/R dirata-ratakan pada fallback.",
                        color = palette.statusWarning,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                state.audioErrors.forEach {
                    Text(it, color = palette.statusDanger, style = MaterialTheme.typography.bodySmall)
                }
            }

            ToggleRow(
                "Profil otomatis (opsional)",
                "Mengikuti rute media Android saat identitas stabil tersedia. Tidak mengubah volume sistem.",
                state.autoProfiles,
                vm::toggleAutoProfiles
            )

            SectionHeading(
                "Perangkat tersedia",
                "Terhubung tidak selalu berarti sedang dipakai. Identitas tidak stabil hanya untuk pemilihan manual."
            )
            state.output.available.forEach { device ->
                var menu by remember(device.key) { mutableStateOf(false) }
                ConsolePanel {
                    Text(device.label, style = MaterialTheme.typography.titleSmall, color = palette.textPrimary)
                    Text(
                        "${device.kind} - ${if (device.stableIdentity) "identitas stabil" else "profil manual"}",
                        color = palette.textMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Box {
                        OutlinedButton({ menu = true }) { Text("Hubungkan preset") }
                        DropdownMenu(menu, { menu = false }) {
                            state.presets.forEach { p ->
                                DropdownMenuItem({ Text(p.name) }, { vm.bindOutputProfile(device, p.id); menu = false })
                            }
                        }
                    }
                }
            }
            if (state.output.available.isEmpty()) {
                Text(
                    "Android belum melaporkan perangkat. Kamu tetap bisa menyimpan profil manual.",
                    color = palette.textMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            OutlinedTextField(
                value = manualName,
                onValueChange = { manualName = it.take(40) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nama profil manual, mis. TWS saya") },
                singleLine = true
            )
            TextButton(
                onClick = {
                    vm.bindOutputProfile(
                        OutputDevice("manual:${java.util.UUID.randomUUID()}", manualName.trim(), "Manual", false),
                        state.activePresetId
                    )
                    manualName = ""
                },
                enabled = manualName.isNotBlank() && state.presets.any { it.id == state.activePresetId } && !state.presetDirty
            ) { Text("Hubungkan ke preset aktif") }
            if (state.presetDirty) {
                Text(
                    "Simpan kurva yang diubah menjadi preset sebelum menghubungkannya.",
                    color = palette.statusWarning,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            state.outputProfiles.forEach { p ->
                ConsolePanel {
                    Text(p.deviceLabel, style = MaterialTheme.typography.titleSmall, color = palette.textPrimary)
                    Text(
                        state.presets.find { it.id == p.presetId }?.name ?: "Preset sudah dihapus",
                        color = palette.textMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                    FlowRow {
                        TextButton({ vm.applyOutputProfile(p.deviceKey) }, enabled = state.presets.any { it.id == p.presetId }) {
                            Text("Terapkan manual")
                        }
                        TextButton({ vm.removeOutputProfile(p.deviceKey) }) { Text("Lepaskan profil") }
                    }
                }
            }
            Spacer(Modifier.height(GlassTokens.Space16))
        }
    }
}
