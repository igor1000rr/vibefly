package by.vibefly.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import by.vibefly.app.agent.InstallAppRequest
import by.vibefly.app.agent.SystemMetricsDto
import by.vibefly.app.data.AppItem
import by.vibefly.app.data.AppStatus
import by.vibefly.app.ui.components.GroupedDivider
import by.vibefly.app.ui.components.GroupedRow
import by.vibefly.app.ui.components.GroupedTable
import by.vibefly.app.ui.components.IosNavBar
import by.vibefly.app.ui.components.IosNavButton
import by.vibefly.app.ui.components.IosToggle
import by.vibefly.app.ui.components.PhosphorIcon
import by.vibefly.app.ui.components.RuntimeStatusCard
import by.vibefly.app.ui.components.SectionHeader
import by.vibefly.app.ui.components.linenBackground
import by.vibefly.app.ui.theme.PhosphorTint
import by.vibefly.app.ui.theme.SkeuColors

@Composable
fun DashboardScreen(
    onAppClick: (String) -> Unit,
    onDeployClick: () -> Unit = {},
    viewModel: DashboardViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDeployDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().linenBackground()) {
        IosNavBar(
            title = "VibeFly",
            leading = {
                IosNavButton(text = "↻", onClick = viewModel::refresh)
            },
            trailing = {
                IosNavButton(
                    text = "+ Deploy",
                    onClick = {
                        showDeployDialog = true
                        onDeployClick()
                    },
                )
            },
        )
        when {
            state.loading && state.apps.isEmpty() -> {
                RuntimeStatusCard()
                LoadingPlaceholder()
            }
            state.error != null && state.apps.isEmpty() -> {
                RuntimeStatusCard()
                ErrorPlaceholder(state.error!!)
            }
            else -> DashboardContent(
                metrics = state.metrics,
                apps = state.apps,
                onAppClick = onAppClick,
                onToggle = viewModel::toggle,
            )
        }
    }

    if (showDeployDialog) {
        DeployDialog(
            deploying = state.deploying,
            error = state.deployError,
            onConfirm = { req ->
                viewModel.deploy(req) { showDeployDialog = false }
            },
            onDismiss = {
                viewModel.clearDeployError()
                showDeployDialog = false
            },
        )
    }
}

@Composable
private fun LoadingPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = SkeuColors.NavBarMidDark)
    }
}

@Composable
private fun ErrorPlaceholder(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Агент недоступен",
                color = SkeuColors.PrimaryText,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message,
                color = SkeuColors.SecondaryText,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun DashboardContent(
    metrics: SystemMetricsDto?,
    apps: List<AppItem>,
    onAppClick: (String) -> Unit,
    onToggle: (AppItem) -> Unit,
) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll),
    ) {
        RuntimeStatusCard()

        SectionHeader("Device health")
        DeviceHealthTable(metrics)

        if (apps.isEmpty()) {
            SectionHeader("Apps")
            EmptyAppsCard()
        } else {
            SectionHeader("Apps · ${apps.size}")
            AppsTable(apps = apps, onAppClick = onAppClick, onToggle = onToggle)
        }

        Text(
            text = if (apps.isEmpty())
                "Нажми + Deploy в верхнем правом углу чтобы развернуть первое приложение."
            else
                "Toggle to start or stop apps. Tap a row for logs and details.",
            color = SkeuColors.SecondaryText,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp),
        )
    }
}

@Composable
private fun EmptyAppsCard() {
    GroupedTable {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Пока ничего не развёрнуто",
                color = SkeuColors.SecondaryText,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun DeviceHealthTable(metrics: SystemMetricsDto?) {
    GroupedTable {
        GroupedRow(
            label = "Battery",
            valueText = metrics?.let { "${it.batteryLevel}%" } ?: "—",
            valueColor = SkeuColors.LinkBlue,
        )
        GroupedDivider()
        GroupedRow(
            label = "Temperature",
            valueText = metrics?.let { "${it.temperatureC.toInt()}°C" } ?: "—",
            valueColor = SkeuColors.PrimaryText,
        )
        GroupedDivider()
        GroupedRow(
            label = "CPU",
            valueText = metrics?.let { "${it.cpuPercent.toInt()}%" } ?: "—",
            valueColor = SkeuColors.PrimaryText,
        )
        GroupedDivider()
        GroupedRow(
            label = "RAM",
            valueText = metrics?.let {
                val usedGb = it.ramUsedMb / 1024.0
                val totalGb = it.ramTotalMb / 1024
                "%.1f / %d GB".format(usedGb, totalGb)
            } ?: "—",
            valueColor = SkeuColors.PrimaryText,
        )
    }
}

@Composable
private fun AppsTable(
    apps: List<AppItem>,
    onAppClick: (String) -> Unit,
    onToggle: (AppItem) -> Unit,
) {
    GroupedTable {
        apps.forEachIndexed { index, app ->
            AppListRow(
                app = app,
                onClick = { onAppClick(app.id) },
                onToggle = { onToggle(app) },
            )
            if (index < apps.lastIndex) GroupedDivider()
        }
    }
}

@Composable
private fun AppListRow(
    app: AppItem,
    onClick: () -> Unit,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 50.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppAvatar(name = app.name, status = app.status)
        Spacer(modifier = Modifier.width(10.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = app.name,
                color = SkeuColors.PrimaryText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitleFor(app),
                color = subtitleColor(app.status),
                fontSize = 11.sp,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        IosToggle(
            checked = app.status == AppStatus.Running,
            onCheckedChange = { onToggle() },
        )
    }
}

private fun subtitleFor(app: AppItem): String = when (app.status) {
    AppStatus.Deploying -> "Deploying…"
    AppStatus.Failed -> "Failed · tap for logs"
    AppStatus.Stopped -> "Stopped"
    AppStatus.Unknown -> "—"
    AppStatus.Running -> app.compactLine
}

private fun subtitleColor(status: AppStatus): Color = when (status) {
    AppStatus.Deploying -> SkeuColors.AccentOrange
    AppStatus.Failed -> SkeuColors.AccentRed
    else -> SkeuColors.MutedText
}

@Composable
private fun AppAvatar(name: String, status: AppStatus) {
    val letter = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val tint = avatarTint(letter, status)
    PhosphorIcon(
        tint = tint,
        size = 32.dp,
        cornerRadius = 7.dp,
        content = {
            Text(
                text = letter,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        },
    )
}

private fun avatarTint(letter: String, status: AppStatus): PhosphorTint = when (status) {
    AppStatus.Deploying -> PhosphorTint.Amber
    AppStatus.Failed -> PhosphorTint.Red
    AppStatus.Stopped, AppStatus.Unknown -> PhosphorTint.Gray
    AppStatus.Running -> when ((letter.firstOrNull()?.code ?: 0) % 4) {
        0 -> PhosphorTint.Green
        1 -> PhosphorTint.Blue
        2 -> PhosphorTint.Violet
        else -> PhosphorTint.Orange
    }
}

/**
 * Диалог развёртывания. Поля:
 *   • ID (slug) — обязательно
 *   • Имя — опционально
 *   • URL бинаря — опционально, https://; agent скачает в workdir/binary
 *   • Команда запуска — обязательно. Если URL задан, хинт покажет "./binary".
 *   • Порт — опционально
 */
@Composable
private fun DeployDialog(
    deploying: Boolean,
    error: String?,
    onConfirm: (InstallAppRequest) -> Unit,
    onDismiss: () -> Unit,
) {
    var id by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var binaryUrl by remember { mutableStateOf("") }
    var startCmd by remember { mutableStateOf("") }
    var portText by remember { mutableStateOf("") }

    val canConfirm = id.isNotBlank() && startCmd.isNotBlank() && !deploying
    val urlLooksValid = binaryUrl.isBlank() || binaryUrl.startsWith("https://")

    AlertDialog(
        onDismissRequest = { if (!deploying) onDismiss() },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        InstallAppRequest(
                            id = id.trim(),
                            name = name.trim().ifBlank { id.trim() },
                            startCmd = startCmd.trim(),
                            port = portText.trim().toIntOrNull(),
                            binaryUrl = binaryUrl.trim().takeIf { it.isNotBlank() },
                        )
                    )
                },
                enabled = canConfirm && urlLooksValid,
            ) { Text(if (deploying) "Разворачиваю…" else "Deploy") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !deploying) { Text("Отмена") }
        },
        title = { Text("Развернуть приложение") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = id,
                    onValueChange = { id = it.filter { c -> c.isLetterOrDigit() || c == '-' || c == '_' } },
                    label = { Text("ID (slug)") },
                    placeholder = { Text("my-bot") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Имя (не обязательно)") },
                    placeholder = { Text("My bot") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = binaryUrl,
                    onValueChange = { binaryUrl = it },
                    label = { Text("URL бинаря (опционально)") },
                    placeholder = { Text("https://github.com/.../release/binary") },
                    singleLine = true,
                    isError = !urlLooksValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = startCmd,
                    onValueChange = { startCmd = it },
                    label = { Text("Команда запуска") },
                    placeholder = {
                        Text(
                            if (binaryUrl.isNotBlank()) "./binary --port 8080"
                            else "./mybin --port 8080"
                        )
                    },
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it.filter { c -> c.isDigit() } },
                    label = { Text("Порт (не обязательно)") },
                    placeholder = { Text("8080") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = error,
                        color = SkeuColors.AccentRed,
                        fontSize = 12.sp,
                    )
                }
                if (!urlLooksValid) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "URL должен начинаться с https://",
                        color = SkeuColors.AccentRed,
                        fontSize = 10.sp,
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = if (binaryUrl.isNotBlank())
                        "Агент скачает бинарь в workdir/binary и chmod +x. Максимум 200 MB, timeout 90s."
                    else
                        "Без URL — положить бинарь в apps_dir/<id>/ вручную (adb или туннель).",
                    color = SkeuColors.SecondaryText,
                    fontSize = 10.sp,
                )
            }
        },
    )
}
