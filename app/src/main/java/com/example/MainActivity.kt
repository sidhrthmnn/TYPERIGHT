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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashReporter.init(this)
        AiPolishBackend.initialize(this)
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
                    MainMinimalScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun MainMinimalScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val settings = remember { KeyboardSettings(context) }
    val dataStore = remember { settings.dataStore }
    val userPrefs by dataStore.userPreferencesFlow.collectAsState(initial = dataStore.currentSnapshot())
    val scrollState = rememberScrollState()

    var isKeyboardEnabled by remember { mutableStateOf(false) }
    var isKeyboardSelected by remember { mutableStateOf(false) }
    var isMicPermissionGranted by remember { mutableStateOf(false) }

    fun refreshStatus() {
        isKeyboardEnabled = checkKeyboardEnabled(context)
        isKeyboardSelected = checkKeyboardSelected(context)
        isMicPermissionGranted = MicrophonePermissionHelper.hasMicrophonePermission(context)
    }

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        isMicPermissionGranted = isGranted
        if (isGranted) {
            android.widget.Toast.makeText(context, "Microphone permission granted! Voice typing is ready.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val activity = context as? ComponentActivity
    val initialTab = remember { activity?.intent?.getIntExtra("target_tab", 0) ?: 0 }
    var selectedTab by remember { mutableIntStateOf(if (initialTab in 0..3) initialTab else 0) }
    val tabs = listOf("Sandbox", "Predictive Systems", "AI Polish", "Settings")

    LaunchedEffect(Unit) {
        refreshStatus()
        val shouldRequestMic = activity?.intent?.getBooleanExtra("request_mic_permission", false) == true
        if (shouldRequestMic && !MicrophonePermissionHelper.hasMicrophonePermission(context)) {
            activity.intent.removeExtra("request_mic_permission")
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refreshStatus()
                val shouldRequestMic = activity?.intent?.getBooleanExtra("request_mic_permission", false) == true
                if (shouldRequestMic && !MicrophonePermissionHelper.hasMicrophonePermission(context)) {
                    activity.intent.removeExtra("request_mic_permission")
                    micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- MINIMAL TOP BAR ---
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
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Keyboard,
                            contentDescription = "Type Right",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = "Type Right",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "100% On-Device Local AI",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Keyboard Status Pill
            val isFullyActive = isKeyboardEnabled && isKeyboardSelected
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isFullyActive) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, if (isFullyActive) Color(0xFF10B981).copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isFullyActive) Color(0xFF10B981) else Color(0xFFF59E0B))
                    )
                    Text(
                        text = if (isFullyActive) "Active" else "Setup Needed",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isFullyActive) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // --- QUICK SETUP CARD (Shows only if setup not complete) ---
        AnimatedVisibility(visible = !isKeyboardEnabled || !isKeyboardSelected || !isMicPermissionGranted) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Complete Keyboard Setup",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (!isKeyboardEnabled) {
                        SetupRowMinimal(
                            title = "1. Enable Keyboard",
                            action = "Enable",
                            testTag = "step_enable_keyboard_button"
                        ) {
                            context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                        }
                    }

                    if (isKeyboardEnabled && !isKeyboardSelected) {
                        SetupRowMinimal(
                            title = "2. Select Type Right IME",
                            action = "Select",
                            testTag = "step_select_keyboard_button"
                        ) {
                            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                            imm?.showInputMethodPicker()
                        }
                    }

                    if (!isMicPermissionGranted) {
                        SetupRowMinimal(
                            title = "3. Microphone Permission",
                            action = "Allow",
                            testTag = "step_mic_permission_button"
                        ) {
                            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                }
            }
        }

        // --- NAVIGATION TABS (SEGMENTED BUTTON BOXES) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            tabs.forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                Surface(
                    onClick = { selectedTab = index },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("tab_${title.lowercase().replace('-', '_')}"),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    ),
                    shadowElevation = if (isSelected) 2.dp else 0.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 3.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                lineHeight = 13.5.sp
                            ),
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            softWrap = true,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // --- TAB CONTENT ---
        when (selectedTab) {
            0 -> SandboxTabSection(
                settings = settings,
                isMicPermissionGranted = isMicPermissionGranted,
                onRequestMicPermission = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }
            )
            1 -> PredictiveSystemsTabSection(settings = settings)
            2 -> AiPolishFlashLiteTabSection(settings = settings)
            3 -> PreferencesTabSection(settings = settings)
        }
    }
}

@Composable
private fun SetupRowMinimal(
    title: String,
    action: String,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        )
        Button(
            onClick = onClick,
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            modifier = Modifier.heightIn(min = 34.dp).testTag(testTag)
        ) {
            Text(action, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun SandboxTabSection(
    settings: KeyboardSettings,
    isMicPermissionGranted: Boolean,
    onRequestMicPermission: () -> Unit
) {
    var inputText by remember { mutableStateOf("Type something here to test typing, predictions, and grammar...") }
    var polishFeedback by remember { mutableStateOf<String?>(null) }
    var isPolishing by remember { mutableStateOf(false) }
    var isVoiceListening by remember { mutableStateOf(false) }
    var voiceLevel by remember { mutableFloatStateOf(0f) }
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val voiceService = remember { VoiceRecordingSttService(context) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live Interactive Sandbox",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            if (!isMicPermissionGranted) {
                                onRequestMicPermission()
                                return@FilledTonalIconButton
                            }
                            if (isVoiceListening) {
                                isVoiceListening = false
                                voiceService.stopRecording(coroutineScope, shouldPolish = false) { finalTx ->
                                    if (finalTx.isNotBlank()) {
                                        inputText = finalTx
                                        polishFeedback = "Voice input transcribed: $finalTx"
                                    }
                                }
                            } else {
                                isVoiceListening = true
                                polishFeedback = "Listening... Speak into the microphone now."
                                voiceService.startRecording(
                                    scope = coroutineScope,
                                    onPartialText = { partial ->
                                        if (partial.isNotBlank()) {
                                            inputText = partial
                                        }
                                    },
                                    onLevelChange = { voiceLevel = it }
                                )
                            }
                        },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (isVoiceListening) Color(0xFFEF4444) else MaterialTheme.colorScheme.primaryContainer,
                            contentColor = if (isVoiceListening) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.size(34.dp).testTag("sandbox_mic_dictation_button")
                    ) {
                        Icon(
                            imageVector = if (isVoiceListening) Icons.Default.Close else Icons.Default.Mic,
                            contentDescription = if (isVoiceListening) "Stop Dictation" else "Voice Dictation",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (inputText.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                inputText = ""
                                polishFeedback = null
                                if (isVoiceListening) {
                                    isVoiceListening = false
                                    voiceService.cancelRecording()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Clear", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            if (isVoiceListening) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFEF4444).copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        VoiceWaveformVisualizer(
                            audioLevel = voiceLevel,
                            accentColor = Color(0xFFEF4444),
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Listening to voice... Speak now. Tap the mic button to finish.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFDC2626),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.fillMaxWidth().testTag("sandbox_text_field"),
                shape = RoundedCornerShape(12.dp),
                placeholder = { Text("Tap to test keyboard input...") },
                minLines = 3,
                maxLines = 5,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
            )

            // Informational Notice on Text Field Availability
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Notice",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Note: Prediction and correction features are not available in all text fields (such as passwords, PINs, or fields with input flags disabling suggestions).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }

            // One-Tap Actions (Flash Lite Polish, Auto Format & Copy)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (inputText.isBlank()) return@Button
                        isPolishing = true
                        polishFeedback = "Polishing with ${AiPolishBackend.label}..."
                        val original = inputText
                        coroutineScope.launch {
                            val start = System.currentTimeMillis()
                            try {
                                val result = AiPolishBackend.generatePolish(original, PolishMode.POLISH)
                                if (!result.isNullOrBlank() && inputText == original) {
                                    inputText = result
                                    polishFeedback = "Polished in ${System.currentTimeMillis() - start}ms via ${AiPolishBackend.label}"
                                } else {
                                    polishFeedback = "No edit applied. Text changed or AI is unavailable."
                                }
                            } catch (e: kotlinx.coroutines.CancellationException) { throw e
                            } catch (e: Exception) { polishFeedback = e.message ?: "Polish failed"
                            } finally { isPolishing = false }
                        }
                    },
                    modifier = Modifier.weight(1f).testTag("sandbox_flash_lite_polish_button"),
                    enabled = !isPolishing && inputText.isNotBlank(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isPolishing) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Polishing...", style = MaterialTheme.typography.labelSmall)
                    } else {
                        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Proofread", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = {
                        if (inputText.isBlank()) return@Button
                        isPolishing = true
                        polishFeedback = "Contextually auto-formatting..."
                        val original = inputText
                        coroutineScope.launch {
                            val start = System.currentTimeMillis()
                            try {
                                val result = AiPolishBackend.generatePolish(original, PolishMode.AUTO_FORMAT)
                                    ?: kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                                        OnDeviceNeuralPolishEngine.getInstance(context).autoFormatAndCorrect(original)
                                    }
                                if (inputText == original) inputText = result
                                polishFeedback = "Auto formatted in ${System.currentTimeMillis() - start}ms"
                            } catch (e: kotlinx.coroutines.CancellationException) { throw e
                            } catch (e: Exception) { polishFeedback = e.message ?: "Auto-format failed"
                            } finally { isPolishing = false }
                        }
                    },
                    modifier = Modifier.weight(1f).testTag("sandbox_auto_format_button"),
                    enabled = !isPolishing && inputText.isNotBlank(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(Icons.Default.AutoFixNormal, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Auto Format", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(inputText))
                        Toast.makeText(context, "Copied text", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(10.dp),
                    enabled = inputText.isNotBlank()
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                }
            }

            polishFeedback?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun PredictiveSystemsTabSection(settings: KeyboardSettings) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val dictionaryManager = remember { DictionaryManager.getInstance(context) }
    val predictiveService = remember { PredictiveTextSuggestionService.getInstance(context) }
    val userDictRepo = remember { UserDictionaryRepository.getInstance(context) }

    val customEntries by userDictRepo.allCustomEntriesFlow.collectAsState(initial = emptyList())
    val frequentWords by remember { userDictRepo.getFrequentlyUsedWordsFlow(15) }.collectAsState(initial = emptyList())
    val blockedSuggestions by userDictRepo.allBlockedSuggestionsFlow.collectAsState(initial = emptyList())
    val totalNGrams by userDictRepo.totalNGramCountFlow.collectAsState(initial = 0)
    val topBigrams by remember { userDictRepo.getTopNGramsFlow(2, 8) }.collectAsState(initial = emptyList())
    val topTrigrams by remember { userDictRepo.getTopNGramsFlow(3, 8) }.collectAsState(initial = emptyList())

    var testInput by remember { mutableStateOf("how are") }
    var selectedEngineFilter by remember { mutableStateOf("All") }
    var newWordInput by remember { mutableStateOf("") }
    var newShortcutInput by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header with Engine Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Predictive & Autocorrect Systems",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Gboard, SwiftKey & Apple QuickType On-Device Engines",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "Active • Sub-5ms",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

            // System Cards Grid
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // 1. Gboard System Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Spellcheck, contentDescription = null, tint = Color(0xFF4285F4), modifier = Modifier.size(18.dp))
                            Text("Google Gboard Core Architecture", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "• SymSpell Precomputed Symmetric Deletion (O(1) fuzzy edit distance lookup across 20,000+ words)\n" +
                                   "• Bivariate Gaussian Spatial Key-Proximity Model (P(tap | key) centroid likelihood)\n" +
                                   "• WordTrie prefix index with unigram frequency ranking",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }

                // 2. Microsoft SwiftKey System Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Psychology, contentDescription = null, tint = Color(0xFF0078D4), modifier = Modifier.size(18.dp))
                            Text("Microsoft SwiftKey N-Gram Architecture", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "• Multi-Order N-Gram Statistical Language Model (Quadgram / Trigram / Bigram Katz Backoff)\n" +
                                   "• Dynamic User Personalization (Real-time on-device bigram learning)\n" +
                                   "• Contextual Emoji Semantic Predictor (Associates context words with matching emojis)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }

                // 3. Apple QuickType System Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.TouchApp, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                            Text("Apple QuickType Interface Architecture", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "• 3-Slot Dynamic Candidate Strip (Left: Literal input | Center: Autocorrect pill | Right: Next word)\n" +
                                   "• One-Tap Backspace Revert (Reverts autocorrect on immediate backspace and suppresses loop)\n" +
                                   "• Smart Contraction & Capitalization normalization (dont -> don't, im -> I'm)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

            // Interactive Live Playground
            Text(
                text = "Live Multi-Engine Playground",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            OutlinedTextField(
                value = testInput,
                onValueChange = { testInput = it },
                modifier = Modifier.fillMaxWidth().testTag("predictive_playground_input"),
                shape = RoundedCornerShape(10.dp),
                placeholder = { Text("Type words e.g. 'how are', 'dont', 'helo', 'i love coffee'") }
            )

            // Quick Samples
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("how are", "dont", "helo wrld", "i love coffee", "good morning", "see you").forEach { sample ->
                    AssistChip(
                        onClick = { testInput = sample },
                        label = { Text(sample, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            // Real-Time Analysis Output
            val words = remember(testInput) {
                testInput.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
            }
            val activePrefix = remember(testInput, words) {
                if (testInput.endsWith(" ")) "" else words.lastOrNull() ?: ""
            }
            val previousWords = remember(testInput, words, activePrefix) {
                if (activePrefix.isEmpty()) words else words.dropLast(1)
            }

            // Input Buffer State
            val bufferState = remember(testInput, activePrefix, previousWords) {
                TextInputBufferState(
                    typedWord = activePrefix,
                    activePrefix = activePrefix,
                    previousWords = previousWords,
                    timestamp = System.currentTimeMillis()
                )
            }

            // Fetch text suggestions through the PredictiveTextSuggestionService layer
            var serviceResult by remember { mutableStateOf(PredictiveTextSuggestions()) }
            LaunchedEffect(bufferState) {
                serviceResult = predictiveService.fetchSuggestions(bufferState)
            }

            val emojiMatches = remember(words) {
                words.mapNotNull { w ->
                    dictionaryManager.gboardEngine.emojiIntentMap[w.lowercase()]?.let { emoji -> "$w -> $emoji" }
                }.distinct()
            }

            // Shortcut Expansion Banner if active
            if (serviceResult.shortcutExpansion != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                        Text(
                            text = "Custom Shortcut: \"$activePrefix\" expands to \"${serviceResult.shortcutExpansion}\"",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            // Apple QuickType 3-Slot Visualizer (powered by service layer)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Service Layer: 3-Slot Output (Apple QuickType & Gboard):",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Slot 1: Left (Literal)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Slot 1 (Literal)", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = "\"${serviceResult.leftCandidate.ifEmpty { activePrefix.ifEmpty { "—" } }}\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }

                        // Slot 2: Center (Autocorrect Highlighted)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (serviceResult.isCenterAutocorrecting) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (serviceResult.isCenterAutocorrecting) "Slot 2 (Autocorrect)" else "Slot 2 (Candidate)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    color = if (serviceResult.isCenterAutocorrecting) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = serviceResult.centerCandidate.ifEmpty { "—" },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (serviceResult.isCenterAutocorrecting) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                            }
                        }

                        // Slot 3: Right (Predictive)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Slot 3 (Next Word)", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = serviceResult.rightCandidate.ifEmpty { "—" },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // SwiftKey Next-Words & Emojis
            if (serviceResult.suggestionsList.isNotEmpty() || emojiMatches.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "SwiftKey Statistical N-Gram Suggestions:",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF0078D4),
                            fontWeight = FontWeight.Bold
                        )

                        if (serviceResult.suggestionsList.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                Text("Suggestions:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                for (word in serviceResult.suggestionsList) {
                                    AssistChip(
                                        onClick = {
                                            testInput = if (testInput.endsWith(" ") || testInput.isEmpty()) "$testInput$word " else "${words.dropLast(1).joinToString(" ")} $word ".trimStart()
                                        },
                                        label = { Text(word, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                        }

                        if (serviceResult.phraseCompletions.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                Text("Phrases:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                for (phrase in serviceResult.phraseCompletions) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                                        modifier = Modifier.clickable { testInput = "$testInput $phrase " }
                                    ) {
                                        Text(
                                            text = phrase,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (emojiMatches.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Contextual emojis:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                for (match in emojiMatches) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                    ) {
                                        Text(
                                            text = match,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Room Database: Custom Dictionary & Shortcuts Management
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Room Custom Dictionary & Shortcuts",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${customEntries.size} custom entries • ${frequentWords.size} frequent words${if (blockedSuggestions.isNotEmpty()) " • ${blockedSuggestions.size} removed" else ""} stored in Room",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Add Custom Word / Shortcut Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newWordInput,
                            onValueChange = { newWordInput = it },
                            placeholder = { Text("Word / Phrase", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1.3f).testTag("custom_word_input"),
                            shape = RoundedCornerShape(8.dp)
                        )
                        OutlinedTextField(
                            value = newShortcutInput,
                            onValueChange = { newShortcutInput = it },
                            placeholder = { Text("Shortcut (e.g. omw)", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("custom_shortcut_input"),
                            shape = RoundedCornerShape(8.dp)
                        )
                        Button(
                            onClick = {
                                if (newWordInput.isNotBlank()) {
                                    coroutineScope.launch {
                                        userDictRepo.addCustomEntry(
                                            word = newWordInput.trim(),
                                            shortcut = newShortcutInput.trim().ifEmpty { null },
                                            dictionaryManager = dictionaryManager
                                        )
                                        newWordInput = ""
                                        newShortcutInput = ""
                                    }
                                }
                            },
                            enabled = newWordInput.isNotBlank(),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("add_custom_word_button")
                        ) {
                            Text("Add", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    // Display Custom Entries
                    if (customEntries.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Custom Words & Shortcuts:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (entry in customEntries) {
                                    InputChip(
                                        selected = false,
                                        onClick = {
                                            testInput = entry.shortcut ?: entry.word
                                        },
                                        label = {
                                            Text(
                                                text = if (!entry.shortcut.isNullOrEmpty()) "${entry.shortcut} → ${entry.word}" else entry.word,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
                                        trailingIcon = {
                                            IconButton(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        userDictRepo.deleteCustomEntry(entry)
                                                    }
                                                },
                                                modifier = Modifier.size(16.dp)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Delete", modifier = Modifier.size(12.dp))
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Display Frequent Words
                    if (frequentWords.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Frequently Used Words (Room):", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (freq in frequentWords.take(10)) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                    ) {
                                        Text(
                                            text = "${freq.word} (${freq.frequency})",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Display Stored N-Gram Frequency Stats
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Offline N-Gram Frequencies (Room):",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "$totalNGrams transitions stored",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (topBigrams.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (ngram in topBigrams) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                    ) {
                                        Text(
                                            text = "${ngram.context} → ${ngram.nextWord} (${ngram.frequency})",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Display Blocked / Removed Suggestions (with unblock / restore action)
                    if (blockedSuggestions.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Removed Suggestions (Long-press to bin):",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = "${blockedSuggestions.size} blocked",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (blocked in blockedSuggestions) {
                                    InputChip(
                                        selected = false,
                                        onClick = { },
                                        label = {
                                            Text(
                                                text = blocked.originalWord.ifEmpty { blocked.word },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        },
                                        colors = InputChipDefaults.inputChipColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                        ),
                                        trailingIcon = {
                                            IconButton(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        userDictRepo.unblockSuggestion(blocked.word)
                                                    }
                                                },
                                                modifier = Modifier.size(16.dp).testTag("unblock_suggestion_${blocked.word}")
                                            ) {
                                                Icon(
                                                    Icons.Default.Refresh,
                                                    contentDescription = "Restore suggestion",
                                                    modifier = Modifier.size(13.dp),
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiPolishFlashLiteTabSection(settings: KeyboardSettings) {
    AiBackendSettings(settings)
}

@Composable
private fun PreferencesTabSection(settings: KeyboardSettings) {
    var autocorrect by remember { mutableStateOf(settings.autocorrectEnabled) }
    var autocorrectSensitivity by remember { mutableStateOf(settings.autocorrectSensitivity) }
    var haptics by remember { mutableStateOf(settings.hapticEnabled) }
    var sound by remember { mutableStateOf(settings.soundEnabled) }
    var numberRow by remember { mutableStateOf(settings.numberRowEnabled) }
    var theme by remember { mutableStateOf(settings.theme) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. Typing & Correction ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Keyboard, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
                        }
                    }
                    Text(
                        text = "Typing & Correction",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                MinimalSwitchRow(
                    title = "Auto-Correction",
                    subtitle = "Automatically correct typos and contractions on space",
                    checked = autocorrect,
                    onCheckedChange = {
                        autocorrect = it
                        settings.autocorrectEnabled = it
                    },
                    testTag = "pref_autocorrect_switch"
                )

                AnimatedVisibility(visible = autocorrect) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Correction Sensitivity",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                KeyboardSettings.SENSITIVITY_MILD to "Mild",
                                KeyboardSettings.SENSITIVITY_BALANCED to "Balanced",
                                KeyboardSettings.SENSITIVITY_AGGRESSIVE to "Aggressive"
                            ).forEach { (sensKey, label) ->
                                FilterChip(
                                    selected = autocorrectSensitivity.equals(sensKey, ignoreCase = true),
                                    onClick = {
                                        autocorrectSensitivity = sensKey
                                        settings.autocorrectSensitivity = sensKey
                                    },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.testTag("pref_sensitivity_${sensKey.lowercase()}")
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                MinimalSwitchRow(
                    title = "Number Row",
                    subtitle = "Show dedicated digit row above QWERTY keyboard",
                    checked = numberRow,
                    onCheckedChange = {
                        numberRow = it
                        settings.numberRowEnabled = it
                    },
                    testTag = "pref_number_row_switch"
                )
            }
        }

        // --- 2. Feedback & Haptics ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.TouchApp, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
                        }
                    }
                    Text(
                        text = "Touch & Feedback",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                MinimalSwitchRow(
                    title = "Haptic Vibration",
                    subtitle = "Tactile haptic pulse on every keypress",
                    checked = haptics,
                    onCheckedChange = {
                        haptics = it
                        settings.hapticEnabled = it
                    },
                    testTag = "pref_haptic_switch"
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                MinimalSwitchRow(
                    title = "Keypress Sound",
                    subtitle = "Subtle auditory click sound on tap",
                    checked = sound,
                    onCheckedChange = {
                        sound = it
                        settings.soundEnabled = it
                    },
                    testTag = "pref_sound_switch"
                )
            }
        }

        // --- 3. Theme & Appearance ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(18.dp))
                        }
                    }
                    Text(
                        text = "Theme & Appearance",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        KeyboardSettings.THEME_LIGHT to "Light",
                        KeyboardSettings.THEME_DARK to "Dark Mode",
                        KeyboardSettings.THEME_NIGHT to "Night (AMOLED)"
                    ).forEach { (themeKey, label) ->
                        FilterChip(
                            selected = theme.equals(themeKey, ignoreCase = true),
                            onClick = {
                                theme = themeKey
                                settings.theme = themeKey
                            },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("theme_chip_${themeKey.lowercase()}")
                        )
                    }
                }
            }
        }

        // --- 4. Privacy Guarantee ---
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "100% On-Device Privacy",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Keystrokes, predictive models, voice dictation, and Gemma 3 AI polish run strictly on your device.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MinimalSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
