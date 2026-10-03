package com.example

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun AppPreferencesScreen(settings: KeyboardSettings, onOpenTyping: () -> Unit, onOpenAi: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var learning by remember { mutableStateOf(settings.personalizedLearningEnabled) }
    var clipboard by remember { mutableStateOf(settings.clipboardEnabled) }
    var clearLearning by remember { mutableStateOf(false) }
    var photoAccess by remember { mutableStateOf(SmartClipboardPolicy.hasPhotoAccess(context)) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) photoAccess = SmartClipboardPolicy.hasPhotoAccess(context)
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    if (clearLearning) AlertDialog(onDismissRequest = { clearLearning = false },
        title = { Text("Clear adaptive typing profile?") },
        text = { Text("Remove the typo fixes learned from accepted polish and the word-use counts used to rank suggestions. Your personal dictionary stays available.") },
        confirmButton = { TextButton(onClick = {
            PersonalTypingProfile.get(context).clear(); clearLearning = false
            android.widget.Toast.makeText(context, "Adaptive typing profile cleared", android.widget.Toast.LENGTH_SHORT).show()
        }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { clearLearning = false }) { Text("Cancel") } })
    var query by rememberSaveable { mutableStateOf("") }
    val prefs by settings.dataStore.userPreferencesFlow.collectAsState(initial = settings.dataStore.currentSnapshot())
    // Read persisted values again when DataStore publishes a setting change or this screen is reopened.
    var autocorrect by remember(prefs) { mutableStateOf(settings.autocorrectEnabled) }
    var sensitivity by remember(prefs) { mutableStateOf(settings.autocorrectSensitivity) }
    var numberRow by remember(prefs) { mutableStateOf(settings.numberRowEnabled) }
    var haptics by remember(prefs) { mutableStateOf(settings.hapticEnabled) }
    var sound by remember(prefs) { mutableStateOf(settings.soundEnabled) }
    var theme by remember(prefs) { mutableStateOf(settings.theme) }
    fun matches(vararg terms: String) = query.isBlank() || terms.any { it.contains(query.trim(), ignoreCase = true) }
    val typing = matches("typing", "auto-correct", "autocorrect", "correction sensitivity", "mild balanced strong aggressive", "number row digits")
    val intelligence = matches("learning personal typing patterns accepted polish typos", "smart clipboard screenshot OTP code photo access")
    val feedback = matches("touch feedback", "haptic vibration", "keypress sound audio")
    val appearance = matches("appearance theme", "light dark midnight night color")
    val more = matches("dictionary words shortcuts predictions", "AI polish languages Gemma model")
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text("Find a setting", style = MaterialTheme.typography.bodyMedium) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear search") } },
            shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().testTag("settings_search"),
            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant)
        )
        if (query.isBlank()) {
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(14.dp))
                Text("Changes save automatically", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (typing) {
            SettingsSectionLabel("Typing", "Everyday essentials")
            AppSettingsCard {
                AppSwitchRow("Auto-correct", "Fix typos as you finish a word", autocorrect,
                    { autocorrect = it; settings.autocorrectEnabled = it }, "pref_autocorrect_switch", Icons.Default.Spellcheck)
                AnimatedVisibility(autocorrect) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Correction strength", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(KeyboardSettings.SENSITIVITY_MILD to "Mild", KeyboardSettings.SENSITIVITY_BALANCED to "Balanced",
                                KeyboardSettings.SENSITIVITY_AGGRESSIVE to "Strong").forEach { (value, label) ->
                                val selected = sensitivity.equals(value, true)
                                Surface(
                                    modifier = Modifier.weight(1f).testTag("pref_sensitivity_${value.lowercase()}")
                                        .selectable(selected, role = Role.RadioButton, onClick = { sensitivity = value; settings.autocorrectSensitivity = value }),
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .35f)) else null
                                ) {
                                    Box(Modifier.heightIn(min = 48.dp).padding(horizontal = 4.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                                        Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        Text(when (sensitivity.lowercase()) {
                            KeyboardSettings.SENSITIVITY_MILD.lowercase() -> "Keep more of what you type."
                            KeyboardSettings.SENSITIVITY_AGGRESSIVE.lowercase() -> "Correct more possible typos."
                            else -> "A balance of correction and control."
                        }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                SettingsDivider()
                AppSwitchRow("Number row", "Keep digits within easy reach", numberRow,
                    { numberRow = it; settings.numberRowEnabled = it }, "pref_number_row_switch", Icons.Default.Numbers)
            }
        }
        if (intelligence) {
            SettingsSectionLabel("Smarter typing", "Personal and on this device")
            AppSettingsCard {
                AppSwitchRow("Learn from my typing", "Remember accepted typo fixes and frequently used words", learning,
                    { learning = it; settings.personalizedLearningEnabled = it }, "pref_learning_switch", Icons.Default.Psychology)
                AppLinkRow("Clear adaptive profile", "Reset accepted fixes and word-use ranking", Icons.Default.RestartAlt,
                    "pref_clear_learning", { clearLearning = true })
                SettingsDivider()
                AppSwitchRow("Smart clipboard", "Suggest copied codes and recent screenshots; tap × to dismiss", clipboard,
                    { clipboard = it; settings.clipboardEnabled = it }, "pref_clipboard_switch", Icons.Default.ContentPaste)
                if (clipboard) AppLinkRow("Screenshot access", if (photoAccess) "Photo access allowed" else "Allow photo access to show recent screenshots",
                    Icons.Default.Image, "pref_screenshot_access", {
                        context.startActivity(android.content.Intent(context, ScreenshotPermissionActivity::class.java))
                    })
                Text("Codes disappear after 2 minutes and are never saved to clipboard history. Screenshots are suggested for 5 minutes.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (feedback) {
            SettingsSectionLabel("Touch & sound", "Choose how each tap feels")
            AppSettingsCard {
                AppSwitchRow("Haptic feedback", "A gentle vibration on each keypress", haptics,
                    { haptics = it; settings.hapticEnabled = it }, "pref_haptic_switch", Icons.Default.Vibration)
                SettingsDivider()
                AppSwitchRow("Keypress sound", "Play a subtle click as you type", sound,
                    { sound = it; settings.soundEnabled = it }, "pref_sound_switch", Icons.Default.VolumeUp)
            }
        }
        if (appearance) {
            SettingsSectionLabel("Appearance", "Find your favorite look")
            AppSettingsCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeTile("Light", KeyboardSettings.THEME_LIGHT, Color(0xFFF5F6FA), Color(0xFF4759CF), theme,
                        Modifier.weight(1f)) { theme = it; settings.theme = it }
                    ThemeTile("Dark", KeyboardSettings.THEME_DARK, Color(0xFF252D3D), Color(0xFFB6C0FF), theme,
                        Modifier.weight(1f)) { theme = it; settings.theme = it }
                    ThemeTile("Midnight", KeyboardSettings.THEME_NIGHT, Color(0xFF070A10), Color(0xFF9DDAC5), theme,
                        Modifier.weight(1f)) { theme = it; settings.theme = it }
                }
            }
        }
        if (more) {
            SettingsSectionLabel("Make it personal")
            AppSettingsCard {
                AppLinkRow("Words & shortcuts", "Manage your personal dictionary", Icons.AutoMirrored.Filled.MenuBook, "settings_open_typing", onOpenTyping)
                SettingsDivider()
                AppLinkRow("AI & languages", "Set up your writing assistant", Icons.Default.AutoAwesome, "settings_open_ai", onOpenAi)
            }
        }
        if (!typing && !intelligence && !feedback && !appearance && !more) {
            AppStatusNote("No settings found", "Try searching for typing, sound, theme or AI.", Icons.Default.Search)
        }
        if (query.isBlank()) {
            AppStatusNote("Your words, your control", "Typing predictions and your personal dictionary are processed on this device.", Icons.Default.Lock)
            Text("TYPE RIGHT  ·  ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp))
        }
    }
}

@Composable
private fun ThemeTile(label: String, value: String, preview: Color, accent: Color, selectedTheme: String,
                      modifier: Modifier, onSelect: (String) -> Unit) {
    val selected = selectedTheme.equals(value, true)
    Surface(
        modifier = modifier.testTag("theme_chip_${value.lowercase()}")
            .selectable(selected, role = Role.RadioButton, onClick = { onSelect(value) }),
        shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.fillMaxWidth().height(56.dp).background(preview, RoundedCornerShape(8.dp)).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(12.dp).background(accent, CircleShape))
                Box(Modifier.fillMaxWidth(.8f).height(4.dp).background(accent.copy(alpha = .45f), CircleShape))
                Box(Modifier.fillMaxWidth(.6f).height(4.dp).background(accent.copy(alpha = .25f), CircleShape))
            }
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}
