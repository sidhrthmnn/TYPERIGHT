package com.example

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateFloatAsState
import androidx.compose.animation.animateDpAsState
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

/**
 * Enhanced Wispr Flow Voice Panel with Gboard-like smooth animations.
 * Features: Real-time speech visualization, multi-tone mode switching,
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
    
    // Recording indicator animation
    val recordingScale by animateFloatAsState(
        targetValue = if (isRecording) 1.1f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh)
    )
    
    // Mic button color animation
    val micColor by animateColorAsState(
        targetValue = if (isRecording) Color(0xFFE53935) else accentColor,
        animationSpec = tween(300, easing = FastOutSlowInEasing)
    )
    
    // Waveform color animation
    val waveformColor by animateColorAsState(
        targetValue = if (isRecording) Color(0xFFE53935) else accentColor,
        animationSpec = tween(300, easing = FastOutSlowInEasing)
    )
    
    // Transcript transition animation
    val transcriptAlpha by animateFloatAsState(
        targetValue = if (polishedTranscript.isNotBlank()) 1f else if (rawTranscript.isNotBlank()) 0.7f else 0.5f,
        animationSpec = tween(300, easing = FastOutSlowInEasing)
    )
    
    // Mode selector animation
    val modeIndicatorOffset by animateDpAsState(
        targetValue = modes.indexOf(currentMode) * 88.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh)
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(keyColor.copy(alpha = 0.98f))
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
                    // Recording indicator dot with pulse
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .graphicsLayer {
                                scaleX = if (isRecording) recordingScale else 1f
                                scaleY = if (isRecording) recordingScale else 1f
                            }
                            .background(if (isRecording) Color(0xFFE53935) else accentColor)
                    )
                    
                    // Animated status text
                    Text(
                        text = if (isRecording) "Wispr Flow Active" else "Wispr Flow Ready",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = keyTextColor,
                        modifier = Modifier.animateContentSize()
                    )
                    
                    // Current mode badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = accentColor.copy(alpha = 0.15f),
                        modifier = Modifier.animateContentSize()
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
                    // Switch to keyboard button
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
                    
                    // Cancel button
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
            
            // Mode Selector Pills with smooth indicator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
            ) {
                // Animated mode indicator
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(32.dp)
                        .offset(x = modeIndicatorOffset)
                        .background(accentColor, RoundedCornerShape(16.dp))
                        .graphicsLayer {
                            translationX = modeIndicatorOffset.value
                        }
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    modes.forEach { mode ->
                        val isSelected = currentMode == mode
                        val pillTextColor by animateColorAsState(
                            targetValue = if (isSelected) Color.White else keyTextColor.copy(alpha = 0.8f),
                            animationSpec = tween(150),
                            label = "pill_text_color"
                        )
                        
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.Transparent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onModeSelect(mode) }
                                .testTag("wispr_mode_${mode.name.lowercase()}"),
                            contentColor = pillTextColor
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
        }
        
        // --- CENTER: Audio Visualizer & Live Transcript Area ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(keyTextColor.copy(alpha = 0.04f))
                .border(1.dp, keyTextColor.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                .padding(10.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Live Transcript Display with smooth transitions
                val displayText = when {
                    polishedTranscript.isNotBlank() -> polishedTranscript
                    rawTranscript.isNotBlank() -> rawTranscript
                    isRecording -> "Listening... Speak naturally"
                    else -> "Tap the microphone to start voice dictation"
                }
                
                AnimatedVisibility(
                    visible = polishedTranscript.isNotBlank() || rawTranscript.isNotBlank() || isRecording,
                    enter = fadeIn(animationSpec = tween(200)) + slideInVertically(
                        initialOffsetY = { 20.dp },
                        animationSpec = tween(200, easing = FastOutSlowInEasing)
                    ),
                    exit = fadeOut(animationSpec = tween(150)) + slideOutVertically(
                        targetOffsetY = { -20.dp },
                        animationSpec = tween(150, easing = FastOutSlowInEasing)
                    )
                ) {
                    Text(
                        text = displayText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = keyTextColor.copy(alpha = transcriptAlpha),
                        textAlign = TextAlign.Center,
                        fontWeight = if (polishedTranscript.isNotBlank()) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .testTag("wispr_voice_transcript_text")
                            .animateContentSize()
                    )
                }
                
                // Dynamic Audio Waveform Visualizer
                WisprWaveformBars(
                    isRecording = isRecording,
                    audioLevel = audioLevel,
                    accentColor = waveformColor,
                    pulseAlpha = pulseAlpha,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .padding(horizontal = 16.dp)
                )
            }
        }
        
        // --- BOTTOM ROW: Controls (Mic, Clear, Insert) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cancel / Clear button
            IconButton(
                onClick = onCancel,
                modifier = Modifier
                    .size(44.dp)
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
            
            // Big pulsing Mic Button with enhanced animation
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(micColor)
                    .graphicsLayer {
                        scaleX = if (isRecording) 1.05f else 1f
                        scaleY = if (isRecording) 1.05f else 1f
                    }
                    .clickable { onToggleRecording() }
                    .testTag("wispr_voice_main_mic_button")
                    .animateContentSize()
            ) {
                // Pulsing ring when recording
                AnimatedVisibility(visible = isRecording) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .graphicsLayer {
                                val infiniteTransition = rememberInfiniteTransition()
                                val scale by infiniteTransition.animateFloat(
                                    initialValue = 1f,
                                    targetValue = 1.3f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(1000, easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse
                                    )
                                )
                                val alpha by infiniteTransition.animateFloat(
                                    initialValue = 0.3f,
                                    targetValue = 0f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(1000, easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse
                                    )
                                )
                                scaleX = scale
                                scaleY = scale
                                this.alpha = alpha
                            }
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        micColor.copy(alpha = 0.4f) to 0f,
                                        micColor.copy(alpha = 0f) to 1f
                                    )
                                )
                            )
                    )
                }
                
                Icon(
                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = if (isRecording) "Stop Listening" else "Start Listening",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            
            // Insert / Done button with animated enable state
            val hasContent = rawTranscript.isNotBlank() || polishedTranscript.isNotBlank()
            val insertBgColor by animateColorAsState(
                targetValue = if (hasContent) accentColor else keyTextColor.copy(alpha = 0.08f),
                animationSpec = tween(200)
            )
            val insertIconColor by animateColorAsState(
                targetValue = if (hasContent) Color.White else keyTextColor.copy(alpha = 0.3f),
                animationSpec = tween(200)
            )
            
            IconButton(
                onClick = {
                    val final = if (polishedTranscript.isNotBlank()) polishedTranscript else rawTranscript
                    if (final.isNotBlank()) {
                        onCommitText(final)
                    }
                },
                enabled = hasContent,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(insertBgColor)
                    .testTag("wispr_voice_insert_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Insert Dictated Text",
                    tint = insertIconColor,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * Enhanced Animated Waveform Bars rendering dynamic audio heights with smooth Gboard-like animation.
 */
@Composable
fun WisprWaveformBars(
    isRecording: Boolean,
    audioLevel: Float,
    accentColor: Color,
    pulseAlpha: Float,
    modifier: Modifier = Modifier
) {
    val barCount = 25
    val baseLevel = if (isRecording) audioLevel.coerceIn(0.15f, 1.0f) else 0.08f
    
    // Smooth animated heights for each bar
    val barHeights = remember {
        mutableStateListOf<Float>().apply {
            repeat(barCount) { add(4.dp.value) }
        }
    }
    
    // Animate bar heights smoothly
    LaunchedEffect(isRecording, audioLevel, pulseAlpha) {
        val snapshot = barHeights.toList()
        for (i in 0 until barCount) {
            val offsetFactor = sin((i.toDouble() / barCount) * Math.PI).toFloat()
            val targetHeight = if (isRecording) {
                (baseLevel * offsetFactor * 32.dp.value + 4.dp.value).coerceIn(4.dp.value, 34.dp.value)
            } else {
                (4.dp.value + offsetFactor * 6.dp.value * pulseAlpha).coerceIn(4.dp.value, 10.dp.value)
            }
            
            // Smooth interpolation
            val currentHeight = if (i < snapshot.size) snapshot[i] else 4.dp.value
            val newHeight = currentHeight + (targetHeight - currentHeight) * 0.3f
            if (i < barHeights.size) {
                barHeights[i] = newHeight
            }
        }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barCount) {
            val offsetFactor = sin((i.toDouble() / barCount) * Math.PI).toFloat()
            val height = if (i < barHeights.size) barHeights[i] else 4.dp.value
            
            val barColor = if (isRecording) {
                accentColor.copy(alpha = (0.5f + offsetFactor * 0.5f).coerceIn(0.3f, 1f))
            } else {
                accentColor.copy(alpha = 0.3f)
            }
            
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(height.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .graphicsLayer {
                        // Subtle scale animation
                        val scale = 1f + (height - 4) / 34 * 0.1f
                        scaleX = scale
                        scaleY = scale
                    }
                    .background(barColor)
                    .animateContentSize(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh))
            )
        }
    }
}
