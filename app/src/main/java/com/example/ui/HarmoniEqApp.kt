package com.example.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.*
import com.example.ui.components.*
import com.example.ui.screens.*
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HarmoniEqApp(viewModel: EqualizerViewModel) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableStateOf(ConsoleTab.EQUALIZER) }
    var openedYouTube by rememberSaveable { mutableStateOf(false) }
    var save by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf(false) }
    var help by remember { mutableStateOf(false) }
    var importText by rememberSaveable { mutableStateOf<String?>(null) }
    var exportText by rememberSaveable { mutableStateOf<String?>(null) }
    var ioBusy by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.toggleBackgroundService()
        if (!granted) viewModel.reportMessage("Izin notifikasi tidak diberikan. Status layanan tetap bisa diperiksa di Output.")
    }
    val toggleService: () -> Unit = {
        if (!state.backgroundServiceActive && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else viewModel.toggleBackgroundService()
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            ioBusy = true
            runCatching { withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val out = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        require(out.size() + count <= 1_000_000) { "Berkas maksimal 1 MB." }
                        out.write(buffer, 0, count)
                    }
                    out.toByteArray().toString(Charsets.UTF_8)
                } ?: error("Berkas tidak dapat dibuka.")
            } }.onSuccess { importText = it }.onFailure { viewModel.reportMessage(it.message ?: "Impor gagal.") }
            ioBusy = false
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val text = exportText
        exportText = null
        if (uri != null && text != null) scope.launch {
            ioBusy = true
            runCatching { withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                    ?: error("Tujuan tidak dapat ditulis.")
            } }.onSuccess { viewModel.reportMessage("Preset kustom berhasil diekspor.") }
                .onFailure { viewModel.reportMessage(it.message ?: "Ekspor gagal.") }
            ioBusy = false
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.reportMessage(null) }
    }
    fun select(next: ConsoleTab) {
        viewModel.endGesture()
        tab = next
        if (next == ConsoleTab.YOUTUBE) {
            openedYouTube = true
            viewModel.switchAudioSourceMode(AudioSourceMode.GLOBAL_SYSTEM)
        }
    }
    BackHandler(tab != ConsoleTab.EQUALIZER) { select(ConsoleTab.EQUALIZER) }
    val tabs = listOf(ConsoleTab.EQUALIZER, ConsoleTab.YOUTUBE, ConsoleTab.EFFECTS, ConsoleTab.PRESETS)
    fun label(t: ConsoleTab) = when (t) { ConsoleTab.EQUALIZER -> "Equalizer"; ConsoleTab.YOUTUBE -> "YouTube"; ConsoleTab.EFFECTS -> "Efek"; ConsoleTab.PRESETS -> "Preset" }
    fun icon(t: ConsoleTab) = when (t) { ConsoleTab.EQUALIZER -> Icons.Rounded.GraphicEq; ConsoleTab.YOUTUBE -> Icons.Rounded.PlayCircle; ConsoleTab.EFFECTS -> Icons.Rounded.Tune; ConsoleTab.PRESETS -> Icons.Rounded.Bookmark }
    val eq: @Composable (Modifier) -> Unit = { modifier ->
        EqualizerScreen(state, viewModel, { select(ConsoleTab.PRESETS) }, { select(ConsoleTab.YOUTUBE) }, { devices = true }, { save = true }, modifier)
    }
    val content: @Composable (ConsoleTab, Modifier) -> Unit = { current, modifier ->
        when (current) {
            ConsoleTab.EQUALIZER -> eq(modifier)
            ConsoleTab.EFFECTS -> EffectsScreen(state, viewModel, modifier)
            ConsoleTab.PRESETS -> PresetsScreen(state, viewModel, { save = true },
                { if (!ioBusy && state.libraryRestored) importer.launch(arrayOf("application/json", "text/plain")) },
                { if (!ioBusy && state.libraryRestored) { exportText = viewModel.exportPresets(); exporter.launch("harmoni-presets.json") } }, modifier)
            ConsoleTab.YOUTUBE -> Unit
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rail = maxWidth >= 600.dp
        val largeText = LocalDensity.current.fontScale >= 1.5f
        val compactHeader = largeText || maxWidth < 360.dp
        val powerActions: @Composable () -> Unit = {
            IconButton({ help = true }) { Icon(Icons.Rounded.HelpOutline, "Panduan & bantuan") }
            TextButton(viewModel::toggleMasterPower, Modifier.testTag("master_power_button")) {
                Icon(Icons.Rounded.PowerSettingsNew, null, Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (state.eqEnabled) "EQ ON" else "BYPASS")
            }
        }
        val twoPane = maxWidth >= 1000.dp && tab != ConsoleTab.EQUALIZER && tab != ConsoleTab.YOUTUBE
        Scaffold(containerColor = GraphiteBase, contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                Surface(color = GraphiteDeck) {
                    if (compactHeader) Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 16.dp)) {
                        Text("HARMONI", Modifier.fillMaxWidth().padding(top = 8.dp), style = MaterialTheme.typography.titleLarge)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) { powerActions() }
                    } else Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                            Text("HARMONI", style = MaterialTheme.typography.titleLarge)
                            Text("Studio equalizer", style = MaterialTheme.typography.labelMedium, color = BoneMuted)
                        }
                        powerActions()
                    }
                }
            }, bottomBar = {
                if (!rail && largeText) Surface(color = GraphiteDeck) {
                    Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(4.dp)) {
                        tabs.chunked(2).forEach { pair ->
                            Row(Modifier.fillMaxWidth()) {
                                pair.forEach { t ->
                                    Row(Modifier.weight(1f).heightIn(min = 56.dp)
                                        .testTag("nav_${t.name.lowercase()}")
                                        .selectable(selected = tab == t, role = Role.Tab, onClick = { select(t) })
                                        .padding(8.dp), verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(icon(t), null, Modifier.size(24.dp), tint = if (tab == t) SageSignal else BoneSecondary)
                                        Text(label(t), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                                            color = if (tab == t) SageSignal else BoneSecondary)
                                    }
                                }
                            }
                        }
                    }
                } else if (!rail) NavigationBar(containerColor = GraphiteDeck) {
                    tabs.forEach { t -> NavigationBarItem(tab == t, { select(t) }, { Icon(icon(t), null) },
                        modifier = Modifier.testTag("nav_${t.name.lowercase()}"), label = { Text(label(t)) }) }
                }
            }) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                if (rail) NavigationRail(containerColor = GraphiteDeck, modifier = Modifier.fillMaxHeight()) {
                    tabs.forEach { t -> NavigationRailItem(tab == t, { select(t) }, { Icon(icon(t), null) },
                        modifier = Modifier.testTag("nav_${t.name.lowercase()}"), label = { Text(label(t)) }) }
                }
                if (twoPane) { eq(Modifier.weight(1f)); VerticalDivider() }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    if (openedYouTube) YouTubeScreen(state, tab == ConsoleTab.YOUTUBE, viewModel::selectPreset,
                        viewModel::toggleMasterPower, { select(ConsoleTab.EQUALIZER) }, { url ->
                            if (Uri.parse(url).scheme == "https") runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                .onFailure { viewModel.reportMessage("Tidak ada aplikasi yang bisa membuka tautan ini.") }
                        })
                    if (tab != ConsoleTab.YOUTUBE) Surface(Modifier.fillMaxSize(), color = GraphiteBase) { content(tab, Modifier.fillMaxSize()) }
                    if (ioBusy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
                }
            }
        }
    }
    if (save) NameDialog("Simpan preset lengkap", onDismiss = { save = false }, onSave = { viewModel.saveCustomPreset(it); save = false })
    if (devices) OutputSettingsSheet(state, viewModel, { devices = false }, {
        runCatching { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
            .onFailure { viewModel.reportMessage("Pengaturan Bluetooth tidak tersedia di perangkat ini.") }
    }, toggleService)
    importText?.let { json ->
        fun apply(copy: Boolean) {
            viewModel.importPresets(json, copy).onSuccess { viewModel.reportMessage("$it preset diimpor. Preset lama tidak ditimpa.") }
                .onFailure { viewModel.reportMessage(it.message ?: "Format preset tidak valid.") }
            importText = null
        }
        AlertDialog(onDismissRequest = { importText = null }, title = { Text("Impor preset") },
            text = { Text("Jika ID preset sudah ada, lewati duplikat atau buat salinan baru. Seluruh berkas diperiksa sebelum koleksi berubah.") },
            confirmButton = { TextButton({ apply(false) }) { Text("Lewati duplikat") } },
            dismissButton = { Row { TextButton({ importText = null }) { Text("Batal") }; TextButton({ apply(true) }) { Text("Buat salinan") } } })
    }
    if (help) AlertDialog(onDismissRequest = { help = false }, title = { Text("Mulai & cek suara") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("1. Pasangkan perangkat lewat Bluetooth Android, lalu pilih output media di Android.")
            Text("2. Mulai musik dengan volume nyaman. Di Equalizer, buka Kelola untuk melihat rute dan kemampuan efek.")
            Text("3. Coba preset, lalu bandingkan EQ ON dan BYPASS. EQ ON adalah permintaan pemrosesan, bukan bukti efek terdengar.")
            Text("4. Simpan preset dan hubungkan ke perangkat. Profil otomatis opsional; tidak pernah menaikkan volume sistem.")
            Text("Tidak terdengar? Aktifkan layanan, coba pemutar lain, atau Audio uji. Sebagian aplikasi, offload Bluetooth, dan perangkat tidak mengizinkan efek global. Tidak ada jaminan semua TWS/speaker/aplikasi kompatibel.")
            Text("Kurva adalah pengaturan, bukan spektrum terukur. Meter hanya tersedia untuk audio uji. Auto-headroom adalah perkiraan, bukan jaminan anti-clipping.")
        }
    }, confirmButton = { TextButton({ help = false }) { Text("Mengerti") } })
}
