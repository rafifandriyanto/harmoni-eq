package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.*
import com.example.ui.EqualizerViewModel
import com.example.ui.components.*
import com.example.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PresetsScreen(state: EqualizerUiState, vm: EqualizerViewModel, onSave: () -> Unit,
    onImport: () -> Unit, onExport: () -> Unit, modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Semua") }
    var favoritesOnly by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<EqPreset?>(null) }
    var renaming by remember { mutableStateOf<EqPreset?>(null) }
    val visible = state.presets.filter { p ->
        (category == "Semua" || p.category == category) && (!favoritesOnly || p.id in state.favoriteIds) &&
            (p.name.contains(query, true) || p.description.contains(query, true))
    }
    LazyColumn(modifier.fillMaxSize().testTag("preset_list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionHeading("Temukan karakter suaramu.", "Preset lengkap: kanal kiri/kanan, efek, dan headroom.")
            if (state.libraryTruncated) Text("Pustaka tersimpan melebihi 200 preset kustom; hanya 200 dipulihkan dan preset sesi ini didahulukan. Sisanya tidak hilang dari berkas lama—ekspor rutin untuk cadangan.",
                color = BrassCaution, style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onSave) { Text("Simpan suara") }
                TextButton(onImport) { Text("Impor JSON") }
                TextButton(onExport, enabled = state.libraryRestored && state.presets.any { it.isCustom }) { Text("Ekspor kustom") }
            }
        }
        item {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().testTag("preset_search"),
                label = { Text("Cari preset") }, singleLine = true, leadingIcon = { Icon(Icons.Rounded.Search, null) })
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("preset_categories")) {
                items(listOf("Semua", "Studio", "Musik", "Vokal", "Headphone", "Speaker", "Gaming", "Kustom")) { c ->
                    FilterChip(category == c, { category = c }, { Text(c) }, Modifier.testTag("preset_category_${c.lowercase()}"))
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(favoritesOnly, { favoritesOnly = !favoritesOnly }, { Text("Favorit") }, leadingIcon = { Icon(Icons.Rounded.Star, null, Modifier.size(18.dp)) })
                Spacer(Modifier.weight(1f))
                Text("${visible.size} preset", color = BoneMuted, style = MaterialTheme.typography.labelMedium)
            }
        }
        if (visible.isEmpty()) item {
            ConsolePanel {
                SectionHeading("Belum ada yang cocok", "Coba kata lain, tandai favorit, atau simpan pengaturanmu sendiri.")
                TextButton({ query = ""; category = "Semua"; favoritesOnly = false }) { Text("Reset filter") }
            }
        }
        items(visible, key = { it.id }) { p ->
            var menu by remember { mutableStateOf(false) }
            val selected = state.activePresetId == p.id
            ConsolePanel(Modifier.testTag("preset_card_${p.id}").clickable { vm.selectPreset(p) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Canvas(Modifier.size(54.dp, 36.dp)) {
                        val path = Path()
                        p.bandGainsDb.forEachIndexed { i, gain ->
                            val x = size.width * i / 19
                            val y = size.height * (15f - gain) / 30f
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(path, if (selected) SageSignal else SteelChannel, style = Stroke(2.dp.toPx()))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.titleSmall)
                        Text(if (selected) "Dipilih${if (state.presetDirty) " · diubah" else ""}" else p.category,
                            color = if (selected) SageSignal else BoneMuted, style = MaterialTheme.typography.labelMedium)
                    }
                    IconButton({ vm.toggleFavorite(p.id) }) {
                        Icon(if (p.id in state.favoriteIds) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            if (p.id in state.favoriteIds) "Hapus favorit ${p.name}" else "Favoritkan ${p.name}", tint = BrassCaution)
                    }
                    Box {
                        IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, "Pilihan ${p.name}") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text("Duplikat") }, { menu = false; vm.duplicatePreset(p) })
                            if (p.isCustom) {
                                DropdownMenuItem({ Text("Ganti nama") }, { menu = false; renaming = p })
                                DropdownMenuItem({ Text("Hapus", color = MadderAlert) }, { menu = false; deleting = p })
                            }
                        }
                    }
                }
                Text(p.description, color = BoneSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    deleting?.let { p -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Hapus ${p.name}?") },
        text = { Text("Preset dihapus dari pustaka. Suara yang sedang diedit tetap dipertahankan.") },
        confirmButton = { TextButton({ vm.deleteCustomPreset(p.id); deleting = null }) { Text("Hapus", color = MadderAlert) } },
        dismissButton = { TextButton({ deleting = null }) { Text("Batal") } }) }
    renaming?.let { p -> NameDialog("Ganti nama preset", p.name, { renaming = null }, { vm.renamePreset(p.id, it) }) }
}
