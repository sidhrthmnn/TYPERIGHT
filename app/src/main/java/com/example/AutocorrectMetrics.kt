package com.example

import java.util.concurrent.atomic.AtomicLong

/** Aggregate session counters only. No words, editor contents or coordinates are retained. */
object AutocorrectMetrics {
    private val applied = AtomicLong()
    private val undone = AtomicLong()
    fun recordApplied() { applied.incrementAndGet() }
    fun recordUndo() { undone.incrementAndGet() }
    data class Snapshot(val applied: Long, val undone: Long) {
        val undoRate get() = if (applied == 0L) 0.0 else undone.toDouble() / applied
    }
    fun snapshot() = Snapshot(applied.get(), undone.get())
    internal fun reset() { applied.set(0); undone.set(0) }
}
