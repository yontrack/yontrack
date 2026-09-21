package net.nemerosa.ontrack.graphql.schema.health

import net.nemerosa.ontrack.model.support.ConnectorGlobalStatus
import org.springframework.boot.health.actuate.endpoint.HealthDescriptor

data class SystemHealth(
    val health: HealthDescriptor,
    val connectors: ConnectorGlobalStatus,
)
