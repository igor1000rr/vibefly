package by.vibefly.app.ui

/**
 * Маршруты навигации. Собраны в одном месте, чтобы не таскать строковые
 * литералы по всему UI.
 */
object Routes {
    const val Dashboard: String = "dashboard"
    const val Chat: String = "chat"
    const val Marketplace: String = "marketplace"
    const val Settings: String = "settings"

    const val AppDetailPattern: String = "app/{id}"
    fun appDetail(id: String): String = "app/$id"
}
