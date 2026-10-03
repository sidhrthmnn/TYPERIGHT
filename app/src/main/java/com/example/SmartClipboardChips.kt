package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Password
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SmartClipboardChips(items: List<SmartClipSuggestion>, accent: Color, textColor: Color,
                        onPaste: (SmartClipSuggestion) -> Unit, onDismiss: (SmartClipSuggestion) -> Unit,
                        modifier: Modifier = Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            Row(Modifier.clip(RoundedCornerShape(16.dp)).background(accent.copy(alpha = .16f)),
                verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.testTag("smart_clip_paste_${item.id}").clickable { onPaste(item) }
                    .heightIn(min = 40.dp).padding(start = 10.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (item.image != null) coil.compose.AsyncImage(model = item.image, contentDescription = "Recent screenshot",
                        modifier = Modifier.size(24.dp).clip(RoundedCornerShape(4.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    else Icon(Icons.Default.Password, "Paste verification code", tint = accent, modifier = Modifier.size(18.dp))
                    Text(item.code ?: "Screenshot", color = textColor, fontSize = 12.sp, maxLines = 1)
                }
                IconButton(onClick = { onDismiss(item) }, modifier = Modifier.size(40.dp).testTag("smart_clip_close_${item.id}")) {
                    Icon(Icons.Default.Close, "Dismiss ${if (item.code != null) "code" else "screenshot"} suggestion", tint = textColor, modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}
