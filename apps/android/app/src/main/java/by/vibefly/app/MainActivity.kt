package by.vibefly.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import by.vibefly.app.ui.VibeflyNavHost
import by.vibefly.app.ui.theme.VibeflyTheme

/**
 * Единственная Activity приложения. Хостит Compose-навигацию.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { VibeflyRoot() }
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
