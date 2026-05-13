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
 * как обычный child-процесс Android-приложения.
 *
 * Что делается при старте:
 *   1. Извлекаются бинари из assets:
 *      • assets/agent/vibefly-agent → filesDir/agent/vibefly-agent
 *      • assets/cloudflared/cloudflared → filesDir/cloudflared/cloudflared (если есть)
 *   2. Делается chmod 755 на оба бинаря
 *   3. Пишется agent.toml с listen + tunnel секцией
 *   4. ProcessBuilder запускает агента; stdout/stderr читаем в Logcat
 *   5. Опрашиваем /health пока он не ответит; обновляем state
 *
 * Auto-restart: если процесс умирает, waitFor возвращается,
 * мы ждём AUTO_RESTART_DELAY_MS и автоматически запускаем агент заново.
 */
object RuntimeManager {

    private const val TAG = "VibeFlyRuntime"
    private const val AGENT_PORT = 3001
    private const val HEALTH_URL = "http://127.0.0.1:$AGENT_PORT/health"

    private const val AUTO_RESTART_DELAY_MS = 3_000L

    enum class State { Idle, Starting, Running, Stopped, Failed }

    data class Status(
        val state: State,
        val version: String?,
        val pid: Int?,
        val error: String?,
        val restartCount: Int = 0,
        val tunnelBinaryAvailable: Boolean = false,
    )

    private val _status = MutableStateFlow(Status(State.Idle, null, null, null))
    val status: StateFlow<Status> = _status.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var process: java.lang.Process? = null

    @Volatile
    private var explicitlyStopped = false

    private fun pidOf(proc: java.lang.Process): Int? = try {
        val m = proc.javaClass.getMethod("pid")
        (m.invoke(proc) as? Long)?.toInt()
    } catch (_: Throwable) {
        null
    }

    fun startIfNeeded(context: Context) {
        if (_status.value.state == State.Running || _status.value.state == State.Starting) {
            Log.i(TAG, "уже запущен или стартует, пропускаю")
            return
        }
        explicitlyStopped = false
        scope.launch { start(context.applicationContext) }
    }

    private suspend fun start(context: Context) {
        val currentRestartCount = _status.value.restartCount
        _status.value = Status(State.Starting, null, null, null, currentRestartCount)
        try {
            val agentDir = File(context.filesDir, "agent").apply { mkdirs() }
            val agentBinary = File(agentDir, "vibefly-agent")
            val agentConfig = File(agentDir, "agent.toml")
            val appsDir = File(context.filesDir, "apps").apply { mkdirs() }
            val logsDir = File(context.filesDir, "logs").apply { mkdirs() }

            // Извлекаем агент из assets.
            if (!agentBinary.exists() || agentBinary.length() == 0L) {
                extractFromAssets(context, "agent/vibefly-agent", agentBinary)
            }
            if (!agentBinary.exists() || agentBinary.length() == 0L) {
                _status.value = Status(
                    state = State.Failed,
                    version = null,
                    pid = null,
                    error = "agent binary не найден в assets — APK собран без CI",
                    restartCount = currentRestartCount,
                )
                Log.w(TAG, "agent binary отсутствует")
                return
            }
            agentBinary.setExecutable(true, false)
            agentBinary.setReadable(true, false)

            // Извлекаем cloudflared (опционально — без него agent работает локально).
            val cloudflaredDir = File(context.filesDir, "cloudflared").apply { mkdirs() }
            val cloudflaredBinary = File(cloudflaredDir, "cloudflared")
            if (!cloudflaredBinary.exists() || cloudflaredBinary.length() == 0L) {
                extractFromAssets(context, "cloudflared/cloudflared", cloudflaredBinary)
            }
            val tunnelAvailable = cloudflaredBinary.exists() && cloudflaredBinary.length() > 0L
            if (tunnelAvailable) {
                cloudflaredBinary.setExecutable(true, false)
                cloudflaredBinary.setReadable(true, false)
                Log.i(TAG, "cloudflared извлечён, ${cloudflaredBinary.length() / 1024 / 1024} MB")
            } else {
                Log.i(TAG, "cloudflared отсутствует, туннель не будет доступен")
            }

            // Генерируем конфиг с учётом tunnel'а.
            agentConfig.writeText(
                buildConfig(appsDir, logsDir, cloudflaredBinary.takeIf { tunnelAvailable })
            )

            // Запуск агента.
            val pb = ProcessBuilder(agentBinary.absolutePath, "--config", agentConfig.absolutePath)
                .redirectErrorStream(true)
                .directory(agentDir)

            val proc: java.lang.Process = pb.start()
            process = proc
            val procPid = pidOf(proc)
            Log.i(TAG, "agent started, pid=$procPid, tunnel=$tunnelAvailable")
            _status.value = Status(
                state = State.Starting,
                version = null,
                pid = procPid,
                error = null,
                restartCount = currentRestartCount,
                tunnelBinaryAvailable = tunnelAvailable,
            )

            scope.launch {
                proc.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { Log.i(TAG, "[agent] $it") }
                }
            }
            scope.launch {
                val exitCode = withContext(Dispatchers.IO) { proc.waitFor() }
                Log.w(TAG, "agent exited with code=$exitCode")
                process = null

                if (explicitlyStopped) {
                    _status.value = Status(
                        State.Stopped, null, null, null, currentRestartCount, tunnelAvailable
                    )
                    Log.i(TAG, "agent stopped by user, не перезапускаем")
                    return@launch
                }

                _status.value = Status(
                    state = State.Stopped,
                    version = null,
                    pid = null,
                    error = "agent exited with $exitCode, перезапуск через ${AUTO_RESTART_DELAY_MS / 1000}s",
                    restartCount = currentRestartCount,
                    tunnelBinaryAvailable = tunnelAvailable,
                )
                delay(AUTO_RESTART_DELAY_MS)
                if (explicitlyStopped) return@launch
                Log.i(TAG, "auto-restart attempt #${currentRestartCount + 1}")
                _status.value = _status.value.copy(restartCount = currentRestartCount + 1)
                start(context)
            }

            val started = waitForHealth(timeoutMs = 10_000)
            if (started) {
                _status.value = Status(
                    state = State.Running,
                    version = "embedded",
                    pid = procPid,
                    error = null,
                    restartCount = currentRestartCount,
                    tunnelBinaryAvailable = tunnelAvailable,
                )
                Log.i(TAG, "agent отвечает на /health")
            } else {
                _status.value = Status(
                    state = State.Failed,
                    version = null,
                    pid = procPid,
                    error = "agent не ответил на /health за 10s",
                    restartCount = currentRestartCount,
                    tunnelBinaryAvailable = tunnelAvailable,
                )
                Log.e(TAG, "agent не ответил на /health")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "start failed", t)
            _status.value = Status(
                State.Failed, null, null, t.message ?: t.toString(),
                _status.value.restartCount,
            )
        }
    }

    fun stop() {
        explicitlyStopped = true
        process?.destroy()
        process = null
        _status.value = Status(State.Stopped, null, null, null, _status.value.restartCount)
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

    /**
     * Генерируем конфиг. Если cloudflaredBinary передан — включаем tunnel в
     * выключенном состоянии (enabled = true, autostart = false). Пользователь
     * сам включит его из Settings через POST /tunnel/start.
     */
    private fun buildConfig(
        appsDir: File,
        logsDir: File,
        cloudflaredBinary: File?,
    ): String = buildString {
        appendLine("# VibeFly agent config — генерируется RuntimeManager.")
        appendLine("listen = \"127.0.0.1:$AGENT_PORT\"")
        appendLine("auth_token = \"\"")
        appendLine("apps_dir = \"${appsDir.absolutePath}\"")
        appendLine("logs_dir = \"${logsDir.absolutePath}\"")
        appendLine("seed_demo_apps = true")
        if (cloudflaredBinary != null) {
            appendLine()
            appendLine("[tunnel]")
            appendLine("enabled = true")
            appendLine("autostart = false")
            appendLine("binary = \"${cloudflaredBinary.absolutePath}\"")
            appendLine("target = \"http://127.0.0.1:$AGENT_PORT\"")
            appendLine("startup_timeout = \"60s\"")
        }
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
