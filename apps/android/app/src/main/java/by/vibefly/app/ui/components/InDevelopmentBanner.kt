package by.vibefly.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.vibefly.app.ui.theme.SkeuColors
import by.vibefly.app.ui.theme.SkeuGradients

/**
 * Тонкая скевоморфная плашка "в разработке". Вешается под IosNavBar для фич,
 * которые визуально готовы (UI можно потыкать), но функционально ещё не
 * подключены к продакшну.
 *
 * Использует жёлтую палитру approval-карточки, чтобы перекликаться с другими
 * предупреждениями в приложении. Не блокирует контент — пользователь и
 * партнёр всё ещё могут пройтись по экрану и понять что задумано.
 */
@Composable
fun InDevelopmentBanner(
    text: String = "В разработке",
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(SkeuGradients.approvalCard())
            .drawBehind {
                // Светлая полоса сверху + тёмная снизу — фирменный inset highlight.
                drawLine(
                    color = Color.White.copy(alpha = 0.6f),
                    start = Offset(0f, 1f),
                    end = Offset(size.width, 1f),
                    strokeWidth = 1f,
                )
                drawLine(
                    color = SkeuColors.ApprovalCardStroke,
                    start = Offset(0f, size.height - 0.5f),
                    end = Offset(size.width, size.height - 0.5f),
                    strokeWidth = 1f,
                )
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "⚒",
                color = SkeuColors.ApprovalCardText,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                color = SkeuColors.ApprovalCardText,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
