package com.aistudio.typeright.util

import timber.log.Timber

/**
 * Logging utility with Timber
 */
object Logger {
    fun init() {
        Timber.plant(Timber.DebugTree())
    }
    
    fun d(tag: String, msg: String) = Timber.tag(tag).d(msg)
    fun i(tag: String, msg: String) = Timber.tag(tag).i(msg)
    fun w(tag: String, msg: String) = Timber.tag(tag).w(msg)
    fun e(tag: String, msg: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Timber.tag(tag).e(throwable, msg)
        } else {
            Timber.tag(tag).e(msg)
        }
    }
}
