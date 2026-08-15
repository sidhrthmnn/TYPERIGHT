package com.example

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import android.util.Log

/**
 * Robust LifecycleOwner and SavedStateRegistryOwner for hosting Jetpack Compose ComposeViews
 * inside an Android InputMethodService (IME soft keyboard).
 *
 * Handles switching, hiding, pausing, resuming, and recreating lifecycle states cleanly
 * without throwing IllegalStateException.
 */
class ComposeSetup : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private var lifecycleRegistry = LifecycleRegistry(this)
    private var store = ViewModelStore()
    private var savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    init {
        initLifecycle()
    }

    private fun initLifecycle() {
        try {
            savedStateRegistryController.performAttach()
            savedStateRegistryController.performRestore(null)
        } catch (e: Exception) {
            Log.w("ComposeSetup", "Error attaching savedStateRegistry: ${e.message}")
        }
        try {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        } catch (e: Exception) {
            Log.w("ComposeSetup", "Error handling ON_CREATE: ${e.message}")
        }
    }

    fun start() {
        try {
            if (lifecycleRegistry.currentState == Lifecycle.State.DESTROYED) {
                lifecycleRegistry = LifecycleRegistry(this)
                store = ViewModelStore()
                savedStateRegistryController = SavedStateRegistryController.create(this)
                initLifecycle()
            }
            if (!lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            }
            if (!lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            }
        } catch (e: Exception) {
            Log.e("ComposeSetup", "Error starting lifecycle: ${e.message}")
        }
    }

    fun stop() {
        try {
            if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            }
            if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            }
        } catch (e: Exception) {
            Log.e("ComposeSetup", "Error stopping lifecycle: ${e.message}")
        }
    }

    fun destroy() {
        try {
            if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            }
            store.clear()
        } catch (e: Exception) {
            Log.e("ComposeSetup", "Error destroying lifecycle: ${e.message}")
        }
    }
}

