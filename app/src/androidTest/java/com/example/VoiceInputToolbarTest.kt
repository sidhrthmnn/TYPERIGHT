package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VoiceInputToolbarTest {
    @get:Rule val compose = createComposeRule()

    @Test fun voiceToolbarContainsNoVisibleTextAndControlsWork() {
        var cancelled = 0
        var finished = 0
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxWidth().height(48.dp)) {
                    VoiceInputToolbar(.4f, Color(0xFF4759CF), Color.Black, { cancelled++ }, { finished++ })
                }
            }
        }
        compose.onAllNodes(hasText("", substring = true), useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithContentDescription("Listening").assertExists()
        compose.onNodeWithTag("voice_cancel").performClick()
        compose.onNodeWithTag("voice_done").performClick()
        compose.runOnIdle { assertEquals(1, cancelled); assertEquals(1, finished) }
    }

    @Test fun processingRemainsIconOnlyAndCanBeCancelled() {
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxWidth().height(48.dp)) {
                    VoiceInputToolbar(0f, Color.Blue, Color.Black, {}, {}, processing = true)
                }
            }
        }
        compose.onAllNodes(hasText("", substring = true), useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithTag("voice_done").assertIsNotEnabled()
        compose.onNodeWithTag("voice_cancel").assertIsEnabled()
        compose.onNodeWithContentDescription("Refining dictation").assertExists()
    }
}
