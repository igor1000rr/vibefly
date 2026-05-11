package by.vibefly.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Палитра скевоморфного iOS 6 для VibeFly.
 *
 * Собраны все цвета и градиенты, используемые в интерфейсе: линеновый фон,
 * glossy nav bar, выпуклые кнопки, iOS toggle, фосфорные иконки, жёлтая approval-карточка.
 */
object SkeuColors {

    // Линеновый фон settings/grouped вью.
    val Linen = Color(0xFFD8D4C8)
    val LinenDark = Color(0xFFB4B1A8)
    val LinenLight = Color(0xFFE2DED2)

    // Белая бумажная поверхность внутри grouped row.
    val PaperWhite = Color(0xFFFFFFFF)
    val PaperBorder = Color(0xFFB4B1A8)
    val RowDivider = Color(0xFFD5D3C9)

    // Текст.
    val PrimaryText = Color(0xFF1A1A1A)
    val SecondaryText = Color(0xFF6D6D72)
    val MutedText = Color(0xFF888888)
    val SectionLabel = Color(0xFF6D6D72)
    val LinkBlue = Color(0xFF1E6CD6)
    val AccentGreen = Color(0xFF2CA84C)
    val AccentOrange = Color(0xFFC87D11)
    val AccentRed = Color(0xFFC83434)

    // Highlight, который ложится сверху карточек/секций как inset white.
    val InsetHighlight = Color(0x99FFFFFF)
    val InsetShadow = Color(0x33000000)

    // Nav bar.
    val NavBarTop = Color(0xFF6C95CF)
    val NavBarMid = Color(0xFF3A6CB5)
    val NavBarMidDark = Color(0xFF2C5AA3)
    val NavBarBottom = Color(0xFF2050A0)
    val NavBarStroke = Color(0xFF1A3F80)
    val NavBarShadow = Color(0xFF14336B)

    // Glossy blue button (внутри nav bar).
    val GlossyBlueTop = Color(0xFF6E98D0)
    val GlossyBlueBottom = Color(0xFF2C5AA3)

    // Primary action button (Redeploy, Send).
    val PrimaryActionTop = Color(0xFF2095F2)
    val PrimaryActionBottom = Color(0xFF006FDC)
    val PrimaryActionStroke = Color(0xFF0058B0)

    // Secondary glossy gray button.
    val GlossyGrayTop = Color(0xFFFAFAFA)
    val GlossyGrayBottom = Color(0xFFD0D0D0)
    val GlossyGrayStroke = Color(0xFF888888)

    // iOS toggle (ON).
    val ToggleOnTop = Color(0xFF4CD964)
    val ToggleOnBottom = Color(0xFF2EAA48)
    val ToggleOnStroke = Color(0xFF1F7E35)

    // iOS toggle (OFF).
    val ToggleOffTop = Color(0xFFD9D6C9)
    val ToggleOffBottom = Color(0xFFB6B3A8)
    val ToggleOffStroke = Color(0xFFA09D93)

    // Фосфорные иконки для Settings/Apps rows.
    val PhosphorGreenTop = Color(0xFF7CDF95)
    val PhosphorGreenBottom = Color(0xFF2EAA48)

    val PhosphorBlueTop = Color(0xFF6FA3E1)
    val PhosphorBlueBottom = Color(0xFF2C5AA3)

    val PhosphorOrangeTop = Color(0xFFF7A256)
    val PhosphorOrangeBottom = Color(0xFFD36B1C)

    val PhosphorRedTop = Color(0xFFF08A8A)
    val PhosphorRedBottom = Color(0xFFC83434)

    val PhosphorVioletTop = Color(0xFFB39DF0)
    val PhosphorVioletBottom = Color(0xFF6A52CC)

    val PhosphorGrayTop = Color(0xFFB6B3A8)
    val PhosphorGrayBottom = Color(0xFF7A7770)

    val PhosphorBlackTop = Color(0xFF4A4A4A)
    val PhosphorBlackBottom = Color(0xFF1A1A1A)

    val PhosphorAmberTop = Color(0xFFF5B452)
    val PhosphorAmberBottom = Color(0xFFC87D11)

    // Жёлтая approval-карточка в чате.
    val ApprovalCardTop = Color(0xFFFFF5CC)
    val ApprovalCardBottom = Color(0xFFFFE89A)
    val ApprovalCardStroke = Color(0xFFD4AB2F)
    val ApprovalCardText = Color(0xFF7A5A0D)
    val ApprovalCardCodeBg = Color(0xFFFFFBE6)

    // Чат пузырьки.
    val BubbleBotTop = Color(0xFFF2F2F2)
    val BubbleBotBottom = Color(0xFFE5E5EA)
    val BubbleBotStroke = Color(0xFFC8C8C8)

    val BubbleUserTop = Color(0xFF2095F2)
    val BubbleUserBottom = Color(0xFF006FDC)
    val BubbleUserStroke = Color(0xFF0058B0)

    // Линованная "тетрадь" в чате.
    val NotebookLine = Color(0xFFD3D8DE)
    val NotebookPaper = Color(0xFFFFFFFF)

    // Tab bar.
    val TabBarTop = Color(0xFFD3D3D3)
    val TabBarBottom = Color(0xFFA5A5A5)
    val TabBarStroke = Color(0xFF6C6C6C)
    val TabBarSelectedTint = Color(0xFF1E6CD6)
    val TabBarUnselectedTint = Color(0xFF5E5E5E)
}

/**
 * Градиенты. Собраны функции, чтобы не пересоздавать в каждом композите.
 */
object SkeuGradients {
    fun navBar() = Brush.verticalGradient(
        0f to SkeuColors.NavBarTop,
        0.5f to SkeuColors.NavBarMid,
        0.51f to SkeuColors.NavBarMidDark,
        1f to SkeuColors.NavBarBottom,
    )

    fun glossyBlueButton() = Brush.verticalGradient(
        listOf(SkeuColors.GlossyBlueTop, SkeuColors.GlossyBlueBottom)
    )

    fun primaryActionButton() = Brush.verticalGradient(
        listOf(SkeuColors.PrimaryActionTop, SkeuColors.PrimaryActionBottom)
    )

    fun glossyGrayButton() = Brush.verticalGradient(
        listOf(SkeuColors.GlossyGrayTop, SkeuColors.GlossyGrayBottom)
    )

    fun toggleOn() = Brush.verticalGradient(
        listOf(SkeuColors.ToggleOnTop, SkeuColors.ToggleOnBottom)
    )

    fun toggleOff() = Brush.verticalGradient(
        listOf(SkeuColors.ToggleOffTop, SkeuColors.ToggleOffBottom)
    )

    fun tabBar() = Brush.verticalGradient(
        listOf(SkeuColors.TabBarTop, SkeuColors.TabBarBottom)
    )

    fun phosphor(top: Color, bottom: Color) = Brush.verticalGradient(
        listOf(top, bottom)
    )

    fun approvalCard() = Brush.verticalGradient(
        listOf(SkeuColors.ApprovalCardTop, SkeuColors.ApprovalCardBottom)
    )

    fun bubbleBot() = Brush.verticalGradient(
        listOf(SkeuColors.BubbleBotTop, SkeuColors.BubbleBotBottom)
    )

    fun bubbleUser() = Brush.verticalGradient(
        listOf(SkeuColors.BubbleUserTop, SkeuColors.BubbleUserBottom)
    )
}

/**
 * Набор фосфорных цветов для иконок (top/bottom градиента).
 */
enum class PhosphorTint(val top: Color, val bottom: Color) {
    Green(SkeuColors.PhosphorGreenTop, SkeuColors.PhosphorGreenBottom),
    Blue(SkeuColors.PhosphorBlueTop, SkeuColors.PhosphorBlueBottom),
    Orange(SkeuColors.PhosphorOrangeTop, SkeuColors.PhosphorOrangeBottom),
    Red(SkeuColors.PhosphorRedTop, SkeuColors.PhosphorRedBottom),
    Violet(SkeuColors.PhosphorVioletTop, SkeuColors.PhosphorVioletBottom),
    Gray(SkeuColors.PhosphorGrayTop, SkeuColors.PhosphorGrayBottom),
    Black(SkeuColors.PhosphorBlackTop, SkeuColors.PhosphorBlackBottom),
    Amber(SkeuColors.PhosphorAmberTop, SkeuColors.PhosphorAmberBottom),
}
