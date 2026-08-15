package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashReporter.init(this)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Black
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

    val activeAiModel = remember { mutableStateOf(settings.aiModel) }
    val activeWhisperModel = remember { mutableStateOf(settings.whisperModel) }
    val activeVoiceLanguage = remember { mutableStateOf(settings.voiceLanguage) }
    val activeVoiceInputMode = remember { mutableStateOf(settings.voiceInputMode) }
    val activeClipboardEnabled = remember { mutableStateOf(settings.clipboardEnabled) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .imePadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        // --- HEADER ---
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "Type Right",
                fontSize = 32.sp,
                fontWeight = FontWeight.Light,
                color = Color.White,
                fontFamily = FontFamily.SansSerif
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "PREMIUM AI KEYBOARD & DICTATION",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.5f),
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.15f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Processed strictly on-device using local intelligence. Zero network permissions. 100% private.",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.7f),
                lineHeight = 19.sp,
                fontFamily = FontFamily.SansSerif
            )
        }

        // --- SETUP CHECKLIST ---
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "SETUP CHECKLIST",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.5f),
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.5.sp
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)

            // Step 1: Enable Keyboard in settings
            SetupStepRow(
                stepNumber = "1",
                title = "Enable Keyboard",
                description = "Activate TypeRight in Android language & input settings.",
                isCompleted = isKeyboardEnabled,
                actionLabel = "Enable",
                onClick = {
                    val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                    context.startActivity(intent)
                }
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)

            // Step 2: Switch Keyboard
            SetupStepRow(
                stepNumber = "2",
                title = "Switch Keyboard",
                description = "Set TypeRight as your active default input method.",
                isCompleted = isKeyboardSelected,
                actionLabel = "Switch",
                onClick = {
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.showInputMethodPicker()
                }
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)

            // Step 3: Mic Permissions
            SetupStepRow(
                stepNumber = "3",
                title = "Microphone Access",
                description = "Allow voice input for on-device WisprFlow dictation.",
                isCompleted = isMicPermissionGranted,
                actionLabel = "Grant",
                onClick = {
                    launcher.launch(Manifest.permission.RECORD_AUDIO)
                }
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)

            Spacer(modifier = Modifier.height(4.dp))

            OutlinedButton(
                onClick = { refreshStatus() },
                border = BorderStroke(1.dp, Color.White),
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "REFRESH STATUS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // --- PREFERENCES ---
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "KEYBOARD PREFERENCES",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.5f),
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.5.sp
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)

            PreferenceSwitchRow(
                title = "Material You Dynamic Color",
                description = "Adapt keyboard background, keys, and accents to your system wallpaper & device palette (Android 12+).",
                checked = activeDynamicThemeEnabled.value,
                onCheckedChange = {
                    activeDynamicThemeEnabled.value = it
                    settings.dynamicThemeEnabled = it
                }
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)

            PreferenceSwitchRow(
                title = "Keyboard Dark Mode",
                description = "Toggle light or dark theme variant for the keyboard interface.",
                checked = activeIsDarkMode.value,
                onCheckedChange = {
                    activeIsDarkMode.value = it
                    settings.isDarkMode = it
                }
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)

            PreferenceSwitchRow(
                title = "Number Row Above QWERTY",
                description = "Display a dedicated 1-0 number row at the top of the main keyboard layout.",
                checked = activeNumberRowEnabled.value,
                onCheckedChange = {
                    activeNumberRowEnabled.value = it
                    settings.numberRowEnabled = it
                }
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)

            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Custom Keyboard Accent Color",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Text(
                    text = "Select an accent color for key highlights, enter button, and trace effects.",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val colors = listOf(
                        "#00E5FF" to Color(0xFF00E5FF), // Neon Cyan
                        "#FF007F" to Color(0xFFFF007F), // Neon Pink
                        "#76FF03" to Color(0xFF76FF03), // Neon Lime
                        "#FF9100" to Color(0xFFFF9100), // Sunset Orange
                        "#9D4EDD" to Color(0xFF9D4EDD), // Royal Violet
                        "#FFD700" to Color(0xFFFFD700), // Classic Gold
                        "#E53935" to Color(0xFFE53935)  // Crimson Red
                    )
                    colors.forEach { (hex, color) ->
                        val isSelected = activeAccentColor.value.equals(hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 3.dp else 0.dp,
                                    color = Color.White,
                                    shape = CircleShape
                                )
                                .clickable {
                                    activeAccentColor.value = hex
                                    settings.accentColor = hex
                                }
                                .testTag("accent_color_$hex"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = if (hex == "#FFD700" || hex == "#76FF03" || hex == "#00E5FF") Color.Black else Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)

            PreferenceDropdownRow(
                title = "Keyboard Height",
                selectedOption = activeHeight.value,
                options = listOf(
                    KeyboardSettings.HEIGHT_SHORT,
                    KeyboardSettings.HEIGHT_NORMAL,
                    KeyboardSettings.HEIGHT_TALL
                ),
                onSelect = {
                    activeHeight.value = it
                    settings.height = it
                }
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)

            PreferenceSwitchRow(
                title = "Keypress Sounds",
                description = "Play audio clicks during typing.",
                checked = soundEnabled.value,
                onCheckedChange = {
                    soundEnabled.value = it
                    settings.soundEnabled = it
                }
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)

            PreferenceSwitchRow(
                title = "Keypress Haptic Feedback",
                description = "Vibrate on tapping keyboard buttons.",
                checked = hapticEnabled.value,
                onCheckedChange = {
                    hapticEnabled.value = it
                    settings.hapticEnabled = it
                }
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)

            PreferenceSwitchRow(
                title = "Smart Next-Word Predictions",
                description = "Enable top bar with smart next-word predictions.",
                checked = autocorrectEnabled.value,
                onCheckedChange = {
                    autocorrectEnabled.value = it
                    settings.autocorrectEnabled = it
                }
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)

            PreferenceSwitchRow(
                title = "Profanity Filtering",
                description = "Filter offensive vocabulary in suggestions.",
                checked = profanityFilterEnabled.value,
                onCheckedChange = {
                    profanityFilterEnabled.value = it
                    settings.profanityFilterEnabled = it
                }
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)

            PreferenceDropdownRow(
                title = "Voice Input Language",
                selectedOption = activeVoiceLanguage.value,
                options = listOf(
                    "en-US",
                    "es-ES",
                    "fr-FR",
                    "de-DE"
                ),
                onSelect = {
                    activeVoiceLanguage.value = it
                    settings.voiceLanguage = it
                }
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)

            PreferenceSwitchRow(
                title = "Enable Clipboard Panel",
                description = "Save and paste clipboard history directly from keyboard.",
                checked = activeClipboardEnabled.value,
                onCheckedChange = {
                    activeClipboardEnabled.value = it
                    settings.clipboardEnabled = it
                }
            )
        }

        // --- DIAGNOSTICS & CRASH LOGS ---
        CrashDebugSection()
    }
}

@Composable
fun SetupStepRow(
    stepNumber: String,
    title: String,
    description: String,
    isCompleted: Boolean,
    actionLabel: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
        ) {
            Text(
                text = "$stepNumber. $title",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }

        if (isCompleted) {
            Box(
                modifier = Modifier
                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "READY",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .background(Color.White, RoundedCornerShape(4.dp))
                    .clickable { onClick() }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = actionLabel.uppercase(),
                    color = Color.Black,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun DiagnosticItem(name: String, isAvailable: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        Text(
            text = if (isAvailable) "[ READY ]" else "[ UNSUPPORTED ]",
            color = if (isAvailable) Color.White else Color.White.copy(alpha = 0.3f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun PreferenceDropdownRow(
    title: String,
    selectedOption: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )

        Box {
            Row(
                modifier = Modifier
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedOption,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .background(Color(0xFF121212))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PreferenceSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
        ) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(description, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, lineHeight = 15.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = Color.White,
                uncheckedThumbColor = Color.White.copy(alpha = 0.4f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.15f),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
fun CrashDebugSection() {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var logs by remember { mutableStateOf(listOf<CrashLogEntry>()) }
    var refreshTrigger by remember { mutableStateOf(0) }
    var showConfirmCrashDialog by remember { mutableStateOf(false) }
    var expandedLogIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(refreshTrigger) {
        logs = CrashReporter.getLogs(context)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("crash_debug_card"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "CRASH & DEBUG DIAGNOSTICS",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.5f),
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.5.sp
        )

        HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)

        Text(
            text = "Monitor uncaught background exceptions, lifecycle incidents, or manually logged errors.",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.6f),
            lineHeight = 17.sp
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { refreshTrigger++ },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("REFRESH LOGS", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }

            OutlinedButton(
                onClick = {
                    CrashReporter.logCustomError(
                        context,
                        "User triggered manual handled error for diagnostics testing."
                    )
                    Toast.makeText(context, "Logged diagnostic error!", Toast.LENGTH_SHORT).show()
                    refreshTrigger++
                },
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("LOG CUSTOM ERROR", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }

            OutlinedButton(
                onClick = { showConfirmCrashDialog = true },
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("SIMULATE CRASH", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)

        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "NO ISSUES CAPTURED",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "System processes are running stably.",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp
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
                    text = "LOGS (${logs.size})",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                TextButton(
                    onClick = {
                        CrashReporter.clearLogs(context)
                        Toast.makeText(context, "Logs cleared", Toast.LENGTH_SHORT).show()
                        refreshTrigger++
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("CLEAR ALL", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                logs.forEachIndexed { index, entry ->
                    val isExpanded = expandedLogIndex == index
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .clickable { expandedLogIndex = if (isExpanded) null else index }
                            .padding(12.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (entry.type == "CRASH") Color.White else Color.White.copy(alpha = 0.5f))
                                    )
                                    Text(
                                        text = entry.type,
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Text(
                                    text = entry.timestamp,
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = entry.exceptionClass.substringAfterLast('.'),
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = entry.message,
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                maxLines = if (isExpanded) 20 else 2,
                                lineHeight = 16.sp
                            )

                            AnimatedVisibility(visible = isExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

                                    Text(
                                        text = "Thread: ${entry.threadName}",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                                            .background(Color(0xFF0A0A0A))
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            text = "ENVIRONMENT",
                                            color = Color.White.copy(alpha = 0.4f),
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = entry.deviceInfo,
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            lineHeight = 13.sp
                                        )
                                    }

                                    Text(
                                        text = "STACK TRACE",
                                        color = Color.White.copy(alpha = 0.4f),
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 150.dp)
                                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                                            .background(Color(0xFF0A0A0A))
                                            .horizontalScroll(rememberScrollState())
                                            .verticalScroll(rememberScrollState())
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            text = entry.stackTrace,
                                            color = Color.White.copy(alpha = 0.8f),
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            lineHeight = 13.sp
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(entry.toFormattedString()))
                                                Toast.makeText(context, "Full log copied to clipboard", Toast.LENGTH_SHORT).show()
                                            },
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                            shape = RoundedCornerShape(4.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(36.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("COPY LOG", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                val sendIntent = Intent().apply {
                                                    action = Intent.ACTION_SEND
                                                    putExtra(Intent.EXTRA_TEXT, entry.toFormattedString())
                                                    type = "text/plain"
                                                }
                                                val shareIntent = Intent.createChooser(sendIntent, "Share Crash Log")
                                                context.startActivity(shareIntent)
                                            },
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                            shape = RoundedCornerShape(4.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(36.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("SHARE LOG", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
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
            title = { Text("Simulate Fatal Crash?", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) },
            text = { Text("This will throw an uncaught RuntimeException immediately, triggering a crash of the main activity process. This verifies the CrashReporter successfully captures full trace state for subsequent app launches.", fontSize = 12.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmCrashDialog = false
                        CrashReporter.triggerSimulatedCrash()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("TRIGGER CRASH", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmCrashDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                ) {
                    Text("CANCEL", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            },
            containerColor = Color(0xFF121212),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.8f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
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

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

