package net.nemerosa.ontrack.extension.github.ingestion.support

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.github.ingestion.payload.IngestionHookPayload
import net.nemerosa.ontrack.extension.github.ingestion.payload.IngestionHookPayloadStorage
import net.nemerosa.ontrack.extension.github.ingestion.processing.model.Repository
import net.nemerosa.ontrack.extension.github.ingestion.queue.IngestionHookQueue
import net.nemerosa.ontrack.model.security.ProjectConfig
import net.nemerosa.ontrack.model.security.ProjectCreation
import net.nemerosa.ontrack.model.security.SecurityService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
@Transactional
class DefaultIngestionEventService(
    private val ingestionModelAccessService: IngestionModelAccessService,
    private val securityService: SecurityService,
    private val storage: IngestionHookPayloadStorage,
    private val queue: IngestionHookQueue,
) : IngestionEventService {
    override fun sendIngestionEvent(
        event: String,
        owner: String,
        repository: String,
        payload: JsonNode,
        payloadSource: String?,
    ): UUID {
        // Checks the access rights on the target project
        val repository = Repository.stub(owner, repository)
        val project = ingestionModelAccessService.findProjectFromRepository(repository)
        if (project != null) {
            securityService.checkProjectFunction(project, ProjectConfig::class.java)
        } else {
            securityService.checkGlobalFunction(ProjectCreation::class.java)
        }
        // Creates the payload
        val ingestionHookPayload = IngestionHookPayload(
            gitHubDelivery = "",
            gitHubEvent = event,
            gitHubHookID = 0,
            gitHubHookInstallationTargetID = 0,
            gitHubHookInstallationTargetType = "",
            payload = payload,
            repository = repository,
            accountName = securityService.currentUser?.name
                ?: error("Missing account name to process the payload")
        )
        securityService.asAdmin {
            // Stores it
            storage.store(ingestionHookPayload, payloadSource)
            // Pushes it on the queue
            queue.queue(ingestionHookPayload)
        }
        // OK
        return ingestionHookPayload.uuid
    }
}