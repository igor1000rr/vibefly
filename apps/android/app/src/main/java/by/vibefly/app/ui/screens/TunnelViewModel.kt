package by.vibefly.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.vibefly.app.agent.TunnelStatusDto
import by.vibefly.app.data.ServiceLocator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Состояние Cloudflare Tunnel.
 */
data class TunnelState(
    val status: TunnelStatusDto = TunnelStatusDto(),
    val busy: Boolean = false,
    val error: String? = null,
)

/**
 * TunnelViewModel — управляет Cloudflare Tunnel через agent API.
 *
 * При старте опрашивает текущее состояние раз в 5 сек, чтобы UI был
 * синхронизирован с agent'ом (cloudflared может упасть или сам переподключиться).
 */
class TunnelViewModel : ViewModel() {

    private val _state = MutableStateFlow(TunnelState())
    val state: StateFlow<TunnelState> = _state.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            while (isActive) {
                delay(5_000)
                refresh()
            }
        }
    }

    fun start() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val s = ServiceLocator.agent().tunnelStart()
                _state.update { it.copy(status = s, busy = false, error = null) }
            } catch (t: Throwable) {
                _state.update { it.copy(busy = false, error = t.localizedMessage ?: "start failed") }
            }
        }
    }

    fun stop() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val s = ServiceLocator.agent().tunnelStop()
                _state.update { it.copy(status = s, busy = false, error = null) }
            } catch (t: Throwable) {
                _state.update { it.copy(busy = false, error = t.localizedMessage ?: "stop failed") }
            }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            try {
                val s = ServiceLocator.agent().tunnelStatus()
                _state.update { it.copy(status = s) }
            } catch (_: Throwable) {
                // Просто пропускаем — агент может перезапускаться.
            }
        }
    }
}
