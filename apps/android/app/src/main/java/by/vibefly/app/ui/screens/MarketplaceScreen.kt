package by.vibefly.app.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import by.vibefly.app.agent.MarketplaceTemplateDto
import by.vibefly.app.ui.components.GroupedDivider
import by.vibefly.app.ui.components.GroupedTable
import by.vibefly.app.ui.components.IosButtonPrimary
import by.vibefly.app.ui.components.IosNavBar
import by.vibefly.app.ui.components.PhosphorIcon
import by.vibefly.app.ui.components.SectionHeader
import by.vibefly.app.ui.components.linenBackground
import by.vibefly.app.ui.theme.PhosphorTint
import by.vibefly.app.ui.theme.SkeuColors

/**
 * Marketplace в скевоморфизме iOS 6.
 *
 * Шаблоны сгруппированы по категориям. Каждая секция — GroupedTable, каждая
 * строка — кастомная TemplateRow с PhosphorIcon, текстами и кнопкой Install.
 */
@Composable
fun MarketplaceScreen(
    viewModel: MarketplaceViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.installedMessage) {
        state.installedMessage?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    Column(modifier = Modifier.fillMaxSize().linenBackground()) {
        IosNavBar(title = "Marketplace")

        when {
            state.loading -> LoadingState()
            state.error != null -> ErrorState(state.error!!)
            else -> CatalogList(
                state = state,
                onInstall = viewModel::install,
            )
        }
    }

    SnackbarHost(snackbar) { Snackbar(it) }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = SkeuColors.NavBarMidDark)
    }
}

@Composable
private fun ErrorState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Не удалось загрузить каталог",
                color = SkeuColors.PrimaryText,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = message, color = SkeuColors.SecondaryText, fontSize = 12.sp)
        }
    }
}

@Composable
private fun CatalogList(
    state: MarketplaceState,
    onInstall: (MarketplaceTemplateDto) -> Unit,
) {
    val grouped = state.templates.groupBy { it.category }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        grouped.forEach { (category, items) ->
            SectionHeader(humanizeCategory(category))
            GroupedTable {
                items.forEachIndexed { index, tpl ->
                    TemplateRow(
                        template = tpl,
                        installing = state.installing == tpl.id,
                        onInstall = { onInstall(tpl) },
                    )
                    if (index < items.lastIndex) GroupedDivider()
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

private fun humanizeCategory(c: String): String = when (c) {
    "productivity" -> "Продуктивность"
    "automation" -> "Автоматизация"
    "privacy" -> "Приватность"
    "devtools" -> "Для разработчиков"
    "bots" -> "Боты"
    "monitoring" -> "Мониторинг"
    "database" -> "Базы данных"
    "content" -> "Контент"
    else -> c.replaceFirstChar { it.uppercase() }
}

private fun categoryTint(category: String): PhosphorTint = when (category) {
    "productivity" -> PhosphorTint.Blue
    "automation" -> PhosphorTint.Violet
    "privacy" -> PhosphorTint.Orange
    "devtools" -> PhosphorTint.Black
    "bots" -> PhosphorTint.Green
    "monitoring" -> PhosphorTint.Red
    "database" -> PhosphorTint.Amber
    "content" -> PhosphorTint.Gray
    else -> PhosphorTint.Gray
}

@Composable
private fun TemplateRow(
    template: MarketplaceTemplateDto,
    installing: Boolean,
    onInstall: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PhosphorIcon(
            tint = categoryTint(template.category),
            size = 40.dp,
            cornerRadius = 8.dp,
            content = {
                Text(
                    text = template.icon,
                    fontSize = 20.sp,
                    color = Color.White,
                )
            },
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = template.name,
                color = SkeuColors.PrimaryText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = template.description,
                color = SkeuColors.SecondaryText,
                fontSize = 11.sp,
                maxLines = 2,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        if (installing) {
            Box(
                modifier = Modifier.size(width = 72.dp, height = 30.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = SkeuColors.NavBarMidDark,
                )
            }
        } else {
            IosButtonPrimary(
                text = "Install",
                onClick = onInstall,
            )
        }
    }
}
