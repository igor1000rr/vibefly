package by.vibefly.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import by.vibefly.app.runtime.RuntimeManager
import by.vibefly.app.ui.theme.SkeuColors

/**
 * Карточка состояния embedded Go-агента. Рисуется только когда state != Idle,
 * чтобы не отсвечивать пустым блоком когда runtime вообще не используется
 * (например пользователь явно переключил Agent URL на внешний адрес).
 *
 * Что показывает:
 *  • цветная LED-точка (зелёная/амбер/красная/серая) + текст состояния
 *  • PID процесса справа если есть
 *  • error message второй строкой если runtime упал
 */
@Composable
fun RuntimeStatusCard(modifier: Modifier = Modifier) {
    val status by RuntimeManager.status.collectAsStateWithLifecycle()
    if (status.state == RuntimeManager.State.Idle) return

    val (label, ledColor) = when (status.state) {
        RuntimeManager.State.Starting -> "Embedded server starting…" to SkeuColors.AccentOrange
        RuntimeManager.State.Running -> "Embedded server running" to SkeuColors.AccentGreen
        RuntimeManager.State.Stopped -> "Embedded server stopped" to SkeuColors.MutedText
        RuntimeManager.State.Failed -> "Embedded server failed" to SkeuColors.AccentRed
        RuntimeManager.State.Idle -> return
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SkeuColors.PaperWhite)
            .border(1.dp, SkeuColors.PaperBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LedDot(color = ledColor)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    color = SkeuColors.PrimaryText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                status.pid?.let { pid ->
                    Text(
                        text = "pid $pid",
                        color = SkeuColors.MutedText,
                        fontSize = 11.sp,
                    )
                }
            }
            status.error?.let { err ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = err,
                    color = SkeuColors.AccentRed,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

/** Точка-индикатор как на iOS 6 — однородный круг 8x8. */
@Composable
private fun LedDot(color: Color) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color)
            .border(0.5.dp, Color.Black.copy(alpha = 0.15f), CircleShape),
    )
}
