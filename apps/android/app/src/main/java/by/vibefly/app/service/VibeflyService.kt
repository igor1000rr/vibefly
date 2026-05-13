package by.vibefly.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import by.vibefly.app.MainActivity
import by.vibefly.app.R
import by.vibefly.app.runtime.RuntimeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Foreground service для embedded Go-агента.
 *
 * Без foreground service Android приоритетно убьёт все процессы приложения
 * когда пользователь свернёт экран или нужна будет память — embedded agent умрёт вместе.
 * Для phone-as-a-server это неприемлемо: сервер должен жить непрерывно.
 *
 * Service логика:
 *   • onStartCommand → становимся foreground с ongoing-уведомлением
   *   • вызываем RuntimeManager.startIfNeeded() — идемпотентно, безопасно
 *   • подписываемся на RuntimeManager.status — обновляем текст уведомления по PID
 *   • START_STICKY — если Android убьёт в OOM-killer, будет перезапущен
 */
class VibeflyService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var statusJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat(buildNotification("Сервер запускается…"))
        RuntimeManager.startIfNeeded(applicationContext)
        observeRuntimeStatus()
        Log.i(TAG, "VibeflyService стартовал, embedded agent запущен")
        return START_STICKY
    }

    override fun onDestroy() {
        statusJob?.cancel()
        super.onDestroy()
    }

    /** Подписываемся на состояние runtime и обновляем текст уведомления. */
    private fun observeRuntimeStatus() {
        statusJob?.cancel()
        statusJob = scope.launch {
            RuntimeManager.status.collect { status ->
                val text = when (status.state) {
                    RuntimeManager.State.Idle -> "Ожидание запуска"
                    RuntimeManager.State.Starting -> "Стартует…"
                    RuntimeManager.State.Running -> "Сервер работает" +
                        (status.pid?.let { " · pid $it" } ?: "")
                    RuntimeManager.State.Stopped -> "Остановлен"
                    RuntimeManager.State.Failed -> "Ошибка: ${status.error ?: "неизвестно"}"
                }
                val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                mgr.notify(NOTIFICATION_ID, buildNotification(text))
            }
        }
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(text: String): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, RuntimeChannels.RUNTIME)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.notification_runtime_title))
            .setContentText(text)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    companion object {
        private const val TAG = "VibeFlyService"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            val intent = Intent(context, VibeflyService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
