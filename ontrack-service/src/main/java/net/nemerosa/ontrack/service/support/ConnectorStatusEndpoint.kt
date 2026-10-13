package net.nemerosa.ontrack.service.support

import net.nemerosa.ontrack.model.support.ConnectorGlobalStatus
import org.springframework.boot.actuate.endpoint.annotation.Endpoint
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation

@Endpoint(id = "connectors")
class ConnectorStatusEndpoint(
        private val connectorStatusJob: ConnectorStatusJob
) {

    @ReadOperation
    fun invoke(): ConnectorGlobalStatus = connectorStatusJob.globalStatus

}