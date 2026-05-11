package by.vibefly.app.runtime

/**
 * JNI-обёртка над namespace-runtime'ом. На старте фазы 2 здесь будут вызовы
 * к Droidspaces или к нашему собственному proot-launcher'у.
 *
 * Сейчас все методы — заглушки, возвращающие success=false, чтобы UI можно было
 * протестировать без реального namespace-runtime.
 */
object RuntimeManager {

    enum class Backend { DROIDSPACES, PROOT, UNAVAILABLE }

    data class Status(
        val backend: Backend,
        val running: Boolean,
        val rootfsReady: Boolean,
        val agentReachable: Boolean,
    )

    fun detect(): Backend {
        // TODO: проверяем CONFIG_USER_NS, cgroups, root — выбираем backend.
        return Backend.UNAVAILABLE
    }

    fun status(): Status = Status(
        backend = detect(),
        running = false,
        rootfsReady = false,
        agentReachable = false,
    )

    fun startIfNeeded(): Boolean {
        // TODO: запуск runtime + agent внутри rootfs.
        return false
    }

    fun stop(): Boolean {
        // TODO: gracefull shutdown.
        return false
    }
}
