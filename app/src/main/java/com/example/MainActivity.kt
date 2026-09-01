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
            MyApplicationTheme {
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
    val supportTier = remember { mutableStateOf(settings.supportTier) }
    val profanityFilterEnabled = remember { mutableStateOf(settings.profanityFilterEnabled) }
    val cloudSyncEnabled = remember { mutableStateOf(settings.cloudSyncEnabled) }
    val activeIsDarkMode = remember { mutableStateOf(settings.isDarkMode) }
    val activeDynamicThemeEnabled = remember { mutableStateOf(settings.dynamicThemeEnabled) }
    val activeNumberRowEnabled = remember { mutableStateOf(settings.numberRowEnabled) }
    val activeAccentColor = remember { mutableStateOf(settings.accentColor) }

    val activeOfflineAiEnabled = remember { mutableStateOf(settings.offlineAiEnabled) }
    val activeGeminiAiEnabled = remember { mutableStateOf(settings.geminiAiEnabled) }
    val activeAiModel = remember { mutableStateOf(settings.aiModel) }
    val activeWhisperModel = remember { mutableStateOf(settings.whisperModel) }
    val activeVoiceLanguage = remember { mutableStateOf(settings.voiceLanguage) }
    val activeVoiceInputMode = remember { mutableStateOf(settings.voiceInputMode) }
    val activeClipboardEnabled = remember { mutableStateOf(settings.clipboardEnabled) }

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Setup", "Typing Sandbox", "Aesthetics", "AI Engine", "Diagnostics")

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
                        imm?.showInputMethodPicker()
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
                // AESTHETICS TAB
                AestheticsSection(
                    settings = settings,
                    activeIsDarkMode = activeIsDarkMode,
                    activeDynamicThemeEnabled = activeDynamicThemeEnabled,
                    activeNumberRowEnabled = activeNumberRowEnabled,
                    activeAccentColor = activeAccentColor,
                    activeHeight = activeHeight,
                    soundEnabled = soundEnabled,
                    hapticEnabled = hapticEnabled
                )
            }
            3 -> {
                // AI & ENGINE TAB
                AiEngineSection(
                    settings = settings,
                    autocorrectEnabled = autocorrectEnabled,
                    profanityFilterEnabled = profanityFilterEnabled,
                    activeVoiceLanguage = activeVoiceLanguage,
                    activeClipboardEnabled = activeClipboardEnabled,
                    activeOfflineAiEnabled = activeOfflineAiEnabled,
                    activeGeminiAiEnabled = activeGeminiAiEnabled,
                    activeAiModel = activeAiModel,
                    activeWhisperModel = activeWhisperModel
                )
            }
            4 -> {
                // DIAGNOSTICS TAB
                CrashDebugSection()
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
                title = "Microphone Permission",
                description = "Allow real-time neural speech recognition and voice dictation.",
                isCompleted = isMicPermissionGranted,
                actionLabel = "Grant",
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
    activeIsDarkMode: MutableState<Boolean>,
    activeDynamicThemeEnabled: MutableState<Boolean>,
    activeNumberRowEnabled: MutableState<Boolean>,
    activeAccentColor: MutableState<String>,
    activeHeight: MutableState<String>,
    soundEnabled: MutableState<Boolean>,
    hapticEnabled: MutableState<Boolean>
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
        KeyboardSettings.HEIGHT_SHORT to "Compact",
        KeyboardSettings.HEIGHT_NORMAL to "Default",
        KeyboardSettings.HEIGHT_TALL to "Tall"
    )

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
            Text(
                text = "Look & Feel Customization",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            ModernPreferenceSwitchRow(
                title = "Material You Dynamic Colors",
                description = "Extract accent and background hues from your Android system wallpaper.",
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
                description = "Use high-contrast OLED dark mode for night typing.",
                checked = activeIsDarkMode.value,
                testTag = "dark_mode_switch",
                onCheckedChange = {
                    activeIsDarkMode.value = it
                    settings.isDarkMode = it
                }
            )

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

            // Keyboard Height Selector
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Keyboard Height",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Adjust key button vertical reach and spacing.",
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
                                .size(36.dp)
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
                                    tint = if (hex == "#212121") Color.White else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

            ModernPreferenceSwitchRow(
                title = "Haptic Vibration Feedback",
                description = "Tactile physical vibration impulse on keystrokes.",
                checked = hapticEnabled.value,
                testTag = "haptic_switch",
                onCheckedChange = {
                    hapticEnabled.value = it
                    settings.hapticEnabled = it
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

            ModernPreferenceSwitchRow(
                title = "Key Click Sound",
                description = "Snappy mechanical key sound on every character tap.",
                checked = soundEnabled.value,
                testTag = "sound_switch",
                onCheckedChange = {
                    soundEnabled.value = it
                    settings.soundEnabled = it
                }
            )
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
    activeWhisperModel: MutableState<String>
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val languages = listOf("en-US", "es-ES", "fr-FR", "de-DE", "hi-IN", "ja-JP")

    var selectedSubTab by remember { mutableStateOf(0) } // 0: AI Engine Pipeline, 1: Auxiliary Settings

    // Live Benchmark State
    var isRunningBenchmark by remember { mutableStateOf(false) }
    var benchmarkResultText by remember { mutableStateOf<String?>(null) }
    var benchmarkDurationMs by remember { mutableStateOf<Long?>(null) }
    var benchmarkEngineUsed by remember { mutableStateOf<String?>(null) }

    val isOfflineOn = activeOfflineAiEnabled.value
    val isGeminiOn = activeGeminiAiEnabled.value

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
                        text = "AI & Proofreading Engines",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Configure On-Device NLP and Google Gemini Cloud pipelines",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Sub-Tab Switcher: AI Engines vs Auxiliary Settings vs SLM Vocab Sync
            TabRow(
                selectedTabIndex = selectedSubTab,
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .padding(2.dp),
                divider = {}
            ) {
                Tab(
                    selected = selectedSubTab == 0,
                    onClick = { selectedSubTab = 0 },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFF10B981)
                            )
                            Text(
                                text = "Engines",
                                fontWeight = if (selectedSubTab == 0) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                )
                Tab(
                    selected = selectedSubTab == 1,
                    onClick = { selectedSubTab = 1 },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Auxiliary",
                                fontWeight = if (selectedSubTab == 1) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                )
                Tab(
                    selected = selectedSubTab == 2,
                    onClick = { selectedSubTab = 2 },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFF8B5CF6)
                            )
                            Text(
                                text = "Vocab Sync",
                                fontWeight = if (selectedSubTab == 2) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            if (selectedSubTab == 0) {
                // --- ENGINE TOGGLE SWITCHES ---

                // 1. Offline AI Engine Toggle
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    border = BorderStroke(
                        1.dp,
                        if (isOfflineOn) Color(0xFF10B981).copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                    color = if (isOfflineOn) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Memory,
                                            contentDescription = null,
                                            tint = if (isOfflineOn) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = "1. Offline AI Engine",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (isOfflineOn) "On-Device Neural Model Active (0ms)" else "Disabled",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isOfflineOn) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Switch(
                                checked = isOfflineOn,
                                onCheckedChange = {
                                    activeOfflineAiEnabled.value = it
                                    settings.offlineAiEnabled = it
                                },
                                modifier = Modifier.testTag("offline_ai_switch")
                            )
                        }

                        Text(
                            text = "TensorFlow Lite neural correction, deterministic grammar rules, contraction and spell correction running 100% locally on CPU/NPU with 0ms latency and total privacy.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp
                        )
                    }
                }

                // 2. Google Gemini Toggle
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    border = BorderStroke(
                        1.dp,
                        if (isGeminiOn) Color(0xFF3B82F6).copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                    color = if (isGeminiOn) Color(0xFF3B82F6).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.CloudQueue,
                                            contentDescription = null,
                                            tint = if (isGeminiOn) Color(0xFF3B82F6) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = "2. Gemini (Cloud AI)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
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
                            text = "Google Gemini Flash API for deep context proofreading, complex grammar reasoning, and nuance tone transformations (Professional, Casual, Rephrase, Shorten, Expand).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp
                        )
                    }
                }

                // --- ACTIVE ARCHITECTURE STATUS BANNER ---
                val (archTitle, archDesc, archColor, archBadge) = when {
                    isOfflineOn && isGeminiOn -> Quadruple(
                        "Hybrid Intelligent Pipeline",
                        "Offline TFLite model processes text first with 0ms latency. If complex styling or low local confidence is detected, automatically escalates to Google Gemini Cloud with seamless offline fallback.",
                        Color(0xFF10B981),
                        "HYBRID (RECOMMENDED)"
                    )
                    isOfflineOn && !isGeminiOn -> Quadruple(
                        "100% On-Device Private Mode",
                        "All proofreading and style transformations run entirely locally via TFLite neural models and rule matrices. Zero cloud calls or network requests are performed.",
                        Color(0xFF0D9488),
                        "OFFLINE ONLY"
                    )
                    !isOfflineOn && isGeminiOn -> Quadruple(
                        "Gemini Cloud Direct Mode",
                        "All text proofreading and tone styling are sent directly to Google Gemini Cloud API. Local on-device NLP processing is bypassed.",
                        Color(0xFF3B82F6),
                        "CLOUD ONLY"
                    )
                    else -> Quadruple(
                        "All AI Engines Disabled",
                        "AI proofreading and tone transformations are turned off. Standard keyboard typing and basic dictionary lookups remain active.",
                        MaterialTheme.colorScheme.error,
                        "AI OFF"
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = archColor.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, archColor.copy(alpha = 0.35f))
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
                                text = archTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = archColor.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = archBadge,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = archColor,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Text(
                            text = archDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }

                // Architecture Pipeline Breakdown
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Pipeline Execution Flow",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    AiArchitectureRow(
                        title = "1. On-Device TFLite & Local Rule Engine",
                        subtitle = if (isOfflineOn) "Active: Neural model + SymSpell + Grammar rules (0ms latency)" else "Bypassed: Offline AI Engine is toggled off",
                        isPassed = isOfflineOn
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                    AiArchitectureRow(
                        title = "2. Google Gemini Cloud Escalation",
                        subtitle = if (isGeminiOn) "Active: Gemini Flash API escalation for deep nuance & tones" else "Disabled: Gemini Cloud AI is toggled off",
                        isPassed = isGeminiOn
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                    AiArchitectureRow(
                        title = "3. Spatial Keyboard Predictions",
                        subtitle = "Prefix Trie & Key Proximity Matrix (Always Active on CPU)",
                        isPassed = true
                    )
                }

                // Live Test Action Button
                Button(
                    onClick = {
                        coroutineScope.launch {
                            isRunningBenchmark = true
                            val sampleText = "i has went to store yesterday and buyed three no wait four apples"
                            val start = System.currentTimeMillis()
                            val aiPolish = AiPolishManager(context)
                            val result = aiPolish.proofreadText(sampleText)
                            val duration = System.currentTimeMillis() - start

                            benchmarkResultText = result
                            benchmarkDurationMs = duration
                            benchmarkEngineUsed = when {
                                isOfflineOn && isGeminiOn -> "Hybrid Pipeline (Local + Gemini Fallback)"
                                isOfflineOn -> "On-Device Offline Engine (TFLite)"
                                isGeminiOn -> "Google Gemini Cloud API"
                                else -> "AI Engines Disabled (Raw Text)"
                            }
                            isRunningBenchmark = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("test_gemini_inference_button"),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isRunningBenchmark
                ) {
                    if (isRunningBenchmark) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Running AI Pipeline...")
                    } else {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Test",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Run Live Pipeline Benchmark")
                    }
                }

                // Benchmark output card if available
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
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Output: ${benchmarkEngineUsed ?: "AI Pipeline"}",
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
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            } else if (selectedSubTab == 1) {
                // --- AUXILIARY SETTINGS SUB-TAB ---
                ModernPreferenceSwitchRow(
                    title = "Next-Word Prediction & Auto-Correct",
                    description = "Instant neural prefix completions and statistical n-gram suggestions.",
                    checked = autocorrectEnabled.value,
                    testTag = "autocorrect_switch",
                    onCheckedChange = {
                        autocorrectEnabled.value = it
                        settings.autocorrectEnabled = it
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                ModernPreferenceSwitchRow(
                    title = "Profanity Filter",
                    description = "Block offensive language from prediction capsules and auto-completion.",
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

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                // Voice Language Selector
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Speech Recognition Locale",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Primary audio language for real-time dictation engine.",
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
            } else {
                // --- SLM VOCAB SYNC SUB-TAB (selectedSubTab == 2) ---
                var isSyncingVocab by remember { mutableStateOf(false) }
                var lastSyncStatusText by remember { mutableStateOf(settings.lastVocabSyncStatus) }
                var vocabAutoEnabled by remember { mutableStateOf(settings.vocabAutoUpdateEnabled) }
                var vocabIntervalHours by remember { mutableStateOf(settings.vocabUpdateIntervalHours) }
                var totalWords by remember { mutableIntStateOf(settings.totalVocabWordsCount) }
                var userWords by remember { mutableIntStateOf(settings.userWordsCount) }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Status Banner
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF8B5CF6).copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.35f))
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudSync,
                                        contentDescription = null,
                                        tint = Color(0xFF8B5CF6),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "Periodic SLM Dictionary Sync",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF8B5CF6).copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = if (vocabAutoEnabled) "ACTIVE" else "PAUSED",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF8B5CF6),
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Text(
                                text = "Background JobService periodically enriches on-device Small Language Model (SLM) Prefix Tries & SymSpell indices with modern internet slang, popular tech terms, and user-specific vocabulary from clipboard logs.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    // Auto-Update Switch
                    ModernPreferenceSwitchRow(
                        title = "Automatic Periodic Update",
                        description = "Sync trending words and harvest user vocabulary in background via JobScheduler.",
                        checked = vocabAutoEnabled,
                        testTag = "vocab_auto_sync_switch",
                        onCheckedChange = { enabled ->
                            vocabAutoEnabled = enabled
                            settings.vocabAutoUpdateEnabled = enabled
                            if (enabled) {
                                DictionaryUpdateScheduler.schedulePeriodicUpdate(context, forceReschedule = true)
                            } else {
                                DictionaryUpdateScheduler.cancelPeriodicUpdate(context)
                            }
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                    // Sync Frequency Interval Selector
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Sync Frequency Interval",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "How often JobScheduler runs the battery-friendly background dictionary update.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        val intervalOptions = listOf(6 to "Every 6h", 12 to "Every 12h", 24 to "Every 24h", 48 to "Every 48h")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            intervalOptions.forEach { (hours, label) ->
                                val isSelected = vocabIntervalHours == hours
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) Color(0xFF8B5CF6).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) Color(0xFF8B5CF6) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier.clickable {
                                        vocabIntervalHours = hours
                                        settings.vocabUpdateIntervalHours = hours
                                        if (vocabAutoEnabled) {
                                            DictionaryUpdateScheduler.schedulePeriodicUpdate(context, forceReschedule = true)
                                        }
                                    }
                                ) {
                                    Text(
                                        text = label,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color(0xFF8B5CF6) else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                    // Live Vocabulary Metrics Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "${DictionaryUpdateService.TRENDING_WORDS.size}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF8B5CF6)
                                )
                                Text(
                                    text = "Trending Terms",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "${maxOf(userWords, 12)}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF10B981)
                                )
                                Text(
                                    text = "User Words",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "${maxOf(totalWords, 110)}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Total In DB",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Trending Vocabulary Sample Tags
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Sample Trending Vocabulary Pack",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val sampleChips = listOf("rizz", "skibidi", "demure", "gemini", "rag", "tflite", "copilot", "prompting", "fyi", "touch grass", "vibe check", "let him cook", "slm", "quantization")
                            sampleChips.forEach { chip ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color(0xFF8B5CF6).copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.3f))
                                ) {
                                    Text(
                                        text = "#$chip",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF8B5CF6)
                                    )
                                }
                            }
                        }
                    }

                    // Trigger Manual Sync Action Button
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isSyncingVocab = true
                                val result = DictionaryUpdateService.syncDictionary(context)
                                isSyncingVocab = false
                                lastSyncStatusText = result.message
                                totalWords = result.totalWordsActive
                                userWords = result.userWordsHarvested
                                Toast.makeText(context, "SLM Dictionary Synced! (${result.totalWordsActive} terms active)", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("sync_vocab_now_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                        enabled = !isSyncingVocab
                    ) {
                        if (isSyncingVocab) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Updating SLM Dictionary...", fontWeight = FontWeight.Bold, color = Color.White)
                        } else {
                            Icon(imageVector = Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sync Vocabulary Now", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (lastSyncStatusText.isNotEmpty()) {
                        Text(
                            text = "Status: $lastSyncStatusText",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun AiArchitectureRow(
    title: String,
    subtitle: String,
    isPassed: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = if (isPassed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = if (isPassed) "Passed" else "Info",
            tint = if (isPassed) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
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

@Composable
fun CrashDebugSection() {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var logs by remember { mutableStateOf(listOf<CrashLogEntry>()) }
    var aiLogs by remember { mutableStateOf(listOf<AiExecutionLogEntry>()) }
    var selectedTab by remember { mutableStateOf(0) } // 0: AI Engine Logs, 1: Crash/Diagnostics
    var refreshTrigger by remember { mutableStateOf(0) }
    var showConfirmCrashDialog by remember { mutableStateOf(false) }
    var expandedLogIndex by remember { mutableStateOf<Int?>(null) }
    var expandedAiLogIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(refreshTrigger) {
        logs = CrashReporter.getLogs(context)
        aiLogs = AiExecutionLogger.getLogs(context)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("crash_debug_card"),
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
                        text = "Diagnostics & AI Execution Logs",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Track Proofreading & AI Polish (Gemini vs AICore)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FilledTonalIconButton(
                    onClick = { refreshTrigger++ },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh Logs",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Tab Selector: AI Execution Logs vs Crash Diagnostics
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("⚡ AI Logs (${aiLogs.size})", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("🛠 System & Crashes (${logs.size})", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) }
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            if (selectedTab == 0) {
                // AI EXECUTION LOGS
                if (aiLogs.isEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "NO AI ACTIONS LOGGED YET",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Use Proofread or AI Polish in the sandbox above to see live Gemini vs AICore logs.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AI Operations History (${aiLogs.size})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        TextButton(
                            onClick = {
                                AiExecutionLogger.clearLogs(context)
                                Toast.makeText(context, "AI Logs cleared", Toast.LENGTH_SHORT).show()
                                refreshTrigger++
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        aiLogs.forEachIndexed { index, entry ->
                            val isExpanded = expandedAiLogIndex == index
                            val isAiCore = entry.engine.contains("AICore", ignoreCase = true)
                            val isGemini = entry.engine.contains("Gemini", ignoreCase = true)

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expandedAiLogIndex = if (isExpanded) null else index },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Operation Type Badge
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                text = entry.operation,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }

                                        // Engine Badge (AICore On-Device vs Gemini Cloud)
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = when {
                                                isAiCore -> Color(0xFF10B981).copy(alpha = 0.2f)
                                                isGemini -> Color(0xFF3B82F6).copy(alpha = 0.2f)
                                                else -> MaterialTheme.colorScheme.surfaceVariant
                                            }
                                        ) {
                                            Text(
                                                text = when {
                                                    isAiCore -> "⚡ ON-DEVICE AICORE"
                                                    isGemini -> "☁️ GEMINI CLOUD"
                                                    else -> "LOCAL ENGINE"
                                                },
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontFamily = FontFamily.Monospace,
                                                color = when {
                                                    isAiCore -> Color(0xFF10B981)
                                                    isGemini -> Color(0xFF3B82F6)
                                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = "Engine: ${entry.engine} (${entry.durationMs}ms)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Spacer(modifier = Modifier.height(2.dp))

                                    Text(
                                        text = "Input: \"${entry.inputSnippet}\"",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = if (isExpanded) 10 else 1
                                    )

                                    Text(
                                        text = "Output: \"${entry.outputSnippet}\"",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = if (isExpanded) 10 else 1
                                    )

                                    AnimatedVisibility(visible = isExpanded) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                                            Text(
                                                text = "Timestamp: ${entry.timestamp}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            OutlinedButton(
                                                onClick = {
                                                    clipboardManager.setText(AnnotatedString(entry.toFormattedString()))
                                                    Toast.makeText(context, "AI log copied!", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Copy Log Entry", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // CRASH / DIAGNOSTIC LOGS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            CrashReporter.logCustomError(
                                context,
                                "User triggered diagnostic check via settings console."
                            )
                            Toast.makeText(context, "Logged diagnostic entry!", Toast.LENGTH_SHORT).show()
                            refreshTrigger++
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Log Check", style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = { showConfirmCrashDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test Crash", style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (logs.isEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "NO SYSTEM ISSUES DETECTED",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "All input method background services are running stably.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Captured Records (${logs.size})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        TextButton(
                            onClick = {
                                CrashReporter.clearLogs(context)
                                Toast.makeText(context, "Logs cleared", Toast.LENGTH_SHORT).show()
                                refreshTrigger++
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear All", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        logs.forEachIndexed { index, entry ->
                            val isExpanded = expandedLogIndex == index
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expandedLogIndex = if (isExpanded) null else index },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (entry.type == "CRASH") MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
                                        ) {
                                            Text(
                                                text = entry.type,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                color = if (entry.type == "CRASH") MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }

                                        Text(
                                            text = entry.timestamp,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = entry.exceptionClass.substringAfterLast('.'),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Spacer(modifier = Modifier.height(2.dp))

                                    Text(
                                        text = entry.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = if (isExpanded) 20 else 2,
                                        lineHeight = 16.sp
                                    )

                                    AnimatedVisibility(visible = isExpanded) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 10.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                                            Text(
                                                text = "Thread: ${entry.threadName}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )

                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.padding(8.dp)) {
                                                    Text(
                                                        text = "STACK TRACE",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = entry.stackTrace,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 10.sp,
                                                        lineHeight = 14.sp,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                OutlinedButton(
                                                    onClick = {
                                                        clipboardManager.setText(AnnotatedString(entry.toFormattedString()))
                                                        Toast.makeText(context, "Full log copied!", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Copy", style = MaterialTheme.typography.labelSmall)
                                                }

                                                OutlinedButton(
                                                    onClick = {
                                                        val sendIntent = Intent().apply {
                                                            action = Intent.ACTION_SEND
                                                            putExtra(Intent.EXTRA_TEXT, entry.toFormattedString())
                                                            type = "text/plain"
                                                        }
                                                        val shareIntent = Intent.createChooser(sendIntent, "Share Diagnostics Log")
                                                        context.startActivity(shareIntent)
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Share", style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showConfirmCrashDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmCrashDialog = false },
            title = {
                Text(
                    "Simulate Crash?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "This intentionally throws an uncaught exception to test that the CrashReporter successfully captures full trace data.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmCrashDialog = false
                        CrashReporter.triggerSimulatedCrash()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Trigger Crash")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmCrashDialog = false }) {
                    Text("Cancel")
                }
            }
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
