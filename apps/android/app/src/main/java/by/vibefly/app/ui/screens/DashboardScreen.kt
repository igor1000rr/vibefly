package by.vibefly.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import by.vibefly.app.R
import by.vibefly.app.agent.SystemMetricsDto
import by.vibefly.app.data.AppItem
import by.vibefly.app.data.AppStatus

/**
 * Главный экран — состояние устройства + список приложений.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onAppClick: (String) -> Unit,
    viewModel: DashboardViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.dashboard_title)) })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { HealthCard(metrics = state.metrics) }
            if (state.error != null) {
                item { ErrorCard(text = state.error.orEmpty()) }
            }
            item {
                Text(
                    text = stringResource(R.string.dashboard_apps_section),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (state.apps.isEmpty() && state.error == null) {
                item {
                    Text(
                        text = stringResource(R.string.dashboard_empty_apps),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.apps, key = { it.id }) { app ->
                AppRow(item = app, onClick = { onAppClick(app.id) })
            }
        }
    }
}

@Composable
private fun HealthCard(metrics: SystemMetricsDto?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Device health",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                HealthMetric(
                    stringResource(R.string.dashboard_health_battery),
                    metrics?.let { "${it.batteryLevel}%" } ?: "\u2014",
                )
                HealthMetric(
                    stringResource(R.string.dashboard_health_temp),
                    metrics?.let { "%.0f\u00B0C".format(it.temperatureC) } ?: "\u2014",
                )
                HealthMetric(
                    stringResource(R.string.dashboard_health_cpu),
                    metrics?.let { "%.0f%%".format(it.cpuPercent) } ?: "\u2014",
                )
                HealthMetric(
                    stringResource(R.string.dashboard_health_ram),
                    metrics?.let {
                        "%.1fG".format(it.ramUsedMb / 1024.0)
                    } ?: "\u2014",
                )
            }
        }
    }
}

@Composable
private fun HealthMetric(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun ErrorCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "\u0410\u0433\u0435\u043D\u0442 \u043D\u0435\u0434\u043E\u0441\u0442\u0443\u043F\u0435\u043D",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun AppRow(item: AppItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    item.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusChip(status = item.status)
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 14.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
        )
    }
}

@Composable
private fun StatusChip(status: AppStatus) {
    val (text, color) = when (status) {
        AppStatus.Running -> stringResource(R.string.status_running) to MaterialTheme.colorScheme.primary
        AppStatus.Deploying -> stringResource(R.string.status_deploying) to MaterialTheme.colorScheme.tertiary
        AppStatus.Stopped -> stringResource(R.string.status_stopped) to MaterialTheme.colorScheme.onSurfaceVariant
        AppStatus.Failed -> stringResource(R.string.status_error) to MaterialTheme.colorScheme.error
        AppStatus.Unknown -> "\u2014" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}
