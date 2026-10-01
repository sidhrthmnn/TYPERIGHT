package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Dictated words belong in the editor; this toolbar contains only voice controls. */
@Composable
internal fun VoiceInputToolbar(audioLevel: Float, accentColor: Color, keyTextColor: Color,
                               onCancel: () -> Unit, onDone: () -> Unit,
                               processing: Boolean = false, processingDescription: String = "Refining dictation", modifier: Modifier = Modifier) {
    Row(modifier.fillMaxSize().padding(horizontal = 6.dp).testTag("voice_input_toolbar"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(onClick = onCancel, modifier = Modifier.size(40.dp).testTag("voice_cancel")) {
            Icon(Icons.Default.Close, "Cancel voice input", tint = keyTextColor, modifier = Modifier.size(22.dp))
        }
        Box(Modifier.weight(1f).height(40.dp).semantics {
            contentDescription = if (processing) processingDescription else "Listening"
        }, contentAlignment = Alignment.Center) {
            if (processing) CircularProgressIndicator(Modifier.size(24.dp), color = accentColor, strokeWidth = 2.dp)
            else VoiceWaveformVisualizer(audioLevel, accentColor, Modifier.size(38.dp))
        }
        IconButton(onClick = onDone, enabled = !processing,
            modifier = Modifier.size(40.dp).background(accentColor.copy(alpha = if (processing) .15f else 1f), CircleShape)
                .testTag("voice_done")) {
            Icon(Icons.Default.Check, "Finish voice input", tint = if (processing) keyTextColor.copy(alpha = .35f) else Color.White,
                modifier = Modifier.size(22.dp))
        }
    }
}
