package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Helper class for managing runtime Microphone (RECORD_AUDIO) permissions
 * required for voice-to-text dictation and real-time audio level visualizer functionality.
 */
class MicrophonePermissionHelper(private val context: Context) {

    /**
     * Checks if the RECORD_AUDIO runtime permission is currently granted.
     */
    fun isPermissionGranted(): Boolean {
        return hasMicrophonePermission(context)
    }

    companion object {
        const val PERMISSION_RECORD_AUDIO = Manifest.permission.RECORD_AUDIO

        /**
         * Static utility method to verify RECORD_AUDIO permission state.
         */
        @JvmStatic
        fun hasMicrophonePermission(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}
