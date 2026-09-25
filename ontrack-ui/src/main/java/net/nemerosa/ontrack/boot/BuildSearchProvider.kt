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
const val BUILD_SEARCH_RESULT_TYPE = "build"

/**
 * Search documents for the builds: the name and the display name are the identifiers, so that
 * an exact build name beats everything, and the description is the free text. The title is the
 * display name when there is one, the name otherwise. The time of the build is its recency.
 *
 * The documents are written in the transaction of the build events, including the change of
 * display name. The documents of a deleted build, or of the builds of a deleted branch, are
 * deleted by the search service.
 */
@Component
class BuildSearchProvider(
    private val structureService: StructureService,
    private val searchDocumentService: SearchDocumentService,
    private val buildDisplayNameService: BuildDisplayNameService,
) : SearchDocumentIndexer, EventListener {

    override val searchResultType = SearchResultType(
        feature = CoreExtensionFeature.INSTANCE.featureDescription,
        id = BUILD_SEARCH_RESULT_TYPE,
        name = "Build",
        description = "Build name in Ontrack",
        order = SearchResultType.ORDER_PROJECT + 2,
    )

    override val indexerName: String = "Builds"

    override fun indexAll(processor: (SearchDocument) -> Unit) {
        structureService.projectList.forEach { project ->
            structureService.getBranchesForProject(project.id).forEach { branch ->
                structureService.forEachBuild(branch, BuildSortDirection.FROM_OLDEST) { build ->
                    processor(build.asSearchDocument())
                    true // Going on
                }
            }
        }
    }

    override fun onEvent(event: Event) {
        when (event.eventType) {
            EventFactory.NEW_BUILD,
            EventFactory.UPDATE_BUILD,
            EventFactory.UPDATE_BUILD_DISPLAY_NAME -> {
                val build = event.getEntity<Build>(ProjectEntityType.BUILD)
                searchDocumentService.index(build.asSearchDocument())
            }
        }
    }

    private fun Build.asSearchDocument(): SearchDocument {
        val displayName = buildDisplayNameService.getFirstBuildDisplayName(this)?.takeIf { it.isNotBlank() }
        return SearchDocument(
            type = BUILD_SEARCH_RESULT_TYPE,
            key = id.toString(),
            projectId = project.id(),
            entity = ProjectEntityID(this),
            title = displayName ?: name,
            identifiers = listOfNotNull(name, displayName),
            text = description?.takeIf { it.isNotBlank() },
            data = mapOf(
                SearchResult.SEARCH_RESULT_BUILD to mapOf(
                    "id" to id(),
                    "name" to name,
                    "description" to description,
                    "branch" to mapOf(
                        "id" to branch.id(),
                        "name" to branch.name,
                        "project" to mapOf(
                            "id" to project.id(),
                            "name" to project.name,
                        ),
                    ),
                ),
                // Display name of the build, or its name
                SearchResult.SEARCH_RESULT_BUILD_RELEASE to (displayName ?: name),
            ).asJson(),
            updatedAt = signature.time,
        )
    }

}
