package by.vibefly.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.vibefly.app.ui.components.ApprovalCard
import by.vibefly.app.ui.components.BubbleBot
import by.vibefly.app.ui.components.BubbleUser
import by.vibefly.app.ui.components.ChatInputBar
import by.vibefly.app.ui.components.ChatTimeSeparator
import by.vibefly.app.ui.components.ToolCallBubble
import by.vibefly.app.ui.components.notebookBackground
import by.vibefly.app.ui.theme.SkeuColors
import by.vibefly.app.ui.theme.SkeuGradients

/**
 * Vibe AI — экран чата с AI-ассистентом сервера.
 *
 * Полная интеграция с tool-calling и реальным AI — фаза 4. Сейчас:
 *  • UI готов целиком (пузыри, tool calls, approval, input bar)
 *  • Лента наполнена demo-сообщениями из мокапа, чтобы видеть стиль
 *  • Send добавляет сообщение в локальный список (без backend)
 */
@Composable
fun ChatScreen() {
    val messages = remember { mutableStateListOf<ChatMessage>().apply { addAll(demoMessages()) } }
    var input by remember { mutableStateOf("") }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize().background(SkeuColors.NotebookPaper)) {
        ChatNavBar(
            title = "Vibe AI",
            subtitle = "online",
            onMenu = { /* TODO: открыть боковое меню */ },
            onInfo = { /* TODO: info sheet */ },
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .notebookBackground()
                .padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            items(messages, key = { it.key }) { msg ->
                RenderMessage(msg)
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }

        ChatInputBar(
            value = input,
            onValueChange = { input = it },
            onSend = {
                val text = input.trim()
                if (text.isNotEmpty()) {
                    messages.add(ChatMessage.User(text = text, key = nextKey()))
                    input = ""
                    // Заглушка: эхо-бот ответит через 0 сообщений (фаза 4 заменит на AI).
                    messages.add(
                        ChatMessage.Bot(
                            text = "Принял. AI-ассистент включится в фазе 4 — пока я только UI.",
                            key = nextKey(),
                        )
                    )
                }
            },
        )
    }
}

/**
 * Кастомный nav bar для чата — фиолетовый аватар + двухстрочный title + кнопки ≡ и i.
 * Не использует общий IosNavBar потому что у него специфичная центральная область.
 */
@Composable
private fun ChatNavBar(
    title: String,
    subtitle: String,
    onMenu: () -> Unit,
    onInfo: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(SkeuGradients.navBar())
            .drawBehind {
                drawLine(
                    color = SkeuColors.NavBarStroke,
                    start = Offset(0f, size.height - 0.5f),
                    end = Offset(size.width, size.height - 0.5f),
                    strokeWidth = 1f,
                )
            },
    ) {
        // Центрированный двухстрочный заголовок с аватаром
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(
                        SkeuGradients.phosphor(
                            SkeuColors.PhosphorVioletTop,
                            SkeuColors.PhosphorVioletBottom,
                        )
                    )
                    .border(1.dp, Color.Black.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "✦",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    style = TextStyle(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.35f),
                            offset = Offset(0f, -1f),
                            blurRadius = 0f,
                        ),
                    ),
                )
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 10.sp,
                )
            }
        }

        // Левая кнопка ≡
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            CircleNavButton(text = "≡", onClick = onMenu, fontSize = 16.sp)
            CircleNavButton(text = "i", onClick = onInfo, fontSize = 11.sp)
        }
    }
}

@Composable
private fun CircleNavButton(
    text: String,
    onClick: () -> Unit,
    fontSize: androidx.compose.ui.unit.TextUnit,
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(SkeuGradients.glossyBlueButton())
            .border(1.dp, SkeuColors.NavBarStroke, CircleShape)
            .drawBehind {
                drawLine(
                    color = Color.White.copy(alpha = 0.35f),
                    start = Offset(4f, 2f),
                    end = Offset(size.width - 4f, 2f),
                    strokeWidth = 1f,
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = fontSize,
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

// ─── Рендер сообщений ────────────────────────────────────────────────────────

@Composable
private fun RenderMessage(msg: ChatMessage) {
    when (msg) {
        is ChatMessage.TimeMarker -> ChatTimeSeparator(text = msg.text)
        is ChatMessage.Bot -> BubbleBot(text = msg.text)
        is ChatMessage.User -> BubbleUser(text = msg.text)
        is ChatMessage.Tools -> ToolCallBubble(intro = msg.intro, calls = msg.calls)
        is ChatMessage.Approval -> ApprovalCard(
            title = msg.title,
            code = msg.code,
            description = msg.description,
            onApply = { /* TODO: вызвать tool */ },
            onPreview = { /* TODO: показать полный SQL */ },
        )
    }
}

// ─── Модель сообщения ────────────────────────────────────────────────────────

private var keyCounter: Long = 1_000L
private fun nextKey(): Long = ++keyCounter

sealed class ChatMessage {
    abstract val key: Long

    data class TimeMarker(val text: String, override val key: Long) : ChatMessage()
    data class Bot(val text: String, override val key: Long) : ChatMessage()
    data class User(val text: String, override val key: Long) : ChatMessage()
    data class Tools(val intro: String, val calls: List<String>, override val key: Long) : ChatMessage()
    data class Approval(
        val title: String,
        val code: String,
        val description: String,
        override val key: Long,
    ) : ChatMessage()
}

private fun demoMessages(): List<ChatMessage> = listOf(
    ChatMessage.TimeMarker("сегодня · 9:38", nextKey()),
    ChatMessage.Bot("Ом мане падме хунг.", nextKey()),
    ChatMessage.Bot("Я Vibe AI — ассистент твоего сервера. С чего начнём?", nextKey()),
    ChatMessage.TimeMarker("9:41", nextKey()),
    ChatMessage.User("У меня лагает azcrm последние полчаса", nextKey()),
    ChatMessage.User("Глянь, что не так", nextKey()),
    ChatMessage.Tools(
        intro = "Смотрю логи, метрики и медленные запросы…",
        calls = listOf(
            "get_logs(azcrm, 30m)",
            "query_metrics(cpu, ram)",
            "db_query(slow)",
        ),
        key = nextKey(),
    ),
    ChatMessage.Bot(
        "Таблица messages разрослась до 2.4M строк. Нет индекса по created_at — пагинация делает full scan.",
        nextKey(),
    ),
    ChatMessage.Approval(
        title = "⚒ предлагаю миграцию",
        code = "CREATE INDEX CONCURRENTLY\nidx_messages_created_at\nON messages(created_at);",
        description = "~40 секунд, без блокировок. Ускорит листинг в 50–100×.",
        key = nextKey(),
    ),
)
