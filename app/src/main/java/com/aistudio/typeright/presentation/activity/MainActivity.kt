package com.aistudio.typeright.presentation.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aistudio.typeright.presentation.ui.theme.TypeRightTheme
import com.aistudio.typeright.presentation.ui.screen.KeyboardInputScreen
import com.aistudio.typeright.presentation.ui.screen.PolishingScreen
import com.aistudio.typeright.presentation.ui.screen.ClipboardScreen
import com.aistudio.typeright.presentation.ui.screen.ThemeScreen
import dagger.hilt.android.AndroidEntryPoint

/**
 * Main activity with tabbed interface
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TypeRightTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Keyboard", "Polishing", "Clipboard", "Theme")
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }
        
        when (selectedTab) {
            0 -> KeyboardInputScreen()
            1 -> PolishingScreen()
            2 -> ClipboardScreen()
            3 -> ThemeScreen()
        }
    }
}
