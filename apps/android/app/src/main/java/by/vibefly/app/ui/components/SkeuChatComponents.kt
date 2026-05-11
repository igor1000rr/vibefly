package by.vibefly.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.vibefly.app.ui.theme.SkeuColors
import by.vibefly.app.ui.theme.SkeuGradients

/**
 * Компоненты для ChatScreen в стиле iOS 6 iMessage:
 *  • NotebookBackground — линованный фон "тетрадь"
 *  • BubbleBot / BubbleUser — серые/синие пузыри
 *  • ToolCallBubble — пузырь со списком "✓ tool(args)"
 *  • ApprovalCard — жёлтая карточка для опасных AI-действий (миграции и т.п.)
 *  • ChatInputBar — кастомный input bar с круглой "+" и Send
 *  • ChatTimeSeparator — центрированный маркер времени "сегодня · 9:38"
 */

/**
 * Линованный фон "тетрадь" — белая бумага с горизонтальными линиями каждые 20dp.
 */
fun Modifier.notebookBackground(): Modifier = this
    .background(SkeuColors.NotebookPaper)
    .drawBehind {
        val spacing = 20.dp.toPx()
        var y = spacing
        while (y < size.height) {
            drawLine(
                color = SkeuColors.NotebookLine,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f,
            )
            y += spacing
        }
    }

/**
 * Центрированный маркер времени в чате.
 */
@Composable
fun ChatTimeSeparator(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = text,
            color = SkeuColors.MutedText,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * Серый пузырь от бота, прижат к левому краю.
 */
@Composable
fun BubbleBot(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 260.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(SkeuGradients.bubbleBot())
                .border(1.dp, SkeuColors.BubbleBotStroke, RoundedCornerShape(14.dp))
                .padding(horizontal = 11.dp, vertical = 7.dp),
        ) {
            Text(
                text = text,
                color = SkeuColors.PrimaryText,
                fontSize = 12.sp,
            )
        }
    }
}

/**
 * Синий пузырь пользователя, прижат к правому краю.
 */
@Composable
fun BubbleUser(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 260.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(SkeuGradients.bubbleUser())
                .border(1.dp, SkeuColors.BubbleUserStroke, RoundedCornerShape(14.dp))
                .drawBehind {
                    drawLine(
                        color = Color.White.copy(alpha = 0.35f),
                        start = Offset(8f, 1f),
                        end = Offset(size.width - 8f, 1f),
                        strokeWidth = 1f,
                    )
                }
                .padding(horizontal = 11.dp, vertical = 7.dp),
        ) {
            Text(
                text = text,
                color = Color.White,
                fontSize = 12.sp,
            )
        }
    }
}

/**
 * Пузырь со списком вызванных AI-инструментов. Слева, как ботовский.
 *
 * intro — обычная фраза наверху, calls — список "tool(arg, arg)" моноширинным шрифтом.
 */
@Composable
fun ToolCallBubble(
    intro: String,
    calls: List<String>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(SkeuGradients.bubbleBot())
                .border(1.dp, SkeuColors.BubbleBotStroke, RoundedCornerShape(14.dp))
                .padding(horizontal = 11.dp, vertical = 7.dp),
        ) {
            Column {
                Text(
                    text = intro,
                    color = SkeuColors.PrimaryText,
                    fontSize = 12.sp,
                )
                Spacer(modifier = Modifier.height(4.dp))
                calls.forEach { call ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "✓",
                            color = SkeuColors.AccentGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = call,
                            color = SkeuColors.PrimaryText,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Жёлтая approval-карточка — AI предлагает потенциально опасное действие
 * (миграция БД, удаление файлов и т.п.), пользователь явно подтверждает.
 *
 * Структура: title (с emoji) → код в моноширинной плашке → описание → две кнопки.
 */
@Composable
fun ApprovalCard(
    title: String,
    code: String,
    description: String,
    onApply: () -> Unit,
    onPreview: () -> Unit,
    modifier: Modifier = Modifier,
    applyLabel: String = "apply",
    previewLabel: String = "preview SQL",
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SkeuGradients.approvalCard())
                .border(1.dp, SkeuColors.ApprovalCardStroke, RoundedCornerShape(8.dp))
                .drawBehind {
                    drawLine(
                        color = Color.White.copy(alpha = 0.6f),
                        start = Offset(2f, 1f),
                        end = Offset(size.width - 2f, 1f),
                        strokeWidth = 1f,
                    )
                }
                .padding(horizontal = 10.dp, vertical = 9.dp),
        ) {
            Column {
                Text(
                    text = title,
                    color = SkeuColors.ApprovalCardText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(5.dp))
                // SQL/код в плашке более тёмного жёлтого
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(SkeuColors.ApprovalCardCodeBg)
                        .border(1.dp, SkeuColors.ApprovalCardStroke, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = code,
                        color = SkeuColors.PrimaryText,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = description,
                    color = SkeuColors.ApprovalCardText,
                    fontSize = 11.sp,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ApprovalApplyButton(
                        text = applyLabel,
                        onClick = onApply,
                        modifier = Modifier.weight(1f),
                    )
                    IosButtonGlossy(
                        text = previewLabel,
                        onClick = onPreview,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * Зелёная "apply" кнопка специально под approval-карточку.
 * Не вынес в общие, потому что используется только тут.
 */
@Composable
private fun ApprovalApplyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(SkeuGradients.toggleOn())
            .border(1.dp, SkeuColors.ToggleOnStroke, RoundedCornerShape(5.dp))
            .drawBehind {
                drawLine(
                    color = Color.White.copy(alpha = 0.4f),
                    start = Offset(1f, 1f),
                    end = Offset(size.width - 1f, 1f),
                    strokeWidth = 1f,
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            style = TextStyle(
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.3f),
                    offset = Offset(0f, -1f),
                    blurRadius = 0f,
                ),
            ),
        )
    }
}

/**
 * Кастомный input bar внизу чата. Круглая "+" слева, поле ввода, синяя кнопка Send.
 */
@Composable
fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit = {},
    modifier: Modifier = Modifier,
    placeholder: String = "Сообщение…",
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(Color(0xFFF0EEE8), Color(0xFFC8C4B8)),
                )
            )
            .drawBehind {
                drawLine(
                    color = SkeuColors.TabBarStroke,
                    start = Offset(0f, 0.5f),
                    end = Offset(size.width, 0.5f),
                    strokeWidth = 1f,
                )
            }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // "+" кнопка
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(SkeuGradients.glossyGrayButton())
                .border(1.dp, SkeuColors.GlossyGrayStroke, CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onAttach,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "+", color = SkeuColors.PrimaryText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.width(6.dp))

        // Поле ввода
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(SkeuColors.PaperWhite)
                .border(1.dp, Color(0xFF999999), RoundedCornerShape(14.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = SkeuColors.MutedText,
                    fontSize = 12.sp,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = false,
                maxLines = 4,
                textStyle = TextStyle(
                    color = SkeuColors.PrimaryText,
                    fontSize = 12.sp,
                ),
                cursorBrush = SolidColor(SkeuColors.NavBarMidDark),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(modifier = Modifier.width(6.dp))

        // Send
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(SkeuGradients.glossyBlueButton())
                .border(1.dp, SkeuColors.NavBarStroke, RoundedCornerShape(12.dp))
                .drawBehind {
                    drawLine(
                        color = Color.White.copy(alpha = 0.35f),
                        start = Offset(0f, 1f),
                        end = Offset(size.width, 1f),
                        strokeWidth = 1f,
                    )
                }
                .clickable(
                    enabled = value.isNotBlank(),
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onSend,
                )
                .padding(horizontal = 12.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Send",
                color = Color.White,
                fontSize = 12.sp,
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
}
