package by.vibefly.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import by.vibefly.app.agent.AppDto
import by.vibefly.app.agent.LogEntryDto
import by.vibefly.app.ui.components.GroupedDivider
import by.vibefly.app.ui.components.GroupedRow
import by.vibefly.app.ui.components.GroupedTable
import by.vibefly.app.ui.components.IosButtonGlossy
import by.vibefly.app.ui.components.IosButtonPrimary
import by.vibefly.app.ui.components.IosNavBar
import by.vibefly.app.ui.components.IosNavButton
import by.vibefly.app.ui.components.PhosphorIcon
import by.vibefly.app.ui.components.SectionHeader
import by.vibefly.app.ui.components.SegmentedControl
import by.vibefly.app.ui.components.linenBackground
import by.vibefly.app.ui.theme.PhosphorTint
import by.vibefly.app.ui.theme.SkeuColors
import by.vibefly.app.ui.theme.SkeuGradients

@Composable
fun AppDetailScreen(
    appId: String,
    onBack: () -> Unit,
    viewModel: AppDetailViewModel = viewModel(factory = AppDetailViewModel.Factory(appId)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var showUninstallConfirm by remember { mutableStateOf(false) }
    val tabs = listOf("Overview", "Logs", "Env", "Deploys")

    Column(modifier = Modifier.fillMaxSize().linenBackground()) {
        IosNavBar(
            title = state.app?.name ?: appId,
            leading = { IosNavButton(text = "‹ Apps", onClick = onBack) },
            trailing = {
                IosNavButton(
                    text = if (state.uninstalling) "…" else "Uninstall",
                    onClick = { if (!state.uninstalling) showUninstallConfirm = true },
                )
            },
        )

        if (state.loading && state.app == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SkeuColors.NavBarMidDark)
            }
            return@Column
        }

        Column(modifier = Modifier.weight(1f)) {
            state.app?.let { Hero(app = it) }

            SegmentedControl(
                items = tabs,
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            when (selectedTab) {
                0 -> OverviewTab(state.app)
                1 -> LogsTab(state.logs, state.streaming)
                2 -> PlaceholderTab("Env editor — фаза 2")
                3 -> PlaceholderTab("История деплоев — фаза 2")
            }
        }

        BottomActions(
            onRestart = viewModel::restart,
            onStop = viewModel::stop,
        )
    }

    if (showUninstallConfirm) {
        AlertDialog(
            onDismissRequest = { if (!state.uninstalling) showUninstallConfirm = false },
            title = { Text("Удалить приложение?") },
            text = {
                Text(
                    "Приложение «${state.app?.name ?: appId}» будет остановлено и удалено вместе со всеми файлами в workdir. Действие невозможно отменить.",
                    color = SkeuColors.SecondaryText,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUninstallConfirm = false
                        viewModel.uninstall(onDone = onBack)
                    },
                    enabled = !state.uninstalling,
                ) { Text("Удалить", color = SkeuColors.AccentRed) }
            },
            dismissButton = {
                TextButton(onClick = { showUninstallConfirm = false }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun Hero(app: AppDto) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PhosphorIcon(
            tint = heroTint(app.status),
            size = 64.dp,
            cornerRadius = 14.dp,
            content = {
                Text(
                    text = app.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            },
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = app.name,
            color = SkeuColors.PrimaryText,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        val subtitle = buildString {
            app.domain?.let { append(it) }
            app.repo?.takeIf { app.domain == null }?.let { append(it) }
            app.branch?.let { append(" · ").append(it) }
        }
        if (subtitle.isNotEmpty()) {
            Text(
                text = subtitle,
                color = SkeuColors.SecondaryText,
                fontSize = 11.sp,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        StatusPill(status = app.status)
    }
}

private data class StatusPillStyle(
    val label: String,
    val brush: Brush,
    val stroke: Color,
)

@Composable
private fun StatusPill(status: String) {
    val style = when (status.lowercase()) {
        "running" -> StatusPillStyle(
            label = "Running",
            brush = SkeuGradients.toggleOn(),
            stroke = SkeuColors.ToggleOnStroke,
        )
        "stopped" -> StatusPillStyle(
            label = "Stopped",
            brush = SkeuGradients.glossyGrayButton(),
            stroke = SkeuColors.GlossyGrayStroke,
        )
        "deploying" -> StatusPillStyle(
            label = "Deploying",
            brush = SkeuGradients.phosphor(
                SkeuColors.PhosphorAmberTop,
                SkeuColors.PhosphorAmberBottom,
            ),
            stroke = SkeuColors.PhosphorAmberBottom,
        )
        "failed" -> StatusPillStyle(
            label = "Failed",
            brush = SkeuGradients.phosphor(
                SkeuColors.PhosphorRedTop,
                SkeuColors.PhosphorRedBottom,
            ),
            stroke = SkeuColors.PhosphorRedBottom,
        )
        else -> StatusPillStyle(
            label = status,
            brush = SkeuGradients.glossyGrayButton(),
            stroke = SkeuColors.GlossyGrayStroke,
        )
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(style.brush)
            .border(1.dp, style.stroke, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = style.label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun heroTint(status: String): PhosphorTint = when (status.lowercase()) {
    "running" -> PhosphorTint.Green
    "deploying" -> PhosphorTint.Amber
    "failed" -> PhosphorTint.Red
    else -> PhosphorTint.Gray
}

@Composable
private fun OverviewTab(app: AppDto?) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        SectionHeader("Status")
        GroupedTable {
            GroupedRow(
                label = "Uptime",
                valueText = humanizeUptime(app?.startedAt),
                valueColor = SkeuColors.PrimaryText,
            )
            GroupedDivider()
            GroupedRow(
                label = "Last deploy",
                valueText = humanizeAgo(app?.lastDeploy) ?: "—",
                valueColor = SkeuColors.PrimaryText,
            )
            GroupedDivider()
            GroupedRow(
                label = "Health checks",
                valueText = "Passing · 142/142",
                valueColor = SkeuColors.AccentGreen,
            )
        }

        SectionHeader("Resources")
        GroupedTable {
            GroupedRow(label = "CPU", valueText = "12%", valueColor = SkeuColors.PrimaryText)
            GroupedDivider()
            GroupedRow(
                label = "Memory",
                valueText = app?.memoryMb?.let { "$it / 512 MB" } ?: "—",
                valueColor = SkeuColors.PrimaryText,
            )
            GroupedDivider()
            GroupedRow(label = "Network", valueText = "3.2 MB/s", valueColor = SkeuColors.PrimaryText)
        }

        SectionHeader("Configuration")
        GroupedTable {
            GroupedRow(
                label = "Port",
                valueText = app?.port?.toString() ?: "—",
                valueColor = SkeuColors.PrimaryText,
            )
            GroupedDivider()
            GroupedRow(
                label = "Branch",
                valueText = app?.branch ?: "—",
                chevron = true,
                onClick = { /* TODO: branch switcher */ },
            )
            GroupedDivider()
            GroupedRow(
                label = "Repository",
                valueText = app?.repo?.takeLastShort() ?: "—",
                chevron = true,
                onClick = { /* TODO: open in browser */ },
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
    }
}

private fun String.takeLastShort(maxLen: Int = 18): String =
    if (length <= maxLen) this else take(maxLen - 1) + "…"

private fun humanizeUptime(startedAt: String?): String {
    if (startedAt.isNullOrEmpty()) return "—"
    return startedAt.take(10)
}

private fun humanizeAgo(timestamp: String?): String? = timestamp?.take(10)

@Composable
private fun LogsTab(logs: List<LogEntryDto>, streaming: Boolean) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        LiveStatusBar(streaming = streaming)
        Spacer(modifier = Modifier.height(6.dp))
        LogsConsole(logs = logs, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun LiveStatusBar(streaming: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (streaming) SkeuColors.AccentGreen else SkeuColors.MutedText),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (streaming) "live · stdout" else "offline",
            color = if (streaming) SkeuColors.AccentGreen else SkeuColors.MutedText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun LogsConsole(logs: List<LogEntryDto>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0A0A0A))
            .border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp)),
    ) {
        if (logs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Ожидание логов…",
                    color = Color(0xFF5A8A5A),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                items(logs, key = { "${it.time}-${it.source}-${it.message.hashCode()}" }) { entry ->
                    LogRow(entry)
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }
        }
    }
}

@Composable
private fun LogRow(entry: LogEntryDto) {
    val msgColor = when (entry.level.lowercase()) {
        "warn" -> Color(0xFFFFD870)
        "error" -> Color(0xFFFF8585)
        else -> Color(0xFF9BE39B)
    }
    val shortTime = entry.time.substringAfter('T').substringBefore('.').take(8)
    Row {
        Text(shortTime, color = Color(0xFF5A8A5A), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Text("[${entry.source}]", color = Color(0xFF7DA7D4), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(entry.message, color = msgColor, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
    }
}

@Composable
private fun PlaceholderTab(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            color = SkeuColors.SecondaryText,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun BottomActions(
    onRestart: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IosButtonGlossy(
            text = "Stop",
            onClick = onStop,
            modifier = Modifier.weight(1f),
        )
        IosButtonPrimary(
            text = "Restart",
            onClick = onRestart,
            modifier = Modifier.weight(1f),
        )
    }
}
