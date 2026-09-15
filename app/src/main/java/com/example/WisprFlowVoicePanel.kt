package com.example

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateFloatAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawCircle
import androidx.compose.ui.graphics.drawscope.drawPath
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Gboard-style Wispr Flow Voice Panel with polished Material 3 Expressive design.
 * Features: real-time waveform, mode chips, live transcript with inline formatting,
 * pulsing mic button, and seamless keyboard switching.
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

    // Continuous pulse animation for recording state
    val infiniteTransition = rememberInfiniteTransition(label = "wispr_voice_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    // Mic button scale animation
    val micScale by animateFloatAsState(
        targetValue = if (isRecording) 1.05f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "mic_scale"
    )

    // Transcript fade animation
    val transcriptAlpha by animateFloatAsState(
        targetValue = if (rawTranscript.isNotBlank() || polishedTranscript.isNotBlank()) 1f else 0.5f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "transcript_alpha"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(keyColor)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- TOP BAR: Status, Mode Chips, Actions ---
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Status Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Pulsing recording indicator
                    AnimatedVisibility(visible = isRecording) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE53935))
                                .graphicsLayer {
                                    scaleX = pulseAlpha * 0.5f + 0.5f
                                    scaleY = pulseAlpha * 0.5f + 0.5f
                                }
                        )
                    }
                    AnimatedVisibility(visible = !isRecording) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(accentColor.copy(alpha = 0.6f))
                        )
                    }

                    Text(
                        text = if (isRecording) "Listening…" else "Wispr Flow",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = keyTextColor
                    )

                    // Mode badge
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = accentColor.copy(alpha = 0.12f),
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Text(
                            text = currentMode.badge,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Keyboard switch
                    IconButton(
                        onClick = onSwitchToKeyboard,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(keyTextColor.copy(alpha = 0.06f))
                            .testTag("switch_to_qwerty_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Keyboard,
                            contentDescription = "Switch to Keyboard",
                            tint = keyTextColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Cancel button
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(keyTextColor.copy(alpha = 0.06f))
                            .testTag("cancel_wispr_voice_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel Voice Dictation",
                            tint = keyTextColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Mode Selector Chips (Gboard-style horizontal scroll)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                modes.forEach { mode ->
                    val isSelected = currentMode == mode
                    val chipBg by animateColorAsState(
                        targetValue = if (isSelected) accentColor else keyTextColor.copy(alpha = 0.06f),
                        animationSpec = tween(180, easing = FastOutSlowInEasing),
                        label = "chip_bg"
                    )
                    val chipTextColor by animateColorAsState(
                        targetValue = if (isSelected) Color.White else keyTextColor.copy(alpha = 0.8f),
                        animationSpec = tween(180, easing = FastOutSlowInEasing),
                        label = "chip_text"
                    )
                    val chipScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.0f else 0.97f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                        label = "chip_scale"
                    )

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = chipBg,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onModeSelect(mode) }
                            .graphicsLayer { scaleX = chipScale; scaleY = chipScale }
                            .testTag("wispr_mode_${mode.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(text = mode.icon, fontSize = 13.sp)
                            Text(
                                text = mode.title,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = chipTextColor
                            )
                        }
                    }
                }
            }
        }

        // --- CENTER: Live Transcript Card with Waveform ---
        val displayText = when {
            polishedTranscript.isNotBlank() -> polishedTranscript
            rawTranscript.isNotBlank() -> rawTranscript
            isRecording -> "Speak naturally… Wispr Flow formats your thoughts in real time"
            else -> "Tap the microphone to start voice dictation"
        }

        val isLiveTranscript = rawTranscript.isNotBlank() || polishedTranscript.isNotBlank()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(keyTextColor.copy(alpha = 0.03f))
                .border(
                    width = 1.dp,
                    color = if (isRecording) accentColor.copy(alpha = 0.3f) else keyTextColor.copy(alpha = 0.06f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(16.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Live transcript with typewriter effect
                AnimatedVisibility(
                    visible = isLiveTranscript || isRecording,
                    enter = fadeIn(animationSpec = tween(250)) + slideInVertically({ -it / 4 }, animationSpec = tween(250)),
                    exit = fadeOut(animationSpec = tween(200)) + slideOutVertically({ it / 4 }, animationSpec = tween(200))
                ) {
                    Text(
                        text = displayText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = keyTextColor.copy(alpha = transcriptAlpha),
                        textAlign = TextAlign.Center,
                        fontWeight = if (polishedTranscript.isNotBlank()) FontWeight.Medium else FontWeight.Normal,
                        fontFamily = if (polishedTranscript.isNotBlank()) FontFamily.Default else FontFamily.Default,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                            .testTag("wispr_voice_transcript_text"),
                        lineHeight = 24.sp
                    )
                }

                // Gboard-style dynamic waveform
                GboardWaveformVisualizer(
                    isRecording = isRecording,
                    audioLevel = audioLevel,
                    accentColor = if (isRecording) Color(0xFFE53935) else accentColor,
                    keyTextColor = keyTextColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }
        }

        // --- BOTTOM: Primary Actions (Gboard-style) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Clear button
            IconButton(
                onClick = onCancel,
                enabled = isLiveTranscript,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (isLiveTranscript) keyTextColor.copy(alpha = 0.08f) else keyTextColor.copy(alpha = 0.04f))
                    .testTag("wispr_voice_clear_button")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Clear Transcript",
                    tint = keyTextColor.copy(alpha = if (isLiveTranscript) 0.7f else 0.3f),
                    modifier = Modifier.size(22.dp)
                )
            }

            // Main Mic Button - Gboard style with pulsing rings
            GboardMicButton(
                isRecording = isRecording,
                accentColor = accentColor,
                pulseAlpha = pulseAlpha,
                micScale = micScale,
                onClick = onToggleRecording,
                modifier = Modifier.testTag("wispr_voice_main_mic_button")
            )

            // Insert/Confirm button
            val hasContent = rawTranscript.isNotBlank() || polishedTranscript.isNotBlank()
            val insertScale by animateFloatAsState(
                targetValue = if (hasContent) 1.0f else 0.9f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                label = "insert_scale"
            )

            IconButton(
                onClick = {
                    val final = if (polishedTranscript.isNotBlank()) polishedTranscript else rawTranscript
                    if (final.isNotBlank()) onCommitText(final)
                },
                enabled = hasContent,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (hasContent) accentColor else keyTextColor.copy(alpha = 0.04f))
                    .graphicsLayer { scaleX = insertScale; scaleY = insertScale }
                    .testTag("wispr_voice_insert_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Insert Formatted Text",
                    tint = if (hasContent) Color.White else keyTextColor.copy(alpha = 0.3f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * Gboard-style Waveform Visualizer with smooth, fluid bars.
 * Matches Google's Material Design voice input visualization.
 */
@Composable
fun GboardWaveformVisualizer(
    isRecording: Boolean,
    audioLevel: Float,
    accentColor: Color,
    keyTextColor: Color,
    modifier: Modifier = Modifier
) {
    val barCount = 24
    val baseLevel = if (isRecording) audioLevel.coerceIn(0.1f, 1.0f) else 0.05f

    // Continuous animation for idle state
    val idleTransition = rememberInfiniteTransition(label = "waveform_idle")
    val idlePhase by idleTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "idle_phase"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .align(Alignment.CenterVertically),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barCount) {
            // Smooth sine wave distribution
            val positionFactor = sin((i.toDouble() / (barCount - 1)) * Math.PI).toFloat()
            val idleOffset = cos((idlePhase + i * 30f) * Math.PI / 180).toFloat() * 0.15f

            val animatedHeight = if (isRecording) {
                // Recording: bars react to audio level with smooth easing
                val targetHeight = (baseLevel * positionFactor * 36.dp.value + 3.dp.value).coerceIn(3.dp, 36.dp)
                animateDpAsState(
                    targetValue = targetHeight.dp,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium),
                    label = "bar_height_$i"
                ).value
            } else {
                // Idle: subtle breathing animation
                val idleHeight = (3.dp.value + positionFactor * 8.dp.value + idleOffset * 4.dp.value).dp.coerceIn(3.dp, 12.dp)
                animateDpAsState(
                    targetValue = idleHeight,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    label = "idle_bar_$i"
                ).value
            }

            val barAlpha = if (isRecording) {
                (0.4f + positionFactor * 0.6f).coerceIn(0.3f, 1f)
            } else {
                0.35f
            }

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(animatedHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (isRecording) {
                            accentColor.copy(alpha = barAlpha)
                        } else {
                            keyTextColor.copy(alpha = 0.25f)
                        }
                    )
                    .graphicsLayer {
                        // Subtle scale on recording for organic feel
                        if (isRecording) {
                            scaleY = 1.0f + (1.0f - positionFactor) * 0.05f
                        }
                    }
            )
        }
    }
}

/**
 * Gboard-style Mic Button with pulsing rings and haptic feedback.
 */
@Composable
fun GboardMicButton(
    isRecording: Boolean,
    accentColor: Color,
    pulseAlpha: Float,
    micScale: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val micColor = if (isRecording) Color(0xFFE53935) else accentColor

    Box(
        modifier = modifier
            .size(64.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .graphicsLayer {
                scaleX = micScale
                scaleY = micScale
            }
    ) {
        // Outer pulse rings (only when recording)
        AnimatedVisibility(visible = isRecording) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .graphicsLayer {
                        val ringScale = 1.0f + pulseAlpha * 0.6f
                        scaleX = ringScale
                        scaleY = ringScale
                    }
            ) {
                androidx.compose.foundation.Canvas(modifier = Modifier.size(64.dp)) {
                    val centerX = size.width / 2f
                    val centerY = size.height / 2f
                    val maxRadius = min(centerX, centerY)

                    for (ring in 0..2) {
                        val ringProgress = (pulseAlpha + ring * 0.33f) % 1.0f
                        val radius = maxRadius * ringProgress
                        val alpha = (1.0f - ringProgress) * 0.25f

                        drawCircle(
                            color = micColor.copy(alpha = alpha),
                            radius = radius,
                            center = androidx.compose.ui.geometry.Offset(centerX, centerY),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                        )
                    }
                }
            }
        }

        // Main button
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(micColor)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = isRecording,
                enter = fadeIn(animationSpec = tween(150)) + scaleIn(initialScale = 0.8f, animationSpec = tween(150)),
                exit = fadeOut(animationSpec = tween(120)) + scaleOut(targetScale = 0.8f, animationSpec = tween(120))
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop Listening",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            AnimatedVisibility(
                visible = !isRecording,
                enter = fadeIn(animationSpec = tween(150)) + scaleIn(initialScale = 0.8f, animationSpec = tween(150)),
                exit = fadeOut(animationSpec = tween(120)) + scaleOut(targetScale = 0.8f, animationSpec = tween(120))
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Start Voice Dictation",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

/**
 * Dedicated Ramble Mode Panel for stream-of-thought dictation.
 * Gboard-style UI with live formatting preview and meta-command hints.
 */
@Composable
fun RambleModePanel(
    isRecording: Boolean,
    audioLevel: Float,
    rawTranscript: String,
    formattedTranscript: String,
    accentColor: Color,
    keyTextColor: Color,
    keyColor: Color,
    onToggleRecording: () -> Unit,
    onConfirmRamble: () -> Unit,
    onCancelRamble: () -> Unit,
    onSwitchToKeyboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ramble_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ramble_pulse_alpha"
    )

    val hasContent = rawTranscript.isNotBlank() || formattedTranscript.isNotBlank()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(keyColor)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isRecording) Color(0xFFE53935) else accentColor)
                        .graphicsLayer {
                            if (isRecording) {
                                scaleX = pulseAlpha * 0.5f + 0.5f
                                scaleY = pulseAlpha * 0.5f + 0.5f
                            }
                        }
                )
                Text(
                    text = if (isRecording) "Ramble Mode Active" else "Ramble Mode",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = keyTextColor
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFE53935).copy(alpha = 0.12f),
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Text(
                        text = "Stream-of-thought",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFE53935),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = onSwitchToKeyboard,
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(keyTextColor.copy(alpha = 0.06f))
                ) {
                    Icon(Icons.Default.Keyboard, "Switch to Keyboard", tint = keyTextColor.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                }
                IconButton(
                    onClick = onCancelRamble,
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(keyTextColor.copy(alpha = 0.06f))
                ) {
                    Icon(Icons.Default.Close, "Cancel Ramble", tint = keyTextColor.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                }
            }
        }

        // Meta-command hints (Gboard-style chips)
        if (!isRecording && !hasContent) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Try saying:",
                    style = MaterialTheme.typography.labelSmall,
                    color = keyTextColor.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Medium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "“make it formal”",
                        "“bullet points”",
                        "“email to boss”",
                        "“checklist”",
                        "“translate to Spanish”"
                    ).forEach { hint ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = keyTextColor.copy(alpha = 0.05f),
                            border = BorderStroke(0.5.dp, keyTextColor.copy(alpha = 0.1f)),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = hint,
                                style = MaterialTheme.typography.labelSmall,
                                color = keyTextColor.copy(alpha = 0.6f),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // Live Transcript Area
        val displayText = when {
            formattedTranscript.isNotBlank() -> formattedTranscript
            rawTranscript.isNotBlank() -> rawTranscript
            isRecording -> "Speak freely… say “make it formal”, “bullet points”, etc. to format"
            else -> "Tap the red button to start Ramble Mode"
        }

        val isLive = rawTranscript.isNotBlank() || formattedTranscript.isNotBlank()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(keyTextColor.copy(alpha = 0.03f))
                .border(
                    width = if (isRecording) 1.5.dp else 1.dp,
                    color = if (isRecording) Color(0xFFE53935).copy(alpha = 0.4f) else keyTextColor.copy(alpha = 0.06f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(16.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AnimatedVisibility(
                    visible = isLive || isRecording,
                    enter = fadeIn(animationSpec = tween(250)) + slideInVertically({ -it / 4 }),
                    exit = fadeOut(animationSpec = tween(200)) + slideOutVertically({ it / 4 })
                ) {
                    Text(
                        text = displayText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = keyTextColor.copy(alpha = if (isLive) 1f else 0.5f),
                        textAlign = TextAlign.Center,
                        fontWeight = if (formattedTranscript.isNotBlank()) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                        lineHeight = 24.sp
                    )
                }

                // Compact waveform for ramble
                GboardWaveformVisualizer(
                    isRecording = isRecording,
                    audioLevel = audioLevel,
                    accentColor = Color(0xFFE53935),
                    keyTextColor = keyTextColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                )

                // Processing indicator
                AnimatedVisibility(visible = !isRecording && rawTranscript.isNotBlank() && formattedTranscript.isBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val spinTransition = rememberInfiniteTransition(label = "ramble_spin")
                        val spinAngle by spinTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            )
                        )
                        androidx.compose.foundation.Canvas(modifier = Modifier.size(20.dp)) {
                            val center = size.width / 2f
                            val radius = center - 2.dp.toPx()
                            drawPath(
                                Path().apply { addCircle(center, center, radius, Path.Direction.CW) },
                                color = Color(0xFFE53935).copy(alpha = 0.6f),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = 2.5.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Formatting…", style = MaterialTheme.typography.labelMedium, color = Color(0xFFE53935), fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        // Bottom Actions
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cancel
            IconButton(
                onClick = onCancelRamble,
                modifier = Modifier.size(48.dp).clip(CircleShape).background(keyTextColor.copy(alpha = 0.06f))
            ) {
                Icon(Icons.Default.Close, "Cancel", tint = keyTextColor.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
            }

            // Main Button (Record/Confirm)
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .clickable { onToggleRecording() }
                    .graphicsLayer { scaleX = micScale; scaleY = micScale }
            ) {
                val btnColor = if (isRecording) Color(0xFFE53935) else accentColor
                val btnScale = if (isRecording) 1.02f else 1.0f

                // Pulse rings
                AnimatedVisibility(visible = isRecording) {
                    Box(modifier = Modifier.size(64.dp).clip(CircleShape).graphicsLayer {
                        val s = 1.0f + pulseAlpha * 0.5f
                        scaleX = s; scaleY = s
                    }) {
                        androidx.compose.foundation.Canvas(modifier = Modifier.size(64.dp)) {
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val maxR = min(cx, cy)
                            for (r in 0..2) {
                                val prog = (pulseAlpha + r * 0.33f) % 1.0f
                                val rad = maxR * prog
                                val a = (1.0f - prog) * 0.25f
                                drawCircle(btnColor.copy(alpha = a), rad, androidx.compose.ui.geometry.Offset(cx, cy), androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                            }
                        }
                    }
                }

                Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(btnColor).padding(4.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.Check else Icons.Default.Mic,
                        contentDescription = if (isRecording) "Confirm & Format" else "Start Ramble",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Insert (only when formatted text exists)
            val insertEnabled = formattedTranscript.isNotBlank()
            IconButton(
                onClick = { onConfirmRamble() },
                enabled = insertEnabled,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (insertEnabled) accentColor else keyTextColor.copy(alpha = 0.04f))
            ) {
                Icon(Icons.Default.ArrowForward, "Insert Formatted Text", tint = if (insertEnabled) Color.White else keyTextColor.copy(alpha = 0.3f), modifier = Modifier.size(24.dp))
            }
        }
    }
}

private var micScale by mutableStateOf(1.0f)