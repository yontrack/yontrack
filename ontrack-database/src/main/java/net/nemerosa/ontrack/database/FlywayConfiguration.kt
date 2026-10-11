package net.nemerosa.ontrack.database

import org.flywaydb.core.api.configuration.FluentConfiguration
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer
import org.springframework.stereotype.Component

@Component
class FlywayConfiguration: FlywayConfigurationCustomizer {
    override fun customize(configuration: FluentConfiguration) {
        configuration.table("schema_version")
        configuration.configuration(POSTGRESQL_SESSION_LOCK)
    }

    companion object {
        /**
         * Flyway locks the migrations with a session-level advisory lock rather than with a
         * transactional one, whose transaction stays open while the migrations run: a
         * `CREATE INDEX CONCURRENTLY` would wait for it forever (#2057).
         */
        val POSTGRESQL_SESSION_LOCK = mapOf("flyway.postgresql.transactional.lock" to "false")
    }
}
