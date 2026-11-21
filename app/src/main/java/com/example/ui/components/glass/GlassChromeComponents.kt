package com.example.ui.components.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.GlassTokens
import com.example.ui.theme.HarmoniEqTheme
import com.example.ui.theme.LocalHarmoniPalette
import com.example.ui.theme.LocalThemeController
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.ThemeSettings

/**
 * GlassTopBar: Mengambang di atas fader dengan Thick Tactile GlassSurface saat scroll,
 * transisi halus ke near-solid saat berhenti.
 */
@Composable
fun GlassTopBar(
    title: String,
    subtitle: String? = null,
    isScrolledUnder: Boolean,
    compact: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val palette = LocalHarmoniPalette.current
    val themeController = LocalThemeController.current
    val reduceMotion = themeController.effectiveReduceMotion
    val reduceTransparency = themeController.settings.reduceTransparency

    val targetNearSolidAlpha = when {
        reduceTransparency -> GlassTokens.ReduceTransparencyAlpha
        !isScrolledUnder -> GlassTokens.NearSolidAlpha
        palette.isDark -> 0.12f
        else -> GlassTokens.LightGlassAlpha
    }

    val animatedAlpha by animateFloatAsState(
        targetValue = targetNearSolidAlpha,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else GlassTokens.TopBarTransitionDurationMs),
        label = "top_bar_opacity"
    )

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (isScrolledUnder) GlassTokens.Space8 else 0.dp)
            .padding(top = if (isScrolledUnder) GlassTokens.Space4 else 0.dp),
        variant = if (isScrolledUnder) GlassVariant.CHROME else GlassVariant.NEAR_SOLID,
        cornerRadius = if (isScrolledUnder) GlassTokens.CornerRadius else 0.dp,
        alphaBoost = if (isScrolledUnder) 0f else (animatedAlpha - GlassTokens.NearSolidAlpha)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = GlassTokens.Space16, vertical = GlassTokens.Space12)
        ) {
            if (compact) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = palette.textPrimary)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions
                )
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleLarge, color = palette.textPrimary)
                        if (subtitle != null) {
                            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = palette.textMuted)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, content = actions)
                }
            }
        }
    }
}

/**
 * GlassBottomSheet: Bottom sheet modal dengan Thick Tactile GlassSurface (near-solid variant).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassBottomSheet(
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = LocalHarmoniPalette.current

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = Color.Transparent,
        contentColor = palette.textPrimary,
        scrimColor = GlassTokens.DialogScrim,
        shape = RoundedCornerShape(
            topStart = GlassTokens.CornerRadius,
            topEnd = GlassTokens.CornerRadius
        ),
        tonalElevation = 0.dp
    ) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            variant = GlassVariant.NEAR_SOLID,
            cornerRadius = GlassTokens.CornerRadius
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                content = content
            )
        }
    }
}

/**
 * GlassDialog: Scrim solid 40% hitam dengan Thick Tactile GlassSurface.
 */
@Composable
fun GlassDialog(
    title: String,
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = LocalHarmoniPalette.current
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(GlassTokens.DialogScrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest
                )
                .padding(GlassTokens.Space24),
            contentAlignment = Alignment.Center
        ) {
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                variant = GlassVariant.NEAR_SOLID
            ) {
                Column(
                    modifier = Modifier.padding(GlassTokens.Space24),
                    verticalArrangement = Arrangement.spacedBy(GlassTokens.Space16)
                ) {
                    Text(title, style = MaterialTheme.typography.titleLarge, color = palette.textPrimary)
                    Column(verticalArrangement = Arrangement.spacedBy(GlassTokens.Space8), content = content)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        dismissButton?.invoke()
                        Spacer(Modifier.width(GlassTokens.Space8))
                        confirmButton()
                    }
                }
            }
        }
    }
}

/**
 * Tombol utama pill / bulat dengan efek kaca tebal (Thick Tactile Glass).
 */
@Composable
fun GlassPrimaryPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val palette = LocalHarmoniPalette.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val alphaBoost = if (pressed) GlassTokens.PressedAlphaDelta else 0f
    val borderBoost = if (pressed) GlassTokens.PressedBorderDelta else 0f

    GlassSurface(
        modifier = modifier
            .drawBehind {
                if (enabled) {
                    drawRoundRect(
                        color = palette.accent.copy(alpha = GlassTokens.PrimaryAmbientGlowAlpha),
                        cornerRadius = CornerRadius(GlassTokens.CornerRadius.toPx(), GlassTokens.CornerRadius.toPx())
                    )
                }
            }
            .defaultMinSize(minHeight = 48.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        variant = GlassVariant.CHROME,
        accentBorder = enabled,
        alphaBoost = alphaBoost,
        borderBoost = borderBoost
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minHeight = 48.dp)
                .padding(horizontal = GlassTokens.Space24, vertical = GlassTokens.Space12),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) palette.accent else palette.textMuted
            )
        }
    }
}

/**
 * Tombol Play/Stop audio berbentuk bulat / pill tebal dengan efek kaca tebal.
 */
@Composable
fun GlassPlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = if (isPlaying) "Hentikan" else "Putar audio uji"
) {
    val palette = LocalHarmoniPalette.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val alphaBoost = if (pressed) 0.08f else 0f
    val borderBoost = if (pressed) 0.10f else 0f

    GlassSurface(
        modifier = modifier
            .drawBehind {
                drawRoundRect(
                    color = palette.accent.copy(alpha = if (isPlaying) 0.15f else 0.08f),
                    cornerRadius = CornerRadius(GlassTokens.CornerRadius.toPx(), GlassTokens.CornerRadius.toPx())
                )
            }
            .defaultMinSize(minHeight = 48.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        variant = GlassVariant.CHROME,
        accentBorder = isPlaying,
        cornerRadius = GlassTokens.CornerRadius,
        alphaBoost = alphaBoost,
        borderBoost = borderBoost
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = 48.dp)
                .padding(horizontal = GlassTokens.Space16, vertical = GlassTokens.Space12),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GlassTokens.Space8)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (isPlaying) palette.accent else palette.accent.copy(alpha = 0.20f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = label,
                    modifier = Modifier.size(18.dp),
                    tint = if (isPlaying) palette.onAccent else palette.accent
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (isPlaying) palette.accent else palette.textPrimary
            )
        }
    }
}

/**
 * Floating Glass Navigation Bar (Thick Tactile Glassmorphism)
 * Mirip dengan tampilan bilah navigasi kapsul mengambang (floating dock/island) di video:
 * - Bentuk rounded pill (kapsul mengambang) dengan margin samping & bawah.
 * - Indikator tab aktif berbentuk kapsul/pill kaca dengan specular border & glow aksen.
 * - Efek blur tebal (RenderEffect API 31+) + specular highlight border.
 */
@Composable
fun <T> GlassFloatingNavigationBar(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    itemLabel: (T) -> String,
    itemIcon: (T) -> androidx.compose.ui.graphics.vector.ImageVector,
    itemTag: (T) -> String = { "nav_${it.toString().lowercase(java.util.Locale.US)}" }
) {
    val palette = LocalHarmoniPalette.current

    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = GlassTokens.Space16, vertical = GlassTokens.Space8),
        variant = GlassVariant.CHROME,
        cornerRadius = 28.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(all = GlassTokens.Space4),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val selected = item == selectedItem

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            if (selected) palette.accent.copy(alpha = 0.15f) else Color.Transparent,
                            RoundedCornerShape(22.dp)
                        )
                        .then(
                            if (selected) {
                                Modifier.border(
                                    width = 1.dp,
                                    color = palette.accent,
                                    shape = RoundedCornerShape(22.dp)
                                )
                            } else {
                                Modifier
                            }
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                            onClick = { onItemSelected(item) }
                        )
                        .testTag(itemTag(item))
                        .padding(vertical = GlassTokens.Space8, horizontal = GlassTokens.Space4),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = itemIcon(item),
                            contentDescription = itemLabel(item),
                            modifier = Modifier.size(22.dp),
                            tint = if (selected) palette.accent else palette.textMuted
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = itemLabel(item),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) palette.accent else palette.textMuted,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * Toolbar A/B floating: pembedaan slot lewat border specular dan Thick Tactile GlassSurface.
 */
@Composable
fun GlassAbToolbar(
    activeSlot: String,
    onToggleSlot: () -> Unit,
    onCopyToOther: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalHarmoniPalette.current
    val shape = RoundedCornerShape(GlassTokens.CornerRadius)
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        variant = GlassVariant.NEAR_SOLID
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(GlassTokens.Space12),
            horizontalArrangement = Arrangement.spacedBy(GlassTokens.Space8),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("A", "B").forEach { slot ->
                val selected = activeSlot == slot
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 44.dp)
                        .clip(shape)
                        .border(
                            width = GlassTokens.BorderWidth,
                            color = if (selected) palette.accent else palette.border,
                            shape = shape
                        )
                        .clickable { if (!selected) onToggleSlot() }
                        .testTag(if (slot == "A") "ab_slot_a" else "ab_compare_button")
                        .padding(horizontal = GlassTokens.Space12, vertical = GlassTokens.Space8),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (selected) "Slot $slot aktif" else "Dengar $slot",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) palette.accent else palette.textSecondary
                    )
                }
            }
            TextButton(onClick = onCopyToOther) {
                Text("Salin", color = palette.textPrimary)
            }
        }
    }
}

// --- Previews Light & Dark untuk setiap komponen glass ---

@Preview(name = "GlassTopBar Dark", showBackground = true, backgroundColor = 0xFF0E1013)
@Composable
private fun PreviewGlassTopBarDark() {
    HarmoniEqTheme(previewSettings = ThemeSettings(mode = ThemeMode.DARK)) {
        GlassTopBar(title = "HARMONI", subtitle = "Equalizer 20-band", isScrolledUnder = true)
    }
}

@Preview(name = "GlassTopBar Light", showBackground = true, backgroundColor = 0xFFF5F2EE)
@Composable
private fun PreviewGlassTopBarLight() {
    HarmoniEqTheme(previewSettings = ThemeSettings(mode = ThemeMode.LIGHT)) {
        GlassTopBar(title = "HARMONI", subtitle = "Equalizer 20-band", isScrolledUnder = false)
    }
}

@Preview(name = "GlassPlayPauseButton Dark", showBackground = true, backgroundColor = 0xFF0E1013)
@Composable
private fun PreviewGlassPlayPauseDark() {
    HarmoniEqTheme(previewSettings = ThemeSettings(mode = ThemeMode.DARK)) {
        Box(Modifier.padding(GlassTokens.Space16)) {
            GlassPlayPauseButton(isPlaying = true, onClick = {})
        }
    }
}

@Preview(name = "GlassPrimaryPillButton Dark", showBackground = true, backgroundColor = 0xFF0E1013)
@Composable
private fun PreviewGlassPillDark() {
    HarmoniEqTheme(previewSettings = ThemeSettings(mode = ThemeMode.DARK)) {
        Box(Modifier.padding(GlassTokens.Space16)) {
            GlassPrimaryPillButton(text = "Simpan suara", onClick = {})
        }
    }
}

@Preview(name = "GlassPrimaryPillButton Light", showBackground = true, backgroundColor = 0xFFF5F2EE)
@Composable
private fun PreviewGlassPillLight() {
    HarmoniEqTheme(previewSettings = ThemeSettings(mode = ThemeMode.LIGHT)) {
        Box(Modifier.padding(GlassTokens.Space16)) {
            GlassPrimaryPillButton(text = "Simpan suara", onClick = {})
        }
    }
}

@Preview(name = "GlassAbToolbar Dark", showBackground = true, backgroundColor = 0xFF0E1013)
@Composable
private fun PreviewGlassAbDark() {
    HarmoniEqTheme(previewSettings = ThemeSettings(mode = ThemeMode.DARK)) {
        Box(Modifier.padding(GlassTokens.Space16)) {
            GlassAbToolbar(activeSlot = "A", onToggleSlot = {}, onCopyToOther = {})
        }
    }
}

@Preview(name = "GlassAbToolbar Light", showBackground = true, backgroundColor = 0xFFF5F2EE)
@Composable
private fun PreviewGlassAbLight() {
    HarmoniEqTheme(previewSettings = ThemeSettings(mode = ThemeMode.LIGHT)) {
        Box(Modifier.padding(GlassTokens.Space16)) {
            GlassAbToolbar(activeSlot = "B", onToggleSlot = {}, onCopyToOther = {})
        }
    }
}

@Preview(name = "GlassDialog Dark", showBackground = true, backgroundColor = 0xFF0E1013)
@Composable
private fun PreviewGlassDialogDark() {
    HarmoniEqTheme(previewSettings = ThemeSettings(mode = ThemeMode.DARK)) {
        GlassSurface(Modifier.padding(GlassTokens.Space16).fillMaxWidth(), variant = GlassVariant.NEAR_SOLID) {
            Column(Modifier.padding(GlassTokens.Space24), verticalArrangement = Arrangement.spacedBy(GlassTokens.Space12)) {
                Text("Simpan preset lengkap", style = MaterialTheme.typography.titleLarge)
                Text("Menyimpan 20 band kiri/kanan dan semua pengaturan efek.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Preview(name = "GlassDialog Light", showBackground = true, backgroundColor = 0xFFF5F2EE)
@Composable
private fun PreviewGlassDialogLight() {
    HarmoniEqTheme(previewSettings = ThemeSettings(mode = ThemeMode.LIGHT)) {
        GlassSurface(Modifier.padding(GlassTokens.Space16).fillMaxWidth(), variant = GlassVariant.NEAR_SOLID) {
            Column(Modifier.padding(GlassTokens.Space24), verticalArrangement = Arrangement.spacedBy(GlassTokens.Space12)) {
                Text("Simpan preset lengkap", style = MaterialTheme.typography.titleLarge)
                Text("Menyimpan 20 band kiri/kanan dan semua pengaturan efek.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
