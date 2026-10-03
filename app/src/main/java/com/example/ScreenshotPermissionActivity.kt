package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/** Started only by the user's Screenshot access settings action. */
class ScreenshotPermissionActivity : ComponentActivity() {
    private val request = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        Toast.makeText(this, if (SmartClipboardPolicy.hasPhotoAccess(this)) "Screenshot suggestions enabled" else "Photo access was not granted", Toast.LENGTH_SHORT).show()
        finish()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) request.launch(SmartClipboardPolicy.photoPermissions())
    }
}
