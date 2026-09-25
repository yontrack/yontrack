package net.nemerosa.ontrack.boot

import net.nemerosa.ontrack.extension.support.CoreExtensionFeature
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.events.EventListener
import net.nemerosa.ontrack.model.structure.*
import org.springframework.stereotype.Component

/**
 * Search result type
 */
const val PROJECT_SEARCH_RESULT_TYPE = "project"

/**
 * Search documents for the projects: the name is the title and the identifier, the description
 * the free text.
 *
 * The documents are written in the transaction of the project events.
 */
@Component
class ProjectSearchProvider(
    private val structureService: StructureService,
    private val searchDocumentService: SearchDocumentService,
) : SearchDocumentIndexer, EventListener {

    override val searchResultType = SearchResultType(
        feature = CoreExtensionFeature.INSTANCE.featureDescription,
        id = PROJECT_SEARCH_RESULT_TYPE,
        name = "Project",
        description = "Project name in Ontrack",
        order = SearchResultType.ORDER_PROJECT,
    )

    override val indexerName: String = "Projects"

    override fun indexAll(processor: (SearchDocument) -> Unit) {
        structureService.projectList.forEach { project ->
            processor(project.asSearchDocument())
        }
    }

    override fun onEvent(event: Event) {
        when (event.eventType) {
            EventFactory.NEW_PROJECT,
            EventFactory.UPDATE_PROJECT,
            EventFactory.ENABLE_PROJECT,
            EventFactory.DISABLE_PROJECT -> {
                val project = event.getEntity<Project>(ProjectEntityType.PROJECT)
                searchDocumentService.index(project.asSearchDocument())
            }

            EventFactory.DELETE_PROJECT -> {
                // The documents of the project, of every type, go with it anyway
                searchDocumentService.delete(PROJECT_SEARCH_RESULT_TYPE, event.getIntValue("PROJECT_ID").toString())
            }
        }
    }

    private fun Project.asSearchDocument() = SearchDocument(
        type = PROJECT_SEARCH_RESULT_TYPE,
        key = id.toString(),
        projectId = id(),
        entity = ProjectEntityID(this),
        title = name,
        identifiers = listOf(name),
        text = description?.takeIf { it.isNotBlank() },
        data = mapOf(
            SearchResult.SEARCH_RESULT_PROJECT to mapOf(
                "id" to id(),
                "name" to name,
                "description" to description,
                "disabled" to isDisabled,
            )
        ).asJson(),
        updatedAt = signature.time,
    )

}
