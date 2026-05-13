package by.vibefly.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import by.vibefly.app.service.VibeflyService
import by.vibefly.app.ui.VibeflyNavHost
import by.vibefly.app.ui.theme.VibeflyTheme

/**
 * Единственная Activity приложения. Хостит Compose-навигацию.
 *
 * Дополнительные обязанности:
 *  • запрос POST_NOTIFICATIONS на Android 13+ (нужен для foreground service)
 *  • запуск VibeflyService — он в свою очередь дёргает RuntimeManager
 *    и превращает embedded agent в "живучий" процесс переживающий сворачивание
 */
class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Не важно дал granted или нет — в любом случае стартуем сервис.
        // Без permission уведомление не покажется, но service всё равно будет работать.
        VibeflyService.start(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { VibeflyRoot() }
        ensureForegroundServiceStarted()
    }

    /**
     * На Android 13+ POST_NOTIFICATIONS — runtime permission. Без него Android
     * просто не покажет ongoing notification, и foreground service рискует
     * быть убит раньше. Просим один раз при первом запуске.
     */
    private fun ensureForegroundServiceStarted() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // До Android 13 permission не нужен — сразу стартуем.
            VibeflyService.start(this)
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            VibeflyService.start(this)
        } else {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
private fun VibeflyRoot() {
    VibeflyTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            VibeflyNavHost()
        }
    }
}
