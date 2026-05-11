package by.vibefly.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Скевоморфная iOS 6 тема. Отбросил dynamic color и dark mode — этот язык
 * живёт только в светлой версии, точно как iPhone до iOS 13.
 *
 * Material 3 colorScheme остаётся, но он вторичный — всё визуальное
 * рисуется прямыми брашами и цветами из SkeuColors.
 */
@Composable
fun VibeflyTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = false,
    @Suppress("UNUSED_PARAMETER") dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val scheme = lightColorScheme(
        primary = SkeuColors.PrimaryActionBottom,
        onPrimary = SkeuColors.PaperWhite,
        background = SkeuColors.Linen,
        onBackground = SkeuColors.PrimaryText,
        surface = SkeuColors.PaperWhite,
        onSurface = SkeuColors.PrimaryText,
        surfaceVariant = SkeuColors.LinenLight,
        onSurfaceVariant = SkeuColors.SecondaryText,
        error = SkeuColors.AccentRed,
    )
    // Легаси-референс чтобы импорт darkColorScheme не ругался как unused.
    @Suppress("UNUSED_VARIABLE") val unused = darkColorScheme()

    MaterialTheme(
        colorScheme = scheme,
        typography = SkeuTypography,
        content = content,
    )
}

/**
 * Типография, подобранная под iOS 6 эстетику. SF Pro не доступен на Android,
 * так что используем системный sans (Roboto) с более плотным спейсингом.
 */
val SkeuTypography = androidx.compose.material3.Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.5.sp,
    ),
)
