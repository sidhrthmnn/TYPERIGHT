package com.example

import android.content.Context
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class GgufModelSpec(
    val id: String, val name: String, val filename: String, val url: String,
    val sha256: String, val bytes: Long, val format: String, val description: String,
    val languages: String, val license: String, val terms: String = "", val custom: Boolean = false
) {
    val sizeLabel: String get() = when {
        bytes == 0L -> "Size supplied by provider"
        bytes >= 1_000_000_000L -> String.format(Locale.ROOT, "%.2f GB", bytes / 1e9)
        else -> "${(bytes / 1e6).toInt()} MB"
    }
    val requiresConsent: Boolean get() = license == "Gemma Terms of Use"
    fun toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("filename", filename); put("url", url)
        put("sha256", sha256); put("bytes", bytes); put("format", format)
        put("description", description); put("languages", languages); put("license", license)
        put("terms", terms); put("custom", custom)
    }
    companion object {
        fun fromJson(json: JSONObject) = GgufModelSpec(
            json.getString("id"), json.getString("name"), json.getString("filename"), json.getString("url"),
            json.getString("sha256"), json.getLong("bytes"), json.getString("format"),
            json.getString("description"), json.getString("languages"), json.getString("license"),
            json.optString("terms"), json.optBoolean("custom")
        ).also {
            require(it.filename.matches(Regex("[A-Za-z0-9_.-]+\\.gguf"))) { "Invalid model filename" }
            require(it.bytes in 0..8_000_000_000L) { "Model is too large for this mobile runtime" }
        }
    }
}

object GgufModelCatalog {
    const val DEFAULT_ID = "local-grmr-1.5b"
    val FORMATS = listOf("chatml", "llama3", "gemma3", "gemma4", "grmr")
    fun bundled(context: Context): List<GgufModelSpec> {
        val json = JSONObject(context.assets.open("model-catalog.json").bufferedReader().use { it.readText() })
        val models = json.getJSONArray("models")
        return (0 until models.length()).map { GgufModelSpec.fromJson(models.getJSONObject(it)) }
    }
    fun all(context: Context): List<GgufModelSpec> {
        val json = JSONArray(KeyboardSettings(context).sharedPreferences.getString("custom_gguf_models", "[]"))
        return bundled(context) + (0 until json.length()).map { GgufModelSpec.fromJson(json.getJSONObject(it)) }
    }
    fun resolve(context: Context, id: String): GgufModelSpec =
        all(context).firstOrNull { it.id == id } ?: bundled(context).first { it.id == DEFAULT_ID }
    fun saveCustom(context: Context, model: GgufModelSpec) {
        require(model.custom)
        val json = JSONArray()
        all(context).filter { it.custom && it.id != model.id }.forEach { json.put(it.toJson()) }
        json.put(model.toJson())
        KeyboardSettings(context).sharedPreferences.edit().putString("custom_gguf_models", json.toString()).commit()
    }
    fun custom(name: String, url: String, sha256: String, format: String): GgufModelSpec {
        val address = url.trim().toHttpUrlOrNull()
        require(address != null && address.isHttps && address.username.isEmpty() && address.password.isEmpty()) { "Use a direct HTTPS model URL" }
        val hash = sha256.trim().lowercase(Locale.ROOT)
        require(hash.matches(Regex("[a-f0-9]{64}"))) { "Enter the provider's 64-character SHA-256 checksum" }
        require(name.trim().isNotEmpty() && name.length <= 80) { "Enter a model name (up to 80 characters)" }
        require(format in FORMATS) { "Choose a supported prompt format" }
        return GgufModelSpec("custom-$hash", name.trim(), "custom-$hash.gguf", address.toString(), hash, 0,
            format, "Your GGUF model. Architecture and quality depend on its provider.", "Provider-defined", "Provider-defined", custom = true)
    }
}
