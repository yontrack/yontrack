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
const val BRANCH_SEARCH_RESULT_TYPE = "branch"

/**
 * Search documents for the branches: the name is the identifier, the description the free text,
 * and the title is `project/branch`, so that a branch is also found on the name of its project.
 *
 * The documents are written in the transaction of the branch events, and those of the branches of
 * a project are rewritten when the project is updated, since they carry its name. The documents
 * of a deleted branch are deleted by the search service.
 */
@Component
class BranchSearchProvider(
    private val structureService: StructureService,
    private val searchDocumentService: SearchDocumentService,
) : SearchDocumentIndexer, EventListener {

    override val searchResultType = SearchResultType(
        feature = CoreExtensionFeature.INSTANCE.featureDescription,
        id = BRANCH_SEARCH_RESULT_TYPE,
        name = "Branch",
        description = "Branch name in Ontrack",
        order = SearchResultType.ORDER_PROJECT + 1,
    )

    override val indexerName: String = "Branches"

    override fun indexAll(processor: (SearchDocument) -> Unit) {
        structureService.projectList.forEach { project ->
            structureService.getBranchesForProject(project.id).forEach { branch ->
                processor(branch.asSearchDocument())
            }
        }
    }

    override fun onEvent(event: Event) {
        when (event.eventType) {
            EventFactory.NEW_BRANCH,
            EventFactory.UPDATE_BRANCH,
            EventFactory.ENABLE_BRANCH,
            EventFactory.DISABLE_BRANCH -> {
                val branch = event.getEntity<Branch>(ProjectEntityType.BRANCH)
                searchDocumentService.index(branch.asSearchDocument())
            }

            EventFactory.UPDATE_PROJECT -> {
                val project = event.getEntity<Project>(ProjectEntityType.PROJECT)
                structureService.getBranchesForProject(project.id).forEach { branch ->
                    searchDocumentService.index(branch.asSearchDocument())
                }
            }
        }
    }

    private fun Branch.asSearchDocument() = SearchDocument(
        type = BRANCH_SEARCH_RESULT_TYPE,
        key = id.toString(),
        projectId = project.id(),
        entity = ProjectEntityID(this),
        title = "${project.name}/$name",
        identifiers = listOf(name),
        text = description?.takeIf { it.isNotBlank() },
        data = mapOf(
            SearchResult.SEARCH_RESULT_BRANCH to mapOf(
                "id" to id(),
                "name" to name,
                "description" to description,
                "disabled" to isDisabled,
                "project" to mapOf(
                    "id" to project.id(),
                    "name" to project.name,
                ),
            )
        ).asJson(),
        updatedAt = signature.time,
    )

}
