package by.vibefly.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import by.vibefly.app.agent.AppDto
import by.vibefly.app.agent.LogEntryDto

/**
 * Экран деталей приложения.
 *
 * Сверху — карточка с метаданными и кнопками restart/stop.
 * Снизу — живые логи на тёмной консольной подложке.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    appId: String,
    onBack: () -> Unit,
    viewModel: AppDetailViewModel = viewModel(factory = AppDetailViewModel.Factory(appId)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.app?.name ?: appId) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.load() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null)
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.app?.let { AppMetaCard(app = it) }
            ActionButtons(
                onRestart = viewModel::restart,
                onStop = viewModel::stop,
            )
            LiveStatusBar(streaming = state.streaming, error = state.error)
            LogsConsole(logs = state.logs, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun AppMetaCard(app: AppDto) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(app.name, style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.size(8.dp))
                StatusBadge(status = app.status)
            }
            app.domain?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            app.repo?.let {
                Text(
                    text = it + (app.branch?.let { b -> "  \u00B7  $b" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                app.port?.let { MetaChip(":${'$'}it") }
                app.memoryMb?.let { MetaChip("${'$'}it MB") }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val color = when (status.lowercase()) {
        "running" -> MaterialTheme.colorScheme.primary
        "deploying" -> MaterialTheme.colorScheme.tertiary
        "failed" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = status,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun MetaChip(text: String) {
    AssistChip(
        onClick = {},
        label = { Text(text) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    )
}

@Composable
private fun ActionButtons(
    onRestart: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AssistChip(
            onClick = onRestart,
            leadingIcon = { Icon(Icons.Outlined.PlayArrow, contentDescription = null) },
            label = { Text("Restart") },
            modifier = Modifier.weight(1f),
        )
        AssistChip(
            onClick = onStop,
            leadingIcon = { Icon(Icons.Outlined.Stop, contentDescription = null) },
            label = { Text("Stop") },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LiveStatusBar(streaming: Boolean, error: String?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    if (streaming) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                ),
        )
        Text(
            text = if (streaming) "live \u00B7 stdout" else "offline",
            style = MaterialTheme.typography.labelSmall,
            color = if (streaming) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        error?.let {
            Text(
                text = "  \u00B7  " + it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun LogsConsole(logs: List<LogEntryDto>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()

    // Автоскролл вниз при появлении новых строк.
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A)),
    ) {
        if (logs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "\u041e\u0436\u0438\u0434\u0430\u043D\u0438\u0435 \u043B\u043E\u0433\u043E\u0432\u2026",
                    color = Color(0xFF5A8A5A),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
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
        Text(
            text = shortTime,
            color = Color(0xFF5A8A5A),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = "[${'$'}{entry.source}]",
            color = Color(0xFF7DA7D4),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = entry.message,
            color = msgColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
        )
    }
}
