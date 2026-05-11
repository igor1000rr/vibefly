package by.vibefly.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.vibefly.app.ui.theme.PhosphorTint
import by.vibefly.app.ui.theme.SkeuColors
import by.vibefly.app.ui.theme.SkeuGradients

/**
 * Скевоморфные iOS 6 компоненты VibeFly. Один файл по двум причинам:
 * импорты проще, и эти примитивы тесно связаны (NavBar часто содержит IosNavButton,
 * GroupedRow обычно держит PhosphorIcon, и так далее).
 *
 * Все размеры в dp подобраны под Note 14S (≈400dp ширина portrait).
 */

// ─── NavBar ───────────────────────────────────────────────────────────────────

private val NavBarHeight = 44.dp

/**
 * Глянцевый синий nav bar в стиле iOS 6. Высота 44dp, четырёхступенчатый градиент,
 * тонкий тёмный stroke снизу, тень под текстом заголовка.
 *
 * leading и trailing — слоты для IosNavButton, иконок или Spacer'ов.
 */
@Composable
fun IosNavBar(
    title: String,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit = {},
    trailing: @Composable () -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(NavBarHeight)
            .background(SkeuGradients.navBar())
            .drawBehind {
                // Тонкая тёмная линия снизу — стык с контентом.
                val stroke = 1f
                drawLine(
                    color = SkeuColors.NavBarStroke,
                    start = Offset(0f, size.height - stroke / 2),
                    end = Offset(size.width, size.height - stroke / 2),
                    strokeWidth = stroke,
                )
            },
    ) {
        // Заголовок строго по центру.
        Text(
            text = title,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Center),
            style = TextStyle(
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.35f),
                    offset = Offset(0f, -1f),
                    blurRadius = 0f,
                ),
            ),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(contentAlignment = Alignment.CenterStart) { leading() }
            Box(contentAlignment = Alignment.CenterEnd) { trailing() }
        }
    }
}

/**
 * Маленькая глянцевая кнопка для nav bar. "‹ Apps", "+ Deploy", "Edit".
 */
@Composable
fun IosNavButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(SkeuGradients.glossyBlueButton())
            .border(1.dp, SkeuColors.NavBarStroke, RoundedCornerShape(4.dp))
            .drawBehind {
                drawLine(
                    color = Color.White.copy(alpha = 0.35f),
                    start = Offset(0f, 1f),
                    end = Offset(size.width, 1f),
                    strokeWidth = 1f,
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            style = TextStyle(
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.35f),
                    offset = Offset(0f, -1f),
                    blurRadius = 0f,
                ),
            ),
        )
    }
}

// ─── Section header (uppercase grey label над grouped table) ────────────────

@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        color = SkeuColors.SectionLabel,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
        style = TextStyle(
            letterSpacing = 0.5.sp,
            shadow = Shadow(
                color = Color.White.copy(alpha = 0.65f),
                offset = Offset(0f, 1f),
                blurRadius = 0f,
            ),
        ),
    )
}

// ─── Grouped table & row ─────────────────────────────────────────────────────

/**
 * Контейнер grouped table — белый блок с тонкой рамкой и закруглёнными углами.
 * Внутри располагаются GroupedRow с автоматическими разделителями.
 */
@Composable
fun GroupedTable(
    modifier: Modifier = Modifier,
    content: @Composable GroupedTableScope.() -> Unit,
) {
    val scope = remember { GroupedTableScopeImpl() }
    // Сначала собираем элементы.
    scope.content()
    val rows = scope.rows

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SkeuColors.PaperWhite)
            .border(1.dp, SkeuColors.PaperBorder, RoundedCornerShape(8.dp))
            .drawBehind {
                // Тонкий белый highlight сверху — псевдо-объём.
                drawLine(
                    color = Color.White.copy(alpha = 0.7f),
                    start = Offset(0f, 1f),
                    end = Offset(size.width, 1f),
                    strokeWidth = 1f,
                )
            },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            rows.forEachIndexed { index, row ->
                row.invoke()
                if (index < rows.lastIndex) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = SkeuColors.RowDivider,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }
    }
}

interface GroupedTableScope {
    fun row(content: @Composable () -> Unit)
}

private class GroupedTableScopeImpl : GroupedTableScope {
    val rows = mutableListOf<@Composable () -> Unit>()
    override fun row(content: @Composable () -> Unit) {
        rows.add(content)
    }
}

/**
 * Строка grouped table. Слева — leading (обычно PhosphorIcon), по центру — текст
 * (label + опциональный hint), справа — trailing (значение, toggle, кнопка).
 *
 * chevron=true рисует серый ›, если строка кликабельная навигация.
 */
@Composable
fun GroupedRow(
    label: String,
    modifier: Modifier = Modifier,
    leading: @Composable (() -> Unit)? = null,
    valueText: String? = null,
    valueColor: Color = SkeuColors.MutedText,
    chevron: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val rowMod = modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = 40.dp)
        .let { if (onClick != null) it.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ) else it }
        .padding(horizontal = 12.dp, vertical = 8.dp)

    Row(
        modifier = rowMod,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(modifier = Modifier.width(10.dp))
        }
        Text(
            text = label,
            color = SkeuColors.PrimaryText,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        if (valueText != null) {
            Text(
                text = valueText,
                color = valueColor,
                fontSize = 12.sp,
                fontWeight = if (valueColor == SkeuColors.LinkBlue) FontWeight.Medium else FontWeight.Normal,
            )
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailing()
        }
        if (chevron) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "›",
                color = SkeuColors.MutedText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Light,
            )
        }
    }
}

// ─── iOS toggle ──────────────────────────────────────────────────────────────

/**
 * Аутентичный iOS-toggle: трек 46x26dp с градиентом, белый thumb 24x24dp
 * сдвигается по горизонтали с анимацией.
 */
@Composable
fun IosToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trackBrush = if (checked) SkeuGradients.toggleOn() else SkeuGradients.toggleOff()
    val strokeColor by animateColorAsState(
        targetValue = if (checked) SkeuColors.ToggleOnStroke else SkeuColors.ToggleOffStroke,
        label = "skeu-toggle-stroke",
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 21.dp else 1.dp,
        label = "skeu-toggle-thumb",
    )

    Box(
        modifier = modifier
            .size(width = 46.dp, height = 26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(trackBrush)
            .border(1.dp, strokeColor, RoundedCornerShape(13.dp))
            .drawBehind {
                // Внутренняя тень сверху.
                drawLine(
                    color = Color.Black.copy(alpha = 0.15f),
                    start = Offset(0f, 1f),
                    end = Offset(size.width, 1f),
                    strokeWidth = 1f,
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onCheckedChange(!checked) },
            ),
    ) {
        // Thumb — белый круг с тонкой обводкой и подложкой.
        Box(
            modifier = Modifier
                .padding(start = thumbOffset, top = 1.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(SkeuColors.PaperWhite)
                .border(1.dp, Color.Black.copy(alpha = 0.2f), CircleShape)
                .drawBehind {
                    drawLine(
                        color = Color.White.copy(alpha = 0.9f),
                        start = Offset(size.width * 0.2f, 2f),
                        end = Offset(size.width * 0.8f, 2f),
                        strokeWidth = 1f,
                    )
                },
        )
    }
}

// ─── Phosphor icon (24dp rounded square с градиентом) ───────────────────────

/**
 * Маленькая "фосфорная" иконка в стиле iOS 6 — закруглённый квадрат с градиентом,
 * тонким white highlight сверху, и контентом внутри (обычно белый глиф).
 */
@Composable
fun PhosphorIcon(
    tint: PhosphorTint,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    cornerRadius: Dp = 5.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(SkeuGradients.phosphor(tint.top, tint.bottom))
            .border(0.5.dp, Color.Black.copy(alpha = 0.25f), RoundedCornerShape(cornerRadius))
            .drawBehind {
                // Inset highlight сверху для объёма.
                drawLine(
                    color = Color.White.copy(alpha = 0.5f),
                    start = Offset(2f, 1.5f),
                    end = Offset(this.size.width - 2f, 1.5f),
                    strokeWidth = 1f,
                )
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/**
 * Удобный shortcut: PhosphorIcon с одним unicode-глифом по центру.
 */
@Composable
fun PhosphorIcon(
    tint: PhosphorTint,
    glyph: String,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    glyphSize: Dp = 14.dp,
) {
    PhosphorIcon(tint = tint, modifier = modifier, size = size) {
        Text(
            text = glyph,
            color = Color.White,
            fontSize = with(androidx.compose.ui.platform.LocalDensity.current) { glyphSize.toSp() },
            fontWeight = FontWeight.Medium,
        )
    }
}

// ─── iOS-кнопки (primary & glossy gray) ─────────────────────────────────────

@Composable
fun IosButtonPrimary(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
) {
    val alpha = if (enabled) 1f else 0.55f
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(SkeuGradients.primaryActionButton())
            .border(1.dp, SkeuColors.PrimaryActionStroke.copy(alpha = alpha), RoundedCornerShape(6.dp))
            .drawBehind {
                drawLine(
                    color = Color.White.copy(alpha = 0.35f),
                    start = Offset(1f, 1f),
                    end = Offset(size.width - 1f, 1f),
                    strokeWidth = 1f,
                )
            }
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White.copy(alpha = alpha),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            style = TextStyle(
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.35f),
                    offset = Offset(0f, -1f),
                    blurRadius = 0f,
                ),
            ),
        )
    }
}

@Composable
fun IosButtonGlossy(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
) {
    val alpha = if (enabled) 1f else 0.55f
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(SkeuGradients.glossyGrayButton())
            .border(1.dp, SkeuColors.GlossyGrayStroke.copy(alpha = alpha), RoundedCornerShape(6.dp))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = SkeuColors.PrimaryText.copy(alpha = alpha),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ─── Segmented control (Overview / Logs / Env / Deploys) ────────────────────

@Composable
fun SegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(5.dp))
            .border(1.dp, SkeuColors.GlossyBlueTop, RoundedCornerShape(5.dp)),
    ) {
        items.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            val itemMod = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(
                    if (isSelected) SolidColor(SkeuColors.NavBarMidDark)
                    else SolidColor(SkeuColors.PaperWhite)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onSelect(index) },
                )
            Box(modifier = itemMod, contentAlignment = Alignment.Center) {
                Text(
                    text = label,
                    color = if (isSelected) Color.White else SkeuColors.NavBarMidDark,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    style = if (isSelected) TextStyle(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.3f),
                            offset = Offset(0f, -1f),
                            blurRadius = 0f,
                        ),
                    ) else TextStyle.Default,
                )
            }
            if (index < items.lastIndex) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeightBox()
                        .background(SkeuColors.GlossyBlueTop)
                )
            }
        }
    }
}

private fun Modifier.fillMaxHeightBox(): Modifier = this.then(
    Modifier.height(28.dp)
)

// ─── Tab bar (нижний таб-бар на 4 вкладки) ──────────────────────────────────

data class TabBarItem(
    val key: String,
    val label: String,
    val glyph: String, // unicode/emoji или одиночный символ
)

@Composable
fun TabBar(
    items: List<TabBarItem>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(SkeuGradients.tabBar())
            .drawBehind {
                drawLine(
                    color = SkeuColors.TabBarStroke,
                    start = Offset(0f, 0.5f),
                    end = Offset(size.width, 0.5f),
                    strokeWidth = 1f,
                )
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            val isSelected = item.key == selectedKey
            val tint = if (isSelected) SkeuColors.TabBarSelectedTint else SkeuColors.TabBarUnselectedTint
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(item.key) },
                    )
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = item.glyph,
                    color = tint,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.label,
                    color = tint,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

// ─── Linen background modifier ──────────────────────────────────────────────

/**
 * Линеновый фон. На первом шаге — просто плотный цвет SkeuColors.Linen.
 * Текстура будет добавлена в полировочном проходе через repeating pattern.
 */
fun Modifier.linenBackground(): Modifier = this.background(SkeuColors.Linen)
