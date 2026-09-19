package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashReporter.init(this)
        enableEdgeToEdge()
        DictionaryUpdateScheduler.schedulePeriodicUpdate(this)

        setContent {
            val context = LocalContext.current
            val settings = remember { KeyboardSettings(context) }
            val dataStore = remember { settings.dataStore }
            val userPrefs by dataStore.userPreferencesFlow.collectAsState(initial = dataStore.currentSnapshot())
            val isDarkTheme = userPrefs.isDarkMode

            MyApplicationTheme(darkTheme = isDarkTheme) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    OnboardingScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun OnboardingScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settings = remember { KeyboardSettings(context) }
    val dataStore = remember { settings.dataStore }
    val userPrefs by dataStore.userPreferencesFlow.collectAsState(initial = dataStore.currentSnapshot())
    val scrollState = rememberScrollState()

    // Interactive states for keyboard status checks
    var isKeyboardEnabled by remember { mutableStateOf(false) }
    var isKeyboardSelected by remember { mutableStateOf(false) }
    var isMicPermissionGranted by remember { mutableStateOf(false) }

    // Dynamic checks on app focus
    LaunchedEffect(Unit) {
        isKeyboardEnabled = checkKeyboardEnabled(context)
        isKeyboardSelected = checkKeyboardSelected(context)
        isMicPermissionGranted = MicrophonePermissionHelper.hasMicrophonePermission(context)
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        isMicPermissionGranted = isGranted
    }

    // Refresh function
    val refreshStatus = {
        isKeyboardEnabled = checkKeyboardEnabled(context)
        isKeyboardSelected = checkKeyboardSelected(context)
        isMicPermissionGranted = MicrophonePermissionHelper.hasMicrophonePermission(context)
    }

    // Auto-refresh when app resumes/gains focus
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refreshStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Settings panel overrides
    val activeHeight = remember { mutableStateOf(settings.height) }
    val soundEnabled = remember { mutableStateOf(settings.soundEnabled) }
    val hapticEnabled = remember { mutableStateOf(settings.hapticEnabled) }
    val autocorrectEnabled = remember { mutableStateOf(settings.autocorrectEnabled) }
    val doubleSpacePeriodEnabled = remember { mutableStateOf(settings.doubleSpacePeriod) }
    val emojiSuggestionsEnabled = remember { mutableStateOf(settings.emojiSuggestionsEnabled) }
    val swipeEnabled = remember { mutableStateOf(settings.swipeEnabled) }
    val supportTier = remember { mutableStateOf(settings.supportTier) }
    val profanityFilterEnabled = remember { mutableStateOf(settings.profanityFilterEnabled) }
    val cloudSyncEnabled = remember { mutableStateOf(settings.cloudSyncEnabled) }
    val activeTheme = remember { mutableStateOf(settings.theme) }
    val activeIsDarkMode = remember { mutableStateOf(settings.isDarkMode) }
    val activeDynamicThemeEnabled = remember { mutableStateOf(settings.dynamicThemeEnabled) }
    val activeNumberRowEnabled = remember { mutableStateOf(settings.numberRowEnabled) }
    val activeAccentColor = remember { mutableStateOf(settings.accentColor) }
    val activeKeyBevelEnabled = remember { mutableStateOf(settings.keyBevelEnabled) }
    val activeRetroMonospace = remember { mutableStateOf(settings.retroMonospaceEnabled) }
    val activeMechanicalSound = remember { mutableStateOf(settings.mechanicalSoundEnabled) }

    val activeOfflineAiEnabled = remember { mutableStateOf(settings.offlineAiEnabled) }
    val activeGeminiAiEnabled = remember { mutableStateOf(settings.geminiAiEnabled) }
    val activeAiModel = remember { mutableStateOf(settings.aiModel) }
    val activeWhisperModel = remember { mutableStateOf(settings.whisperModel) }
    val activeVoiceLanguage = remember { mutableStateOf(settings.voiceLanguage) }
    val activeVoiceInputMode = remember { mutableStateOf(settings.voiceInputMode) }
    val activeWisprFlowMode = remember { mutableStateOf(settings.wisprFlowMode) }
    val activeClipboardEnabled = remember { mutableStateOf(settings.clipboardEnabled) }
    val activeKeyboardLanguage = remember { mutableStateOf(settings.keyboardLanguage) }
    val activeAiLanguage = remember { mutableStateOf(settings.aiLanguage) }
    val activeManglishEnabled = remember { mutableStateOf(settings.manglishTransliterationEnabled) }

    LaunchedEffect(userPrefs) {
        activeHeight.value = userPrefs.keyboardHeight
        soundEnabled.value = userPrefs.soundEnabled
        hapticEnabled.value = userPrefs.hapticEnabled
        autocorrectEnabled.value = userPrefs.autocorrectEnabled
        doubleSpacePeriodEnabled.value = userPrefs.doubleSpacePeriod
        emojiSuggestionsEnabled.value = userPrefs.emojiSuggestionsEnabled
        swipeEnabled.value = userPrefs.swipeEnabled
        supportTier.value = userPrefs.supportTier
        profanityFilterEnabled.value = userPrefs.profanityFilterEnabled
        cloudSyncEnabled.value = userPrefs.cloudSyncEnabled
        activeTheme.value = userPrefs.theme
        activeIsDarkMode.value = userPrefs.isDarkMode
        activeDynamicThemeEnabled.value = userPrefs.dynamicThemeEnabled
        activeNumberRowEnabled.value = userPrefs.numberRowEnabled
        activeAccentColor.value = userPrefs.accentColor
        activeKeyBevelEnabled.value = userPrefs.keyBevelEnabled
        activeRetroMonospace.value = userPrefs.retroMonospace
        activeMechanicalSound.value = userPrefs.mechanicalSound
        activeOfflineAiEnabled.value = userPrefs.offlineAiEnabled
        activeGeminiAiEnabled.value = userPrefs.geminiAiEnabled
        activeAiModel.value = userPrefs.aiModel
        activeWhisperModel.value = userPrefs.whisperModel
        activeVoiceLanguage.value = userPrefs.voiceLanguage
        activeVoiceInputMode.value = userPrefs.voiceInputMode
        activeWisprFlowMode.value = userPrefs.wisprFlowMode
        activeClipboardEnabled.value = userPrefs.clipboardEnabled
        activeKeyboardLanguage.value = userPrefs.keyboardLanguage
        activeAiLanguage.value = userPrefs.aiLanguage
        activeManglishEnabled.value = userPrefs.manglishTransliterationEnabled
    }

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Setup", "Typing Sandbox", "Appearance", "Gemini & Settings")

    var testInputText by remember { mutableStateOf("") }

    val completedStepsCount = (if (isKeyboardEnabled) 1 else 0) +
            (if (isKeyboardSelected) 1 else 0) +
            (if (isMicPermissionGranted) 1 else 0)

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // --- HERO HEADER ---
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Keyboard,
                                    contentDescription = "Type Right Icon",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Type Right",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Zero-Lag AI & Voice Keyboard",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                    ) {
                        Text(
                            text = "v${BuildConfig.VERSION_NAME} • Pixel 11",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Setup Progress Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "System Setup Readiness",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$completedStepsCount/3 Ready",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (completedStepsCount == 3) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { completedStepsCount / 3f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = if (completedStepsCount == 3) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }

        // --- NAVIGATION TABS ---
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.fillMaxWidth(),
            edgePadding = 0.dp,
            divider = {},
            containerColor = Color.Transparent
        ) {
            tabs.forEachIndexed { index, tabTitle ->
                val isSelected = selectedTab == index
                val tabBg by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    animationSpec = tween(150),
                    label = "tab_bg"
                )
                val tabContentColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(150),
                    label = "tab_content_color"
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = tabBg,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .clickable { selectedTab = index }
                ) {
                    Text(
                        text = tabTitle,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = tabContentColor
                    )
                }
            }
        }

        // --- TAB CONTENT ---
        when (selectedTab) {
            0 -> {
                // SETUP TAB
                SetupSection(
                    isKeyboardEnabled = isKeyboardEnabled,
                    isKeyboardSelected = isKeyboardSelected,
                    isMicPermissionGranted = isMicPermissionGranted,
                    onEnableClick = {
                        val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(intent)
                    },
                    onSelectClick = {
                        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                        if (!isKeyboardEnabled) {
                            Toast.makeText(context, "Please enable Type Right first in Android Settings (Step 1)", Toast.LENGTH_SHORT).show()
                            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            context.startActivity(intent)
                        } else {
                            imm?.showInputMethodPicker()
                        }
                    },
                    onMicClick = {
                        launcher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onRefresh = refreshStatus
                )
            }
            1 -> {
                // LIVE TYPING SANDBOX TAB
                TypingSandboxSection(
                    inputText = testInputText,
                    onInputTextChange = { testInputText = it },
                    isKeyboardActive = isKeyboardSelected
                )
            }
            2 -> {
                // RETRO THEMES & AESTHETICS TAB
                AestheticsSection(
                    settings = settings,
                    activeTheme = activeTheme,
                    activeIsDarkMode = activeIsDarkMode,
                    activeDynamicThemeEnabled = activeDynamicThemeEnabled,
                    activeNumberRowEnabled = activeNumberRowEnabled,
                    activeAccentColor = activeAccentColor,
                    activeHeight = activeHeight,
                    soundEnabled = soundEnabled,
                    hapticEnabled = hapticEnabled,
                    activeKeyBevelEnabled = activeKeyBevelEnabled,
                    activeRetroMonospace = activeRetroMonospace,
                    activeMechanicalSound = activeMechanicalSound
                )
            }
            3 -> {
                // GEMINI & SETTINGS TAB
                AiEngineSection(
                    settings = settings,
                    autocorrectEnabled = autocorrectEnabled,
                    profanityFilterEnabled = profanityFilterEnabled,
                    activeVoiceLanguage = activeVoiceLanguage,
                    activeClipboardEnabled = activeClipboardEnabled,
                    activeOfflineAiEnabled = activeOfflineAiEnabled,
                    activeGeminiAiEnabled = activeGeminiAiEnabled,
                    activeAiModel = activeAiModel,
                    activeWhisperModel = activeWhisperModel,
                    activeKeyboardLanguage = activeKeyboardLanguage,
                    activeAiLanguage = activeAiLanguage,
                    activeManglishEnabled = activeManglishEnabled,
                    doubleSpacePeriodEnabled = doubleSpacePeriodEnabled,
                    emojiSuggestionsEnabled = emojiSuggestionsEnabled,
                    swipeEnabled = swipeEnabled,
                    activeVoiceInputMode = activeVoiceInputMode,
                    activeWisprFlowMode = activeWisprFlowMode
                )
            }
        }
    }
}

@Composable
fun SetupSection(
    isKeyboardEnabled: Boolean,
    isKeyboardSelected: Boolean,
    isMicPermissionGranted: Boolean,
    onEnableClick: () -> Unit,
    onSelectClick: () -> Unit,
    onMicClick: () -> Unit,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Quick Activation",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Complete these steps to activate your keyboard",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalIconButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("refresh_status_button")
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh Setup Status",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            SetupStepCard(
                stepNumber = "1",
                title = "Enable Type Right",
                description = "Turn on the Type Right keyboard service in Android System Settings.",
                isCompleted = isKeyboardEnabled,
                actionLabel = "Enable",
                testTag = "enable_keyboard_button",
                onClick = onEnableClick
            )

            SetupStepCard(
                stepNumber = "2",
                title = "Select as Default IME",
                description = "Choose Type Right as your primary active input method.",
                isCompleted = isKeyboardSelected,
                actionLabel = "Select",
                testTag = "select_keyboard_button",
                onClick = onSelectClick
            )

            SetupStepCard(
                stepNumber = "3",
                title = "Microphone (Optional)",
                description = "Required only for voice dictation. All typing, swipe, and AI text features work without this.",
                isCompleted = isMicPermissionGranted,
                actionLabel = if (isMicPermissionGranted) "Granted" else "Grant",
                testTag = "grant_mic_button",
                onClick = onMicClick
            )
        }
    }
}

@Composable
fun SetupStepCard(
    stepNumber: String,
    title: String,
    description: String,
    isCompleted: Boolean,
    actionLabel: String,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (isCompleted) Color(0xFF10B981).copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        border = BorderStroke(
            1.dp,
            if (isCompleted) Color(0xFF10B981).copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f).padding(end = 12.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isCompleted) Color(0xFF10B981) else MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(26.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isCompleted) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Text(
                                text = stepNumber,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            }

            if (isCompleted) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "READY",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF10B981)
                    )
                }
            } else {
                Button(
                    onClick = onClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .height(36.dp)
                        .testTag(testTag),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun TypingSandboxSection(
    inputText: String,
    onInputTextChange: (String) -> Unit,
    isKeyboardActive: Boolean
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Live Keyboard Sandbox",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Tap below to test zero-lag typing, suggestions & AI tools",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isKeyboardActive) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = if (isKeyboardActive) "ACTIVE" else "NOT SELECTED",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (isKeyboardActive) Color(0xFF10B981) else MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            OutlinedTextField(
                value = inputText,
                onValueChange = onInputTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp)
                    .testTag("sandbox_text_field"),
                placeholder = {
                    Text(
                        text = "Tap here to open Type Right keyboard and test fast typing, swipe gestures, auto-correction, and AI rewrites...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${inputText.length} chars | ${if (inputText.isBlank()) 0 else inputText.trim().split(Regex("\\s+")).size} words",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (inputText.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { onInputTextChange("") },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear Text", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear", style = MaterialTheme.typography.labelSmall)
                        }

                        Button(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(inputText))
                                Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Text", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // Quick AI Action Test Chips
            if (inputText.isNotEmpty()) {
                val coroutineScope = rememberCoroutineScope()
                var isProcessingAi by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Instant AI Engine Tests (On-Device AICore + Gemini Fallback):",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // 1. Proofreading Test
                        AssistChip(
                            onClick = {
                                if (!isProcessingAi) {
                                    isProcessingAi = true
                                    coroutineScope.launch {
                                        try {
                                            val result = AiPolishManager(context).proofreadText(inputText)
                                            if (result.isNotBlank()) onInputTextChange(result)
                                        } finally {
                                            isProcessingAi = false
                                        }
                                    }
                                }
                            },
                            label = { Text("✏️ Proofread", style = MaterialTheme.typography.labelSmall) }
                        )

                        // 2. Formal Polish Test
                        AssistChip(
                            onClick = {
                                if (!isProcessingAi) {
                                    isProcessingAi = true
                                    coroutineScope.launch {
                                        try {
                                            val result = AiPolishManager(context).polishText(inputText, "formalize")
                                            if (result.isNotBlank()) onInputTextChange(result)
                                        } finally {
                                            isProcessingAi = false
                                        }
                                    }
                                }
                            },
                            label = { Text("👔 Polish (Formal)", style = MaterialTheme.typography.labelSmall) }
                        )

                        // 3. Casual Polish Test
                        AssistChip(
                            onClick = {
                                if (!isProcessingAi) {
                                    isProcessingAi = true
                                    coroutineScope.launch {
                                        try {
                                            val result = AiPolishManager(context).polishText(inputText, "casual")
                                            if (result.isNotBlank()) onInputTextChange(result)
                                        } finally {
                                            isProcessingAi = false
                                        }
                                    }
                                }
                            },
                            label = { Text("😊 Polish (Casual)", style = MaterialTheme.typography.labelSmall) }
                        )

                        // 4. Shorten Polish Test
                        AssistChip(
                            onClick = {
                                if (!isProcessingAi) {
                                    isProcessingAi = true
                                    coroutineScope.launch {
                                        try {
                                            val result = AiPolishManager(context).polishText(inputText, "shorten")
                                            if (result.isNotBlank()) onInputTextChange(result)
                                        } finally {
                                            isProcessingAi = false
                                        }
                                    }
                                }
                            },
                            label = { Text("⚡ Shorten", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AestheticsSection(
    settings: KeyboardSettings,
    activeTheme: MutableState<String>,
    activeIsDarkMode: MutableState<Boolean>,
    activeDynamicThemeEnabled: MutableState<Boolean>,
    activeNumberRowEnabled: MutableState<Boolean>,
    activeAccentColor: MutableState<String>,
    activeHeight: MutableState<String>,
    soundEnabled: MutableState<Boolean>,
    hapticEnabled: MutableState<Boolean>,
    activeKeyBevelEnabled: MutableState<Boolean>,
    activeRetroMonospace: MutableState<Boolean>,
    activeMechanicalSound: MutableState<Boolean>
) {
    val accentColors = listOf(
        "#006A60" to "Emerald Teal",
        "#1E88E5" to "Ocean Blue",
        "#7C4DFF" to "Deep Violet",
        "#00B0FF" to "Cyan Sky",
        "#FF6D00" to "Vibrant Amber",
        "#E91E63" to "Rose Ruby",
        "#43A047" to "Forest Green",
        "#212121" to "Monochrome"
    )

    val heightOptions = listOf(
        KeyboardSettings.HEIGHT_SHORT to "Compact (Default)",
        KeyboardSettings.HEIGHT_NORMAL to "Normal",
        KeyboardSettings.HEIGHT_TALL to "Tall",
        KeyboardSettings.HEIGHT_CUSTOM to "Custom"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. THEMES & COLOR PALETTE ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Themes & Colors",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Choose from modern Material themes or classic retro computer aesthetics",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                val retroThemes = listOf(
                    Triple(KeyboardSettings.THEME_DARK, "Modern Dark", "Neutral charcoal surfaces and soft indigo accent"),
                    Triple(KeyboardSettings.THEME_LIGHT, "Modern Light", "Clean bright surfaces with high legibility"),
                    Triple(KeyboardSettings.THEME_AMOLED, "AMOLED Black", "Pure #000000 pixels for maximum OLED battery saving"),
                    Triple(KeyboardSettings.THEME_RETRO_BEIGE, "IBM Model M (Beige)", "1980s putty chassis, eggshell keys, burnt orange return"),
                    Triple(KeyboardSettings.THEME_RETRO_CRT_GREEN, "CRT Phosphor Green", "Mainframe cyber terminal with glowing emerald phosphor"),
                    Triple(KeyboardSettings.THEME_RETRO_AMBER, "CRT Amber Terminal", "Monochrome warm amber glow with high-contrast chassis"),
                    Triple(KeyboardSettings.THEME_RETRO_MAC1984, "1984 Macintosh", "Iconic Apple platinum chassis and deep slate modifiers"),
                    Triple(KeyboardSettings.THEME_RETRO_SYNTHWAVE, "80s Cyber Synthwave", "Outrun neon magenta return & cyber cyan keycaps")
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    retroThemes.forEach { (themeKey, themeTitle, themeDesc) ->
                        val isSelected = activeTheme.value == themeKey
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    activeTheme.value = themeKey
                                    settings.theme = themeKey
                                    activeIsDarkMode.value = settings.isDarkMode
                                }
                                .testTag("theme_preset_$themeKey"),
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                if (isSelected) 1.5.dp else 1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.size(36.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    color = when (themeKey) {
                                        KeyboardSettings.THEME_RETRO_BEIGE -> Color(0xFFDDD4C4)
                                        KeyboardSettings.THEME_RETRO_CRT_GREEN -> Color(0xFF0F1411)
                                        KeyboardSettings.THEME_RETRO_AMBER -> Color(0xFF16120C)
                                        KeyboardSettings.THEME_RETRO_MAC1984 -> Color(0xFFD2D5D6)
                                        KeyboardSettings.THEME_RETRO_SYNTHWAVE -> Color(0xFF140C24)
                                        KeyboardSettings.THEME_AMOLED -> Color(0xFF000000)
                                        KeyboardSettings.THEME_DARK -> Color(0xFF1E1F23)
                                        else -> Color(0xFFECEFF2)
                                    },
                                    border = BorderStroke(1.dp, Color(0x33000000))
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "⌨",
                                            fontSize = 18.sp,
                                            color = when (themeKey) {
                                                KeyboardSettings.THEME_RETRO_CRT_GREEN -> Color(0xFF39FF14)
                                                KeyboardSettings.THEME_RETRO_AMBER -> Color(0xFFFFB000)
                                                KeyboardSettings.THEME_RETRO_SYNTHWAVE -> Color(0xFF00F0FF)
                                                KeyboardSettings.THEME_DARK, KeyboardSettings.THEME_AMOLED -> Color(0xFFF1F3F5)
                                                else -> Color(0xFF2B251D)
                                            }
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = themeTitle,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        fontFamily = if (themeKey.startsWith("retro")) FontFamily.Monospace else FontFamily.SansSerif,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = themeDesc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        activeTheme.value = themeKey
                                        settings.theme = themeKey
                                        activeIsDarkMode.value = settings.isDarkMode
                                    }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                ModernPreferenceSwitchRow(
                    title = "Material You Dynamic Colors",
                    description = "Extract accent and background hues from your Android wallpaper.",
                    checked = activeDynamicThemeEnabled.value,
                    testTag = "dynamic_theme_switch",
                    onCheckedChange = {
                        activeDynamicThemeEnabled.value = it
                        settings.dynamicThemeEnabled = it
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                ModernPreferenceSwitchRow(
                    title = "Dark Theme",
                    description = "Use high-contrast dark palette for comfortable typing in low light.",
                    checked = activeIsDarkMode.value,
                    testTag = "dark_mode_switch",
                    onCheckedChange = {
                        activeIsDarkMode.value = it
                        settings.isDarkMode = it
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                // Accent Color Palette
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Accent Color Palette",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Personalize highlight badges, enter key, and suggestion pills.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        accentColors.forEach { (hex, name) ->
                            val isSelected = activeAccentColor.value.equals(hex, ignoreCase = true)
                            val color = try {
                                Color(android.graphics.Color.parseColor(hex))
                            } catch (e: Exception) {
                                MaterialTheme.colorScheme.primary
                            }

                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        activeAccentColor.value = hex
                                        settings.accentColor = hex
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = name,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 2. KEYCAPS & VISUAL STYLING ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Keyboard,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Keycaps & Visuals",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Customize tactile bevels, key outlines, and font rendering",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                ModernPreferenceSwitchRow(
                    title = "3D Mechanical Key Bevels",
                    description = "Authentic stepped keycaps with physical tactile drop shadows and mechanical switch look.",
                    checked = activeKeyBevelEnabled.value,
                    testTag = "key_bevel_switch",
                    onCheckedChange = {
                        activeKeyBevelEnabled.value = it
                        settings.keyBevelEnabled = it
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                var keyBorders by remember { mutableStateOf(settings.keyBordersEnabled) }
                ModernPreferenceSwitchRow(
                    title = "Key Borders & Outlines",
                    description = "Display distinct rectangular outlines framing individual keys for enhanced contrast.",
                    checked = keyBorders,
                    testTag = "key_borders_switch",
                    onCheckedChange = {
                        keyBorders = it
                        settings.keyBordersEnabled = it
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                ModernPreferenceSwitchRow(
                    title = "Retro Terminal Monospace Font",
                    description = "Classic fixed-width computer typography across key legends, spacebar, and suggestion pills.",
                    checked = activeRetroMonospace.value,
                    testTag = "retro_monospace_switch",
                    onCheckedChange = {
                        activeRetroMonospace.value = it
                        settings.retroMonospaceEnabled = it
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                var popupKeypress by remember { mutableStateOf(settings.popupOnKeypress) }
                ModernPreferenceSwitchRow(
                    title = "Popup on Keypress",
                    description = "Show visual character preview bubble floating above keys upon touch.",
                    checked = popupKeypress,
                    testTag = "popup_keypress_switch",
                    onCheckedChange = {
                        popupKeypress = it
                        settings.popupOnKeypress = it
                    }
                )
            }
        }

        // --- 3. SOUND & HAPTICS ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Sound & Haptics",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Acoustic audio snaps and physical vibration tactile response",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                ModernPreferenceSwitchRow(
                    title = "Vintage Mechanical Click Audio",
                    description = "Satisfying tactile acoustic snap of vintage mechanical buckling spring switches.",
                    checked = activeMechanicalSound.value,
                    testTag = "mechanical_sound_switch",
                    onCheckedChange = {
                        activeMechanicalSound.value = it
                        settings.mechanicalSoundEnabled = it
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                ModernPreferenceSwitchRow(
                    title = "Key Click Sound",
                    description = "Standard tactile tap click on keystrokes.",
                    checked = soundEnabled.value,
                    testTag = "sound_switch",
                    onCheckedChange = {
                        soundEnabled.value = it
                        settings.soundEnabled = it
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                ModernPreferenceSwitchRow(
                    title = "Haptic Vibration Feedback",
                    description = "Subtle tactile vibration impulse on keystrokes.",
                    checked = hapticEnabled.value,
                    testTag = "haptic_switch",
                    onCheckedChange = {
                        hapticEnabled.value = it
                        settings.hapticEnabled = it
                    }
                )
            }
        }

        // --- 4. LAYOUT & ERGONOMICS ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF10B981).copy(alpha = 0.15f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Straighten,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Layout & Ergonomics",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Reachability, keyboard height, gestures, and system bar clearance",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                ModernPreferenceSwitchRow(
                    title = "Dedicated Number Row",
                    description = "Display numerical keys directly above the QWERTY row for rapid digits.",
                    checked = activeNumberRowEnabled.value,
                    testTag = "number_row_switch",
                    onCheckedChange = {
                        activeNumberRowEnabled.value = it
                        settings.numberRowEnabled = it
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                // Keyboard Height
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Keyboard Height",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Adjust key vertical reach. Compact is recommended to maximize screen visibility.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        heightOptions.forEach { (key, label) ->
                            val isSelected = activeHeight.value == key
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        activeHeight.value = key
                                        settings.height = key
                                    },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                )
                            ) {
                                Text(
                                    text = label,
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                // One-Handed Mode
                var oneHanded by remember { mutableStateOf(settings.oneHandedMode) }
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "One-Handed Mode",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Shrink keyboard toward the left or right edge for effortless thumb reach.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    val oneHandedOptions = listOf(
                        "off" to "Full Width",
                        "right" to "Right Hand",
                        "left" to "Left Hand"
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        oneHandedOptions.forEach { (mode, label) ->
                            val isSelected = oneHanded == mode
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        oneHanded = mode
                                        settings.oneHandedMode = mode
                                    }
                                    .testTag("one_handed_option_$mode"),
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                )
                            ) {
                                Text(
                                    text = label,
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                var spaceSwipe by remember { mutableStateOf(settings.spaceSwipeEnabled) }
                ModernPreferenceSwitchRow(
                    title = "Spacebar Cursor Trackpad",
                    description = "Slide finger horizontally across spacebar to scrub and position the text cursor precisely.",
                    checked = spaceSwipe,
                    testTag = "space_swipe_switch",
                    onCheckedChange = {
                        spaceSwipe = it
                        settings.spaceSwipeEnabled = it
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                var backspaceSwipe by remember { mutableStateOf(settings.backspaceSwipeEnabled) }
                ModernPreferenceSwitchRow(
                    title = "Swipe to Delete",
                    description = "Slide left from backspace to rapidly highlight and delete entire words.",
                    checked = backspaceSwipe,
                    testTag = "backspace_swipe_switch",
                    onCheckedChange = {
                        backspaceSwipe = it
                        settings.backspaceSwipeEnabled = it
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                // System Navigation Bar Clearance
                var navBarClearance by remember { mutableStateOf(settings.navBarClearance) }
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "System Navigation Bar Clearance",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Prevents keys from overlapping the bottom Android system bar or gesture indicator.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    val clearanceOptions = listOf(
                        "auto" to "Auto",
                        "gesture" to "Gesture (48dp)",
                        "3button" to "3-Button (56dp)",
                        "none" to "Minimal"
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        clearanceOptions.forEach { (mode, label) ->
                            val isSelected = navBarClearance == mode
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        navBarClearance = mode
                                        settings.navBarClearance = mode
                                    }
                                    .testTag("nav_clearance_$mode"),
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                )
                            ) {
                                Text(
                                    text = label,
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AiEngineSection(
    settings: KeyboardSettings,
    autocorrectEnabled: MutableState<Boolean>,
    profanityFilterEnabled: MutableState<Boolean>,
    activeVoiceLanguage: MutableState<String>,
    activeClipboardEnabled: MutableState<Boolean>,
    activeOfflineAiEnabled: MutableState<Boolean>,
    activeGeminiAiEnabled: MutableState<Boolean>,
    activeAiModel: MutableState<String>,
    activeWhisperModel: MutableState<String>,
    activeKeyboardLanguage: MutableState<String>,
    activeAiLanguage: MutableState<String>,
    activeManglishEnabled: MutableState<Boolean>,
    doubleSpacePeriodEnabled: MutableState<Boolean>,
    emojiSuggestionsEnabled: MutableState<Boolean>,
    swipeEnabled: MutableState<Boolean>,
    activeVoiceInputMode: MutableState<String>,
    activeWisprFlowMode: MutableState<WisprFlowMode>
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val languages = listOf("en-US", "ml-IN", "hi-IN", "es-ES", "fr-FR", "de-DE", "ja-JP")
    val aiLanguages = listOf(
        "English" to "English (Default)",
        "Malayalam" to "Malayalam (മലയാളം)",
        "Manglish" to "Manglish (Phonetic)",
        "Hindi" to "Hindi (हिन्दी)",
        "Spanish" to "Spanish (Español)",
        "French" to "French (Français)",
        "German" to "German (Deutsch)"
    )

    val keyboardLanguages = listOf(
        "en" to "English (QWERTY)",
        "ml" to "Malayalam (Manglish)"
    )

    var testInputText by remember { mutableStateOf("i has went to store yesterday and buyed three no wait four apples") }
    var isRunningBenchmark by remember { mutableStateOf(false) }
    var benchmarkResultText by remember { mutableStateOf<String?>(null) }
    var benchmarkDurationMs by remember { mutableStateOf<Long?>(null) }
    var benchmarkEngineUsed by remember { mutableStateOf<String?>(null) }

    val isGeminiOn = activeGeminiAiEnabled.value

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. GOOGLE GEMINI FREE CLOUD AI ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF3B82F6).copy(alpha = 0.15f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CloudQueue,
                                    contentDescription = null,
                                    tint = Color(0xFF3B82F6),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Google Gemini Free AI",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF10B981).copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "FREE API",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF10B981),
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                            Text(
                                text = if (isGeminiOn) "Cloud Intelligence Active" else "Disabled",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isGeminiOn) Color(0xFF3B82F6) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = isGeminiOn,
                        onCheckedChange = {
                            activeGeminiAiEnabled.value = it
                            settings.geminiAiEnabled = it
                        },
                        modifier = Modifier.testTag("gemini_ai_switch")
                    )
                }

                Text(
                    text = "Cloud-powered contextual proofreading, nuance tone transformations (Professional, Casual, Rephrase, Shorten, Expand), and grammar reasoning via Google Gemini Free API.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                // Gemini Model Tier Selector
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Gemini Model Tier",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Select model tier balancing processing speed and linguistic nuance.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val modelTiers = listOf(
                        Triple("gemini-2.5-flash", "Gemini 2.5 Flash", "Recommended: Balanced speed and superior quality"),
                        Triple("gemini-2.5-flash-lite", "Gemini 2.5 Flash Lite", "Ultra-fast latency for instant suggestions"),
                        Triple("gemini-1.5-flash", "Gemini 1.5 Flash", "High stability and broad context")
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        modelTiers.forEach { (modelId, modelName, modelDesc) ->
                            val isSelected = activeAiModel.value == modelId
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        activeAiModel.value = modelId
                                        settings.aiModel = modelId
                                    }
                                    .testTag("gemini_model_$modelId"),
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    if (isSelected) 1.5.dp else 1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            activeAiModel.value = modelId
                                            settings.aiModel = modelId
                                        }
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = modelName,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = modelDesc,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                // Capabilities Preview Chips
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Active AI Capabilities",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val capabilities = listOf(
                            "⚡ Proofread & Fix All",
                            "👔 Professional Tone",
                            "💬 Friendly & Casual",
                            "⚡ Concise & Shorten",
                            "📝 Expand & Rephrase"
                        )
                        capabilities.forEach { cap ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                            ) {
                                Text(
                                    text = cap,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                // Interactive Live Test Playground
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Live Gemini Polish Playground",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    OutlinedTextField(
                        value = testInputText,
                        onValueChange = { testInputText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gemini_test_input"),
                        label = { Text("Sample sentence to polish") },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = false,
                        maxLines = 3
                    )

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isRunningBenchmark = true
                                val start = System.currentTimeMillis()
                                val aiPolish = AiPolishManager(context)
                                val result = aiPolish.proofreadText(testInputText)
                                val duration = System.currentTimeMillis() - start

                                benchmarkResultText = result
                                benchmarkDurationMs = duration
                                benchmarkEngineUsed = "Google Gemini (${activeAiModel.value})"
                                isRunningBenchmark = false
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("test_gemini_inference_button"),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isRunningBenchmark && isGeminiOn
                    ) {
                        if (isRunningBenchmark) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Calling Gemini API...")
                        } else {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = "Test",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Test Gemini Polish")
                        }
                    }

                    if (benchmarkResultText != null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = benchmarkEngineUsed ?: "Google Gemini",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "${benchmarkDurationMs ?: 0} ms",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(0xFF10B981)
                                    )
                                }
                                Text(
                                    text = benchmarkResultText ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- 2. TYPING & AUTOCORRECT CONTROLS ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Typing & Autocorrect",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Statistical n-gram suggestions, predictive text, and gestures",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                ModernPreferenceSwitchRow(
                    title = "Next-Word Prediction & Auto-Correct",
                    description = "Instant prefix completions and statistical n-gram suggestions on the toolbar strip.",
                    checked = autocorrectEnabled.value,
                    testTag = "autocorrect_switch",
                    onCheckedChange = {
                        autocorrectEnabled.value = it
                        settings.autocorrectEnabled = it
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                ModernPreferenceSwitchRow(
                    title = "Gesture & Glide Typing",
                    description = "Swipe finger continuously across letters to form words effortlessly.",
                    checked = swipeEnabled.value,
                    testTag = "swipe_typing_switch",
                    onCheckedChange = {
                        swipeEnabled.value = it
                        settings.swipeEnabled = it
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                ModernPreferenceSwitchRow(
                    title = "Quick Double-Space Period",
                    description = "Double-tapping spacebar automatically inserts a period followed by a space.",
                    checked = doubleSpacePeriodEnabled.value,
                    testTag = "double_space_period_switch",
                    onCheckedChange = {
                        doubleSpacePeriodEnabled.value = it
                        settings.doubleSpacePeriod = it
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                ModernPreferenceSwitchRow(
                    title = "Contextual Emoji Suggestions",
                    description = "Predict and display relevant emojis dynamically based on typed text.",
                    checked = emojiSuggestionsEnabled.value,
                    testTag = "emoji_suggestions_switch",
                    onCheckedChange = {
                        emojiSuggestionsEnabled.value = it
                        settings.emojiSuggestionsEnabled = it
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                ModernPreferenceSwitchRow(
                    title = "Profanity Filter",
                    description = "Block offensive language from prediction suggestions and auto-completion.",
                    checked = profanityFilterEnabled.value,
                    testTag = "profanity_filter_switch",
                    onCheckedChange = {
                        profanityFilterEnabled.value = it
                        settings.profanityFilterEnabled = it
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                ModernPreferenceSwitchRow(
                    title = "Clipboard History Manager",
                    description = "Save and pin recent text snippets directly in the keyboard toolbar.",
                    checked = activeClipboardEnabled.value,
                    testTag = "clipboard_manager_switch",
                    onCheckedChange = {
                        activeClipboardEnabled.value = it
                        settings.clipboardEnabled = it
                    }
                )
            }
        }

        // --- 3. LANGUAGES & SPEECH DICTATION ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF8B5CF6).copy(alpha = 0.15f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = Color(0xFF8B5CF6),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Languages & Dictation",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Multilingual typing, phonetic transliteration, and voice recognition",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                ModernPreferenceSwitchRow(
                    title = "Manglish Transliteration Mode",
                    description = "Type in English phonetic letters (e.g., 'namaskaram', 'nandi') to produce Malayalam script.",
                    checked = activeManglishEnabled.value,
                    testTag = "manglish_transliteration_switch",
                    onCheckedChange = {
                        activeManglishEnabled.value = it
                        settings.manglishTransliterationEnabled = it
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                // AI Proofreading Language
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "AI Proofreading & Polish Language",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Target language style used when proofreading or translating text.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        aiLanguages.forEach { (code, label) ->
                            val isSelected = activeAiLanguage.value.equals(code, ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.clickable {
                                    activeAiLanguage.value = code
                                    settings.aiLanguage = code
                                }
                            ) {
                                Text(
                                    text = label,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                // Keyboard Input Language
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Active Keyboard Language",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Primary input layout script. Long-press spacebar to switch on the fly.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        keyboardLanguages.forEach { (code, label) ->
                            val isSelected = activeKeyboardLanguage.value.contains(code, ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        activeKeyboardLanguage.value = code
                                        settings.keyboardLanguage = code
                                    }
                            ) {
                                Text(
                                    text = label,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                // Voice Processing Mode
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Voice Input Mode",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Choose between ultra-fast cloud transcription or private on-device speech.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val voiceModes: List<Pair<String, String>> = listOf(
                            KeyboardSettings.VOICE_MODE_CLOUD to "Fast Cloud",
                            KeyboardSettings.VOICE_MODE_LOCAL to "Private On-Device"
                        )
                        voiceModes.forEach { (modeKey, label) ->
                            val isSelected = activeVoiceInputMode.value == modeKey
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        activeVoiceInputMode.value = modeKey
                                        settings.voiceInputMode = modeKey
                                    }
                            ) {
                                Text(
                                    text = label,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                // Wispr Continuous Dictation Flow
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Wispr Continuous Dictation Flow",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Filter filler words (um, ah), or generate structured bullet thoughts automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val flowModes: List<Pair<WisprFlowMode, String>> = listOf(
                            WisprFlowMode.AUTO to "Smart Polish",
                            WisprFlowMode.VERBATIM to "Literal",
                            WisprFlowMode.BULLETS to "Bullets"
                        )
                        flowModes.forEach { (mode, label) ->
                            val isSelected = activeWisprFlowMode.value == mode
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        activeWisprFlowMode.value = mode
                                        settings.wisprFlowMode = mode
                                    }
                            ) {
                                Text(
                                    text = label,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                // Speech Recognition Locale
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Speech Recognition Locale",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Target speech recognition locale for voice input.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        languages.forEach { lang ->
                            val isSelected = activeVoiceLanguage.value == lang
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.clickable {
                                    activeVoiceLanguage.value = lang
                                    settings.voiceLanguage = lang
                                }
                            ) {
                                Text(
                                    text = lang,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModernPreferenceSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

private fun checkKeyboardEnabled(context: Context): Boolean {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    val enabledImes = imm?.enabledInputMethodList ?: emptyList()
    return enabledImes.any { it.packageName == context.packageName }
}

private fun checkKeyboardSelected(context: Context): Boolean {
    val currentIme = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.DEFAULT_INPUT_METHOD
    )
    return currentIme != null && currentIme.startsWith(context.packageName)
}
