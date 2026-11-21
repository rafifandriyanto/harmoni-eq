package com.example.ui.screens

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.EqPreset
import com.example.model.EqualizerUiState
import com.example.ui.components.glass.GlassBottomSheet
import com.example.ui.theme.GlassTokens
import com.example.ui.theme.LocalHarmoniPalette

@Composable
fun YouTubeScreen(
    uiState: EqualizerUiState,
    isActive: Boolean,
    onSelectPreset: (EqPreset) -> Unit,
    onToggleMasterPower: () -> Unit,
    onOpenEqualizerTab: () -> Unit,
    onOpenExternalYouTube: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalHarmoniPalette.current
    var web by remember { mutableStateOf<WebView?>(null) }
    var input by remember { mutableStateOf("") }
    var url by remember { mutableStateOf(YouTubeNavigation.HOME) }
    var showSearch by remember { mutableStateOf(false) }
    var showPresets by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var inputError by remember { mutableStateOf<String?>(null) }
    var canBack by remember { mutableStateOf(false) }
    var canForward by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf<View?>(null) }
    var fullscreenCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    val external by rememberUpdatedState(onOpenExternalYouTube)

    fun closeFullscreen() {
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
        fullscreen = null
    }

    BackHandler(isActive && (fullscreen != null || canBack)) {
        if (fullscreen != null) closeFullscreen() else web?.goBack()
    }
    DisposableEffect(Unit) {
        onDispose {
            web?.let { it.stopLoading(); it.webChromeClient = null; it.removeAllViews(); it.destroy() }
        }
    }
    LaunchedEffect(isActive) {
        if (isActive) web?.onResume() else { closeFullscreen(); web?.onPause() }
    }

    Box(
        if (isActive) modifier.fillMaxSize().background(palette.canvas)
        else modifier.size(0.dp)
    ) {
        Column(if (isActive) Modifier.fillMaxSize() else Modifier.size(0.dp)) {
            if (isActive) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = GlassTokens.Space8),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton({ web?.goBack() }, enabled = canBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Halaman sebelumnya")
                    }
                    Text("YouTube", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                    IconButton({ showSearch = !showSearch }, Modifier.testTag("youtube_show_search")) {
                        Icon(Icons.Rounded.Search, "Cari atau buka tautan")
                    }
                    Box {
                        IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, "Pilihan browser") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text("Muat ulang") }, { menu = false; error = null; web?.reload() })
                            DropdownMenuItem({ Text("Beranda YouTube") }, { menu = false; error = null; web?.loadUrl(YouTubeNavigation.HOME) })
                            DropdownMenuItem({ Text("Maju") }, { menu = false; web?.goForward() }, enabled = canForward)
                            DropdownMenuItem({ Text("Buka di aplikasi / browser") }, { menu = false; external(url) })
                        }
                    }
                }
                if (showSearch) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = GlassTokens.Space12),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it; inputError = null },
                            modifier = Modifier.weight(1f).testTag("youtube_search_input"),
                            label = { Text("Kata kunci / tautan YouTube") },
                            singleLine = true,
                            isError = inputError != null,
                            supportingText = inputError?.let { message -> { Text(message) } }
                        )
                        TextButton({
                            YouTubeNavigation.resolve(input)
                                .onSuccess { target -> error = null; web?.loadUrl(target); showSearch = false }
                                .onFailure { inputError = it.message ?: "Tautan tidak valid." }
                        }) { Text(if (input.contains("youtube.com") || input.contains("youtu.be")) "Buka" else "Cari") }
                    }
                }
                if (progress < 100 && error == null) {
                    LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                }
            }
            Box(
                if (isActive) Modifier.weight(1f).fillMaxWidth().testTag("youtube_webview_container")
                else Modifier.size(0.dp).testTag("youtube_webview_container")
            ) {
                AndroidView(
                    modifier = if (isActive) Modifier.fillMaxSize() else Modifier.size(0.dp),
                    factory = { context ->
                        WebView(context).apply {
                            setBackgroundColor(palette.canvas.toArgb())
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
                                    if (request?.isForMainFrame == true && request.hasGesture() && request.url.scheme == "https") {
                                        external(target)
                                    }
                                    return true
                                }
                                override fun onPageStarted(view: WebView?, next: String?, favicon: Bitmap?) {
                                    url = next ?: url
                                    error = null
                                    progress = 0
                                }
                                override fun onPageFinished(view: WebView?, next: String?) {
                                    url = next ?: url
                                    canBack = view?.canGoBack() == true
                                    canForward = view?.canGoForward() == true
                                    progress = 100
                                }
                                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, failure: WebResourceError?) {
                                    if (request?.isForMainFrame == true) {
                                        error = "Halaman tidak dapat dimuat. Periksa internet atau buka YouTube di luar aplikasi."
                                    }
                                }
                                override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, response: WebResourceResponse?) {
                                    if (request?.isForMainFrame == true) {
                                        error = "YouTube menolak pemuatan halaman (${response?.statusCode}). Coba browser resmi."
                                    }
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) { progress = newProgress }
                                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                    if (fullscreen != null) { callback?.onCustomViewHidden(); return }
                                    fullscreen = view
                                    fullscreenCallback = callback
                                }
                                override fun onHideCustomView() {
                                    fullscreen = null
                                    fullscreenCallback = null
                                }
                            }
                            web = this
                            loadUrl(YouTubeNavigation.HOME)
                        }
                    },
                    update = {
                        it.visibility = if (isActive) View.VISIBLE else View.INVISIBLE
                        it.importantForAccessibility = if (isActive) {
                            View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                        } else {
                            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                        }
                    }
                )
                if (isActive) {
                    error?.let { message ->
                        Surface(Modifier.fillMaxSize(), color = palette.surface) {
                            Column(Modifier.padding(GlassTokens.Space24), verticalArrangement = Arrangement.Center) {
                                Text(message, style = MaterialTheme.typography.bodyLarge, color = palette.textPrimary)
                                Button({ error = null; web?.reload() }) { Text("Coba lagi") }
                                TextButton({ external(url) }) { Text("Buka di luar aplikasi") }
                            }
                        }
                    }
                }
            }
            if (isActive) {
                Surface(color = palette.nearSolidChrome.copy(alpha = GlassTokens.NearSolidAlpha)) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = GlassTokens.Space12, vertical = GlassTokens.Space8)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    uiState.presets.find { it.id == uiState.activePresetId }?.name ?: "Kurva kustom",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = palette.textPrimary
                                )
                                Text(
                                    if (!uiState.eqEnabled) "Bypass"
                                    else if (uiState.hardwareAttached) "Efek terpasang, uji dengan bypass"
                                    else "Efek belum terpasang",
                                    color = palette.textMuted,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            IconButton(onToggleMasterPower, Modifier.testTag("youtube_quick_eq_toggle")) {
                                Icon(
                                    Icons.Rounded.PowerSettingsNew,
                                    "Ganti bypass",
                                    tint = if (uiState.eqEnabled) palette.accent else palette.textMuted
                                )
                            }
                            TextButton(onOpenEqualizerTab, Modifier.testTag("youtube_open_eq_button")) { Text("Edit EQ") }
                            IconButton({ showPresets = true }) { Icon(Icons.Rounded.Tune, "Pilih preset") }
                        }
                        Text(
                            "Pemutaran mengikuti kebijakan YouTube; lintas-tab atau latar belakang tidak dijamin.",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textMuted
                        )
                    }
                }
            }
        }
        if (fullscreen != null && isActive) {
            AndroidView(
                modifier = Modifier.fillMaxSize().background(palette.canvas),
                factory = { context ->
                    FrameLayout(context).apply {
                        fullscreen?.let {
                            (it.parent as? ViewGroup)?.removeView(it)
                            addView(it, ViewGroup.LayoutParams(-1, -1))
                        }
                    }
                }
            )
        }
    }

    if (showPresets) {
        GlassBottomSheet(onDismissRequest = { showPresets = false }) {
            LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 460.dp),
                contentPadding = PaddingValues(GlassTokens.Space16)
            ) {
                items(uiState.presets, key = { it.id }) { preset ->
                    TextButton(
                        onClick = { onSelectPreset(preset); showPresets = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(preset.name, color = palette.textPrimary)
                    }
                }
            }
        }
    }
}
