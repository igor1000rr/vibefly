package by.vibefly.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import by.vibefly.app.R
import by.vibefly.app.agent.MarketplaceTemplateDto

/**
 * Каталог one-click приложений. Каждый элемент — карточка с эмодзи-иконкой, названием,
 * описанием и кнопкой Install. Между секциями — заголовки категорий.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketplaceScreen(
    viewModel: MarketplaceViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(state.installedMessage) {
        state.installedMessage?.let {
            snackbarHost.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.marketplace_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHost) { Snackbar(it) } },
    ) { padding ->
        when {
            state.loading -> LoadingState(padding)
            state.error != null -> ErrorState(padding, state.error.orEmpty())
            else -> CatalogList(padding, state, viewModel::install)
        }
    }
}

@Composable
private fun LoadingState(padding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorState(padding: PaddingValues, message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0437\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044C \u043A\u0430\u0442\u0430\u043B\u043E\u0433\n$message",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun CatalogList(
    padding: PaddingValues,
    state: MarketplaceState,
    onInstall: (MarketplaceTemplateDto) -> Unit,
) {
    val grouped = state.templates.groupBy { it.category }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        grouped.forEach { (category, items) ->
            item(key = "header-$category") {
                CategoryHeader(category)
            }
            items(items, key = { it.id }) { tpl ->
                TemplateCard(
                    template = tpl,
                    installing = state.installing == tpl.id,
                    onInstall = { onInstall(tpl) },
                )
            }
        }
        item { Box(modifier = Modifier.size(16.dp)) }
    }
}

@Composable
private fun CategoryHeader(category: String) {
    Text(
        text = humanizeCategory(category),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

private fun humanizeCategory(c: String): String = when (c) {
    "productivity" -> "\u041F\u0440\u043E\u0434\u0443\u043A\u0442\u0438\u0432\u043D\u043E\u0441\u0442\u044C"
    "automation" -> "\u0410\u0432\u0442\u043E\u043C\u0430\u0442\u0438\u0437\u0430\u0446\u0438\u044F"
    "privacy" -> "\u041F\u0440\u0438\u0432\u0430\u0442\u043D\u043E\u0441\u0442\u044C"
    "devtools" -> "\u0414\u043B\u044F \u0440\u0430\u0437\u0440\u0430\u0431\u043E\u0442\u0447\u0438\u043A\u043E\u0432"
    "bots" -> "\u0411\u043E\u0442\u044B"
    "monitoring" -> "\u041C\u043E\u043D\u0438\u0442\u043E\u0440\u0438\u043D\u0433"
    "database" -> "\u0411\u0430\u0437\u044B \u0434\u0430\u043D\u043D\u044B\u0445"
    "content" -> "\u041A\u043E\u043D\u0442\u0435\u043D\u0442"
    else -> c.replaceFirstChar { it.uppercase() }
}

@Composable
private fun TemplateCard(
    template: MarketplaceTemplateDto,
    installing: Boolean,
    onInstall: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            IconBubble(template.icon)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = template.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = template.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (template.tags.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        template.tags.take(3).forEach { tag ->
                            AssistChip(
                                onClick = {},
                                label = { Text(tag, fontSize = 11.sp) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                ),
                            )
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            FilledTonalButton(
                onClick = onInstall,
                enabled = !installing,
            ) {
                if (installing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Install")
                }
            }
        }
    }
}

@Composable
private fun IconBubble(emoji: String) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = emoji, fontSize = 24.sp)
    }
}
