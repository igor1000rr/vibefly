package by.vibefly.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import by.vibefly.app.BuildConfig
import by.vibefly.app.data.ServiceLocator
import by.vibefly.app.data.SettingsStore
import by.vibefly.app.ui.components.GroupedDivider
import by.vibefly.app.ui.components.GroupedRow
import by.vibefly.app.ui.components.GroupedTable
import by.vibefly.app.ui.components.IosNavBar
import by.vibefly.app.ui.components.IosToggle
import by.vibefly.app.ui.components.PhosphorIcon
import by.vibefly.app.ui.components.SectionHeader
import by.vibefly.app.ui.components.linenBackground
import by.vibefly.app.ui.theme.PhosphorTint
import by.vibefly.app.ui.theme.SkeuColors
import kotlinx.coroutines.launch

/**
 * Настройки в скевоморфизме iOS 6.
 *
 * Реально функциональные строки:
 *  • Agent URL (Cloudflare phosphor) → edit dialog
 *  • Auth token (Blue phosphor) → edit dialog
 *  • Provider (Violet phosphor) → picker dialog
 *
 * Декоративные/placeholder (живут в rememberSaveable, без backend):
 *  • Auto-mode, Throttle, Auto-restart on OOM, Daily backup, Battery limit
 *
 * Когда соответствующие фичи появятся в SettingsStore — связь поднимется без правок UI.
 */
@Composable
fun SettingsScreen() {
    val settingsStore = remember { ServiceLocator.settings() }
    val snapshot by settingsStore.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var showUrlDialog by remember { mutableStateOf(false) }
    var showTokenDialog by remember { mutableStateOf(false) }
    var showProviderDialog by remember { mutableStateOf(false) }

    var autoMode by rememberSaveable { mutableStateOf(false) }
    var throttle by rememberSaveable { mutableStateOf(true) }
    var autoRestartOom by rememberSaveable { mutableStateOf(true) }
    var dailyBackup by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().linenBackground()) {
        IosNavBar(title = "Settings")

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // ─── CONNECTIONS ─────────────────────────────────────────────────
            SectionHeader("Connections")
            GroupedTable {
                GroupedRow(
                    label = "Agent URL",
                    leading = { PhosphorIcon(tint = PhosphorTint.Orange, glyph = "☁") },
                    valueText = snapshot.baseUrl.removePrefix("http://").removePrefix("https://"),
                    chevron = true,
                    onClick = { showUrlDialog = true },
                )
                GroupedDivider()
                GroupedRow(
                    label = "Auth token",
                    leading = { PhosphorIcon(tint = PhosphorTint.Blue, glyph = "●") },
                    valueText = if (snapshot.authToken.isBlank()) "Not set" else "•••• " + snapshot.authToken.takeLast(4),
                    chevron = true,
                    onClick = { showTokenDialog = true },
                )
            }

            // ─── AI ASSISTANT ────────────────────────────────────────────────
            SectionHeader("AI Assistant")
            GroupedTable {
                GroupedRow(
                    label = "Provider",
                    leading = { PhosphorIcon(tint = PhosphorTint.Violet, glyph = "✦") },
                    valueText = snapshot.aiProvider.displayName,
                    chevron = true,
                    onClick = { showProviderDialog = true },
                )
                GroupedDivider()
                GroupedRow(
                    label = "Tokens used",
                    leading = { PhosphorIcon(tint = PhosphorTint.Green, glyph = "$") },
                    valueText = "— / 2M",
                    chevron = true,
                    onClick = { /* TODO: usage screen */ },
                )
                GroupedDivider()
                GroupedRow(
                    label = "Auto-mode",
                    leading = { PhosphorIcon(tint = PhosphorTint.Black, glyph = "⚡") },
                    trailing = { IosToggle(checked = autoMode, onCheckedChange = { autoMode = it }) },
                )
            }

            // ─── DEVICE ──────────────────────────────────────────────────────
            SectionHeader("Device")
            GroupedTable {
                GroupedRow(
                    label = "Battery limit",
                    leading = { PhosphorIcon(tint = PhosphorTint.Green, glyph = "▮") },
                    valueText = "30 – 80%",
                    chevron = true,
                    onClick = { /* TODO: range picker */ },
                )
                GroupedDivider()
                GroupedRow(
                    label = "Throttle > 50°C",
                    leading = { PhosphorIcon(tint = PhosphorTint.Red, glyph = "♨") },
                    trailing = { IosToggle(checked = throttle, onCheckedChange = { throttle = it }) },
                )
                GroupedDivider()
                GroupedRow(
                    label = "Auto-restart on OOM",
                    leading = { PhosphorIcon(tint = PhosphorTint.Blue, glyph = "↻") },
                    trailing = { IosToggle(checked = autoRestartOom, onCheckedChange = { autoRestartOom = it }) },
                )
            }

            // ─── BACKUPS ─────────────────────────────────────────────────────
            SectionHeader("Backups")
            GroupedTable {
                GroupedRow(
                    label = "Daily backup",
                    leading = { PhosphorIcon(tint = PhosphorTint.Orange, glyph = "▤") },
                    trailing = { IosToggle(checked = dailyBackup, onCheckedChange = { dailyBackup = it }) },
                )
                GroupedDivider()
                GroupedRow(
                    label = "Destination",
                    leading = { PhosphorIcon(tint = PhosphorTint.Amber, glyph = "↥") },
                    valueText = if (dailyBackup) "R2 · vibefly" else "Not configured",
                    chevron = true,
                    onClick = { /* TODO: destination picker */ },
                )
                GroupedDivider()
                GroupedRow(
                    label = "Last backup",
                    leading = { PhosphorIcon(tint = PhosphorTint.Gray, glyph = "◷") },
                    valueText = "—",
                    chevron = true,
                    onClick = { /* TODO: backup history */ },
                )
            }

            // ─── About footer ────────────────────────────────────────────────
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "VibeFly ${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE}",
                    color = SkeuColors.MutedText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showUrlDialog) {
        EditTextDialog(
            title = "Agent URL",
            initial = snapshot.baseUrl,
            placeholder = "http://127.0.0.1:3001",
            keyboardType = KeyboardType.Uri,
            onConfirm = {
                scope.launch { settingsStore.setBaseUrl(it) }
                showUrlDialog = false
            },
            onDismiss = { showUrlDialog = false },
        )
    }

    if (showTokenDialog) {
        EditTextDialog(
            title = "Auth token",
            initial = snapshot.authToken,
            placeholder = "Bearer токен агента (пусто = без auth)",
            keyboardType = KeyboardType.Password,
            onConfirm = {
                scope.launch { settingsStore.setAuthToken(it) }
                showTokenDialog = false
            },
            onDismiss = { showTokenDialog = false },
        )
    }

    if (showProviderDialog) {
        ProviderPickerDialog(
            current = snapshot.aiProvider,
            onPick = { provider ->
                settingsStore.setAiProvider(provider)
                showProviderDialog = false
            },
            onDismiss = { showProviderDialog = false },
        )
    }
}

@Composable
private fun EditTextDialog(
    title: String,
    initial: String,
    placeholder: String,
    keyboardType: KeyboardType,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(value.trim()) }) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    placeholder = { Text(placeholder) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

@Composable
private fun ProviderPickerDialog(
    current: SettingsStore.AiProvider,
    onPick: (SettingsStore.AiProvider) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        },
        title = { Text("AI Provider") },
        text = {
            Column {
                SettingsStore.AiProvider.values().forEach { provider ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (provider == current) "✓  ${provider.displayName}" else "    ${provider.displayName}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 8.dp),
                            color = if (provider == current) SkeuColors.LinkBlue else SkeuColors.PrimaryText,
                            fontWeight = if (provider == current) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .padding(start = 0.dp)
                    )
                    // Кликабельный обёрт ниже — Row уже на месте, делаем интерактивным целиком.
                    Spacer(modifier = Modifier.width(0.dp))
                }
                // Простой набор кликабельных строк через отдельную колонку:
                Column(modifier = Modifier.fillMaxWidth()) {
                    SettingsStore.AiProvider.values().forEach { provider ->
                        TextButton(
                            onClick = { onPick(provider) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = provider.displayName,
                                color = if (provider == current) SkeuColors.LinkBlue else SkeuColors.PrimaryText,
                                fontWeight = if (provider == current) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        },
    )
}
