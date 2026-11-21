package com.example.data.autoeq

import com.example.model.HeadphoneProfile
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Lightweight, dependency-free parser for official jaakkopasanen/AutoEq CSV files
 * and repository index payloads (INDEX.md and GitHub Git Trees JSON).
 */
object AutoEqCsvParser {

    private val preferredGainHeaders = listOf(
        "equalization",
        "parametric_eq",
        "fixed_band_eq",
        "gain",
        "gain_db",
        "db",
        "raw"
    )

    private val indexLineRegex = Regex("""^\s*[-*]\s*\[([^\]]+)]\(([^)]+)\)\s*$""")

    /**
     * Parses AutoEQ CSV text into sorted (frequencyHz, gainDb) pairs.
     * Supports both official multi-column AutoEQ CSVs (detecting the `frequency` and
     * `equalization` columns automatically) and simple 2-column `frequency,gain` CSVs.
     */
    fun parseCsv(csvContent: String): List<Pair<Float, Float>> {
        if (csvContent.isBlank()) return emptyList()

        val lines = csvContent
            .removePrefix("\uFEFF")
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()

        if (lines.isEmpty()) return emptyList()

        val delimiter = detectDelimiter(lines.first())
        val firstCells = splitCsvRow(lines.first(), delimiter)

        var freqCol = 0
        var gainCol = 1
        var dataStartIndex = 0

        val isHeaderRow = firstCells.firstOrNull()?.toFloatOrNull() == null
        if (isHeaderRow) {
            val normalizedHeaders = firstCells.map { it.trim().lowercase() }
            val detectedFreq = normalizedHeaders.indexOfFirst {
                it == "frequency" || it == "freq" || it == "hz" || it.startsWith("freq")
            }
            if (detectedFreq >= 0) freqCol = detectedFreq

            val detectedGain = preferredGainHeaders.firstNotNullOfOrNull { target ->
                normalizedHeaders.indexOfFirst { it == target }.takeIf { it >= 0 }
            } ?: normalizedHeaders.indexOfFirst { idx ->
                idx.contains("equalization") || idx.contains("gain") || idx.contains("eq")
            }.takeIf { it >= 0 }

            if (detectedGain != null && detectedGain != freqCol) {
                gainCol = detectedGain
            } else if (freqCol == 1) {
                gainCol = 0
            }
            dataStartIndex = 1
        }

        val byFrequency = LinkedHashMap<Float, Float>()
        for (i in dataStartIndex until lines.size) {
            val cells = splitCsvRow(lines[i], delimiter)
            if (cells.size <= maxOf(freqCol, gainCol)) continue
            val freq = cells[freqCol].trim().toFloatOrNull() ?: continue
            val gain = cells[gainCol].trim().toFloatOrNull() ?: continue
            if (!freq.isFinite() || !gain.isFinite() || freq <= 0f) continue
            byFrequency[freq] = gain
        }

        return byFrequency.entries
            .sortedBy { it.key }
            .map { it.key to it.value }
    }

    /**
     * Parses `results/INDEX.md` from jaakkopasanen/AutoEq into sorted [HeadphoneProfile] entries
     * with jsDelivr CDN CSV URLs.
     */
    fun parseIndexMarkdown(markdown: String, cdnResultsBaseUrl: String): List<HeadphoneProfile> {
        if (markdown.isBlank()) return emptyList()
        val base = cdnResultsBaseUrl.trimEnd('/')
        val deduped = LinkedHashMap<String, HeadphoneProfile>()

        markdown.lineSequence().forEach { line ->
            val match = indexLineRegex.find(line) ?: return@forEach
            val displayName = match.groupValues[1].trim()
            val rawRelPath = match.groupValues[2].trim().removePrefix("./").trimEnd('/')
            if (displayName.isBlank() || rawRelPath.isBlank()) return@forEach

            val decodedPath = runCatching { URLDecoder.decode(rawRelPath, "UTF-8") }.getOrDefault(rawRelPath)
            val segments = decodedPath.split('/').filter { it.isNotBlank() }
            if (segments.size < 2) return@forEach

            val folderModelName = segments.last()
            val type = normalizeFormFactor(decodedPath)
            val encodedSegments = segments.joinToString("/") { encodePathSegment(it) }
            val encodedFile = encodePathSegment("$folderModelName.csv")
            val csvUrl = "$base/$encodedSegments/$encodedFile"

            // Prefer primary measurement sources (oratory1990 / crinacle / Rtings) when duplicate names exist
            val existing = deduped[displayName.lowercase()]
            if (existing == null || sourcePriority(decodedPath) < sourcePriority(existing.csvUrl)) {
                deduped[displayName.lowercase()] = HeadphoneProfile(
                    name = displayName,
                    type = type,
                    csvUrl = csvUrl
                )
            }
        }

        return deduped.values.sortedBy { it.name.lowercase() }
    }

    /**
     * Parses GitHub Git Trees API JSON (`git/trees/master?recursive=1`) for AutoEQ measurement CSV files under `results/`.
     */
    fun parseGitHubTreeJson(json: String, cdnResultsBaseUrl: String): List<HeadphoneProfile> {
        if (json.isBlank()) return emptyList()
        val base = cdnResultsBaseUrl.trimEnd('/')
        val root = JSONObject(json)
        val tree = root.optJSONArray("tree") ?: return emptyList()
        val deduped = LinkedHashMap<String, HeadphoneProfile>()

        for (i in 0 until tree.length()) {
            val item = tree.optJSONObject(i) ?: continue
            if (item.optString("type") != "blob") continue
            val path = item.optString("path")
            if (!path.startsWith("results/") || !path.endsWith(".csv", ignoreCase = true)) continue

            val rel = path.removePrefix("results/")
            val segments = rel.split('/').filter { it.isNotBlank() }
            if (segments.size < 3) continue
            val fileName = segments.last()
            val folderName = segments[segments.size - 2]
            val modelName = fileName.removeSuffix(".csv")
            // Official AutoEQ model measurement CSVs match their parent folder name
            if (!modelName.equals(folderName, ignoreCase = true)) continue

            val type = normalizeFormFactor(rel)
            val encodedRel = segments.joinToString("/") { encodePathSegment(it) }
            val csvUrl = "$base/$encodedRel"

            val existing = deduped[modelName.lowercase()]
            if (existing == null || sourcePriority(rel) < sourcePriority(existing.csvUrl)) {
                deduped[modelName.lowercase()] = HeadphoneProfile(
                    name = modelName,
                    type = type,
                    csvUrl = csvUrl
                )
            }
        }

        return deduped.values.sortedBy { it.name.lowercase() }
    }

    internal fun normalizeFormFactor(pathOrType: String): String {
        val lower = pathOrType.lowercase()
        return when {
            lower.contains("in-ear") || lower.contains("iem") -> "In-ear"
            lower.contains("earbud") -> "Earbud"
            lower.contains("on-ear") -> "On-ear"
            lower.contains("over-ear") -> "Over-ear"
            else -> "Over-ear"
        }
    }

    private fun sourcePriority(path: String): Int {
        val lower = path.lowercase()
        return when {
            lower.contains("oratory1990") -> 0
            lower.contains("crinacle") -> 1
            lower.contains("rtings") -> 2
            lower.contains("innerfidelity") -> 3
            lower.contains("headphonecom") -> 4
            else -> 5
        }
    }

    private fun detectDelimiter(headerLine: String): Char {
        val commas = headerLine.count { it == ',' }
        val semicolons = headerLine.count { it == ';' }
        val tabs = headerLine.count { it == '\t' }
        return when {
            tabs > commas && tabs >= semicolons -> '\t'
            semicolons > commas -> ';'
            else -> ','
        }
    }

    private fun splitCsvRow(line: String, delimiter: Char): List<String> {
        val result = ArrayList<String>(10)
        val current = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == delimiter && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        result.add(current.toString())
        return result
    }

    internal fun encodePathSegment(segment: String): String =
        URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
}
