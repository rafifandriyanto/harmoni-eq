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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.AudioSourceMode
import com.example.model.ConsoleTab
import com.example.ui.components.AutoEqBottomSheet
import com.example.ui.components.NameDialog
import com.example.ui.components.OutputSettingsSheet
import com.example.ui.components.glass.GlassDialog
import com.example.ui.components.glass.GlassTopBar
import com.example.ui.components.glass.StaticRadialBackdrop
import com.example.ui.screens.EffectsScreen
import com.example.ui.screens.EqualizerScreen
import com.example.ui.screens.PresetsScreen
import com.example.ui.screens.ReliabilityScreen
import com.example.ui.screens.YouTubeScreen
import com.example.ui.theme.GlassTokens
import com.example.ui.theme.LocalGlassBlurRadius
import com.example.ui.theme.LocalGlassBlurSuppressed
import com.example.ui.theme.LocalHarmoniPalette
import com.example.ui.theme.LocalThemeController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HarmoniEqApp(viewModel: EqualizerViewModel) {
    val context = LocalContext.current
    val palette = LocalHarmoniPalette.current
    val reduceMotion = LocalThemeController.current.effectiveReduceMotion
    val haptics = LocalHapticFeedback.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var tab by rememberSaveable { mutableStateOf(ConsoleTab.EQUALIZER) }
    var openedYouTube by rememberSaveable { mutableStateOf(false) }
    var save by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf(false) }
    var autoEqOpen by remember { mutableStateOf(false) }
    var reliabilityOpen by rememberSaveable { mutableStateOf(false) }
    var help by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf<String?>(null) }
    var exportText by remember { mutableStateOf<String?>(null) }
    var ioBusy by remember { mutableStateOf(false) }

    var isScrolledUnderTopBar by remember { mutableStateOf(false) }
    var isDraggingOrScrolling by remember { mutableStateOf(false) }
    var consoleRevealed by remember { mutableStateOf(reduceMotion) }

    LaunchedEffect(Unit) {
        consoleRevealed = true
    }

    val consoleEntranceAlphaState = animateFloatAsState(
        targetValue = if (consoleRevealed) 1f else 0.94f,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 180),
        label = "console_entrance_alpha"
    )

    val lifecycleOwner = LocalLifecycleOwner.current
    val reliabilityManager = remember(context) { ReliabilityManager(context) }
    val reliabilitySetupCompleted by reliabilityManager.setupCompletedFlow.collectAsStateWithLifecycle(initialValue = false)
    var reliabilityStatus by remember { mutableStateOf(reliabilityManager.inspectStatus()) }

    LaunchedEffect(state.backgroundServiceActive, devices, reliabilityOpen) {
        reliabilityStatus = reliabilityManager.inspectStatus()
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                reliabilityStatus = reliabilityManager.inspectStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val overlayOpen = save || devices || autoEqOpen || reliabilityOpen || help || importText != null
    val autoEqViewModel: AutoEqViewModel = viewModel()

    // State D synchronization: if user manually edits a fader or preset, keep AutoEqViewModel detached
    LaunchedEffect(state.activeAutoEqProfile) {
        if (state.activeAutoEqProfile == null) {
            val currentAutoEq = autoEqViewModel.uiState.value as? AutoEqUiState.Success
            if (currentAutoEq?.selectedProfile != null) {
                autoEqViewModel.detachAutoEq()
            }
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.toggleBackgroundService()
        if (!granted) viewModel.reportMessage("Izin notifikasi tidak diberikan. Status layanan tetap bisa diperiksa di Output.")
    }
    val toggleService: () -> Unit = {
        if (!state.backgroundServiceActive && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.toggleBackgroundService()
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            ioBusy = true
            runCatching {
                withContext(Dispatchers.IO) {
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
                }
            }.onSuccess { importText = it }.onFailure { viewModel.reportMessage(it.message ?: "Impor gagal.") }
            ioBusy = false
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val text = exportText
        exportText = null
        if (uri != null && text != null) scope.launch {
            ioBusy = true
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                        ?: error("Tujuan tidak dapat ditulis.")
                }
            }.onSuccess { viewModel.reportMessage("Preset kustom berhasil diekspor.") }
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
        isScrolledUnderTopBar = false
        if (next == ConsoleTab.YOUTUBE) {
            openedYouTube = true
            viewModel.switchAudioSourceMode(AudioSourceMode.GLOBAL_SYSTEM)
        }
    }

    BackHandler(tab != ConsoleTab.EQUALIZER) { select(ConsoleTab.EQUALIZER) }

    val tabs = listOf(ConsoleTab.EQUALIZER, ConsoleTab.YOUTUBE, ConsoleTab.EFFECTS, ConsoleTab.PRESETS)
    fun label(t: ConsoleTab) = when (t) {
        ConsoleTab.EQUALIZER -> "Equalizer"
        ConsoleTab.YOUTUBE -> "YouTube"
        ConsoleTab.EFFECTS -> "Efek"
        ConsoleTab.PRESETS -> "Preset"
    }
    fun icon(t: ConsoleTab) = when (t) {
        ConsoleTab.EQUALIZER -> Icons.Rounded.GraphicEq
        ConsoleTab.YOUTUBE -> Icons.Rounded.PlayCircle
        ConsoleTab.EFFECTS -> Icons.Rounded.Tune
        ConsoleTab.PRESETS -> Icons.Rounded.Bookmark
    }

    val eq: @Composable (Modifier) -> Unit = { modifier ->
        EqualizerScreen(
            state = state,
            vm = viewModel,
            onPresets = { select(ConsoleTab.PRESETS) },
            onYouTube = { select(ConsoleTab.YOUTUBE) },
            onDevices = { devices = true },
            onAutoEq = { autoEqOpen = true },
            onSave = { save = true },
            onInteractionChange = { isDraggingOrScrolling = it },
            onScrolledUnderChange = { isScrolledUnderTopBar = it },
            modifier = modifier
        )
    }

    val content: @Composable (ConsoleTab, Modifier) -> Unit = { current, modifier ->
        when (current) {
            ConsoleTab.EQUALIZER -> eq(modifier)
            ConsoleTab.EFFECTS -> EffectsScreen(
                state = state,
                vm = viewModel,
                onScrolledUnderChange = { isScrolledUnderTopBar = it },
                modifier = modifier
            )
            ConsoleTab.PRESETS -> PresetsScreen(
                state = state,
                vm = viewModel,
                onSave = { save = true },
                onImport = { if (!ioBusy) importer.launch(arrayOf("application/json", "text/plain")) },
                onExport = {
                    if (!ioBusy) {
                        exportText = viewModel.exportPresets()
                        exporter.launch("harmoni-presets.json")
                    }
                },
                onScrolledUnderChange = { isScrolledUnderTopBar = it },
                modifier = modifier
            )
            ConsoleTab.YOUTUBE -> Unit
        }
    }

    val targetGlassBlurRadius by remember {
        derivedStateOf {
            if (isDraggingOrScrolling || overlayOpen) {
                GlassTokens.DragBlurRadius
            } else {
                GlassTokens.IdleBlurRadius
            }
        }
    }
    val animatedGlassBlurRadius by animateDpAsState(
        targetValue = targetGlassBlurRadius,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else GlassTokens.BlurTransitionDurationMs),
        label = "dynamic_glass_blur_radius"
    )

    CompositionLocalProvider(
        LocalGlassBlurSuppressed provides (overlayOpen || isDraggingOrScrolling),
        LocalGlassBlurRadius provides animatedGlassBlurRadius
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(palette.canvas)
                .graphicsLayer { alpha = consoleEntranceAlphaState.value }
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val rail = maxWidth >= 600.dp
                val largeText = LocalDensity.current.fontScale >= 1.5f
                val compactHeader = largeText || maxWidth < 360.dp
                val twoPane = maxWidth >= 1000.dp && tab != ConsoleTab.EQUALIZER && tab != ConsoleTab.YOUTUBE

                Scaffold(
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = { SnackbarHost(snackbar) },
                    topBar = {
                        GlassTopBar(
                            title = "HARMONI",
                            subtitle = "Equalizer 20-band",
                            isScrolledUnder = isScrolledUnderTopBar && !overlayOpen,
                            compact = compactHeader,
                            eqActive = state.eqEnabled,
                            autoEqActive = state.activeAutoEqProfile != null
                        ) {
                            IconButton(
                                onClick = { devices = true },
                                modifier = Modifier.testTag("theme_settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Settings,
                                    contentDescription = "Pengaturan",
                                    tint = palette.textSecondary
                                )
                            }
                            TextButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.toggleMasterPower()
                                },
                                modifier = Modifier.testTag("master_power_button")
                            ) {
                                Icon(
                                    Icons.Rounded.PowerSettingsNew,
                                    null,
                                    Modifier.size(20.dp),
                                    tint = if (state.eqEnabled) palette.accent else palette.textMuted
                                )
                                Spacer(Modifier.width(GlassTokens.Space4))
                                Text(
                                    if (state.eqEnabled) "EQ ON" else "BYPASS",
                                    color = if (state.eqEnabled) palette.accent else palette.textMuted,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    },
                    bottomBar = {
                        if (!rail && largeText) {
                            Surface(color = palette.nearSolidChrome.copy(alpha = GlassTokens.NearSolidAlpha)) {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .windowInsetsPadding(WindowInsets.navigationBars)
                                        .padding(GlassTokens.Space4)
                                ) {
                                    tabs.chunked(2).forEach { pair ->
                                        Row(Modifier.fillMaxWidth()) {
                                            pair.forEach { t ->
                                                Row(
                                                    Modifier
                                                        .weight(1f)
                                                        .heightIn(min = 56.dp)
                                                        .testTag("nav_${t.name.lowercase()}")
                                                        .selectable(selected = tab == t, role = Role.Tab, onClick = { select(t) })
                                                        .padding(GlassTokens.Space8),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(GlassTokens.Space8)
                                                ) {
                                                    Icon(
                                                        icon(t),
                                                        null,
                                                        Modifier.size(24.dp),
                                                        tint = if (tab == t) palette.accent else palette.textSecondary
                                                    )
                                                    Text(
                                                        label(t),
                                                        Modifier.weight(1f),
                                                        style = MaterialTheme.typography.labelLarge,
                                                        color = if (tab == t) palette.accent else palette.textSecondary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (!rail) {
                            com.example.ui.components.glass.GlassFloatingNavigationBar(
                                items = tabs,
                                selectedItem = tab,
                                onItemSelected = { select(it) },
                                itemLabel = { label(it) },
                                itemIcon = { icon(it) },
                                itemTag = { "nav_${it.name.lowercase()}" },
                                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                            )
                        }
                    }
                ) { padding ->
                    Row(Modifier.fillMaxSize().padding(padding)) {
                        if (rail) {
                            NavigationRail(
                                containerColor = palette.nearSolidChrome.copy(alpha = GlassTokens.NearSolidAlpha),
                                modifier = Modifier.fillMaxHeight()
                            ) {
                                tabs.forEach { t ->
                                    NavigationRailItem(
                                        selected = tab == t,
                                        onClick = { select(t) },
                                        icon = { Icon(icon(t), null) },
                                        modifier = Modifier.testTag("nav_${t.name.lowercase()}"),
                                        label = { Text(label(t)) }
                                    )
                                }
                            }
                        }
                        if (twoPane) {
                            eq(Modifier.weight(1f))
                            VerticalDivider(color = palette.borderSubtle)
                        }
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            if (openedYouTube) {
                                YouTubeScreen(
                                    uiState = state,
                                    isActive = tab == ConsoleTab.YOUTUBE,
                                    onSelectPreset = viewModel::selectPreset,
                                    onToggleMasterPower = viewModel::toggleMasterPower,
                                    onOpenEqualizerTab = { select(ConsoleTab.EQUALIZER) },
                                    onOpenExternalYouTube = { url ->
                                        if (Uri.parse(url).scheme == "https") {
                                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                                .onFailure { viewModel.reportMessage("Tidak ada aplikasi yang bisa membuka tautan ini.") }
                                        }
                                    },
                                    modifier = if (tab == ConsoleTab.YOUTUBE) Modifier.fillMaxSize() else Modifier.size(0.dp)
                                )
                            }
                            if (tab != ConsoleTab.YOUTUBE) {
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    color = palette.canvas
                                ) {
                                    content(tab, Modifier.fillMaxSize())
                                }
                            }
                            if (ioBusy) {
                                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
                            }
                        }
                    }
                }
            }
        }
    }

    if (save) {
        NameDialog(
            title = "Simpan preset lengkap",
            onDismiss = { save = false },
            onSave = { viewModel.saveCustomPreset(it); save = false }
        )
    }
    if (devices) {
        OutputSettingsSheet(
            state = state,
            vm = viewModel,
            reliabilityStatus = reliabilityStatus,
            reliabilitySetupCompleted = reliabilitySetupCompleted,
            onOpenReliability = {
                devices = false
                reliabilityOpen = true
            },
            onOpenHelp = {
                devices = false
                help = true
            },
            onDismiss = { devices = false },
            onBluetoothSettings = {
                runCatching { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                    .onFailure { viewModel.reportMessage("Pengaturan Bluetooth tidak tersedia di perangkat ini.") }
            },
            onService = toggleService
        )
    }
    if (reliabilityOpen) {
        ReliabilityScreen(
            reliabilityManager = reliabilityManager,
            onBack = { reliabilityOpen = false },
            onReportMessage = { viewModel.reportMessage(it) }
        )
    }
    if (autoEqOpen) {
        AutoEqBottomSheet(
            autoEqViewModel = autoEqViewModel,
            onDismiss = { autoEqOpen = false },
            onApplyProfile = { profile, gainsArray ->
                // AutoEqViewModel.selectProfile already ramped StudioAudioEngine smoothly over 100ms
                viewModel.applyAutoEqProfile(profile, gainsArray.toList(), sendToEngine = false)
            },
            onDisableAutoEq = {
                viewModel.disableAutoEq()
            },
            onShowMessage = { message ->
                viewModel.reportMessage(message)
            }
        )
    }
    importText?.let { json ->
        fun apply(copy: Boolean) {
            viewModel.importPresets(json, copy)
                .onSuccess { viewModel.reportMessage("$it preset diimpor. Preset lama tidak ditimpa.") }
                .onFailure { viewModel.reportMessage(it.message ?: "Format preset tidak valid.") }
            importText = null
        }
        GlassDialog(
            title = "Impor preset",
            onDismissRequest = { importText = null },
            confirmButton = { TextButton({ apply(false) }) { Text("Lewati duplikat") } },
            dismissButton = {
                Row {
                    TextButton({ importText = null }) { Text("Batal") }
                    TextButton({ apply(true) }) { Text("Buat salinan") }
                }
            }
        ) {
            Text(
                "Jika ID preset sudah ada, lewati duplikat atau buat salinan baru. Seluruh berkas diperiksa sebelum koleksi berubah.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary
            )
        }
    }
    if (help) {
        GlassDialog(
            title = "Mulai & cek suara",
            onDismissRequest = { help = false },
            confirmButton = { TextButton({ help = false }) { Text("Mengerti") } }
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(GlassTokens.Space12)
            ) {
                Text("1. Pasangkan perangkat lewat Bluetooth Android, lalu pilih output media di Android.", color = palette.textSecondary)
                Text("2. Mulai musik dengan volume nyaman. Di Equalizer, buka Kelola untuk melihat rute, tema, dan kemampuan efek.", color = palette.textSecondary)
                Text("3. Coba preset, lalu bandingkan EQ ON dan BYPASS. EQ ON adalah permintaan pemrosesan, bukan bukti efek terdengar.", color = palette.textSecondary)
                Text("4. Simpan preset dan hubungkan ke perangkat. Profil otomatis opsional dan tidak pernah menaikkan volume sistem.", color = palette.textSecondary)
                Text("Tidak terdengar? Aktifkan layanan, coba pemutar lain, atau Audio uji. Sebagian aplikasi dan offload Bluetooth tidak mengizinkan efek global.", color = palette.textMuted)
                Text("Kurva adalah pengaturan, bukan spektrum terukur. Meter hanya tersedia untuk audio uji.", color = palette.textMuted)
            }
        }
    }
}
