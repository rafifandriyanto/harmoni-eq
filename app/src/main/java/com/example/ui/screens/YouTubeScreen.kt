package com.example.ui.screens

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.model.*
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun YouTubeScreen(uiState: EqualizerUiState, isActive: Boolean, onSelectPreset: (EqPreset) -> Unit,
    onToggleMasterPower: () -> Unit, onOpenEqualizerTab: () -> Unit,
    onOpenExternalYouTube: (String) -> Unit, modifier: Modifier = Modifier) {
    var web by remember { mutableStateOf<WebView?>(null) }
    var input by rememberSaveable { mutableStateOf("") }
    var url by remember { mutableStateOf(YouTubeNavigation.HOME) }
    var showSearch by remember { mutableStateOf(false) }
    var showPresets by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var inputError by remember { mutableStateOf<String?>(null) }
    var canBack by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf<View?>(null) }
    var fullscreenCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    val external by rememberUpdatedState(onOpenExternalYouTube)
    fun closeFullscreen() { fullscreenCallback?.onCustomViewHidden(); fullscreenCallback = null; fullscreen = null }
    BackHandler(isActive && (fullscreen != null || canBack)) {
        if (fullscreen != null) closeFullscreen() else web?.goBack()
    }
    DisposableEffect(Unit) { onDispose {
        web?.let { it.stopLoading(); it.webChromeClient = null; it.removeAllViews(); it.destroy() }
    } }
    LaunchedEffect(isActive) {
        if (isActive) web?.onResume() else { closeFullscreen(); web?.onPause() }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> web?.onPause()
                Lifecycle.Event.ON_RESUME -> if (isActive) web?.onResume() else web?.onPause()
                Lifecycle.Event.ON_DESTROY -> web?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Box(modifier.fillMaxSize().background(GraphiteBase)
        .then(if (isActive) Modifier else Modifier.semantics { invisibleToUser() })) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ web?.goBack() }, enabled = canBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Halaman sebelumnya") }
                Text("YouTube", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                IconButton({ showSearch = !showSearch }, Modifier.testTag("youtube_show_search")) { Icon(Icons.Rounded.Search, "Cari atau buka tautan") }
                Box {
                    IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, "Pilihan browser") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("Muat ulang") }, { menu = false; error = null; web?.reload() })
                        DropdownMenuItem({ Text("Beranda YouTube") }, { menu = false; error = null; web?.loadUrl(YouTubeNavigation.HOME) })
                        DropdownMenuItem({ Text("Maju") }, { menu = false; web?.goForward() }, enabled = web?.canGoForward() == true)
                        DropdownMenuItem({ Text("Buka di aplikasi / browser") }, { menu = false; external(url) })
                    }
                }
            }
            if (showSearch) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(input, { input = it; inputError = null }, Modifier.weight(1f).testTag("youtube_search_input"),
                    label = { Text("Kata kunci / tautan YouTube") }, singleLine = true, isError = inputError != null,
                    supportingText = inputError?.let { message -> { Text(message) } })
                TextButton({
                    YouTubeNavigation.resolve(input).onSuccess { target -> error = null; web?.loadUrl(target); showSearch = false }
                        .onFailure { inputError = it.message ?: "Tautan tidak valid." }
                }) { Text(if (input.contains("youtube.com") || input.contains("youtu.be")) "Buka" else "Cari") }
            }
            if (progress < 100 && error == null) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
            Box(Modifier.weight(1f).fillMaxWidth().testTag("youtube_webview_container")) {
                AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
                    WebView(context).apply {
                        setBackgroundColor(android.graphics.Color.rgb(16, 19, 24))
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val target = request?.url?.toString().orEmpty()
                                if (YouTubeNavigation.trustedNavigation(target)) return false
                                if (request?.isForMainFrame == true && request.hasGesture() && request.url.scheme == "https") external(target)
                                return true
                            }
                            override fun onPageStarted(view: WebView?, next: String?, favicon: Bitmap?) {
                                url = next ?: url; error = null; progress = 0
                            }
                            override fun onPageFinished(view: WebView?, next: String?) {
                                url = next ?: url; canBack = view?.canGoBack() == true; progress = 100
                            }
                            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, failure: WebResourceError?) {
                                if (request?.isForMainFrame == true) error = "Halaman tidak dapat dimuat. Periksa internet atau buka YouTube di luar aplikasi."
                            }
                            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, response: WebResourceResponse?) {
                                if (request?.isForMainFrame == true) error = "YouTube menolak pemuatan halaman (${response?.statusCode}). Coba browser resmi."
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) { progress = newProgress }
                            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                if (fullscreen != null) { callback?.onCustomViewHidden(); return }
                                fullscreen = view; fullscreenCallback = callback
                            }
                            override fun onHideCustomView() { fullscreen = null; fullscreenCallback = null }
                        }
                        web = this
                        // Komposisi pertama bisa terjadi saat tab tidak aktif
                        // (mis. proses dipulihkan): langsung pause, jangan andalkan
                        // LaunchedEffect yang sudah jalan sebelum web ada.
                        if (!isActive) onPause()
                        loadUrl(YouTubeNavigation.HOME)
                    }
                }, update = {
                    it.visibility = if (isActive) View.VISIBLE else View.INVISIBLE
                    it.importantForAccessibility = if (isActive) View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                        else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                })
                error?.let { message ->
                    Surface(Modifier.fillMaxSize(), color = GraphitePanel) {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.Center) {
                            Text(message, style = MaterialTheme.typography.bodyLarge)
                            Button({ error = null; web?.reload() }) { Text("Coba lagi") }
                            TextButton({ external(url) }) { Text("Buka di luar aplikasi") }
                        }
                    }
                }
            }
            Surface(color = GraphitePanel) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(uiState.presets.find { it.id == uiState.activePresetId }?.name ?: "Kurva kustom", style = MaterialTheme.typography.labelLarge)
                            Text(if (!uiState.eqEnabled) "Bypass" else if (uiState.hardwareAttached) "Efek terpasang • uji dengan bypass" else "Efek belum terpasang",
                                color = BoneMuted, style = MaterialTheme.typography.labelMedium)
                        }
                        IconButton(onToggleMasterPower, Modifier.testTag("youtube_quick_eq_toggle")) { Icon(Icons.Rounded.PowerSettingsNew, "Ganti bypass") }
                        TextButton(onOpenEqualizerTab, Modifier.testTag("youtube_open_eq_button")) { Text("Edit EQ") }
                        IconButton({ showPresets = true }) { Icon(Icons.Rounded.Tune, "Pilih preset") }
                    }
                    Text("Pemutaran mengikuti kebijakan YouTube; lintas-tab/latar belakang tidak dijamin.", style = MaterialTheme.typography.bodySmall, color = BoneMuted)
                }
            }
        }
        if (fullscreen != null && isActive) AndroidView(modifier = Modifier.fillMaxSize().background(GraphiteBase), factory = { context ->
            FrameLayout(context).apply {
                fullscreen?.let { (it.parent as? ViewGroup)?.removeView(it); addView(it, ViewGroup.LayoutParams(-1, -1)) }
            }
        })
    }
    if (showPresets) ModalBottomSheet(onDismissRequest = { showPresets = false }) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp), contentPadding = PaddingValues(16.dp)) {
            items(uiState.presets, key = { it.id }) { preset -> TextButton({ onSelectPreset(preset); showPresets = false }, Modifier.fillMaxWidth()) { Text(preset.name) } }
        }
    }
}
