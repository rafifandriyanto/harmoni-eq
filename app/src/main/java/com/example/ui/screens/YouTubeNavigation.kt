package com.example.ui.screens

import java.net.URI
import java.net.URLEncoder

object YouTubeNavigation {
    const val HOME = "https://m.youtube.com"
    private fun youtubeHost(host: String) = host == "youtube.com" || host.endsWith(".youtube.com") || host == "youtu.be"
    fun trustedNavigation(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.rawUserInfo == null && (uri.port == -1 || uri.port == 443) &&
            (youtubeHost(uri.host.orEmpty().lowercase()) || uri.host == "accounts.google.com")
    }.getOrDefault(false)
    fun resolve(raw: String): Result<String> = runCatching {
        val input = raw.trim()
        require(input.length <= 2048) { "Teks terlalu panjang." }
        if (input.isBlank()) HOME else {
            val looksLikeUrl = input.contains(":") || input.startsWith("//") || input.startsWith("www.") ||
                input.startsWith("youtube.com") || input.startsWith("m.youtube.com") || input.startsWith("youtu.be")
            if (looksLikeUrl) {
                val url = if (input.startsWith("https://")) input else if (!input.contains(":") && !input.startsWith("//")) "https://$input" else input
                val uri = URI(url)
                require(trustedNavigation(url) && youtubeHost(uri.host.orEmpty().lowercase())) { "Gunakan tautan HTTPS youtube.com atau youtu.be." }
                url
            } else "$HOME/results?search_query=${URLEncoder.encode(input, "UTF-8")}" 
        }
    }
}
