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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
import by.vibefly.app.ui.components.SectionHeader
import by.vibefly.app.ui.components.linenBackground
import by.vibefly.app.ui.theme.PhosphorTint
import by.vibefly.app.ui.theme.SkeuColors

/**
 * Главный экран VibeFly — Device Health + список приложений в стиле iOS 6.
 *
 *  • IosNavBar сверху ("VibeFly" + кнопка "+ Deploy")
 *  • Grouped table с 4 метриками устройства
 *  • Grouped table с приложениями, у каждого IosToggle для старт/стоп
 *
 * TabBar рисуется NavHost'ом снаружи, не здесь.
 */
@Composable
fun DashboardScreen(
    onAppClick: (String) -> Unit,
    onDeployClick: () -> Unit,
    viewModel: DashboardViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().linenBackground()) {
        IosNavBar(
            title = "VibeFly",
            trailing = {
                IosNavButton(text = "+ Deploy", onClick = onDeployClick)
            },
        )
        when {
            state.loading && state.apps.isEmpty() -> LoadingPlaceholder()
            state.error != null && state.apps.isEmpty() -> ErrorPlaceholder(state.error!!)
            else -> DashboardContent(
                metrics = state.metrics,
                apps = state.apps,
                onAppClick = onAppClick,
                onToggle = viewModel::toggle,
            )
        }
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
        SectionHeader("Device health")
        DeviceHealthTable(metrics)

        SectionHeader("Apps · ${apps.size}")
        AppsTable(apps = apps, onAppClick = onAppClick, onToggle = onToggle)

        Text(
            text = "Toggle to start or stop apps. Tap a row for logs and details.",
            color = SkeuColors.SecondaryText,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp),
        )
    }
}

// ─── Device Health ───────────────────────────────────────────────────────────

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

// ─── Apps list ───────────────────────────────────────────────────────────────

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

/**
 * Кастомная двух-строчная строка для приложения. GroupedRow рисует только одну
 * строку, поэтому здесь дублируем его layout с поправкой на subtitle.
 */
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
                text = app.subtitle + (app.memoryMb?.let { " · $it MB" } ?: ""),
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

/**
 * Цвет аватарки определяется первой буквой имени + статусом.
 */
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
