package com.example

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Optional, user-configured cloud polish. The typing pipeline never calls this class. */
internal object CloudPolishEngine {
    private val client = OkHttpClient.Builder().callTimeout(45, TimeUnit.SECONDS).build()
    private const val ALIAS = "typeright.cloud.polish"
    private fun prefs(context: Context) = context.getSharedPreferences("typeright_cloud_credentials", Context.MODE_PRIVATE)
    fun isConfigured(context: Context) = prefs(context).contains("key")
    private fun encryptionKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    suspend fun saveKey(context: Context, value: String) = withContext(Dispatchers.IO) {
        if (value.isBlank()) { prefs(context).edit().clear().commit(); return@withContext }
        require(value.trim().length in 20..256) { "Enter a valid Gemini API key" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, encryptionKey()) }
        val encrypted = cipher.doFinal(value.trim().toByteArray(Charsets.UTF_8))
        check(prefs(context).edit().putString("key", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP)).commit()) { "Could not save cloud credentials" }
    }
    private fun readKey(context: Context): String {
        val p = prefs(context)
        check(p.contains("key")) { "Add your Gemini API key in AI polish settings" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, encryptionKey(), GCMParameterSpec(128, Base64.decode(p.getString("iv", ""), Base64.NO_WRAP)))
        }
        return cipher.doFinal(Base64.decode(p.getString("key", ""), Base64.NO_WRAP)).toString(Charsets.UTF_8)
    }
    internal fun requestBody(input: String, mode: PolishMode, context: TextContext?): String = JSONObject()
        .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text",
            PolishPromptBuilder.build(input, mode, "plain", context = context))))))
        .put("generationConfig", JSONObject().put("temperature", .1).put("maxOutputTokens", 2048)).toString()
    internal fun parseOutput(body: String, input: String, mode: PolishMode): String {
        val parts = JSONObject(body).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts")
        val output = (0 until parts.length()).map { parts.getJSONObject(it) }.filter { !it.optBoolean("thought") }
            .joinToString("") { it.optString("text") }
        val clean = AiOutputValidator.sanitize(output, input)
        check(AiOutputValidator.isValid(input, clean, mode)) { "Cloud model could not produce a safe edit" }
        return clean
    }
    suspend fun polish(context: Context, input: String, mode: PolishMode, textContext: TextContext?): String = withContext(Dispatchers.IO) {
        require(input.length <= 6000) { "Select less text for AI polish (maximum 6,000 characters)" }
        val model = KeyboardSettings(context).cloudModel
        require(model.matches(Regex("gemini-[a-zA-Z0-9.\\-]+"))) { "Enter a valid Gemini model ID" }
        val request = Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .header("x-goog-api-key", readKey(context)).post(requestBody(input, mode, textContext).toRequestBody("application/json".toMediaType())).build()
        val call = client.newCall(request)
        val body = suspendCancellableCoroutine<String> { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(IOException("Cloud polish is unavailable. Check your connection.")) }
                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!continuation.isActive) return
                        if (!it.isSuccessful) continuation.resumeWithException(IOException("Cloud polish failed (HTTP ${it.code}). Check the model and API key."))
                        else continuation.resume(it.body?.string().orEmpty())
                    }
                }
            })
        }
        parseOutput(body, input, mode)
    }
}
