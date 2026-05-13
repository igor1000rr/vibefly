package by.vibefly.app.runtime

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * RuntimeManager — запускает Go-агента, упакованного в assets/agent/vibefly-agent,
 * как обычный child-процесс Android-приложения. Без root, без namespace,
 * без Droidspaces — этап 1 phone-as-a-server.
 *
 * Что делается при старте:
 *   1. Извлекается бинарь из assets в filesDir/agent/vibefly-agent
 *   2. Делается chmod 755 (через File.setExecutable)
 *   3. Пишется agent.toml с listen = 127.0.0.1:3001
 *   4. ProcessBuilder запускает агента; stdout/stderr читаем в Logcat
 *   5. Опрашиваем /health пока он не ответит; обновляем state
 *
 * При выходе из приложения процесс остаётся живым пока есть foreground service.
 * Если процесс умирает — мы это видим (waitFor вернулся) и обновляем state.
 */
object RuntimeManager {

    private const val TAG = "VibeFlyRuntime"
    private const val AGENT_PORT = 3001
    private const val HEALTH_URL = "http://127.0.0.1:$AGENT_PORT/health"

    enum class State { Idle, Starting, Running, Stopped, Failed }

    data class Status(
        val state: State,
        val version: String?,
        val pid: Int?,
        val error: String?,
    )

    private val _status = MutableStateFlow(Status(State.Idle, null, null, null))
    val status: StateFlow<Status> = _status.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var process: Process? = null

    /**
     * Запустить агента если ещё не запущен. Идемпотентно — повторные вызовы
     * безопасны.
     */
    fun startIfNeeded(context: Context) {
        if (_status.value.state == State.Running || _status.value.state == State.Starting) {
            Log.i(TAG, "уже запущен или стартует, пропускаю")
            return
        }
        scope.launch { start(context.applicationContext) }
    }

    private suspend fun start(context: Context) {
        _status.value = Status(State.Starting, null, null, null)
        try {
            val agentDir = File(context.filesDir, "agent").apply { mkdirs() }
            val agentBinary = File(agentDir, "vibefly-agent")
            val agentConfig = File(agentDir, "agent.toml")
            val appsDir = File(context.filesDir, "apps").apply { mkdirs() }
            val logsDir = File(context.filesDir, "logs").apply { mkdirs() }

            // Шаг 1: извлекаем бинарь, если ещё не извлечён.
            if (!agentBinary.exists() || agentBinary.length() == 0L) {
                extractFromAssets(context, "agent/vibefly-agent", agentBinary)
            }
            if (!agentBinary.exists() || agentBinary.length() == 0L) {
                _status.value = Status(
                    state = State.Failed,
                    version = null,
                    pid = null,
                    error = "agent binary не найден в assets — APK собран без CI или вручную без бинаря",
                )
                Log.w(TAG, "agent binary отсутствует, embedded runtime не запускается")
                return
            }
            agentBinary.setExecutable(true, false)
            agentBinary.setReadable(true, false)

            // Шаг 2: пишем минимальный конфиг.
            agentConfig.writeText(buildConfig(appsDir, logsDir))

            // Шаг 3: запуск.
            val pb = ProcessBuilder(agentBinary.absolutePath, "--config", agentConfig.absolutePath)
                .redirectErrorStream(true)
                .directory(agentDir)
            // Android: дочерний процесс наследует UID приложения (Linux UID-based sandbox).
            // Никаких лишних прав он не получит.

            val proc = pb.start()
            process = proc
            Log.i(TAG, "agent started, pid=${proc.pid()}")
            _status.value = Status(
                state = State.Starting,
                version = null,
                pid = proc.pid().toInt(),
                error = null,
            )

            // Читаем stdout в Logcat, чтобы можно было дебажить через adb logcat -s VibeFlyRuntime.
            scope.launch {
                proc.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { Log.i(TAG, "[agent] $it") }
                }
            }
            // Следим за смертью процесса.
            scope.launch {
                val exitCode = withContext(Dispatchers.IO) { proc.waitFor() }
                Log.w(TAG, "agent exited with code=$exitCode")
                _status.value = Status(
                    state = State.Stopped,
                    version = null,
                    pid = null,
                    error = "agent exited with $exitCode",
                )
                process = null
            }

            // Ждём пока /health ответит, до 10 секунд.
            val started = waitForHealth(timeoutMs = 10_000)
            if (started) {
                _status.value = Status(
                    state = State.Running,
                    version = "embedded",
                    pid = proc.pid().toInt(),
                    error = null,
                )
                Log.i(TAG, "agent отвечает на /health, готов к работе")
            } else {
                _status.value = Status(
                    state = State.Failed,
                    version = null,
                    pid = proc.pid().toInt(),
                    error = "agent не ответил на /health за 10s",
                )
                Log.e(TAG, "agent не ответил на /health")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "start failed", t)
            _status.value = Status(State.Failed, null, null, t.message ?: t.toString())
        }
    }

    /**
     * Остановить агента. Используется при явном пользовательском действии.
     * Foreground-service-keepalive сам по себе вызывает start, не stop.
     */
    fun stop() {
        process?.destroy()
        process = null
        _status.value = Status(State.Stopped, null, null, null)
    }

    private fun extractFromAssets(context: Context, assetPath: String, target: File) {
        try {
            context.assets.open(assetPath).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            Log.i(TAG, "извлёк ${target.absolutePath} (${target.length()} bytes)")
        } catch (e: IOException) {
            Log.w(TAG, "не удалось извлечь $assetPath: ${e.message}")
        }
    }

    private fun buildConfig(appsDir: File, logsDir: File): String = buildString {
        appendLine("# VibeFly agent config — генерируется RuntimeManager при первом запуске.")
        appendLine("listen = \"127.0.0.1:$AGENT_PORT\"")
        appendLine("auth_token = \"\"")
        appendLine("apps_dir = \"${appsDir.absolutePath}\"")
        appendLine("logs_dir = \"${logsDir.absolutePath}\"")
        // На Android нет systemd → supervisor.Available() = false → store пустой.
        // Demo apps включаем для наглядности первого запуска.
        appendLine("seed_demo_apps = true")
    }

    private suspend fun waitForHealth(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (pingHealth()) return true
            delay(500)
        }
        return false
    }

    private fun pingHealth(): Boolean {
        return try {
            val url = URL(HEALTH_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 1_000
            conn.readTimeout = 1_000
            conn.requestMethod = "GET"
            val code = conn.responseCode
            conn.disconnect()
            code == 200
        } catch (_: IOException) {
            false
        }
    }
}
