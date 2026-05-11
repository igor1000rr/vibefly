package by.vibefly.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Автостарт runtime после перезагрузки телефона. Без этого пользователевские
 * приложения не поднимутся до ручного открытия VibeFly.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                VibeflyService.start(context)
            }
        }
    }
}
