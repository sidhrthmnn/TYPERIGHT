package com.example

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Data model for recording each AI execution (Proofreading or AI Polish)
 * specifying whether it was performed via Google Gemini Cloud API or On-Device AICore.
 */
data class AiExecutionLogEntry(
    val timestamp: String,
    val operation: String,       // "Proofreading" or "AI Polish (Formal)", etc.
    val engine: String,          // "On-Device AICore (Gemini Nano)" or "Google Gemini Cloud API" or "Local Rule Engine"
    val inputSnippet: String,
    val outputSnippet: String,
    val durationMs: Long
) {
    fun toFormattedString(): String {
        return """
            =========================================
            [TIMESTAMP] $timestamp
            [TYPE] AI_EXECUTION
            [OPERATION] $operation
            [ENGINE] $engine
            [DURATION] ${durationMs}ms
            [INPUT] $inputSnippet
            [OUTPUT] $outputSnippet
            =========================================
        """.trimIndent()
    }
}

object AiExecutionLogger {
    private const val TAG = "AiExecutionLogger"
    private const val FILE_NAME = "ai_execution_logs.txt"
    const val ENGINE_AICORE = "On-Device AICore (Gemini Nano)"
    const val ENGINE_GEMINI_CLOUD = "Google Gemini Cloud API"
    const val ENGINE_OFFLINE_LOCAL = "Local Neural & Rule Engine (Offline Fallback)"

    private fun getLogFile(context: Context): File {
        return File(context.filesDir, FILE_NAME)
    }

    /**
     * Records an AI execution log stating if it was done via Gemini or on-device AICore.
     * Logs to Logcat (Log.i) and persists to internal storage file.
     */
    @Synchronized
    fun logAiAction(
        context: Context,
        operation: String,
        engine: String,
        input: String,
        output: String,
        durationMs: Long = 0
    ) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val inputSnippet = if (input.length > 80) input.take(77) + "..." else input
        val outputSnippet = if (output.length > 80) output.take(77) + "..." else output

        // 1. Log to Android Logcat
        Log.i(
            TAG,
            "⚡ [AI EXECUTION LOG] Operation: '$operation' | Engine: '$engine' | Duration: ${durationMs}ms | Input: \"$inputSnippet\" -> Output: \"$outputSnippet\""
        )

        // 2. Persist to file
        try {
            val entry = AiExecutionLogEntry(
                timestamp = timestamp,
                operation = operation,
                engine = engine,
                inputSnippet = inputSnippet,
                outputSnippet = outputSnippet,
                durationMs = durationMs
            )
            val file = getLogFile(context)
            file.appendText(entry.toFormattedString() + "\n\n")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist AI execution log: ${e.message}")
        }
    }

    /**
     * Retrieves all recorded AI execution logs.
     */
    @Synchronized
    fun getLogs(context: Context): List<AiExecutionLogEntry> {
        val file = getLogFile(context)
        if (!file.exists()) return emptyList()

        val logs = mutableListOf<AiExecutionLogEntry>()
        try {
            val content = file.readText()
            val sections = content.split("=========================================")
            for (section in sections) {
                val trimmed = section.trim()
                if (trimmed.isEmpty()) continue

                var timestamp = ""
                var operation = "AI Action"
                var engine = "Unknown"
                var input = ""
                var output = ""
                var durationMs = 0L

                val lines = trimmed.split("\n")
                for (line in lines) {
                    val trimmedLine = line.trim()
                    when {
                        trimmedLine.startsWith("[TIMESTAMP]") -> timestamp = trimmedLine.removePrefix("[TIMESTAMP]").trim()
                        trimmedLine.startsWith("[OPERATION]") -> operation = trimmedLine.removePrefix("[OPERATION]").trim()
                        trimmedLine.startsWith("[ENGINE]") -> engine = trimmedLine.removePrefix("[ENGINE]").trim()
                        trimmedLine.startsWith("[DURATION]") -> {
                            val durStr = trimmedLine.removePrefix("[DURATION]").removeSuffix("ms").trim()
                            durationMs = durStr.toLongOrNull() ?: 0L
                        }
                        trimmedLine.startsWith("[INPUT]") -> input = trimmedLine.removePrefix("[INPUT]").trim()
                        trimmedLine.startsWith("[OUTPUT]") -> output = trimmedLine.removePrefix("[OUTPUT]").trim()
                    }
                }

                if (timestamp.isNotEmpty()) {
                    logs.add(
                        AiExecutionLogEntry(
                            timestamp = timestamp,
                            operation = operation,
                            engine = engine,
                            inputSnippet = input,
                            outputSnippet = output,
                            durationMs = durationMs
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading AI execution logs: ${e.message}")
        }

        return logs.reversed()
    }

    /**
     * Clear all recorded AI execution logs.
     */
    @Synchronized
    fun clearLogs(context: Context): Boolean {
        val file = getLogFile(context)
        return if (file.exists()) file.delete() else true
    }
}
