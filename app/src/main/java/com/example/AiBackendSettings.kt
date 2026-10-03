package com.example

import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiBackendSettings(settings: KeyboardSettings) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    var engine by remember { mutableStateOf(settings.activeAiEngine) }
    val enabled = engine == ActiveAiEngine.OFFLINE
    val download by LocalGgufModel.state.collectAsState()
    var ready by remember { mutableStateOf(LocalGgufModel.isReady(context)) }
    var allLanguages by remember { mutableStateOf(settings.isAllAiLanguagesSelected) }
    var codes by remember { mutableStateOf(settings.getSelectedAiLanguageCodes()) }
    var showLanguages by rememberSaveable { mutableStateOf(false) }
    var input by rememberSaveable { mutableStateOf("Helo wrld I is typing this on my phon") }
    var result by rememberSaveable { mutableStateOf<String?>(null) }
    var testError by remember { mutableStateOf<String?>(null) }
    var polishing by remember { mutableStateOf(false) }
    fun refresh() {
        engine = settings.activeAiEngine
        allLanguages = settings.isAllAiLanguagesSelected
        codes = settings.getSelectedAiLanguageCodes()
        ready = LocalGgufModel.isReady(context)
    }
    DisposableEffect(settings) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refresh() }
        settings.sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { settings.sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    LaunchedEffect(download.busy) { ready = LocalGgufModel.isReady(context) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        GgufModelSettings(settings)
        SettingsSectionLabel("Languages", "Write in the languages you use")
        AppSettingsCard(Modifier.testTag("languages_card")) {
            AppSwitchRow("Detect automatically", "Consider all available language options", allLanguages, {
                if (it) settings.selectAllAiLanguages() else settings.deselectAllAiLanguages()
                refresh()
            }, "all_languages_switch", Icons.Default.Language)
            if (!allLanguages) {
                SettingsDivider()
                if (codes.isEmpty()) Text("English is used until you choose a language.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                else FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    codes.take(6).forEach { code ->
                        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Text(ModelLanguages.findByCode(code)?.name ?: code, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(8.dp))
                        }
                    }
                    if (codes.size > 6) Text("+${codes.size - 6} more", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(8.dp))
                }
            }
            OutlinedButton(onClick = { showLanguages = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("manage_languages_button")) {
                Text("Choose languages")
            }
        }
        SettingsSectionLabel("Try a polish", "See what a little clarity can do")
        AppSettingsCard(Modifier.testTag("quick_test_card")) {
            OutlinedTextField(value = input, onValueChange = { input = it }, enabled = !polishing,
                label = { Text("Your draft") }, minLines = 3, shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("polish_playground_input"))
            Button(enabled = !polishing && input.isNotBlank() && enabled && ready && LocalGgufModel.termsAccepted(context) && GgufPolishEngine.isSupported(), onClick = {
                polishing = true; result = null; testError = null
                scope.launch {
                    try { result = AiPolishBackend.generatePolish(input, PolishMode.PROOFREAD) ?: "No changes made." }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { testError = e.message ?: "Polish failed. Try a shorter draft." }
                    finally { polishing = false }
                }
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("polish_test_button")) {
                if (polishing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp)); Text(if (polishing) "Polishing your draft…" else "Polish draft")
            }
            if (!ready || !enabled) Text("Set up the model above to try AI polish.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            testError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            result?.let { output ->
                SettingsDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Polished draft", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = { clipboard.setText(AnnotatedString(output)) }) { Icon(Icons.Default.ContentCopy, "Copy polished draft") }
                }
                Text(output, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("polish_test_result"))
            }
        }
        AppStatusNote("Private by design", "This model polishes text on your phone. A connection is only needed for its initial download.", Icons.Default.Lock)
    }
    if (showLanguages) MultiLanguagePickerDialog(settings) { showLanguages = false; refresh() }
}

@Composable
private fun MultiLanguagePickerDialog(
    settings: KeyboardSettings,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCodes by remember { mutableStateOf(settings.getSelectedAiLanguageCodes().toMutableSet()) }
    var activeCategory by remember { mutableStateOf("All") }

    val categories = listOf("All", "Selected (${selectedCodes.size})", "Popular", "Indic", "European", "Asian", "Middle East", "African")

    val filteredLanguages = remember(searchQuery, activeCategory, selectedCodes) {
        val q = searchQuery.trim().lowercase()
        ModelLanguages.ALL.filter { lang ->
            val matchesCategory = when (activeCategory) {
                "All" -> true
                "Popular" -> lang.isPopular
                "Indic" -> lang.region == ModelLanguages.REGION_INDIC || lang.code in listOf("hi", "ml", "ta", "te", "bn", "mr", "gu", "kn", "pa", "ur")
                "European" -> lang.region == ModelLanguages.REGION_EUROPEAN || lang.code in listOf("es", "fr", "de", "it", "pt", "ru", "nl", "pl")
                "Asian" -> lang.region == ModelLanguages.REGION_EAST_ASIAN || lang.code in listOf("zh-Hans", "zh-Hant", "ja", "ko", "vi", "th", "id")
                "Middle East" -> lang.region == ModelLanguages.REGION_MIDDLE_EAST || lang.code in listOf("ar", "fa", "tr", "he")
                "African" -> lang.region == ModelLanguages.REGION_AFRICAN || lang.code in listOf("sw", "am", "yo", "ig", "ha")
                else -> selectedCodes.contains(lang.code)
            }
            val matchesQuery = q.isEmpty() ||
                lang.name.lowercase().contains(q) ||
                lang.nativeName.lowercase().contains(q) ||
                lang.code.lowercase().contains(q)
            matchesCategory && matchesQuery
        }
    }

    Dialog(
        onDismissRequest = {
            settings.setSelectedAiLanguageCodes(selectedCodes)
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Select Languages",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${selectedCodes.size} languages selected for polish",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    IconButton(onClick = {
                        settings.setSelectedAiLanguageCodes(selectedCodes)
                        onDismiss()
                    }) {
                        Icon(Icons.Default.Clear, contentDescription = "Close")
                    }
                }

                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search languages…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Category filter chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = activeCategory == cat,
                            onClick = { activeCategory = cat },
                            label = { Text(cat, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                // Quick buttons: Select All / Popular / Clear
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = {
                            selectedCodes = ModelLanguages.ALL_CODES.toMutableSet()
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text("Select All", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(
                        onClick = {
                            selectedCodes = ModelLanguages.DEFAULT_SELECTED_CODES.toMutableSet()
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text("Default", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(
                        onClick = {
                            selectedCodes.clear()
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text("Clear All", style = MaterialTheme.typography.labelSmall)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                // Scrollable List of Languages with Checkboxes (Multiple selection)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(filteredLanguages, key = { it.code }) { lang ->
                        val isChecked = selectedCodes.contains(lang.code)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    val next = selectedCodes.toMutableSet()
                                    if (isChecked) next.remove(lang.code) else next.add(lang.code)
                                    selectedCodes = next
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    val next = selectedCodes.toMutableSet()
                                    if (checked) next.add(lang.code) else next.remove(lang.code)
                                    selectedCodes = next
                                },
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = lang.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (lang.nativeName.isNotBlank() && !lang.nativeName.equals(lang.name, ignoreCase = true)) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                        ) {
                                            Text(
                                                text = lang.nativeName,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "${lang.code.uppercase()} · ${lang.region}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                                )
                            }
                        }
                    }
                }

                // Done Button
                Button(
                    onClick = {
                        settings.setSelectedAiLanguageCodes(selectedCodes)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Apply (${selectedCodes.size} Languages Selected)",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
