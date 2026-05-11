package by.vibefly.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import by.vibefly.app.R

/**
 * AI-ассистент. Полная верстка (пузырьки, tool cards, approval) — в фазе 4.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen() {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.chat_title)) }) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "AI \u2014 \u0432 \u0444\u0430\u0437\u0435 4",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
