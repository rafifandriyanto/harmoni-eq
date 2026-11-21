package com.example.data.autoeq

import com.example.model.HeadphoneProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches headphone indices and raw measurement CSV files from the official
 * jaakkopasanen/AutoEq repository using jsDelivr CDN (fast, no GitHub API rate limits)
 * with GitHub Git Trees API fallback. Uses standard [HttpURLConnection] with zero
 * third-party networking dependencies.
 */
class AutoEqRemoteDataSource(
    private val httpGet: (String) -> String = ::defaultHttpGet
) {
    companion object {
        const val CDN_RESULTS_BASE = "https://cdn.jsdelivr.net/gh/jaakkopasanen/AutoEq@master/results"
        const val CDN_INDEX_URL = "$CDN_RESULTS_BASE/INDEX.md"
        const val GITHUB_TREE_URL = "https://api.github.com/repos/jaakkopasanen/AutoEq/git/trees/master?recursive=1"

        private val ALLOWED_HOSTS = setOf(
            "cdn.jsdelivr.net",
            "raw.githubusercontent.com",
            "api.github.com"
        )
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000
        private const val MAX_RESPONSE_BYTES = 16 * 1024 * 1024 // 16 MB cap

        fun isTrustedAutoEqUrl(rawUrl: String): Boolean {
            return runCatching {
                val url = URL(rawUrl)
                url.protocol.equals("https", ignoreCase = true) &&
                    url.host.lowercase() in ALLOWED_HOSTS
            }.getOrDefault(false)
        }

        private fun defaultHttpGet(rawUrl: String): String {
            require(isTrustedAutoEqUrl(rawUrl)) { "Untrusted AutoEQ URL rejected." }
            val connection = (URL(rawUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("Accept", "text/plain, text/csv, application/json")
                setRequestProperty("User-Agent", "HarmoniEQ-Android/1.0")
            }
            try {
                val code = connection.responseCode
                if (code !in 200..299) {
                    throw IllegalStateException("HTTP $code while fetching AutoEQ data.")
                }
                connection.inputStream.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= MAX_RESPONSE_BYTES) { "AutoEQ response exceeded safe size limit." }
                        output.write(buffer, 0, read)
                    }
                    return output.toByteArray().toString(Charsets.UTF_8)
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    /**
     * Downloads and parses the headphone model catalog from jsDelivr CDN (`results/INDEX.md`),
     * automatically falling back to the GitHub Trees API if needed.
     */
    suspend fun fetchHeadphoneIndex(): List<HeadphoneProfile> = withContext(Dispatchers.IO) {
        val cdnAttempt = runCatching {
            val md = httpGet(CDN_INDEX_URL)
            AutoEqCsvParser.parseIndexMarkdown(md, CDN_RESULTS_BASE)
        }.getOrNull()

        if (!cdnAttempt.isNullOrEmpty()) {
            return@withContext cdnAttempt
        }

        val treeJson = httpGet(GITHUB_TREE_URL)
        val treeProfiles = AutoEqCsvParser.parseGitHubTreeJson(treeJson, CDN_RESULTS_BASE)
        check(treeProfiles.isNotEmpty()) { "AutoEQ catalog returned no headphone profiles." }
        treeProfiles
    }

    /**
     * Downloads the raw CSV measurement file for [profile].
     * Automatically falls back from jsDelivr CDN to raw.githubusercontent.com if CDN returns an error.
     */
    suspend fun fetchProfileCsv(profile: HeadphoneProfile): String = withContext(Dispatchers.IO) {
        require(isTrustedAutoEqUrl(profile.csvUrl)) { "Invalid AutoEQ CSV URL." }
        val primary = runCatching { httpGet(profile.csvUrl) }.getOrNull()
        if (!primary.isNullOrBlank()) {
            return@withContext primary
        }

        // Fallback to raw.githubusercontent.com if jsDelivr had a transient cache miss
        if (profile.csvUrl.startsWith(CDN_RESULTS_BASE)) {
            val suffix = profile.csvUrl.removePrefix(CDN_RESULTS_BASE)
            val rawGithubUrl = "https://raw.githubusercontent.com/jaakkopasanen/AutoEq/master/results$suffix"
            return@withContext httpGet(rawGithubUrl)
        }

        error("Could not download AutoEQ CSV for ${profile.name}.")
    }
}
