package com.example

import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun GgufModelSettings(settings: KeyboardSettings) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val links = LocalUriHandler.current
    var models by remember { mutableStateOf(GgufModelCatalog.all(context)) }
    var selected by remember { mutableStateOf(LocalGgufModel.selected(context).id) }
    var enabled by remember { mutableStateOf(settings.activeAiEngine == ActiveAiEngine.OFFLINE) }
    var revision by remember { mutableIntStateOf(0) }
    val state by LocalGgufModel.state.collectAsState()
    var job by remember { mutableStateOf<Job?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var consent by remember { mutableStateOf<GgufModelSpec?>(null) }
    var remove by remember { mutableStateOf<GgufModelSpec?>(null) }
    var showCustom by rememberSaveable { mutableStateOf(false) }
    fun refresh() {
        models = GgufModelCatalog.all(context)
        selected = LocalGgufModel.selected(context).id
        enabled = settings.activeAiEngine == ActiveAiEngine.OFFLINE
        revision++
    }
    DisposableEffect(settings) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refresh() }
        settings.sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { settings.sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    LaunchedEffect(state.busy) { refresh() }
    fun download(model: GgufModelSpec) {
        error = null
        job = scope.launch {
            try { LocalGgufModel.download(context, model); refresh() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Download failed. Try again." }
        }
    }
    AppSettingsCard(Modifier.testTag("ai_engine_card")) {
        AppSwitchRow("On-device AI", "Choose a model for private text polishing", enabled, {
            settings.setActiveAiEngine(if (it) ActiveAiEngine.OFFLINE else ActiveAiEngine.NONE)
            refresh()
        }, "ai_engine_switch", Icons.Default.AutoAwesome)
        if (enabled) {
            SettingsDivider()
            Text("Choose your model", style = MaterialTheme.typography.titleMedium)
            Text("Models stay on your phone. Keep this page open during downloads. Select an installed model whenever you want to switch.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            models.forEach { model ->
                key(model.id, revision) {
                    val installed = LocalGgufModel.isReady(context, model)
                    val downloading = state.busy && state.modelId == model.id
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        RadioButton(selected == model.id, onClick = { LocalGgufModel.select(context, model); refresh() },
                            modifier = Modifier.testTag("model_select_${model.id}"))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(model.name + if (model.id == GgufModelCatalog.DEFAULT_ID) " · Recommended" else "",
                                style = MaterialTheme.typography.titleSmall)
                            Text("${model.sizeLabel} · ${model.languages}" + if (installed) " · Installed" else "",
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(model.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (!GgufPolishEngine.isSupported()) Text("Requires a 64-bit Android device.", color = MaterialTheme.colorScheme.error)
                            else if (downloading) {
                                if (state.progress > 0) LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                                else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (state.progress > 0) "Downloading · ${(state.progress * 100).toInt()}%" else "Downloading…", Modifier.weight(1f))
                                    TextButton(onClick = { job?.cancel() }) { Text("Cancel") }
                                }
                            } else if (installed) {
                                TextButton(enabled = !state.busy, onClick = { remove = model }, modifier = Modifier.testTag("model_remove_${model.id}")) { Text("Remove download") }
                            } else {
                                Button(enabled = !state.busy, onClick = {
                                    if (LocalGgufModel.termsAccepted(context, model)) download(model) else consent = model
                                }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("model_download_${model.id}")) {
                                    Text("Download · ${model.sizeLabel}")
                                }
                            }
                            if (model.terms.isNotEmpty()) TextButton(onClick = { links.openUri(model.terms) }) { Text(model.license) }
                        }
                    }
                    SettingsDivider()
                }
            }
            OutlinedButton(onClick = { showCustom = true }, enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("add_custom_model")) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Add another GGUF model")
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("model_download_error")) }
            Text("Speed and memory use depend on your phone and the selected model.", style = MaterialTheme.typography.bodySmall)
        }
    }
    consent?.let { model ->
        AlertDialog(onDismissRequest = { consent = null }, title = { Text("Gemma 3 terms") },
            text = { Column {
                Text("Gemma 3 uses Google's Gemma terms and prohibited-use policy. Review both before downloading.")
                TextButton(onClick = { links.openUri(model.terms) }) { Text("Read terms") }
                TextButton(onClick = { links.openUri("https://ai.google.dev/gemma/prohibited_use_policy") }) { Text("Read use policy") }
            } }, confirmButton = { Button(onClick = { LocalGgufModel.acceptTerms(context, true, model); consent = null; download(model) }, Modifier.testTag("model_accept_terms")) { Text("Accept & download") } },
            dismissButton = { TextButton(onClick = { consent = null }) { Text("Cancel") } })
    }
    remove?.let { model ->
        AlertDialog(onDismissRequest = { remove = null }, title = { Text("Remove ${model.name}?") },
            text = { Text("This frees ${model.sizeLabel}. You can download it again later.") },
            confirmButton = { TextButton(onClick = {
                remove = null
                scope.launch { try { LocalGgufModel.remove(context, model); refresh() } catch (e: Exception) { if (e is CancellationException) throw e; error = e.message } }
            }) { Text("Remove") } }, dismissButton = { TextButton(onClick = { remove = null }) { Text("Cancel") } })
    }
    if (showCustom) CustomGgufModelDialog(onDismiss = { showCustom = false }, onAdd = { model ->
        GgufModelCatalog.saveCustom(context, model); LocalGgufModel.select(context, model)
        showCustom = false; refresh(); download(model)
    })
}

@Composable
private fun CustomGgufModelDialog(onDismiss: () -> Unit, onAdd: (GgufModelSpec) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var sha by rememberSaveable { mutableStateOf("") }
    var format by rememberSaveable { mutableStateOf("chatml") }
    var expanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add a GGUF model") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Use a direct HTTPS link to a single GGUF file. Copy its SHA-256 checksum from the provider to verify the download. Choose a model compatible with this llama.cpp runtime. The download limit is 8 GB; available phone memory also matters.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(name, { name = it }, label = { Text("Model name") }, singleLine = true, modifier = Modifier.testTag("custom_model_name"))
            OutlinedTextField(url, { url = it }, label = { Text("GGUF download URL") }, singleLine = true, modifier = Modifier.testTag("custom_model_url"))
            OutlinedTextField(sha, { sha = it }, label = { Text("SHA-256 checksum") }, singleLine = true, modifier = Modifier.testTag("custom_model_sha"))
            Box {
                OutlinedButton(onClick = { expanded = true }) { Text("Prompt format: $format") }
                DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                    GgufModelCatalog.FORMATS.forEach { choice -> DropdownMenuItem(text = { Text(choice) }, onClick = { format = choice; expanded = false }) }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } }, confirmButton = { Button(onClick = {
            try { onAdd(GgufModelCatalog.custom(name, url, sha, format)) } catch (e: IllegalArgumentException) { error = e.message }
        }, modifier = Modifier.testTag("custom_model_add")) { Text("Add & download") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
