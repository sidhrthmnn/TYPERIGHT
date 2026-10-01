package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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

class MainActivity : ComponentActivity() {
    private var navigationRequest by mutableIntStateOf(0)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasExtra("target_tab")) navigationRequest++
    }

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

            SideEffect {
                androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !isDarkTheme
                    isAppearanceLightNavigationBars = !isDarkTheme
                }
            }
            CompanionAppTheme(darkTheme = isDarkTheme, midnight = userPrefs.theme == KeyboardSettings.THEME_NIGHT) {
                MainMinimalScreen(navigationRequest = navigationRequest)
            }
        }
    }
}

@Composable
fun MainMinimalScreen(modifier: Modifier = Modifier, navigationRequest: Int = 0) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val settings = remember { KeyboardSettings(context) }
    val dataStore = remember { settings.dataStore }
    val userPrefs by dataStore.userPreferencesFlow.collectAsState(initial = dataStore.currentSnapshot())
    val scrollStates = List(4) { rememberScrollState() }
    val savedPages = rememberSaveableStateHolder()
    val focusManager = LocalFocusManager.current
    val softwareKeyboard = LocalSoftwareKeyboardController.current

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
    val initialTab = remember { activity?.intent?.getIntExtra("target_tab", 3) ?: 3 }
    var selectedTab by rememberSaveable { mutableIntStateOf(if (initialTab in 0..3) initialTab else 3) }
    val tabs = listOf("Home", "Typing", "AI polish", "Settings")
    val tabIcons = listOf(Icons.Default.Home, Icons.AutoMirrored.Filled.MenuBook, Icons.Default.AutoAwesome, Icons.Default.Settings)
    fun navigate(index: Int) {
        focusManager.clearFocus()
        softwareKeyboard?.hide()
        selectedTab = index
    }
    BackHandler(enabled = selectedTab != 3) { navigate(3) }

    LaunchedEffect(navigationRequest) {
        if (navigationRequest > 0) {
            val destination = activity?.intent?.getIntExtra("target_tab", 3) ?: 3
            navigate(if (destination in 0..3) destination else 3)
        }
    }

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

    Scaffold(
        modifier = modifier.fillMaxSize().imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(shape = RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.EditNote, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                    }
                }
                Text("Type Right", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                val active = isKeyboardEnabled && isKeyboardSelected
                Surface(
                    onClick = { navigate(0) }, modifier = Modifier.testTag("app_setup_status"),
                    color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                    shape = CircleShape, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(if (active) Icons.Default.CheckCircle else Icons.Default.Tune, null,
                            tint = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp))
                        Text(if (active) "Ready" else "Set up", style = MaterialTheme.typography.labelMedium,
                            color = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                tabs.forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = selectedTab == index, onClick = { navigate(index) },
                        icon = { Icon(tabIcons[index], null, modifier = Modifier.size(22.dp)) },
                        label = { Text(title, style = MaterialTheme.typography.labelMedium) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant, unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant),
                        modifier = Modifier.testTag(listOf("nav_home", "nav_typing", "nav_ai", "nav_settings")[index])
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentAlignment = Alignment.TopCenter) {
            savedPages.SaveableStateProvider(selectedTab) {
                Column(
                    Modifier.widthIn(max = 680.dp).fillMaxSize().verticalScroll(scrollStates[selectedTab])
                        .padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 28.dp).testTag("app_page_$selectedTab"),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (selectedTab == 0) "Your space" else tabs[selectedTab].replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.headlineLarge)
                        Text(listOf("Get ready to write. Try something new.", "Personal words. Smarter suggestions.",
                            "A little clarity, whenever you need it.", "Small details. Better typing.")[selectedTab],
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    when (selectedTab) {
                        0 -> {
                            if (!isKeyboardEnabled || !isKeyboardSelected || !isMicPermissionGranted) {
                                AppSettingsCard {
                                    Text("Let's get you set up", style = MaterialTheme.typography.titleMedium)
                                    Text("A couple of steps and you're ready to write.", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (!isKeyboardEnabled) SetupRowMinimal("Enable Type Right", "Enable", "step_enable_keyboard_button") {
                                        context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                                    }
                                    if (isKeyboardEnabled && !isKeyboardSelected) SetupRowMinimal("Choose Type Right", "Choose", "step_select_keyboard_button") {
                                        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.showInputMethodPicker()
                                    }
                                    if (!isMicPermissionGranted) SetupRowMinimal("Voice typing (optional)", "Allow", "step_mic_permission_button") {
                                        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            }
                            SandboxTabSection(settings, isMicPermissionGranted) { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                        }
                        1 -> PredictiveSystemsTabSection(settings)
                        2 -> AiBackendSettings(settings)
                        3 -> AppPreferencesScreen(settings, onOpenTyping = { navigate(1) }, onOpenAi = { navigate(2) })
                    }
                }
            }
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
            modifier = Modifier.heightIn(min = 48.dp).testTag(testTag)
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
    var inputText by rememberSaveable { mutableStateOf("Type something here to test typing, predictions, and grammar...") }
    var polishFeedback by remember { mutableStateOf<String?>(null) }
    var isPolishing by remember { mutableStateOf(false) }
    var isVoiceListening by remember { mutableStateOf(false) }
    var voiceLevel by remember { mutableFloatStateOf(0f) }
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val voiceService = remember { VoiceRecordingSttService(context) }
    DisposableEffect(voiceService) { onDispose { voiceService.cancelRecording() } }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                    text = "Try it out",
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
                                voiceService.stopRecording(coroutineScope, shouldPolish = false) { finalTx ->
                                    isVoiceListening = false
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
                                    onLevelChange = { voiceLevel = it },
                                    onError = { message -> isVoiceListening = false; voiceLevel = 0f; polishFeedback = message }
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

    var testInput by rememberSaveable { mutableStateOf("how are") }
    var newWordInput by rememberSaveable { mutableStateOf("") }
    var newShortcutInput by rememberSaveable { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Your dictionary",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Names, phrases and shortcuts you use often",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "On device",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

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
                                text = "Your words & shortcuts",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${customEntries.size} custom entries • ${frequentWords.size} frequent words${if (blockedSuggestions.isNotEmpty()) " • ${blockedSuggestions.size} removed" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Add Custom Word / Shortcut Row
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = newWordInput,
                            onValueChange = { newWordInput = it },
                            placeholder = { Text("Word / Phrase", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("custom_word_input"),
                            shape = RoundedCornerShape(8.dp)
                        )
                        OutlinedTextField(
                            value = newShortcutInput,
                            onValueChange = { newShortcutInput = it },
                            placeholder = { Text("Shortcut (e.g. omw)", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("custom_shortcut_input"),
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
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("add_custom_word_button")
                        ) {
                            Text("Save word", style = MaterialTheme.typography.labelSmall)
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
                            Text("Frequently used words", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
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
                                text = "Learned phrases",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "$totalNGrams patterns",
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
                                    text = "Hidden suggestions",
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
            var showPlayground by rememberSaveable { mutableStateOf(false) }
            TextButton(onClick = { showPlayground = !showPlayground }) {
                Text(if (showPlayground) "Hide prediction preview" else "Try predictions")
                Icon(if (showPlayground) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
            }
            AnimatedVisibility(showPlayground) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Interactive Live Playground
            Text(
                text = "Try a suggestion",
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
                        text = "Suggestion preview",
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
                                Text("Your input", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                    text = if (serviceResult.isCenterAutocorrecting) "Correction" else "Suggestion",
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
                                Text("Next word", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

                }
            }
        }
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
