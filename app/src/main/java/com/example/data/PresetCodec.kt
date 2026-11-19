package com.example.data

import com.example.model.*
import org.json.JSONArray
import org.json.JSONObject

/** Versioned sound-only format. Parsing completes before callers mutate the library. */
object PresetCodec {
    fun encode(presets: List<EqPreset>): String = JSONObject().apply {
        put("schemaVersion", 2)
        put("presets", JSONArray().apply { presets.forEach { put(encodePreset(it)) } })
    }.toString(2)

    fun decode(text: String, legacyStorage: Boolean = false): List<EqPreset> {
        require(text.length <= 1_000_000) { "Berkas terlalu besar (maksimal 1 MB)." }
        val array = if (text.trimStart().startsWith("[")) {
            require(legacyStorage) { "Berkas harus berformat Harmoni EQ versi 2." }
            JSONArray(text)
        } else {
            val root = JSONObject(text)
            require((root.get("schemaVersion") as? Number)?.toDouble() == 2.0) { "Versi preset tidak didukung." }
            root.getJSONArray("presets")
        }
        // Batas 200 hanya untuk impor; data tersimpan yang lebih besar tetap dibaca
        // agar restore selesai dan LibraryMerge memangkasnya secara jujur.
        if (!legacyStorage) require(array.length() <= 200) { "Maksimal 200 preset per impor." }
        val decoded = List(array.length()) { decodePreset(array.getJSONObject(it), legacyStorage) }
        // Data tersimpan lama boleh berisi ID duplikat: dedup (menang yang pertama)
        // agar restore tidak macet permanen. Impor tetap menolak duplikat.
        if (legacyStorage) return decoded.distinctBy { it.id }
        require(decoded.map { it.id }.distinct().size == decoded.size) { "ID preset duplikat dalam berkas." }
        return decoded
    }

    private fun encodePreset(p: EqPreset) = JSONObject().apply {
        val s = p.soundSnapshot()
        put("id", p.id); put("name", p.name); put("category", p.category); put("description", p.description)
        put("sound", JSONObject().apply {
            put("left", JSONArray(s.left)); put("right", JSONArray(s.right)); put("linked", s.linked)
            put("bassEnabled", s.bassEnabled); put("bass", s.bass.toDouble())
            put("surroundEnabled", s.surroundEnabled); put("surround", s.surround.toDouble())
            put("width", s.width.toDouble()); put("balance", s.balance.toDouble())
            put("preampEnabled", s.preampEnabled); put("preamp", s.preamp.toDouble())
            put("limiter", s.limiter); put("reverb", s.reverb.name); put("autoHeadroom", s.autoHeadroom)
        })
    }

    private fun decodePreset(o: JSONObject, legacy: Boolean): EqPreset {
        val id = o.getString("id").also { require(it.isNotBlank() && it.length <= 128) }
        val name = o.getString("name").trim().also { require(it.isNotEmpty() && it.length <= 60) { "Nama preset tidak valid." } }
        val s = o.optJSONObject("sound")?.let { j ->
            SoundSnapshot(
                left = gains(j.getJSONArray("left")), right = gains(j.getJSONArray("right")),
                linked = boolean(j, "linked"), bassEnabled = boolean(j, "bassEnabled"),
                bass = number(j, "bass", 0f..1f), surroundEnabled = boolean(j, "surroundEnabled"),
                surround = number(j, "surround", 0f..1f), width = number(j, "width", 0f..2f),
                balance = number(j, "balance", -1f..1f), preampEnabled = boolean(j, "preampEnabled"),
                preamp = number(j, "preamp", -12f..12f), limiter = boolean(j, "limiter"),
                reverb = ReverbRoomPreset.valueOf(j.getString("reverb")), autoHeadroom = if (j.has("autoHeadroom")) boolean(j, "autoHeadroom") else false
            ).also { require(!it.linked || it.left == it.right) { "Kanal tertaut harus memiliki gain yang sama." } }
        } ?: run {
            require(legacy) { "Snapshot suara tidak lengkap." }
            val a = o.getJSONArray("gains")
            require(a.length() in 1..64)
            val raw = List(a.length()) { a.getDouble(it).toFloat().also { n -> require(n.isFinite() && n in -15f..15f) } }
            val left = List(20) { raw[it * raw.size / 20] }
            val bass = o.optDouble("bassBoost", 0.0).toFloat()
            val surround = o.optDouble("virtualizer", 0.0).toFloat()
            val width = o.optDouble("stereoWidth", 1.0).toFloat()
            require(bass.isFinite() && bass in 0f..1f && surround.isFinite() && surround in 0f..1f && width.isFinite() && width in 0f..2f)
            SoundSnapshot(left = left, bassEnabled = bass > 0, bass = bass,
                surroundEnabled = surround > 0, surround = surround, width = width)
        }
        return EqPreset(id, name, o.optString("category", "Kustom").take(40),
            o.optString("description", "Preset stereo tersimpan.").take(500),
            s.left, s.bass, s.surround, s.width, true, s)
    }

    private fun boolean(o: JSONObject, key: String): Boolean =
        o.get(key) as? Boolean ?: throw IllegalArgumentException("Nilai $key harus boolean.")

    private fun gains(a: JSONArray): List<Float> {
        require(a.length() == 20) { "Preset harus memiliki 20 band per kanal." }
        return List(20) { a.getDouble(it).toFloat().also { n ->
            require(n.isFinite() && n in -15f..15f) { "Gain band harus -15 hingga +15 dB." }
        } }
    }
    private fun number(o: JSONObject, key: String, range: ClosedFloatingPointRange<Float>): Float =
        o.getDouble(key).toFloat().also { require(it.isFinite() && it in range) { "Nilai $key tidak valid." } }
}
