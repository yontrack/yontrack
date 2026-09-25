package net.nemerosa.ontrack.extension.git

import net.nemerosa.ontrack.extension.git.property.GitBranchConfigurationProperty
import net.nemerosa.ontrack.extension.git.property.GitBranchConfigurationPropertyType
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.events.EventListener
import net.nemerosa.ontrack.model.structure.*
import org.springframework.stereotype.Component

/**
 * Search documents for the Git branches of the branches: one document per branch having a Git
 * branch property, whose Git branch is the title and the identifier. The time of the branch is
 * its recency.
 *
 * The documents are written by the hooks of the [GitBranchConfigurationPropertyType], in the
 * transaction of the change of property, and rewritten when the branch or its project is
 * updated, since they carry their names. The documents of a deleted branch are deleted by the
 * search service.
 */
@Component
class GitBranchSearchIndexer(
    extensionFeature: GitExtensionFeature,
    private val propertyService: PropertyService,
    private val structureService: StructureService,
    private val searchDocumentService: SearchDocumentService,
) : SearchDocumentIndexer, EventListener {

    override val searchResultType = SearchResultType(
        feature = extensionFeature.featureDescription,
        id = SEARCH_RESULT_TYPE,
        name = "Git Branch",
        description = "Git branch associated to an Ontrack branch",
        order = SearchResultType.ORDER_PROPERTIES + 50,
    )

    override val indexerName: String = "Git Branches"

    override fun indexAll(processor: (SearchDocument) -> Unit) {
        propertyService.forEachEntityWithProperty<GitBranchConfigurationPropertyType, GitBranchConfigurationProperty> { entityId, property ->
            if (entityId.type == ProjectEntityType.BRANCH) {
                structureService.findBranchByID(ID.of(entityId.id))?.let { branch ->
                    processor(branch.asSearchDocument(property.branch))
                }
            }
        }
    }

    /**
     * The Git branch of a branch has been set or changed.
     */
    fun onGitBranchChanged(branch: Branch, property: GitBranchConfigurationProperty) {
        searchDocumentService.index(branch.asSearchDocument(property.branch))
    }

    /**
     * The Git branch of a branch has been removed.
     */
    fun onGitBranchDeleted(branch: Branch) {
        searchDocumentService.delete(SEARCH_RESULT_TYPE, branch.id.toString())
    }

    override fun onEvent(event: Event) {
        when (event.eventType) {
            // The enabling and disabling events carry the branch as it was before the change
            EventFactory.UPDATE_BRANCH,
            EventFactory.ENABLE_BRANCH,
            EventFactory.DISABLE_BRANCH -> structureService.findBranchByID(
                event.getEntity<Branch>(ProjectEntityType.BRANCH).id
            )?.let { reindex(it) }

            EventFactory.UPDATE_PROJECT -> {
                val project = event.getEntity<Project>(ProjectEntityType.PROJECT)
                structureService.getBranchesForProject(project.id).forEach { reindex(it) }
            }
        }
    }

    private fun reindex(branch: Branch) {
        propertyService.getPropertyValue(branch, GitBranchConfigurationPropertyType::class.java)?.let { property ->
            onGitBranchChanged(branch, property)
        }
    }

    private fun Branch.asSearchDocument(gitBranch: String) = SearchDocument(
        type = SEARCH_RESULT_TYPE,
        key = id.toString(),
        projectId = project.id(),
        entity = ProjectEntityID(this),
        title = gitBranch,
        identifiers = listOf(gitBranch),
        text = null,
        data = mapOf(
            SearchResult.SEARCH_RESULT_BRANCH to searchDocumentData(),
            SEARCH_RESULT_GIT_BRANCH to gitBranch,
        ).asJson(),
        updatedAt = signature.time,
    )

    companion object {
        const val SEARCH_RESULT_TYPE = "git-branch"
        const val SEARCH_RESULT_GIT_BRANCH = "gitBranch"
    }
}
