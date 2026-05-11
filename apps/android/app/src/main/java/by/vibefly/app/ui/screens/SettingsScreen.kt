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
 * Настройки. Полный список (Cloudflare Tunnel, Tailscale, AI-провайдеры,
 * Battery limit, Throttle, Backups) — в фазе 1–2.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "\u041D\u0430\u0441\u0442\u0440\u043E\u0439\u043A\u0438 \u2014 \u0432 \u0444\u0430\u0437\u0435 1",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
