package by.vibefly.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import by.vibefly.app.agent.MarketplaceEnvFieldDto
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
 * Тап Install:
 *   • Если у шаблона нет env_schema — сразу инстолируется
 *   • Если есть — открывается InstallDialog с полями для каждого env-параметра
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
                onInstall = viewModel::beginInstall,
            )
        }
    }

    SnackbarHost(snackbar) { Snackbar(it) }

    // Install dialog для шаблонов с env_schema.
    state.pendingInstall?.let { template ->
        InstallDialog(
            template = template,
            onCancel = viewModel::cancelInstall,
            onConfirm = { env -> viewModel.confirmInstall(template, env) },
        )
    }
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

/**
 * Диалог установки. Рисует каждое поле из envSchema как OutlinedTextField.
 *
 * Required-поля помечены звёздочкой, secret — со скрытием символов.
 * Install активен только когда все required-поля заполнены.
 */
@Composable
private fun InstallDialog(
    template: MarketplaceTemplateDto,
    onCancel: () -> Unit,
    onConfirm: (Map<String, String>) -> Unit,
) {
    // Состояние полей — каждое инициализируется default-значением.
    val values = remember(template.id) {
        mutableStateMapOf<String, String>().apply {
            template.envSchema.forEach { f ->
                put(f.key, f.default.orEmpty())
            }
        }
    }

    val missingRequired by remember(template.id) {
        derivedStateOf(values, template.envSchema)
    }

    AlertDialog(
        onDismissRequest = onCancel,
        confirmButton = {
            TextButton(
                onClick = { onConfirm(values.toMap()) },
                enabled = missingRequired.isEmpty(),
            ) {
                Text(
                    text = "Install",
                    color = if (missingRequired.isEmpty()) SkeuColors.LinkBlue
                            else SkeuColors.MutedText,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel") }
        },
        title = {
            Column {
                Text(
                    text = "Установка ${template.name}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = template.description,
                    color = SkeuColors.SecondaryText,
                    fontSize = 12.sp,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                template.envSchema.forEach { field ->
                    EnvFieldEditor(
                        field = field,
                        value = values[field.key].orEmpty(),
                        onValueChange = { values[field.key] = it },
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
                if (missingRequired.isNotEmpty()) {
                    Text(
                        text = "Заполни обязательные поля: " + missingRequired.joinToString(", "),
                        color = SkeuColors.AccentRed,
                        fontSize = 11.sp,
                    )
                }
            }
        },
    )
}

@Composable
private fun EnvFieldEditor(
    field: MarketplaceEnvFieldDto,
    value: String,
    onValueChange: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = field.label,
                color = SkeuColors.PrimaryText,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (field.required) {
                Text(
                    text = " *",
                    color = SkeuColors.AccentRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (!field.hint.isNullOrEmpty()) {
            Text(
                text = field.hint,
                color = SkeuColors.SecondaryText,
                fontSize = 10.sp,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = field.placeholder?.let { { Text(it, fontSize = 12.sp) } },
            singleLine = true,
            visualTransformation = if (field.secret) PasswordVisualTransformation()
                                   else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (field.secret) KeyboardType.Password else KeyboardType.Text,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Возвращает список ключей required-полей, которые пустые. Если список пуст —
 * install кнопка активна.
 */
@Composable
private fun derivedStateOf(
    values: Map<String, String>,
    schema: List<MarketplaceEnvFieldDto>,
): androidx.compose.runtime.State<List<String>> = androidx.compose.runtime.remember(values, schema) {
    androidx.compose.runtime.derivedStateOf {
        schema.filter { it.required && values[it.key].isNullOrBlank() }.map { it.label }
    }
}
