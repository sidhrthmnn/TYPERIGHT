package com.example

import android.content.SharedPreferences
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun AiBackendSettings(settings: KeyboardSettings) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    var termsAccepted by remember { mutableStateOf(LocalGgufModel.termsAccepted(context)) }
    var engine by remember { mutableStateOf(settings.activeAiEngine) }
    val isEngineEnabled = engine == ActiveAiEngine.OFFLINE

    val download by LocalGgufModel.state.collectAsState()
    var ready by remember { mutableStateOf(LocalGgufModel.isReady(context)) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    var downloadError by remember { mutableStateOf<String?>(null) }

    var isAllLanguagesSelected by remember { mutableStateOf(settings.isAllAiLanguagesSelected) }
    var selectedCodes by remember { mutableStateOf(settings.getSelectedAiLanguageCodes()) }
    var showLanguagePicker by remember { mutableStateOf(false) }

    var testInput by remember { mutableStateOf("Helo wrld I is typing this on my phon") }
    var testResult by remember { mutableStateOf<String?>(null) }
    var isTestingPolish by remember { mutableStateOf(false) }

    fun refreshLanguages() {
        isAllLanguagesSelected = settings.isAllAiLanguagesSelected
        selectedCodes = settings.getSelectedAiLanguageCodes()
    }

    DisposableEffect(settings) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            engine = settings.activeAiEngine
            refreshLanguages()
        }
        settings.sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { settings.sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    LaunchedEffect(download.busy) {
        ready = LocalGgufModel.isReady(context)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Card 1: On-Device Gemma 3 Engine ---
        Card(
            modifier = Modifier.fillMaxWidth().testTag("ai_engine_card"),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "On-Device AI Polish",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Google Gemma 3 1B · 100% Offline & Private",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = isEngineEnabled,
                        onCheckedChange = { enabled ->
                            val newEngine = if (enabled) ActiveAiEngine.OFFLINE else ActiveAiEngine.NONE
                            settings.setActiveAiEngine(newEngine)
                            engine = newEngine
                        },
                        modifier = Modifier.testTag("ai_engine_switch")
                    )
                }

                if (isEngineEnabled) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                    if (!GgufPolishEngine.isSupported()) {
                        Text(
                            text = "Local GGUF requires a 64-bit Android device.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else if (ready) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Gemma 3 model installed & ready (806 MB)",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    } else if (download.busy) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            LinearProgressIndicator(
                                progress = { download.progress },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Downloading model: ${(download.progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                                TextButton(onClick = { downloadJob?.cancel() }) {
                                    Text("Cancel", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        val next = !termsAccepted
                                        termsAccepted = next
                                        LocalGgufModel.acceptTerms(context, next)
                                    }
                                    .padding(vertical = 4.dp)
                            ) {
                                Checkbox(
                                    checked = termsAccepted,
                                    onCheckedChange = {
                                        termsAccepted = it
                                        LocalGgufModel.acceptTerms(context, it)
                                    },
                                    modifier = Modifier.testTag("gemma_terms_checkbox")
                                )
                                Text(
                                    text = "I accept the Gemma Terms of Use",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.width(4.dp))
                                TextButton(
                                    onClick = { uriHandler.openUri("https://ai.google.dev/gemma/terms") },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("(Terms)", style = MaterialTheme.typography.labelSmall)
                                }
                            }

                            Button(
                                enabled = termsAccepted,
                                onClick = {
                                    downloadError = null
                                    downloadJob = scope.launch {
                                        try {
                                            LocalGgufModel.download(context)
                                            ready = LocalGgufModel.isReady(context)
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            downloadError = e.message ?: "Download failed"
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(46.dp).testTag("download_gguf"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Download Gemma 3 Model (806 MB)")
                            }

                            downloadError?.let {
                                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        // --- Card 2: Multilingual Polish Languages (Supports Multiple Languages) ---
        Card(
            modifier = Modifier.fillMaxWidth().testTag("polish_languages_card"),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Section Header
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
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Polish Languages",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Supported by Gemma 3 multilingual engine",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isAllLanguagesSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = if (isAllLanguagesSelected) "All 140+" else "${selectedCodes.size} Selected",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isAllLanguagesSelected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp).testTag("languages_selected_badge")
                        )
                    }
                }

                // Master Toggle: All 140+ Languages
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isAllLanguagesSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    border = BorderStroke(
                        1.dp,
                        if (isAllLanguagesSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val next = !isAllLanguagesSelected
                            if (next) settings.selectAllAiLanguages() else settings.deselectAllAiLanguages()
                            refreshLanguages()
                        }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                            Text(
                                text = "Enable All 140+ Supported Languages",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Gemma 3 will auto-detect and polish text in any language",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isAllLanguagesSelected,
                            onCheckedChange = { checked ->
                                if (checked) settings.selectAllAiLanguages() else settings.deselectAllAiLanguages()
                                refreshLanguages()
                            },
                            modifier = Modifier.testTag("all_languages_switch")
                        )
                    }
                }

                // If Master Toggle is OFF: Show selected language chips + Add button
                if (!isAllLanguagesSelected) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Active Target Languages:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Flowing chips row of selected languages
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (selectedCodes.isEmpty()) {
                                Text(
                                    text = "No languages selected (English will be used)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                selectedCodes.forEach { code ->
                                    val lang = ModelLanguages.findByCode(code)
                                    val label = lang?.name ?: code.uppercase()
                                    InputChip(
                                        selected = true,
                                        onClick = {
                                            settings.toggleAiLanguage(code, false)
                                            refreshLanguages()
                                        },
                                        label = {
                                            Text(
                                                text = if (lang?.nativeName != null && !lang.nativeName.equals(lang.name, ignoreCase = true))
                                                    "$label (${lang.nativeName})"
                                                else label,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
                                        trailingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = "Remove $label",
                                                modifier = Modifier.size(14.dp)
                                            )
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = InputChipDefaults.inputChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    )
                                }
                            }
                        }

                        // Manage / Add Languages Button
                        OutlinedButton(
                            onClick = { showLanguagePicker = true },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("manage_languages_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Select / Manage Languages (${ModelLanguages.ALL.size}+ available)")
                        }
                    }
                }
            }
        }

        // --- Card 3: Minimalist Quick Polish Test ---
        Card(
            modifier = Modifier.fillMaxWidth().testTag("quick_test_card"),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Quick Polish Test",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                OutlinedTextField(
                    value = testInput,
                    onValueChange = { testInput = it },
                    enabled = !isTestingPolish,
                    modifier = Modifier.fillMaxWidth().testTag("polish_playground_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Button(
                    enabled = !isTestingPolish && testInput.isNotBlank() && isEngineEnabled && ready,
                    onClick = {
                        isTestingPolish = true
                        testResult = null
                        scope.launch {
                            val start = System.currentTimeMillis()
                            try {
                                val output = AiPolishBackend.generatePolish(testInput, PolishMode.PROOFREAD)
                                testResult = if (output.isNullOrBlank()) "No changes made."
                                else "Polished in ${System.currentTimeMillis() - start} ms:\n$output"
                            } catch (e: Exception) {
                                testResult = e.message ?: "Polish failed"
                            } finally {
                                isTestingPolish = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(46.dp).testTag("polish_test_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isTestingPolish) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text("Polishing with Gemma 3…")
                    } else {
                        Text("Test with Gemma 3")
                    }
                }

                testResult?.let {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = it,
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }

    // --- Multi-Language Selection Dialog ---
    if (showLanguagePicker) {
        MultiLanguagePickerDialog(
            settings = settings,
            onDismiss = {
                showLanguagePicker = false
                refreshLanguages()
            }
        )
    }
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
                    placeholder = { Text("Search 140+ languages (e.g. Malayalam, Hindi, Spanish)…") },
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
