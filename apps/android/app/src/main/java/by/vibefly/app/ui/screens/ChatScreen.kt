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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import by.vibefly.app.agent.ChatMessage
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
 *  • ChatViewModel со StubAiClient выдаёт echo-ответы посимвольным стримом
 *  • При rotation история чата сохраняется (живёт в ViewModel)
 *  • Approval.apply пока заглушка — реальный tool call появится в фазе 4
 */
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }

    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size, state.pendingText) {
        val total = state.messages.size + (if (state.pendingText.isNotEmpty()) 1 else 0)
        if (total > 0) listState.animateScrollToItem(total - 1)
    }

    Column(modifier = Modifier.fillMaxSize().background(SkeuColors.NotebookPaper)) {
        ChatNavBar(
            title = "Vibe AI",
            subtitle = if (state.sending) "typing…" else "online",
            onMenu = { /* TODO: open side menu */ },
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
            items(state.messages, key = { it.key }) { msg ->
                RenderMessage(msg, onApply = viewModel::applyApproval)
            }
            // Pending pузырь от AI — растёт по буквам, без key (одиночный)
            if (state.pendingText.isNotEmpty()) {
                item(key = "pending") {
                    BubbleBot(text = state.pendingText.trim() + "▍")
                }
            }
            item(key = "tail-spacer") { Spacer(modifier = Modifier.height(8.dp)) }
        }

        ChatInputBar(
            value = input,
            onValueChange = { input = it },
            onSend = {
                val text = input.trim()
                if (text.isNotEmpty()) {
                    viewModel.send(text)
                    input = ""
                }
            },
        )
    }
}

/**
 * Nav bar чата. Три слота в Row: [≡]  [✦ Vibe AI / online]  [i].
 */
@Composable
private fun ChatNavBar(
    title: String,
    subtitle: String,
    onMenu: () -> Unit,
    onInfo: () -> Unit,
) {
    Row(
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
            }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleNavButton(text = "≡", onClick = onMenu, fontSize = 16.sp)

        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
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

        CircleNavButton(text = "i", onClick = onInfo, fontSize = 11.sp)
    }
}

@Composable
private fun CircleNavButton(
    text: String,
    onClick: () -> Unit,
    fontSize: TextUnit,
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

// ─── Рендер сообщений ───────────────────────────────────────────────────────

@Composable
private fun RenderMessage(
    msg: ChatMessage,
    onApply: (ChatMessage.Approval) -> Unit,
) {
    when (msg) {
        is ChatMessage.TimeMarker -> ChatTimeSeparator(text = msg.text)
        is ChatMessage.Bot -> BubbleBot(text = msg.text)
        is ChatMessage.User -> BubbleUser(text = msg.text)
        is ChatMessage.Tools -> ToolCallBubble(intro = msg.intro, calls = msg.calls)
        is ChatMessage.Approval -> ApprovalCard(
            title = msg.title,
            code = msg.code,
            description = msg.description,
            onApply = { onApply(msg) },
            onPreview = { /* TODO: show full SQL/diff */ },
        )
    }
}
