package by.vibefly.app.runtime

import android.content.Context
import android.util.Log
import by.vibefly.app.data.ServiceLocator
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
import java.security.SecureRandom

/**
 * RuntimeManager — запускает Go-агента, упакованного в assets.
 *
 * Auth token: при первом старте генерируется 32-байтовый hex-токен, сохраняется в
 * EncryptedSharedPreferences и пишется в agent.toml.
 *
 * Фаза 2: rootfs-tarball (Alpine minirootfs ~3 MB) извлекается в filesDir/rootfs/.
 * Агент сам распакует его в filesDir/rootfs-base/ в фоне.
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
        val rootfsBundled: Boolean = false,
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

            val authToken = ensureAuthToken()

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
                Log.i(TAG, "cloudflared отсутствует")
            }

            // Фаза 2: извлекаем Alpine minirootfs тарбол из assets. Агент сам распакует его
            // в rootfs-base/ при первом старте (это займёт 5-10 сек, бывает в фоне).
            val rootfsDir = File(context.filesDir, "rootfs").apply { mkdirs() }
            val rootfsTarball = File(rootfsDir, "alpine-minirootfs.tar.gz")
            if (!rootfsTarball.exists() || rootfsTarball.length() == 0L) {
                extractFromAssets(context, "rootfs/alpine-minirootfs.tar.gz", rootfsTarball)
            }
            val rootfsBundled = rootfsTarball.exists() && rootfsTarball.length() > 0L
            val rootfsBaseDir = File(context.filesDir, "rootfs-base")
            if (rootfsBundled) {
                Log.i(TAG, "rootfs tarball извлечён, ${rootfsTarball.length() / 1024} KB")
            } else {
                Log.i(TAG, "rootfs tarball отсутствует — chroot-runtime не будет доступен")
            }

            agentConfig.writeText(
                buildConfig(
                    appsDir = appsDir,
                    logsDir = logsDir,
                    cloudflaredBinary = cloudflaredBinary.takeIf { tunnelAvailable },
                    authToken = authToken,
                    rootfsTarball = rootfsTarball.takeIf { rootfsBundled },
                    rootfsBaseDir = rootfsBaseDir,
                )
            )

            val pb = ProcessBuilder(agentBinary.absolutePath, "--config", agentConfig.absolutePath)
                .redirectErrorStream(true)
                .directory(agentDir)

            val proc: java.lang.Process = pb.start()
            process = proc
            val procPid = pidOf(proc)
            Log.i(TAG, "agent started, pid=$procPid, tunnel=$tunnelAvailable, rootfs=$rootfsBundled, auth=enabled")
            _status.value = Status(
                state = State.Starting,
                version = null,
                pid = procPid,
                error = null,
                restartCount = currentRestartCount,
                tunnelBinaryAvailable = tunnelAvailable,
                rootfsBundled = rootfsBundled,
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
                        State.Stopped, null, null, null, currentRestartCount, tunnelAvailable, rootfsBundled
                    )
                    return@launch
                }

                _status.value = Status(
                    state = State.Stopped,
                    version = null,
                    pid = null,
                    error = "agent exited with $exitCode, перезапуск через ${AUTO_RESTART_DELAY_MS / 1000}s",
                    restartCount = currentRestartCount,
                    tunnelBinaryAvailable = tunnelAvailable,
                    rootfsBundled = rootfsBundled,
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
                    rootfsBundled = rootfsBundled,
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
                    rootfsBundled = rootfsBundled,
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

    private fun ensureAuthToken(): String {
        val settings = ServiceLocator.settings()
        val existing = settings.current().authToken
        if (existing.isNotBlank()) return existing

        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        val sb = StringBuilder(64)
        for (b in bytes) {
            sb.append(String.format("%02x", b.toInt() and 0xff))
        }
        val token = sb.toString()
        settings.setAuthToken(token)
        Log.i(TAG, "сгенерирован новый auth_token (32 байта)")
        return token
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

    private fun buildConfig(
        appsDir: File,
        logsDir: File,
        cloudflaredBinary: File?,
        authToken: String,
        rootfsTarball: File?,
        rootfsBaseDir: File,
    ): String = buildString {
        appendLine("# VibeFly agent config — генерируется RuntimeManager.")
        appendLine("listen = \"127.0.0.1:$AGENT_PORT\"")
        appendLine("auth_token = \"$authToken\"")
        appendLine("apps_dir = \"${appsDir.absolutePath}\"")
        appendLine("logs_dir = \"${logsDir.absolutePath}\"")
        appendLine("seed_demo_apps = false")
        if (rootfsTarball != null) {
            appendLine("rootfs_tarball_path = \"${rootfsTarball.absolutePath}\"")
            appendLine("rootfs_base_dir = \"${rootfsBaseDir.absolutePath}\"")
        }
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
