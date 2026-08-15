package com.example

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CrashLogEntry(
    val timestamp: String,
    val type: String, // "CRASH" or "CUSTOM_ERROR"
    val exceptionClass: String,
    val message: String,
    val stackTrace: String,
    val deviceInfo: String,
    val threadName: String
) {
    fun toFormattedString(): String {
        return """
            =========================================
            [TIMESTAMP] $timestamp
            [TYPE] $type
            [THREAD] $threadName
            [EXCEPTION] $exceptionClass
            [MESSAGE] $message
            
            [DEVICE INFO]
            $deviceInfo
            
            [STACK TRACE]
            $stackTrace
            =========================================
        """.trimIndent()
    }
}

object CrashReporter {
    private const val TAG = "CrashReporter"
    private const val FILE_NAME = "crash_logs.txt"
    private var isInitialized = false

    @Synchronized
    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true

        val appContext = context.applicationContext
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                logException(appContext, "CRASH", thread.name, throwable)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log uncaught exception", e)
            } finally {
                // Pass on to default handler so app crashes/handles normally
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
        Log.i(TAG, "CrashReporter initialized successfully.")
    }

    /**
     * Get the log file reference.
     */
    private fun getLogFile(context: Context): File {
        return File(context.filesDir, FILE_NAME)
    }

    /**
     * Log an exception to the crash logs file.
     */
    @Synchronized
    fun logException(context: Context, type: String, threadName: String, throwable: Throwable) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            
            // Extract stack trace
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            throwable.printStackTrace(pw)
            val stackTrace = sw.toString()

            // Gather Device info
            val packageInfo = try {
                context.packageManager.getPackageInfo(context.packageName, 0)
            } catch (e: Exception) {
                null
            }
            val appVersionName = packageInfo?.versionName ?: "Unknown"
            val appVersionCode = packageInfo?.let { 
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.longVersionCode else it.versionCode 
            } ?: "Unknown"

            val deviceInfo = """
                App Version: $appVersionName ($appVersionCode)
                OS Version: Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})
                Device: ${Build.MANUFACTURER} ${Build.MODEL}
                Hardware/Board: ${Build.HARDWARE} / ${Build.BOARD}
            """.trimIndent()

            val entry = CrashLogEntry(
                timestamp = timestamp,
                type = type,
                exceptionClass = throwable.javaClass.name,
                message = throwable.localizedMessage ?: "No message provided",
                stackTrace = stackTrace,
                deviceInfo = deviceInfo,
                threadName = threadName
            )

            // Append to file
            val file = getLogFile(context)
            file.appendText(entry.toFormattedString() + "\n\n")
            Log.d(TAG, "Logged $type to ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error writing crash log", e)
        }
    }

    /**
     * Allows manual logging of errors/custom debugging details.
     */
    @Synchronized
    fun logCustomError(context: Context, message: String, throwable: Throwable? = null) {
        val t = throwable ?: Exception(message)
        logException(context, "CUSTOM_ERROR", Thread.currentThread().name, t)
    }

    /**
     * Read and parse all logged entries from the log file.
     */
    @Synchronized
    fun getLogs(context: Context): List<CrashLogEntry> {
        val file = getLogFile(context)
        if (!file.exists()) return emptyList()

        val logs = mutableListOf<CrashLogEntry>()
        try {
            val content = file.readText()
            val sections = content.split("=========================================")
            
            for (section in sections) {
                val trimmed = section.trim()
                if (trimmed.isEmpty()) continue

                // Parse the sections back to visual structure or just keep them raw
                var timestamp = ""
                var type = "CRASH"
                var threadName = "unknown"
                var exceptionClass = ""
                var message = ""
                var deviceInfo = ""
                var stackTrace = ""

                val lines = trimmed.split("\n")
                var state = 0 // 0: metadata, 1: device info, 2: stack trace
                val deviceInfoLines = mutableListOf<String>()
                val stackTraceLines = mutableListOf<String>()

                for (line in lines) {
                    val trimmedLine = line.trim()
                    if (trimmedLine.startsWith("[TIMESTAMP]")) {
                        timestamp = trimmedLine.removePrefix("[TIMESTAMP]").trim()
                    } else if (trimmedLine.startsWith("[TYPE]")) {
                        type = trimmedLine.removePrefix("[TYPE]").trim()
                    } else if (trimmedLine.startsWith("[THREAD]")) {
                        threadName = trimmedLine.removePrefix("[THREAD]").trim()
                    } else if (trimmedLine.startsWith("[EXCEPTION]")) {
                        exceptionClass = trimmedLine.removePrefix("[EXCEPTION]").trim()
                    } else if (trimmedLine.startsWith("[MESSAGE]")) {
                        message = trimmedLine.removePrefix("[MESSAGE]").trim()
                    } else if (trimmedLine.startsWith("[DEVICE INFO]")) {
                        state = 1
                    } else if (trimmedLine.startsWith("[STACK TRACE]")) {
                        state = 2
                    } else {
                        if (state == 1) {
                            if (trimmedLine.isNotEmpty()) deviceInfoLines.add(line)
                        } else if (state == 2) {
                            stackTraceLines.add(line)
                        }
                    }
                }

                if (timestamp.isNotEmpty()) {
                    logs.add(
                        CrashLogEntry(
                            timestamp = timestamp,
                            type = type,
                            exceptionClass = exceptionClass,
                            message = message,
                            stackTrace = stackTraceLines.joinToString("\n").trim(),
                            deviceInfo = deviceInfoLines.joinToString("\n").trim(),
                            threadName = threadName
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading crash logs", e)
        }

        return logs.reversed() // Show newest first
    }

    /**
     * Get raw text content of logs file.
     */
    @Synchronized
    fun getRawLogs(context: Context): String {
        val file = getLogFile(context)
        return if (file.exists()) file.readText() else "No logs found."
    }

    /**
     * Clear all logs.
     */
    @Synchronized
    fun clearLogs(context: Context): Boolean {
        val file = getLogFile(context)
        return if (file.exists()) {
            file.delete()
        } else {
            true
        }
    }

    /**
     * Throw a test exception to verify crash logging.
     */
    fun triggerSimulatedCrash() {
        throw RuntimeException("Simulated Diagnostic Crash: Verify that CrashReporter captures this correctly.")
    }
}
