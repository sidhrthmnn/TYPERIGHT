package com.aistudio.typeright

import android.app.Application
import com.aistudio.typeright.util.Logger
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point with Hilt initialization
 */
@HiltAndroidApp
class TypeRightApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        Logger.init()
    }
}
