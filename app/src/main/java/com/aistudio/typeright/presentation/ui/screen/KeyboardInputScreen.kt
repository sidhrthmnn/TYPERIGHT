package com.aistudio.typeright.presentation.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.aistudio.typeright.presentation.viewmodel.KeyboardViewModel
import com.aistudio.typeright.presentation.ui.components.SuggestionStrip

/**
 * Screen for keyboard input and prediction display
 */
@Composable
fun KeyboardInputScreen(
    viewModel: KeyboardViewModel = hiltViewModel()
) {
    val keyboardState by viewModel.keyboardState.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    var inputText by remember { mutableStateOf("") }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            text = "TypeRight Keyboard",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        TextField(
            value = inputText,
            onValueChange = { text ->
                inputText = text
                viewModel.updateText(text, text.length)
            },
            label = { Text("Type something...") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            maxLines = 5
        )
        
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(16.dp)
            )
        }
        
        if (suggestions.isNotEmpty()) {
            Text(
                text = "Suggestions",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            SuggestionStrip(
                suggestions = suggestions,
                onSuggestionSelected = { suggestion ->
                    inputText = inputText.plus(" ").plus(suggestion)
                    viewModel.updateText(inputText, inputText.length)
                }
            )
        }
    }
}
