package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
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

    /**
     * Requests the microphone runtime permission using an ActivityResultLauncher.
     */
    fun requestPermission(launcher: ActivityResultLauncher<String>) {
        launcher.launch(Manifest.permission.RECORD_AUDIO)
    }

    /**
     * Opens system Application Details Settings page so the user can manually enable permission
     * if permanently denied.
     */
    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
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
