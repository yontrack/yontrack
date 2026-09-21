package net.nemerosa.ontrack.extension.github.ingestion.support

import tools.jackson.databind.JsonNode
import java.util.*

interface IngestionEventService {

    fun sendIngestionEvent(
        event: String,
        owner: String,
        repository: String,
        payload: JsonNode,
        payloadSource: String?,
    ): UUID

}