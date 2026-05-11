package by.vibefly.app.agent

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO для marketplace API агента.
 */

@Serializable
data class MarketplaceEnvFieldDto(
    val key: String,
    val label: String,
    val hint: String? = null,
    val secret: Boolean = false,
    val default: String? = null,
    val required: Boolean = false,
    val placeholder: String? = null,
)

@Serializable
data class MarketplaceTemplateDto(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val icon: String,
    val homepage: String? = null,
    val repo: String? = null,
    val image: String? = null,
    @SerialName("start_cmd") val startCmd: String,
    @SerialName("default_port") val defaultPort: Int? = null,
    @SerialName("memory_max") val memoryMax: String? = null,
    @SerialName("env_schema") val envSchema: List<MarketplaceEnvFieldDto> = emptyList(),
    val tags: List<String> = emptyList(),
)

@Serializable
data class MarketplaceInstallRequest(
    @SerialName("app_id") val appId: String,
    val domain: String? = null,
    val port: Int? = null,
    val env: Map<String, String> = emptyMap(),
)
