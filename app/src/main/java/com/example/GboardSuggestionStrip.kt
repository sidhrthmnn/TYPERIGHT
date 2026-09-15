package com.example

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateFloatAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke

/**
 * Gboard-style Suggestion Strip Component.
 * Displays 3 word predictions as pill-shaped chips with smooth animations.
 * Center chip highlights auto-corrections with accent color.
 */
@Composable
fun GboardSuggestionStrip(
    suggestions: List<String>,
    activePrefix: String,
    gboardResult: GboardSuggestionResult,
    previousWord: String?,
    dictionaryManager: DictionaryManager,
    isCorrection: Boolean,
    accentColor: Color,
    keyTextColor: Color,
    keyColor: Color,
    style: KeyboardStyle,
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val showStrip = suggestions.any { it.isNotBlank() } || activePrefix.isNotBlank()

    AnimatedVisibility(
        visible = showStrip,
        enter = fadeIn(animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                slideInVertically({ -it }, animationSpec = tween(180, easing = FastOutSlowInEasing)),
        exit = fadeOut(animationSpec = tween(150, easing = FastOutLinearInEasing)) +
               slideOutVertically({ it }, animationSpec = tween(150, easing = FastOutLinearInEasing)),
        label = "suggestion_strip_visibility"
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentWidth(Alignment.CenterHorizontally),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Show up to 3 suggestions
                val displaySuggestions = if (suggestions.size >= 3) suggestions.take(3) else suggestions.padEnd(3, "")

                displaySuggestions.forEachIndexed { index, word ->
                    val isEmpty = word.isBlank()
                    val middleWord = suggestions.getOrNull(1) ?: ""
                    val isMiddleAutoCorrecting = gboardResult.isCenterAutocorrecting || (
                        activePrefix.isNotEmpty() &&
                        middleWord.isNotEmpty() &&
                        middleWord.lowercase() != activePrefix.lowercase()
                    )

                    val isCorrectionActive = activePrefix.isNotEmpty() && (
                        (index == 1 && (isMiddleAutoCorrecting || suggestions.size == 1)) ||
                        (index == 1 && dictionaryManager.isSpellingCorrection(activePrefix, word, previousWord)) ||
                        (activePrefix.lowercase() == "i" && word == "I" && index == 1)
                    )

                    val isLiteralRawTyped = index == 0 &&
                        activePrefix.isNotEmpty() &&
                        word.lowercase() == activePrefix.lowercase()

                    val isRightSuggestion = index == 2 && word.isNotBlank()

                    val chipInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    val chipPressed by chipInteraction.collectIsPressedAsState()
                    val chipHovered by chipInteraction.collectIsHoveredAsState()

                    // Smooth scale animation on press
                    val chipScale by animateFloatAsState(
                        targetValue = if (chipPressed) 0.92f else 1.0f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
                        label = "suggestion_chip_scale_$index"
                    )

                    // Background color animation
                    val chipBgColor by animateColorAsState(
                        targetValue = when {
                            isCorrectionActive -> accentColor
                            chipPressed -> keyTextColor.copy(alpha = 0.1f)
                            chipHovered -> keyTextColor.copy(alpha = 0.05f)
                            isLiteralRawTyped -> keyTextColor.copy(alpha = 0.04f)
                            else -> keyTextColor.copy(alpha = 0.03f)
                        },
                        animationSpec = tween(150, easing = FastOutSlowInEasing),
                        label = "suggestion_chip_bg_$index"
                    )

                    // Border color animation
                    val chipBorderColor by animateColorAsState(
                        targetValue = when {
                            isCorrectionActive -> accentColor.copy(alpha = 0.9f)
                            isLiteralRawTyped -> accentColor.copy(alpha = 0.3f)
                            else -> keyTextColor.copy(alpha = 0.08f)
                        },
                        animationSpec = tween(150, easing = FastOutSlowInEasing),
                        label = "suggestion_chip_border_$index"
                    )

                    // Text color
                    val textColor = if (isCorrectionActive) Color.White else keyTextColor

                    // Border width
                    val borderWidth = if (isCorrectionActive) 1.5.dp else 0.5.dp

                    AnimatedContent(
                        key = word,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(120, easing = LinearOutSlowInEasing)) +
                            scaleIn(initialScale = 0.9f, animationSpec = tween(120, easing = FastOutSlowInEasing)) togetherWith
                            fadeOut(animationSpec = tween(80, easing = FastOutLinearInEasing)) +
                            scaleOut(targetScale = 0.9f, animationSpec = tween(80, easing = FastOutLinearInEasing))
                        },
                        label = "suggestion_word_content_$index"
                    ) { targetWord ->
                        if (isEmpty) {
                            Box(
                                modifier = Modifier
                                    .width(0.dp)
                                    .height(32.dp)
                            )
                        } else {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = chipBgColor,
                                modifier = Modifier
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .border(BorderStroke(borderWidth, chipBorderColor), RoundedCornerShape(18.dp))
                                    .clickable(
                                        interactionSource = chipInteraction,
                                        indication = null
                                    ) { onSuggestionClick(targetWord) }
                                    .graphicsLayer { scaleX = chipScale; scaleY = chipScale }
                                    .padding(horizontal = 8.dp)
                                    .testTag("suggestion_chip_$index"),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (isCorrectionActive) {
                                        Icon(
                                            imageVector = Icons.Default.AutoFixHigh,
                                            contentDescription = "Auto-correct",
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                    Text(
                                        text = if (isLiteralRawTyped) "\"$targetWord\"" else targetWord,
                                        color = textColor,
                                        fontSize = 13.sp,
                                        fontWeight = if (isCorrectionActive) FontWeight.Bold else FontWeight.Medium,
                                        fontFamily = if (style.isMonospace) FontFamily.Monospace else FontFamily.SansSerif,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // Smart emoji suggestion (Gboard feature)
                if (suggestions.any { it.isNotBlank() } && activePrefix.isNotBlank()) {
                    val smartEmoji = when (activePrefix.lowercase().trim()) {
                        "love" -> "❤️"
                        "fire", "lit" -> "🔥"
                        "happy", "smile" -> "😊"
                        "laugh", "lol", "haha" -> "😂"
                        "cool" -> "😎"
                        "sad", "cry" -> "😢"
                        "party" -> "🎉"
                        "clap" -> "👏"
                        "ok", "okay" -> "👍"
                        "coffee", "tea" -> "☕"
                        "car" -> "🚗"
                        "heart" -> "💖"
                        "dog" -> "🐶"
                        "cat" -> "🐱"
                        "star" -> "⭐"
                        "yes", "check" -> "✅"
                        "no" -> "❌"
                        "pray", "thanks" -> "🙏"
                        "100", "hundred" -> "💯"
                        else -> null
                    }

                    if (smartEmoji != null) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = accentColor.copy(alpha = 0.12f),
                            modifier = Modifier
                                .height(32.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .clickable { onSuggestionClick(smartEmoji) }
                                .padding(horizontal = 4.dp)
                                .testTag("smart_emoji_suggestion"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = smartEmoji, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Gboard-style Idle Toolbar with quick-access tools.
 * Shows: Apps grid, Clipboard, Proofread, GIF, Translate/AI, Mic
 */
@Composable
fun GboardIdleToolbar(
    isToolsDrawerOpen: Boolean,
    isClipboard: Boolean,
    isVoiceTyping: Boolean,
    isAssistant: Boolean,
    recentClipText: String?,
    accentColor: Color,
    keyTextColor: Color,
    keyColor: Color,
    onToolsToggle: () -> Unit,
    onClipboardToggle: () -> Unit,
    onProofreadClick: () -> Unit,
    onEmojiToggle: () -> Unit,
    onAiPolishClick: () -> Unit,
    onVoiceTypingToggle: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tools = listOf(
        ToolItem(Icons.Default.Apps, "Tools", onToolsToggle, isToolsDrawerOpen),
        ToolItem(Icons.Default.ContentPaste, "Clipboard", onClipboardToggle, isClipboard),
        ToolItem(Icons.Default.AutoFixHigh, "Proofread", onProofreadClick, false, isAccent = true),
        ToolItem(Icons.Default.Mood, "GIF", onEmojiToggle, false),
        ToolItem(Icons.Default.Translate, "Translate", onAiPolishClick, isAssistant),
        ToolItem(Icons.Default.Mic, "Voice", onVoiceTypingToggle, isVoiceTyping, tintOverride = if (isVoiceTyping) Color.Red else null)
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tools.forEach { tool ->
            if (tool.isAccent) {
                // Proofread pill button
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = accentColor.copy(alpha = 0.18f),
                    border = BorderStroke(0.5.dp, accentColor.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .height(32.dp)
                        .clickable { tool.onClick() }
                        .testTag("toolbar_${tool.contentDescription.toLowerCase()}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = tool.icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = tool.label,
                            color = accentColor,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                val isActive = tool.isActive
                val bgColor = if (isActive) accentColor.copy(alpha = 0.12f) else Color.Transparent
                val tintColor = tool.tintOverride ?: (if (isActive) accentColor else keyTextColor.copy(alpha = 0.7f))

                IconButton(
                    onClick = tool.onClick,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(bgColor)
                        .testTag("toolbar_${tool.contentDescription.toLowerCase()}")
                ) {
                    Icon(
                        imageVector = tool.icon,
                        contentDescription = tool.contentDescription,
                        tint = tintColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

data class ToolItem(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val isActive: Boolean = false,
    val isAccent: Boolean = false,
    val tintOverride: Color? = null,
    val contentDescription: String = ""
)

/**
 * Quick Paste Chip for when clipboard has content.
 */
@Composable
fun QuickPasteChip(
    clipText: String,
    accentColor: Color,
    keyTextColor: Color,
    onPaste: () -> Unit,
    onToolsToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Tools button
        IconButton(
            onClick = onToolsToggle,
            modifier = Modifier.size(36.dp).clip(CircleShape).background(keyTextColor.copy(alpha = 0.06f))
        ) {
            Icon(Icons.Default.Apps, "Tools", tint = keyTextColor.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
        }

        // Paste chip - takes most space
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = accentColor.copy(alpha = 0.15f),
            border = BorderStroke(0.5.dp, accentColor.copy(alpha = 0.35f)),
            modifier = Modifier
                .weight(1f)
                .height(32.dp)
                .clickable { onPaste() }
                .testTag("toolbar_quick_paste_chip"),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.ContentPaste, "Paste", tint = accentColor, modifier = Modifier.size(15.dp))
                Text(
                    text = "Paste: \"${if (clipText.length > 24) clipText.take(22) + "…" else clipText}\"",
                    color = keyTextColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }

        // Mic button
        IconButton(
            onClick = onVoiceTypingToggle,
            modifier = Modifier.size(36.dp).clip(CircleShape).background(keyTextColor.copy(alpha = 0.06f))
        ) {
            Icon(Icons.Default.Mic, "Voice", tint = keyTextColor.copy(alpha = 0.75f), modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * Expanded Toolbar with all tools (Gboard-style).
 */
@Composable
fun GboardExpandedToolbar(
    accentColor: Color,
    keyTextColor: Color,
    activeAiEngine: ActiveAiEngine,
    onCollapse: () -> Unit,
    onEngineToggle: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClipboardToggle: () -> Unit,
    onVoiceTypingToggle: () -> Unit,
    onProofreadClick: () -> Unit,
    onAiPolishClick: () -> Unit,
    onOpenSettings: () -> Unit,
    isVoiceTyping: Boolean,
    isClipboard: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Collapse button
        IconButton(
            onClick = onCollapse,
            modifier = Modifier.size(36.dp).clip(CircleShape).background(keyTextColor.copy(alpha = 0.06f))
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Collapse", tint = keyTextColor.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
        }

        // AI Engine indicator
        AiEngineIndicatorBadge(
            activeEngine = activeAiEngine,
            accentColor = accentColor,
            keyTextColor = keyTextColor,
            onClick = onEngineToggle
        )

        // Tool buttons
        listOf(
            ToolItem(Icons.AutoMirrored.Filled.Undo, "Undo", onUndo, false),
            ToolItem(Icons.AutoMirrored.Filled.Redo, "Redo", onRedo, false),
            ToolItem(Icons.Default.ContentPaste, "Clipboard", onClipboardToggle, isClipboard),
            ToolItem(Icons.Default.Mic, "Voice", onVoiceTypingToggle, isVoiceTyping, tintOverride = if (isVoiceTyping) Color.Red else null),
            ToolItem(Icons.Default.Spellcheck, "Proofread", onProofreadClick, false),
            ToolItem(Icons.Default.AutoFixHigh, "AI Polish", onAiPolishClick, false),
            ToolItem(Icons.Default.Settings, "Settings", onOpenSettings, false)
        ).forEach { tool ->
            val isActive = tool.isActive
            val bgColor = if (isActive) accentColor.copy(alpha = 0.12f) else Color.Transparent
            val tintColor = tool.tintOverride ?: (if (isActive) accentColor else keyTextColor.copy(alpha = 0.7f))

            IconButton(
                onClick = tool.onClick,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(bgColor)
                    .testTag("toolbar_${tool.contentDescription.toLowerCase()}")
            ) {
                Icon(
                    imageVector = tool.icon,
                    contentDescription = tool.contentDescription,
                    tint = tintColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}