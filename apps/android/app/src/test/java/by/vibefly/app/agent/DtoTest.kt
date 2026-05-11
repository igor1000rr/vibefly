package by.vibefly.app.agent

import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertEquals

class DtoTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun health_parses() {
        val raw = """{"status":"ok","version":"0.0.1-dev","time":"2026-05-11T18:00:00Z"}"""
        val dto = json.decodeFromString(HealthDto.serializer(), raw)
        assertEquals("ok", dto.status)
        assertEquals("0.0.1-dev", dto.version)
    }

    @Test
    fun system_metrics_parse() {
        val raw = """
            {
              "timestamp":"2026-05-11T18:00:00Z",
              "battery_level":78,
              "battery_status":"Discharging",
              "temperature_c":38.5,
              "cpu_percent":23.4,
              "ram_used_mb":2150,
              "ram_total_mb":6144,
              "uptime_seconds":312640
            }
        """.trimIndent()
        val dto = json.decodeFromString(SystemMetricsDto.serializer(), raw)
        assertEquals(78, dto.batteryLevel)
        assertEquals("Discharging", dto.batteryStatus)
        assertEquals(6144, dto.ramTotalMb)
    }

    @Test
    fun app_dto_parses_with_optional_fields() {
        val raw = """{"id":"a","name":"a","status":"running"}"""
        val dto = json.decodeFromString(AppDto.serializer(), raw)
        assertEquals("a", dto.id)
        assertEquals(null, dto.repo)
        assertEquals(null, dto.port)
    }
}
