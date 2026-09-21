package net.nemerosa.ontrack.extension.influxdb

import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator

class InfluxDBExtensionHealthIndicator(
    private val influxDBConnection: InfluxDBConnection
) : HealthIndicator {

    override fun health(): Health {
        val ok: Boolean = try {
            influxDBConnection.isValid(true)
        } catch (ex: Exception) {
            false
        }
        return if (ok) {
            Health.up().build()
        } else {
            Health.down().build()
        }
    }

}