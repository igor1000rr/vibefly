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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import by.vibefly.app.R

/**
 * Главный экран — состояние устройства + список приложений.
 * Пока все данные — фейки, чтобы видеть верстку. Реальное состояние придёт из
 * AgentClient + RuntimeManager после интеграции.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onAppClick: (String) -> Unit,
) {
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
            item { HealthCard() }
            item {
                Text(
                    text = stringResource(R.string.dashboard_apps_section),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(FakeApps) { app ->
                AppRow(item = app, onClick = { onAppClick(app.id) })
            }
        }
    }
}

@Composable
private fun HealthCard() {
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
                HealthMetric(stringResource(R.string.dashboard_health_battery), "78%")
                HealthMetric(stringResource(R.string.dashboard_health_temp), "38\u00B0C")
                HealthMetric(stringResource(R.string.dashboard_health_cpu), "23%")
                HealthMetric(stringResource(R.string.dashboard_health_ram), "2.1G")
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

private data class AppListItem(
    val id: String,
    val name: String,
    val subtitle: String,
    val status: AppStatus,
)

private enum class AppStatus { Running, Deploying, Stopped }

private val FakeApps = listOf(
    AppListItem("amina-bot", "amina-bot", "@AIAMINABOT \u00B7 :3001", AppStatus.Running),
    AppListItem("tonforge-api", "tonforge-api", "api.tonforge.org", AppStatus.Running),
    AppListItem("azcrm-staging", "azcrm-staging", "staging.azgroup.net", AppStatus.Deploying),
    AppListItem("analytics-cron", "analytics-cron", "internal \u00B7 cron", AppStatus.Stopped),
)

@Composable
private fun AppRow(item: AppListItem, onClick: () -> Unit) {
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
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = item.subtitle,
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
