package com.example

import android.graphics.PointF
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/** Owns word ranges across fast append-only edits, including asynchronous Compose/WebView editors. */
internal class TypingCoordinator(
    private val scope: CoroutineScope,
    private val ranker: CandidateRanker,
    private val connection: () -> InputConnection?,
    private val composingWord: () -> String,
    private val allowed: () -> Boolean,
    private val reportedCursor: () -> Int?,
    private val resolved: (String, String, List<String>, List<PointF?>, RankedCorrection, Boolean, String, String) -> Unit
) {
    private data class Request(var before: String, val after: String, val original: String, val trailing: String,
        var context: List<String>, val taps: List<PointF?>, val layout: String, val editor: InputConnection, val epoch: Long)
    private val queue = Channel<Request>(32)
    private val pending = mutableListOf<Request>()
    private var epoch = 0L
    private var expectedCursor: Int? = null
    private val ownedCursors = ArrayDeque<Int>()
    // Transient editor state only. Never persisted or passed to the adaptive learner.
    private var ownedBefore: String? = null
    private var ownedAfter = ""
    private var publicationBefore: String? = null
    init { scope.launch {
        for (request in queue) {
            if (request.epoch != epoch) continue
            if (request.trailing == ".") delay(100)
            val followingSpan = ownedBefore?.takeIf { it.startsWith(request.before) }
                ?.substring(request.before.length).orEmpty() + request.after
            val result = withContext(Dispatchers.Default) {
                ranker.awaitDictionaries()
                val following = followingSpan.take(200).split(Regex("[^\\p{L}\\p{M}\\p{N}'’]+"))
                    .filter(String::isNotEmpty).take(2).map(MultilingualLexicon::normalize)
                ranker.rank(request.original, request.context, request.taps, following = following, layout = request.layout)
            }
            // InputConnection may enqueue edits until the next frame; validate outside the key callback.
            delay(16)
            if (request.epoch != epoch || connection() !== request.editor || !allowed()) { pending.remove(request); continue }
            val ic = request.editor
            var before = ic.getTextBeforeCursor(20000, 0)?.toString().orEmpty()
            // Some editors publish a shorter, older prefix while owned writes are queued.
            // Wait for evidence; never apply against the stale snapshot or infer that it is accepted.
            for (attempt in 0 until 6) {
                val expected=ownedBefore ?: break
                if(before.endsWith(expected)) { publicationBefore=null; break }
                val knownQueuedSnapshot=publicationBefore?.let { before.startsWith(it) } == true
                if((!expected.startsWith(before) && !knownQueuedSnapshot) || request.epoch != epoch) break
                delay(16)
                before=ic.getTextBeforeCursor(20000,0)?.toString().orEmpty()
            }
            if(request.epoch != epoch) continue
            val after = ic.getTextAfterCursor(20000, 0)?.toString().orEmpty()
            val owned=ownedBefore
            if (owned == null || !owned.startsWith(request.before) || !before.endsWith(owned) || after != request.after || !ic.getSelectedText(0).isNullOrEmpty()) { invalidate(); continue }
            val suffix = request.original + request.trailing
            if (!request.before.endsWith(suffix)) { invalidate(); continue }
            val tail = owned.substring(request.before.length)
            if (request.trailing == "." && tail.firstOrNull()?.isLetterOrDigit() == true) { pending.remove(request); continue }
            val replacement = result.automatic
            var applied = false
            var updatedBefore = before
            if (replacement != null) {
                val prefix = request.before.dropLast(suffix.length)
                updatedBefore = before.dropLast(suffix.length+tail.length) + replacement + request.trailing + tail
                val active = composingWord()
                val end = (expectedCursor ?: cursor(ic, before)) + replacement.length-request.original.length
                publicationBefore=before
                ic.beginBatchEdit()
                try {
                    if (!ic.finishComposingText()) { invalidate(); continue }
                    if (!ic.deleteSurroundingText(suffix.length + tail.length, 0) || !ic.commitText(replacement + request.trailing + tail, 1)) { invalidate(); continue }
                    if (active.isNotEmpty() && tail.endsWith(active)) {
                        val extracted = runCatching { ic.getExtractedText(ExtractedTextRequest(),0) }.getOrNull()
                        if (extracted != null) ic.setComposingRegion(end-active.length, end)
                        else {
                            // Editors may omit extracted text. Restore exact composition without guessing global offsets.
                            ic.deleteSurroundingText(active.length,0)
                            ic.setComposingText(active,1)
                        }
                    }
                    applied = true
                } finally { ic.endBatchEdit() }
                pending.filter { it !== request && it.before.startsWith(request.before) }.forEach {
                    it.before = prefix + replacement + request.trailing + it.before.substring(request.before.length)
                    it.context = priorWords(it.before.dropLast(it.original.length+it.trailing.length))
                }
                ownedBefore = prefix + replacement + request.trailing + tail; ownedAfter = after; expectedCursor = end
                rememberCursor()
            }
            pending.remove(request)
            resolved(request.original, replacement ?: request.original, request.context, request.taps, result, applied && tail.isEmpty(), updatedBefore, request.trailing)
            if(pending.isEmpty()) ownedBefore=ownedBefore?.takeLast(512)
        }
    } }
    private fun capture(): Boolean {
        if (!allowed()) return false
        if (ownedBefore != null) return true
        val ic = connection() ?: return false
        if (!ic.getSelectedText(0).isNullOrEmpty()) return false
        ownedBefore = ic.getTextBeforeCursor(512,0)?.toString().orEmpty()
        ownedAfter = ic.getTextAfterCursor(20000,0)?.toString().orEmpty()
        expectedCursor = cursor(ic,ownedBefore!!)
        return true
    }
    fun prepare() { capture() }
    fun beforeCursor(limit: Int): String? = ownedBefore?.takeLast(limit)
    fun surroundingTokens(): Pair<String, String>? = ownedBefore?.let { before ->
        before.takeLastWhile(TypingPolicy::isWordCharacter) to ownedAfter.takeWhile(TypingPolicy::isWordCharacter)
    }
    /** Called before setComposingText, while the old prefix is still visible in the editor. */
    fun compose(word: String, replacedPrefix: String) {
        if (!capture()) return
        val before = ownedBefore!!
        if (!before.endsWith(replacedPrefix)) { invalidate(); return }
        ownedBefore = before.dropLast(replacedPrefix.length)+word
        expectedCursor = expectedCursor?.plus(word.length-replacedPrefix.length)
        rememberCursor()
    }
    fun boundaryCommitted(original: String, output: String, trailing: String) {
        val before = ownedBefore ?: return
        if (!before.endsWith(original)) { invalidate(); return }
        ownedBefore = before.dropLast(original.length)+output+trailing
        expectedCursor = expectedCursor?.plus(output.length+trailing.length-original.length)
        rememberCursor()
    }
    fun literal(text: String) { ownedBefore = ownedBefore?.plus(text); expectedCursor = expectedCursor?.plus(text.length); rememberCursor() }
    fun isIdentifierSpan(trailing: String = ""): Boolean {
        val token=ownedBefore?.removeSuffix(trailing)?.trimEnd()?.takeLastWhile { !it.isWhitespace() }.orEmpty()
        return token.contains('@') || token.startsWith('#') || token.contains(".") || token.contains('/') || token.contains('_')
    }
    fun completed(original: String, trailing: String, context: List<String>, taps: List<PointF?>, layout: String) {
        val ic = connection() ?: return
        if (!allowed() || isIdentifierSpan(trailing)) return
        val before = ownedBefore ?: ic.getTextBeforeCursor(20000,0)?.toString().orEmpty()
        if (!before.endsWith(original+trailing) || !ic.getSelectedText(0).isNullOrEmpty()) return
        val prior = priorWords(before.dropLast(original.length+trailing.length)).ifEmpty { context }
        val request = Request(before, if(ownedBefore != null) ownedAfter else ic.getTextAfterCursor(20000,0)?.toString().orEmpty(),original,trailing,prior,taps.toList(),layout,ic,epoch)
        if (pending.size >= 32) { invalidate(); return }
        pending.add(request)
        if (queue.trySend(request).isFailure) { invalidate(); return }
        if (expectedCursor == null) expectedCursor = cursor(ic,before)
    }
    fun appended(delta: Int? = null) { if (delta != null) { expectedCursor = expectedCursor?.plus(delta); rememberCursor() } }
    private fun rememberCursor() { expectedCursor?.let { ownedCursors.addLast(it) }; while(ownedCursors.size > 256) ownedCursors.removeFirst() }
    /** Android may deliver earlier owned selection updates after a rapid burst of key callbacks. */
    fun selection(start: Int, end: Int): Boolean {
        if(start != end) { invalidate(); return false }
        val acknowledged=ownedCursors.indexOfLast { it==end }
        if(acknowledged >= 0) {
            repeat(acknowledged+1) { ownedCursors.removeFirst() }
            return expectedCursor != end // Ignore only a known, queued notification; worker still validates actual text.
        }
        if(expectedCursor?.let { it != end } == true) invalidate()
        return false
    }
    fun invalidate() { epoch++; pending.clear(); ownedBefore = null; ownedAfter = ""; expectedCursor = null; publicationBefore=null; ownedCursors.clear() }
    private fun priorWords(prefix: String) = prefix.takeLast(200).split(Regex("[^\\p{L}\\p{M}\\p{N}'’]+")) .filter(String::isNotEmpty).takeLast(5)
    private fun cursor(ic: InputConnection, before: String): Int = runCatching { ic.getExtractedText(ExtractedTextRequest(),0)?.let { it.startOffset + it.selectionEnd } }.getOrNull() ?: reportedCursor() ?: before.length
}
