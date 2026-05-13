package by.vibefly.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
 * Секции:
 *   • CONNECTIONS — Demo mode, Agent URL, Auth token
 *   • PUBLIC TUNNEL — Cloudflare tunnel toggle + публичный URL
 *   • AI ASSISTANT, DEVICE, BACKUPS — прежние
 */
@Composable
fun SettingsScreen() {
    val settingsStore = remember { ServiceLocator.settings() }
    val snapshot by settingsStore.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val tunnelVm: TunnelViewModel = viewModel()
    val tunnelState by tunnelVm.state.collectAsStateWithLifecycle()

    var showUrlDialog by remember { mutableStateOf(false) }
    var showTokenDialog by remember { mutableStateOf(false) }
    var showProviderDialog by remember { mutableStateOf(false) }

    var autoMode by rememberSaveable { mutableStateOf(false) }
    var throttle by rememberSaveable { mutableStateOf(true) }
    var autoRestartOom by rememberSaveable { mutableStateOf(true) }
    var dailyBackup by rememberSaveable { mutableStateOf(false) }

    val openUrlDialog: (() -> Unit)? = if (snapshot.demoMode) null else ({ showUrlDialog = true })
    val openTokenDialog: (() -> Unit)? = if (snapshot.demoMode) null else ({ showTokenDialog = true })

    Column(modifier = Modifier.fillMaxSize().linenBackground()) {
        IosNavBar(title = "Settings")

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // ─── CONNECTIONS ────────────────────────────────────────
            SectionHeader("Connections")
            GroupedTable {
                GroupedRow(
                    label = "Demo mode",
                    leading = { PhosphorIcon(tint = PhosphorTint.Violet, glyph = "★") },
                    trailing = {
                        IosToggle(
                            checked = snapshot.demoMode,
                            onCheckedChange = { settingsStore.setDemoMode(it) },
                        )
                    },
                )
                GroupedDivider()
                GroupedRow(
                    label = "Agent URL",
                    leading = { PhosphorIcon(tint = PhosphorTint.Orange, glyph = "☁") },
                    valueText = if (snapshot.demoMode) "mock://"
                                else snapshot.baseUrl.removePrefix("http://").removePrefix("https://"),
                    chevron = !snapshot.demoMode,
                    onClick = openUrlDialog,
                )
                GroupedDivider()
                GroupedRow(
                    label = "Auth token",
                    leading = { PhosphorIcon(tint = PhosphorTint.Blue, glyph = "●") },
                    valueText = when {
                        snapshot.demoMode -> "—"
                        snapshot.authToken.isBlank() -> "Not set"
                        else -> "•••• " + snapshot.authToken.takeLast(4)
                    },
                    chevron = !snapshot.demoMode,
                    onClick = openTokenDialog,
                )
            }
            if (snapshot.demoMode) {
                Text(
                    text = "Демо-режим: все данные фейковые, сетевые вызовы отключены.",
                    color = SkeuColors.AccentOrange,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
                )
            }

            // ─── PUBLIC TUNNEL ───────────────────────────────────────
            TunnelSection(
                state = tunnelState,
                onStart = { tunnelVm.start() },
                onStop = { tunnelVm.stop() },
            )

            // ─── AI ASSISTANT ─────────────────────────────────────────
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
                    onClick = { },
                )
                GroupedDivider()
                GroupedRow(
                    label = "Auto-mode",
                    leading = { PhosphorIcon(tint = PhosphorTint.Black, glyph = "⚡") },
                    trailing = { IosToggle(checked = autoMode, onCheckedChange = { autoMode = it }) },
                )
            }

            // ─── DEVICE ──────────────────────────────────────────────
            SectionHeader("Device")
            GroupedTable {
                GroupedRow(
                    label = "Battery limit",
                    leading = { PhosphorIcon(tint = PhosphorTint.Green, glyph = "▮") },
                    valueText = "30 – 80%",
                    chevron = true,
                    onClick = { },
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

            // ─── BACKUPS ────────────────────────────────────────────
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
                    onClick = { },
                )
                GroupedDivider()
                GroupedRow(
                    label = "Last backup",
                    leading = { PhosphorIcon(tint = PhosphorTint.Gray, glyph = "◷") },
                    valueText = "—",
                    chevron = true,
                    onClick = { },
                )
            }

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

/**
 * Секция Public Tunnel — показывает статус туннеля, public URL, кнопку start/stop.
 */
@Composable
private fun TunnelSection(
    state: TunnelState,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val status = state.status
    val provider = status.provider

    SectionHeader("Public tunnel")
    GroupedTable {
        GroupedRow(
            label = "Cloudflare",
            leading = { PhosphorIcon(tint = PhosphorTint.Orange, glyph = "→") },
            valueText = when {
                provider == "none" -> "Not available"
                state.busy -> "…"
                status.active -> "On"
                else -> "Off"
            },
            trailing = if (provider != "none") ({
                IosToggle(
                    checked = status.active,
                    onCheckedChange = { wanted ->
                        if (wanted) onStart() else onStop()
                    },
                )
            }) else null,
        )
        if (status.active && status.publicUrl != null) {
            GroupedDivider()
            GroupedRow(
                label = "Public URL",
                leading = { PhosphorIcon(tint = PhosphorTint.Green, glyph = "↗") },
                valueText = status.publicUrl.removePrefix("https://"),
                valueColor = SkeuColors.LinkBlue,
                chevron = false,
                onClick = {
                    clipboard.setText(AnnotatedString(status.publicUrl))
                },
            )
        }
        if (status.lastError != null) {
            GroupedDivider()
            GroupedRow(
                label = "Error",
                leading = { PhosphorIcon(tint = PhosphorTint.Red, glyph = "!") },
                valueText = status.lastError,
                valueColor = SkeuColors.AccentRed,
            )
        }
    }
    val hint = when {
        provider == "none" -> "Туннель недоступен — cloudflared бинарь отсутствует в APK."
        status.active -> "Телефон доступен из интернета. Тапни URL чтобы скопировать."
        else -> "Включи чтобы получить публичный HTTPS URL через Cloudflare."
    }
    Text(
        text = hint,
        color = SkeuColors.SecondaryText,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
    )
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
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                placeholder = { Text(placeholder) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.fillMaxWidth(),
            )
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
            Column(modifier = Modifier.fillMaxWidth()) {
                SettingsStore.AiProvider.values().forEach { provider ->
                    val selected = provider == current
                    TextButton(
                        onClick = { onPick(provider) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = (if (selected) "✓ " else "  ") + provider.displayName,
                            color = if (selected) SkeuColors.LinkBlue else SkeuColors.PrimaryText,
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
    )
}
