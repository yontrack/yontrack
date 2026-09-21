package net.nemerosa.ontrack.graphql.schema.health

import net.nemerosa.ontrack.model.security.GlobalSettings
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.support.ConnectorGlobalStatusService
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller

@Controller
class SystemHealthController(
    private val securityService: SecurityService,
    private val connectorGlobalStatusService: ConnectorGlobalStatusService,
    private val healthEndpoint: HealthEndpoint,
) {

    @QueryMapping
    fun systemHealth(): SystemHealth {
        securityService.checkGlobalFunction(GlobalSettings::class.java)
        return SystemHealth(
            health = healthEndpoint.health(),
            connectors = connectorGlobalStatusService.globalStatus,
        )
    }

}