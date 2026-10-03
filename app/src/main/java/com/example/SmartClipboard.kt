package com.example

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.security.MessageDigest

data class SmartClipSuggestion(val id: String, val code: String? = null, val image: Uri? = null,
                              val mimeType: String = "image/png", val timestamp: Long)

object SmartClipboardPolicy {
    const val OTP_LIFETIME = 120_000L
    const val SCREENSHOT_LIFETIME = 300_000L
    private val digits = Regex("(?<![\\p{L}\\p{N}+.\\-/])\\d{4,8}(?![\\p{L}\\p{N}]|[.\\-/]\\d)")
    private val keyword = Regex("\\b(?:otp|one[ -]time(?: password| code)?|verification(?: code)?|security code|login code|passcode|code)\\b", RegexOption.IGNORE_CASE)

    fun otp(text: String): String? {
        val clean = text.trim()
        if (clean.length > 1000) return null
        if (clean.matches(Regex("\\d{4,8}"))) return clean
        val markers = keyword.findAll(clean).toList()
        val candidates = digits.findAll(clean).filter { number ->
            markers.any { marker ->
                val distance = if (number.range.first > marker.range.last) number.range.first - marker.range.last
                    else marker.range.first - number.range.last
                distance in 1..40
            }
        }.map { it.value }.distinct().toList()
        return candidates.singleOrNull()
    }

    fun recent(timestamp: Long, now: Long, lifetime: Long) = timestamp > 0 && now - timestamp in 0..lifetime
    fun screenshot(name: String, path: String) = name.startsWith("screenshot", true) ||
        path.split('/', '\\').any { it.equals("screenshots", true) }
    fun identity(value: String, timestamp: Long): String = MessageDigest.getInstance("SHA-256")
        .digest("$timestamp:$value".toByteArray()).joinToString("") { "%02x".format(it) }

    fun photoPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    fun hasPhotoAccess(context: Context) = photoPermissions().any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

/** Observes clipboard/media only while a non-sensitive keyboard is visible. No SMS access or OTP history. */
class SmartClipboardController(private val context: Context, private val scope: CoroutineScope,
                               private val enabled: () -> Boolean) {
    val suggestions = MutableStateFlow<List<SmartClipSuggestion>>(emptyList())
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val prefs = context.getSharedPreferences("smart_clipboard_dismissals", Context.MODE_PRIVATE)
    private val dismissed = LinkedHashSet(prefs.getStringSet("ids", emptySet()).orEmpty())
    private var active = false
    private var generation = 0
    private var refreshJob: Job? = null
    private var expiryJob: Job? = null
    private var legacyClipIdentity: String? = null
    private var legacyClipTime = 0L
    private val listener = ClipboardManager.OnPrimaryClipChangedListener { legacyClipIdentity = null; refresh() }
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { refresh() }
    }

    fun start() {
        stop()
        if (!enabled()) return
        active = true
        clipboard.addPrimaryClipChangedListener(listener)
        runCatching { context.contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer) }
        refresh()
        expiryJob = scope.launch { while (isActive) { delay(10_000); refresh() } }
    }

    fun stop() {
        active = false; generation++
        clipboard.removePrimaryClipChangedListener(listener)
        runCatching { context.contentResolver.unregisterContentObserver(observer) }
        refreshJob?.cancel(); expiryJob?.cancel()
        suggestions.value = emptyList()
    }

    fun dismiss(item: SmartClipSuggestion) {
        synchronized(dismissed) {
            dismissed.add(item.id)
            while (dismissed.size > 64) dismissed.remove(dismissed.first())
            prefs.edit().putStringSet("ids", dismissed.toSet()).apply()
        }
        suggestions.value = suggestions.value.filterNot { it.id == item.id }
    }

    fun refresh() {
        if (!active) return
        if (!enabled()) { stop(); return }
        val request = ++generation
        refreshJob?.cancel()
        val now = System.currentTimeMillis()
        val otp = runCatching {
            val clip = clipboard.primaryClip ?: return@runCatching null
            if (clip.itemCount == 0 || clip.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true) return@runCatching null
            val text = clip.getItemAt(0).text?.toString() ?: return@runCatching null
            val code = SmartClipboardPolicy.otp(text) ?: return@runCatching null
            val identity = SmartClipboardPolicy.identity(text, 0)
            if (identity != legacyClipIdentity) { legacyClipIdentity = identity; legacyClipTime = now }
            val timestamp = if (Build.VERSION.SDK_INT >= 26) clip.description.timestamp else legacyClipTime
            if (!SmartClipboardPolicy.recent(timestamp, now, SmartClipboardPolicy.OTP_LIFETIME)) return@runCatching null
            SmartClipSuggestion(SmartClipboardPolicy.identity(text, timestamp), code = code, timestamp = timestamp)
        }.getOrNull()
        refreshJob = scope.launch {
            val images = withContext(Dispatchers.IO) { recentScreenshots(now) }
            if (active && generation == request) suggestions.value = (listOfNotNull(otp) + images)
                .filterNot { isDismissed(it.id) }.take(3)
        }
    }

    private fun isDismissed(id: String) = synchronized(dismissed) { id in dismissed }

    internal fun recentScreenshots(now: Long): List<SmartClipSuggestion> {
        if (!SmartClipboardPolicy.hasPhotoAccess(context)) return emptyList()
        return runCatching {
            val pathColumn = if (Build.VERSION.SDK_INT >= 29) MediaStore.Images.Media.RELATIVE_PATH else MediaStore.Images.Media.DATA
            val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED, MediaStore.Images.Media.MIME_TYPE, pathColumn)
            val result = mutableListOf<SmartClipSuggestion>()
            val selection = "${MediaStore.Images.Media.DATE_ADDED} >= ? AND " +
                "(LOWER(${MediaStore.Images.Media.DISPLAY_NAME}) LIKE 'screenshot%' OR LOWER($pathColumn) LIKE '%screenshots/%')" +
                if (Build.VERSION.SDK_INT >= 29) " AND ${MediaStore.Images.Media.IS_PENDING} = 0" else ""
            context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, selection,
                arrayOf(((now - SmartClipboardPolicy.SCREENSHOT_LIFETIME) / 1000).toString()), "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { cursor ->
                while (cursor.moveToNext() && result.size < 2) {
                    val name = cursor.getString(1).orEmpty(); val path = cursor.getString(4).orEmpty()
                    val timestamp = cursor.getLong(2) * 1000
                    val mime = cursor.getString(3).orEmpty()
                    if (!SmartClipboardPolicy.screenshot(name, path) || !SmartClipboardPolicy.recent(timestamp, now, SmartClipboardPolicy.SCREENSHOT_LIFETIME) ||
                        mime !in setOf("image/png", "image/jpeg", "image/webp")) continue
                    val uri = android.content.ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(0))
                    val id = SmartClipboardPolicy.identity(uri.toString(), timestamp)
                    if (!isDismissed(id)) result.add(SmartClipSuggestion(id, image = uri, mimeType = mime, timestamp = timestamp))
                }
            }
            result
        }.getOrDefault(emptyList())
    }
}
