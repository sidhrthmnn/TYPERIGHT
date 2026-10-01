package com.example

/** Revisable hypotheses replace the current segment; finalized segments accumulate once. */
internal class VoiceTranscriptBuffer {
    private val committed = mutableListOf<String>()
    private var partial = ""
    val text: String get() = (committed + partial).filter { it.isNotBlank() }.joinToString(" ")
    fun updatePartial(value: String) { partial = value.trim() }
    fun commitSegment(final: String) {
        val value = final.trim().ifEmpty { partial }
        if (value.isNotBlank()) committed.add(value)
        partial = ""
    }
    fun clear() { committed.clear(); partial = "" }
}
