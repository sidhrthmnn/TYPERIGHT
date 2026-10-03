package com.example

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Dedicated Wispr Flow Voice Panel for Gboard.
 * Provides real-time speech visualization, multi-tone mode switching,
 * live auto-cleanup preview, and seamless one-tap insertion.
 */
@Composable
fun WisprFlowVoicePanel(
    isRecording: Boolean,
    audioLevel: Float,
    rawTranscript: String,
    polishedTranscript: String,
    currentMode: WisprFlowMode,
    accentColor: Color,
    keyTextColor: Color,
    keyColor: Color,
    onModeSelect: (WisprFlowMode) -> Unit,
    onToggleRecording: () -> Unit,
    onCommitText: (String) -> Unit,
    onCancel: () -> Unit,
    onSwitchToKeyboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val modes = WisprFlowMode.values()
    val scrollState = rememberScrollState()

    // Smooth pulsing wave animation for voice visualizer
    val infiniteTransition = rememberInfiniteTransition(label = "wispr_voice_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(keyColor.copy(alpha = 0.95f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // --- TOP ROW: Header, Mode Pills & Close ---
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isRecording) Color(0xFFE53935) else accentColor)
                    )
                    Text(
                        text = if (isRecording) "Wispr Flow Active" else "Wispr Flow Ready",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = keyTextColor
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = accentColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = currentMode.badge,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onSwitchToKeyboard,
                        modifier = Modifier.size(32.dp).testTag("switch_to_qwerty_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Keyboard,
                            contentDescription = "Switch to Keyboard",
                            tint = keyTextColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(32.dp).testTag("cancel_wispr_voice_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel Voice",
                            tint = keyTextColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Mode Selector Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                modes.forEach { mode ->
                    val isSelected = currentMode == mode
                    val pillBg by animateColorAsState(
                        targetValue = if (isSelected) accentColor else keyTextColor.copy(alpha = 0.08f),
                        animationSpec = tween(150),
                        label = "pill_bg"
                    )
                    val pillTextColor by animateColorAsState(
                        targetValue = if (isSelected) Color.White else keyTextColor.copy(alpha = 0.8f),
                        animationSpec = tween(150),
                        label = "pill_text_color"
                    )

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = pillBg,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onModeSelect(mode) }
                            .testTag("wispr_mode_${mode.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = mode.icon,
                                fontSize = 12.sp
                            )
                            Text(
                                text = mode.title,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = pillTextColor
                            )
                        }
                    }
                }
            }
        }

        // --- CENTER: Live Transcript Area ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(keyTextColor.copy(alpha = 0.04f))
                .border(1.dp, keyTextColor.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            val displayText = when {
                polishedTranscript.isNotBlank() -> polishedTranscript
                rawTranscript.isNotBlank() -> rawTranscript
                isRecording -> "Speak now..."
                else -> "Tap the microphone to start voice dictation"
            }

            Text(
                text = displayText,
                style = MaterialTheme.typography.bodyLarge,
                color = if (rawTranscript.isNotBlank() || polishedTranscript.isNotBlank()) keyTextColor else keyTextColor.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                fontWeight = if (polishedTranscript.isNotBlank()) FontWeight.Medium else FontWeight.Normal,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .testTag("wispr_voice_transcript_text")
            )
        }

        // --- BOTTOM ROW: Controls (Mic with Ripple Animation, Clear, Insert) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cancel / Clear button
            IconButton(
                onClick = onCancel,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(keyTextColor.copy(alpha = 0.08f))
                    .testTag("wispr_voice_clear_button")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Clear Voice Text",
                    tint = keyTextColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Compact Mic with concentric audio level ripple animation
            VoiceRippleMic(
                isRecording = isRecording,
                audioLevel = audioLevel,
                accentColor = if (isRecording) Color(0xFFE53935) else accentColor,
                onClick = onToggleRecording
            )

            // Insert / Done button
            val hasContent = rawTranscript.isNotBlank() || polishedTranscript.isNotBlank()
            IconButton(
                onClick = {
                    val final = if (polishedTranscript.isNotBlank()) polishedTranscript else rawTranscript
                    if (final.isNotBlank()) {
                        onCommitText(final)
                    }
                },
                enabled = hasContent,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (hasContent) accentColor else keyTextColor.copy(alpha = 0.08f))
                    .testTag("wispr_voice_insert_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Insert Dictated Text",
                    tint = if (hasContent) Color.White else keyTextColor.copy(alpha = 0.3f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * Compact Microphone Button with dynamic audio-reactive concentric ripples.
 * Eliminates busy listening animations while providing clear tactile voice feedback.
 */
@Composable
fun VoiceRippleMic(
    isRecording: Boolean,
    audioLevel: Float,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "voice_ripple_anim")
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple_pulse"
    )
    val animatedAudioLevel by animateFloatAsState(
        targetValue = if (isRecording) audioLevel.coerceIn(0f, 1f) else 0f,
        animationSpec = spring(
            stiffness = Spring.StiffnessLow,
            dampingRatio = Spring.DampingRatioMediumBouncy
        ),
        label = "ripple_audio_level"
    )

    Box(
        modifier = modifier.size(68.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isRecording) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val baseRadius = 24.dp.toPx()
                val maxRadius = size.width / 2f

                // Outer animated ripple ring 1
                val r1 = baseRadius + (maxRadius - baseRadius) * pulseProgress
                val a1 = (1f - pulseProgress) * 0.40f * (0.35f + 0.65f * animatedAudioLevel)
                drawCircle(color = accentColor.copy(alpha = a1), radius = r1, center = center)

                // Secondary phased ripple ring 2
                val progress2 = (pulseProgress + 0.5f) % 1f
                val r2 = baseRadius + (maxRadius - baseRadius) * progress2
                val a2 = (1f - progress2) * 0.30f * (0.35f + 0.65f * animatedAudioLevel)
                drawCircle(color = accentColor.copy(alpha = a2), radius = r2, center = center)

                // Inner reactive voice halo
                val innerRadius = baseRadius + (6.dp.toPx() * animatedAudioLevel)
                drawCircle(
                    color = accentColor.copy(alpha = 0.20f + 0.30f * animatedAudioLevel),
                    radius = innerRadius,
                    center = center
                )
            }
        }

        val micBgColor = if (isRecording) Color(0xFFE53935) else accentColor
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(micBgColor)
                .clickable { onClick() }
                .testTag("wispr_voice_main_mic_button")
        ) {
            Icon(
                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = if (isRecording) "Stop Listening" else "Start Listening",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
