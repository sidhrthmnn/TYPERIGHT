package com.example

import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun AiBackendSettings(settings: KeyboardSettings) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    var termsAccepted by remember { mutableStateOf(LocalGgufModel.termsAccepted(context)) }
    var engine by remember { mutableStateOf(settings.activeAiEngine) }
    val download by LocalGgufModel.state.collectAsState()
    var ready by remember { mutableStateOf(LocalGgufModel.isReady(context)) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("helo wrld I is writing this on my phone") }
    var result by remember { mutableStateOf<String?>(null) }
    var polishing by remember { mutableStateOf(false) }
    var polishJob by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(settings) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> engine = settings.activeAiEngine }
        settings.sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { settings.sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    LaunchedEffect(download.busy) { ready = LocalGgufModel.isReady(context) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("AI Polish", style = MaterialTheme.typography.titleMedium)
            Text("Choose where your text is processed.", style = MaterialTheme.typography.bodySmall)
            listOf(ActiveAiEngine.OFFLINE, ActiveAiEngine.NONE).forEach { option ->
                Row(Modifier.fillMaxWidth()) {
                    RadioButton(selected = engine == option, enabled = !polishing,
                        onClick = { settings.setActiveAiEngine(option); engine = option; result = null },
                        modifier = Modifier.testTag("polish_backend_${option.name.lowercase()}"))
                    Column(Modifier.weight(1f).padding(top = 10.dp)) {
                        Text(option.title)
                        Text(when (option) {
                            ActiveAiEngine.OFFLINE -> "Processes text 100% on this phone. Works offline after the model download."
                            else -> "Disable AI text assistance."
                        }, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (engine == ActiveAiEngine.OFFLINE) {
                Text("Gemma 3 1B Instruct · 4-bit GGUF · 806 MB", style = MaterialTheme.typography.bodyMedium)
                Row {
                    Checkbox(checked = termsAccepted, onCheckedChange = {
                        termsAccepted = it; LocalGgufModel.acceptTerms(context, it)
                    }, modifier = Modifier.testTag("gemma_terms_checkbox"))
                    Text("I agree to the Gemma Terms of Use, including its prohibited-use policy.",
                        modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { uriHandler.openUri("https://ai.google.dev/gemma/terms") }) { Text("Gemma Terms of Use") }
                TextButton(onClick = { uriHandler.openUri("https://ai.google.dev/gemma/prohibited_use_policy") }) { Text("Prohibited-use policy") }
                Text("Download once, then polish offline. Short selections work best; speed and quality depend on your device.",
                    style = MaterialTheme.typography.bodySmall)
                if (!GgufPolishEngine.isSupported()) {
                    Text("Local GGUF needs a 64-bit Android device.", color = MaterialTheme.colorScheme.error)
                } else if (ready) {
                    Text("Model ready for offline use", modifier = Modifier.testTag("gguf_ready"))
                } else if (download.busy) {
                    LinearProgressIndicator(progress = { download.progress }, modifier = Modifier.fillMaxWidth())
                    Text("Downloading: ${(download.progress * 100).toInt()}%")
                    TextButton(onClick = { downloadJob?.cancel(); message = "Download cancelled. Tap download to retry." }) {
                        Text("Cancel download")
                    }
                } else {
                    Button(enabled = termsAccepted, onClick = {
                        message = null
                        downloadJob = scope.launch {
                            try {
                                LocalGgufModel.download(context)
                                ready = LocalGgufModel.isReady(context)
                            } catch (e: CancellationException) { throw e
                            } catch (e: Exception) { message = e.message ?: "Download failed. Tap to retry." }
                        }
                    }, modifier = Modifier.testTag("download_gguf")) { Text("Download model (806 MB)") }
                }
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            HorizontalDivider()
            Text("Test polish engine", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(value = input, onValueChange = { input = it }, enabled = !polishing,
                modifier = Modifier.fillMaxWidth().testTag("polish_playground_input"))
            listOf(PolishMode.PROOFREAD, PolishMode.POLISH, PolishMode.PROFESSIONAL, PolishMode.CASUAL, PolishMode.SHORTEN).forEach { mode ->
                OutlinedButton(enabled = !polishing && input.isNotBlank() && engine != ActiveAiEngine.NONE &&
                    (engine != ActiveAiEngine.OFFLINE || (ready && termsAccepted && GgufPolishEngine.isSupported())),
                    onClick = {
                        polishing = true
                        result = null
                        val selectedLabel = engine.title
                        polishJob = scope.launch {
                            val start = System.currentTimeMillis()
                            try {
                                val output = AiPolishBackend.generatePolish(input, mode)
                                result = if (output.isNullOrBlank()) "Local polish returned no changes."
                                    else "$selectedLabel · ${System.currentTimeMillis() - start} ms\n$output"
                            } catch (e: CancellationException) { throw e
                            } catch (e: Exception) { result = e.message ?: "Polish failed"
                            } finally { polishing = false }
                        }
                    }, modifier = Modifier.testTag("polish_button_${mode.name.lowercase()}")) {
                    Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
                }
            }
            if (polishing) {
                Text("Polishing with ${engine.title}…")
                TextButton(onClick = { polishJob?.cancel() }) { Text("Cancel polish") }
            }
            result?.let { Text(it, modifier = Modifier.testTag("polish_result")) }
        }
    }
}
