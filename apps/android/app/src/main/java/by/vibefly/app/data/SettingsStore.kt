package by.vibefly.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Преференсы приложения.
 *
 * Секреты (auth token, future API keys) хранятся в EncryptedSharedPreferences
 * поверх AndroidX Security. Базовый URL и флаги — в обычных SharedPreferences,
 * потому что это не секреты.
 *
 * Состояние выставляется как StateFlow, чтобы ServiceLocator мог реактивно
 * пересобирать AgentApi при смене хоста/токена/demo-mode.
 */
class SettingsStore(context: Context) {

    private val plain: SharedPreferences = context.getSharedPreferences(PLAIN_NAME, Context.MODE_PRIVATE)
    private val secure: SharedPreferences = createSecure(context)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    fun current(): Snapshot = _state.value

    fun setBaseUrl(url: String) {
        plain.edit().putString(KEY_BASE_URL, url.trim()).apply()
        refresh()
    }

    fun setAuthToken(token: String) {
        secure.edit().putString(KEY_AUTH_TOKEN, token.trim()).apply()
        refresh()
    }

    fun setAiProvider(provider: AiProvider) {
        plain.edit().putString(KEY_AI_PROVIDER, provider.name).apply()
        refresh()
    }

    fun setDemoMode(enabled: Boolean) {
        plain.edit().putBoolean(KEY_DEMO_MODE, enabled).apply()
        refresh()
    }

    private fun refresh() {
        _state.value = read()
    }

    private fun read(): Snapshot = Snapshot(
        baseUrl = plain.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL,
        authToken = secure.getString(KEY_AUTH_TOKEN, "").orEmpty(),
        aiProvider = runCatching {
            AiProvider.valueOf(plain.getString(KEY_AI_PROVIDER, AiProvider.Llama.name) ?: AiProvider.Llama.name)
        }.getOrDefault(AiProvider.Llama),
        demoMode = plain.getBoolean(KEY_DEMO_MODE, false),
    )

    data class Snapshot(
        val baseUrl: String,
        val authToken: String,
        val aiProvider: AiProvider,
        val demoMode: Boolean,
    )

    enum class AiProvider(val displayName: String) {
        Llama("Llama 3.1 70B (free)"),
        DeepSeek("DeepSeek Coder (free)"),
        ClaudeHaiku("Claude Haiku (Pro)"),
        ClaudeSonnet("Claude Sonnet (Studio)"),
        Gpt4oMini("GPT-4o mini (Pro)"),
    }

    companion object {
        const val DEFAULT_BASE_URL: String = "http://127.0.0.1:3001"

        private const val PLAIN_NAME = "vibefly_prefs"
        private const val SECURE_NAME = "vibefly_secure"

        private const val KEY_BASE_URL = "base_url"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_AI_PROVIDER = "ai_provider"
        private const val KEY_DEMO_MODE = "demo_mode"

        private fun createSecure(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                SECURE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }
}
