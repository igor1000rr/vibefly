package by.vibefly.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import by.vibefly.app.service.RuntimeChannels

/**
 * Глобальный application class. Пока только регистрирует каналы уведомлений.
 * В будущем сюда переедет DI-граф (Koin/Hilt) и инициализация RuntimeManager.
 */
class VibeflyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        registerNotificationChannels()
    }

    private fun registerNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            RuntimeChannels.RUNTIME,
            getString(R.string.notification_channel_runtime_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_runtime_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }
}
