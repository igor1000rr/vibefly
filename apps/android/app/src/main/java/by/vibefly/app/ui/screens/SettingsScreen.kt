package by.vibefly.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import by.vibefly.app.BuildConfig
import by.vibefly.app.R
import by.vibefly.app.data.ServiceLocator
import by.vibefly.app.data.SettingsStore
import kotlinx.coroutines.launch

/**
 * Настройки. Адрес агента, Bearer-токен, AI-провайдер.
 *
 * Прямой доступ к ServiceLocator без ViewModel — в фазе 1 этого достаточно,
 * в фазе 2 выделим SettingsViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val settingsStore = remember { ServiceLocator.settings() }
    val snapshot by settingsStore.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var baseUrl by remember(snapshot.baseUrl) { mutableStateOf(snapshot.baseUrl) }
    var token by remember(snapshot.authToken) { mutableStateOf(snapshot.authToken) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SectionTitle(stringResource(R.string.settings_section_connections))
            }
            item {
                Card(shape = RoundedCornerShape(12.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Cloud, contentDescription = null)
                            Text(
                                text = "Agent URL",
                                modifier = Modifier.padding(start = 8.dp),
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                        OutlinedTextField(
                            value = baseUrl,
                            onValueChange = { baseUrl = it },
                            label = { Text("http://host:port") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = "\u041F\u043E \u0443\u043C\u043E\u043B\u0447\u0430\u043D\u0438\u044E http://127.0.0.1:3001 (\u0432 \u0444\u0430\u0437\u0435 2). \u0414\u043B\u044F \u044D\u043C\u0443\u043B\u044F\u0442\u043E\u0440\u0430 \u2014 http://10.0.2.2:3001. \u0414\u043B\u044F LAN \u2014 IP \u0434\u0435\u0441\u043A\u0442\u043E\u043F\u0430.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Key, contentDescription = null)
                            Text(
                                text = "Bearer token",
                                modifier = Modifier.padding(start = 8.dp),
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                        OutlinedTextField(
                            value = token,
                            onValueChange = { token = it },
                            label = { Text("\u041F\u0443\u0441\u0442\u043E \u2014 auth \u043E\u0442\u043A\u043B\u044E\u0447\u0451\u043D") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = {
                                baseUrl = SettingsStore.DEFAULT_BASE_URL
                                token = ""
                                scope.launch {
                                    settingsStore.setBaseUrl(SettingsStore.DEFAULT_BASE_URL)
                                    settingsStore.setAuthToken("")
                                }
                            }) { Text("\u0421\u0431\u0440\u043E\u0441\u0438\u0442\u044C") }
                            TextButton(onClick = {
                                scope.launch {
                                    settingsStore.setBaseUrl(baseUrl)
                                    settingsStore.setAuthToken(token)
                                }
                            }) { Text("\u0421\u043E\u0445\u0440\u0430\u043D\u0438\u0442\u044C") }
                        }
                    }
                }
            }

            item { SectionTitle(stringResource(R.string.settings_section_ai)) }
            item {
                Card(shape = RoundedCornerShape(12.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Memory, contentDescription = null)
                            Text(
                                text = "Provider",
                                modifier = Modifier.padding(start = 8.dp),
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                        Text(
                            text = "\u0412 \u0444\u0430\u0437\u0435 4. \u041F\u043E\u043A\u0430 \u0432\u044B\u0431\u043E\u0440 \u043D\u0435 \u0432\u043B\u0438\u044F\u0435\u0442 \u043D\u0430 \u043F\u043E\u0432\u0435\u0434\u0435\u043D\u0438\u0435.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            SettingsStore.AiProvider.values().take(3).forEach { provider ->
                                AssistChip(
                                    onClick = { settingsStore.setAiProvider(provider) },
                                    label = { Text(provider.displayName) },
                                    colors = if (snapshot.aiProvider == provider) {
                                        AssistChipDefaults.assistChipColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    } else AssistChipDefaults.assistChipColors(),
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.settings_about, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
