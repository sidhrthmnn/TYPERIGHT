package com.aistudio.typeright.presentation.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aistudio.typeright.domain.model.ToneStyle
import com.aistudio.typeright.presentation.viewmodel.PolishingViewModel
import com.aistudio.typeright.presentation.ui.components.ToneTransformationPanel

/**
 * Screen for text polishing and tone transformation
 */
@Composable
fun PolishingScreen(
    viewModel: PolishingViewModel = hiltViewModel()
) {
    val toneResult by viewModel.toneResult.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    
    var inputText by remember { mutableStateOf("") }
    var selectedTone by remember { mutableStateOf(ToneStyle.PROFESSIONAL) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Text Polishing Assistant",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        TextField(
            value = inputText,
            onValueChange = { inputText = it },
            label = { Text("Enter text to polish") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            maxLines = 5
        )
        
        Text(
            text = "Select Tone",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 16.dp)
        )
        
        ToneStyle.values().forEach { tone ->
            Button(
                onClick = { selectedTone = tone },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(tone.name)
            }
        }
        
        Button(
            onClick = { viewModel.transformTone(inputText, selectedTone) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            enabled = inputText.isNotEmpty() && !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator()
            } else {
                Text("Transform")
            }
        }
        
        if (error != null) {
            Text(
                text = "Error: $error",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
        
        if (toneResult != null) {
            ToneTransformationPanel(
                originalText = inputText,
                transformedText = toneResult ?: "",
                onApply = { inputText = toneResult ?: "" },
                onCancel = { viewModel.clearResults() },
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}
