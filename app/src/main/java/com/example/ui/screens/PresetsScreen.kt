package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.EqPreset
import com.example.model.EqualizerUiState
import com.example.ui.EqualizerViewModel
import com.example.ui.components.ConsolePanel
import com.example.ui.components.NameDialog
import com.example.ui.components.SectionHeading
import com.example.ui.components.glass.GlassDialog
import com.example.ui.components.glass.GlassPrimaryPillButton
import com.example.ui.theme.GlassTokens
import com.example.ui.theme.LocalHarmoniPalette
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PresetsScreen(
    state: EqualizerUiState,
    vm: EqualizerViewModel,
    onSave: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
    onInteractionChange: (Boolean) -> Unit = {},
    onScrolledUnderChange: (Boolean) -> Unit = {}
) {
    val palette = LocalHarmoniPalette.current
    val listState = rememberLazyListState()
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Semua") }
    var favoritesOnly by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<EqPreset?>(null) }
    var renaming by remember { mutableStateOf<EqPreset?>(null) }

    // snapshotFlow: baca offset scroll di luar komposisi agar tidak recompose per piksel.
    LaunchedEffect(listState) {
        launch {
            snapshotFlow { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 8 }
                .distinctUntilChanged()
                .collect { onScrolledUnderChange(it) }
        }
        launch {
            snapshotFlow { listState.isScrollInProgress }
                .distinctUntilChanged()
                .collect { onInteractionChange(it) }
        }
    }

    val visible = remember(state.presets, state.favoriteIds, query, category, favoritesOnly) {
        state.presets.filter { p ->
            (category == "Semua" || p.category == category) &&
                (!favoritesOnly || p.id in state.favoriteIds) &&
                (p.name.contains(query, true) || p.description.contains(query, true))
        }
    }
    val hasCustom = remember(state.presets) { state.presets.any { it.isCustom } }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().testTag("preset_list"),
        contentPadding = PaddingValues(GlassTokens.Space16),
        verticalArrangement = Arrangement.spacedBy(GlassTokens.Space12)
    ) {
        item {
            SectionHeading("Karakter suara", "Preset lengkap: kanal kiri/kanan, efek, dan headroom.")
            FlowRow(
                modifier = Modifier.padding(top = GlassTokens.Space8),
                horizontalArrangement = Arrangement.spacedBy(GlassTokens.Space8),
                verticalArrangement = Arrangement.spacedBy(GlassTokens.Space8)
            ) {
                GlassPrimaryPillButton(text = "Simpan suara", onClick = onSave)
                TextButton(onImport) { Text("Impor JSON") }
                TextButton(onExport, enabled = hasCustom) { Text("Ekspor kustom") }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().testTag("preset_search"),
                label = { Text("Cari preset") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null) }
            )
        }
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(GlassTokens.Space8),
                modifier = Modifier.testTag("preset_categories")
            ) {
                items(listOf("Semua", "Studio", "Musik", "Vokal", "Headphone", "Speaker", "Gaming", "Kustom")) { c ->
                    FilterChip(
                        selected = category == c,
                        onClick = { category = c },
                        label = { Text(c) },
                        modifier = Modifier.testTag("preset_category_${c.lowercase()}")
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = favoritesOnly,
                    onClick = { favoritesOnly = !favoritesOnly },
                    label = { Text("Favorit") },
                    leadingIcon = { Icon(Icons.Rounded.Star, null, Modifier.size(18.dp)) }
                )
                Spacer(Modifier.weight(1f))
                Text("${visible.size} preset", color = palette.textMuted, style = MaterialTheme.typography.labelMedium)
            }
        }
        if (visible.isEmpty()) {
            item {
                ConsolePanel {
                    SectionHeading("Belum ada yang cocok", "Coba kata lain, tandai favorit, atau simpan pengaturanmu sendiri.")
                    TextButton({ query = ""; category = "Semua"; favoritesOnly = false }) { Text("Reset filter") }
                }
            }
        }
        items(visible, key = { it.id }) { p ->
            var menu by remember { mutableStateOf(false) }
            val selected = state.activePresetId == p.id
            val density = LocalDensity.current
            val curvePath = remember(p.bandGainsDb) { Path() }
            val curveStroke = remember(density) { with(density) { Stroke(2.dp.toPx()) } }
            ConsolePanel(Modifier.testTag("preset_card_${p.id}").clickable { vm.selectPreset(p) }) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(GlassTokens.Space12)
                ) {
                    Canvas(Modifier.size(56.dp, 36.dp)) {
                        curvePath.rewind()
                        p.bandGainsDb.forEachIndexed { i, gain ->
                            val x = size.width * i / 19f
                            val y = size.height * (15f - gain) / 30f
                            if (i == 0) curvePath.moveTo(x, y) else curvePath.lineTo(x, y)
                        }
                        drawPath(
                            curvePath,
                            if (selected) palette.accent else palette.textMuted,
                            style = curveStroke
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.titleSmall, color = palette.textPrimary)
                        Text(
                            if (selected) "Dipilih${if (state.presetDirty) ", diubah" else ""}" else p.category,
                            color = if (selected) palette.accent else palette.textMuted,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    IconButton({ vm.toggleFavorite(p.id) }) {
                        Icon(
                            if (p.id in state.favoriteIds) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            if (p.id in state.favoriteIds) "Hapus favorit ${p.name}" else "Favoritkan ${p.name}",
                            tint = palette.accent
                        )
                    }
                    Box {
                        IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, "Pilihan ${p.name}") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text("Duplikat") }, { menu = false; vm.duplicatePreset(p) })
                            if (p.isCustom) {
                                DropdownMenuItem({ Text("Ganti nama") }, { menu = false; renaming = p })
                                DropdownMenuItem({ Text("Hapus", color = palette.statusDanger) }, { menu = false; deleting = p })
                            }
                        }
                    }
                }
                Text(p.description, color = palette.textSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    deleting?.let { p ->
        GlassDialog(
            title = "Hapus ${p.name}?",
            onDismissRequest = { deleting = null },
            confirmButton = {
                TextButton({ vm.deleteCustomPreset(p.id); deleting = null }) {
                    Text("Hapus", color = palette.statusDanger)
                }
            },
            dismissButton = {
                TextButton({ deleting = null }) { Text("Batal") }
            }
        ) {
            Text(
                "Preset dihapus dari pustaka. Suara yang sedang diedit tetap dipertahankan.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary
            )
        }
    }
    renaming?.let { p ->
        NameDialog("Ganti nama preset", p.name, { renaming = null }, { vm.renamePreset(p.id, it) })
    }
}
