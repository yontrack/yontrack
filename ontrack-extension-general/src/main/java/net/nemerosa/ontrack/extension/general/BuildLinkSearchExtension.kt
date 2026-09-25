package net.nemerosa.ontrack.extension.general

import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.job.Schedule
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.events.BuildLinkListener
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.events.EventListener
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.*
import org.springframework.stereotype.Component

/**
 * Search documents for the build links, to find the builds which use a given build: one document
 * per qualified link, owned by its source build, found on `targetProject:targetBuild` — and on
 * `targetProject:displayName` when the target has a display name, as an extra identifier of the
 * same document. The time of the source build is its recency.
 *
 * The documents are written in the transaction of the change: the creation and deletion of a link,
 * the update of its source or target build, and the change of display name of its target. The
 * documents of a deleted source build or branch are deleted by the search service, and those of a
 * deleted target build here. The deletion of the branch or project of a target — which would
 * need to look at all its builds — is left to the reconciliation job, which runs every day.
 */
@Component
class BuildLinkSearchExtension(
    extensionFeature: GeneralExtensionFeature,
    private val structureService: StructureService,
    private val buildDisplayNameService: BuildDisplayNameService,
    private val searchDocumentService: SearchDocumentService,
    private val securityService: SecurityService,
) : AbstractExtension(extensionFeature), SearchDocumentIndexer, BuildLinkListener, EventListener {

    override val indexerName: String = "Build links"

    /**
     * Daily reconciliation, for the deletions of the branch or project of a target
     */
    override val indexerSchedule: Schedule = Schedule.EVERY_DAY

    override val searchResultType = SearchResultType(
        feature = extensionFeature.featureDescription,
        id = SEARCH_RESULT_TYPE,
        name = "Linked Build",
        description = "Reference to a linked project and build, using format project:[build] where the target build is optional",
        order = SearchResultType.ORDER_PROPERTIES + 30,
    )

    override fun indexAll(processor: (SearchDocument) -> Unit) {
        structureService.forEachBuildLink { from, to, qualifier ->
            processor(asSearchDocument(from, to, qualifier))
        }
    }

    override fun onBuildLinkAdded(from: Build, to: Build, qualifier: String) {
        searchDocumentService.index(asSearchDocument(from, to, qualifier))
    }

    override fun onBuildLinkDeleted(from: Build, to: Build, qualifier: String) {
        searchDocumentService.delete(SEARCH_RESULT_TYPE, key(from, to, qualifier))
    }

    override fun onEvent(event: Event) {
        when (event.eventType) {
            EventFactory.UPDATE_BUILD -> {
                val build = event.getEntity<Build>(ProjectEntityType.BUILD)
                linksFrom(build).forEach { link ->
                    searchDocumentService.index(asSearchDocument(build, link.build, link.qualifier))
                }
                linksTo(build).forEach { link ->
                    searchDocumentService.index(asSearchDocument(link.build, build, link.qualifier))
                }
            }

            EventFactory.UPDATE_BUILD_DISPLAY_NAME -> {
                val build = event.getEntity<Build>(ProjectEntityType.BUILD)
                linksTo(build).forEach { link ->
                    searchDocumentService.index(asSearchDocument(link.build, build, link.qualifier))
                }
            }

            // Posted before the build is deleted, in the transaction of its deletion
            EventFactory.DELETE_BUILD -> {
                val build = structureService.findBuildByID(ID.of(event.getIntValue("BUILD_ID")))
                if (build != null) {
                    linksTo(build).forEach { link ->
                        searchDocumentService.delete(SEARCH_RESULT_TYPE, key(link.build, build, link.qualifier))
                    }
                }
            }
        }
    }

    /**
     * All the links from a build
     */
    private fun linksFrom(build: Build): List<BuildLink> = securityService.asAdmin {
        structureService.getQualifiedBuildsUsedBy(build, offset = 0, size = ALL).pageItems
    }

    /**
     * All the links to a build
     */
    private fun linksTo(build: Build): List<BuildLink> = securityService.asAdmin {
        structureService.getQualifiedBuildsUsing(build, offset = 0, size = ALL).pageItems
    }

    private fun key(from: Build, to: Build, qualifier: String) = "${from.id}::${to.id}::$qualifier"

    private fun asSearchDocument(from: Build, to: Build, qualifier: String): SearchDocument {
        val targetProject = to.project.name
        val displayName = buildDisplayNameService.getFirstBuildDisplayName(to)?.takeIf { it.isNotBlank() }
        return SearchDocument(
            type = SEARCH_RESULT_TYPE,
            key = key(from, to, qualifier),
            projectId = from.project.id(),
            entity = ProjectEntityID(from),
            title = "$targetProject:${displayName ?: to.name}",
            identifiers = listOfNotNull(
                "$targetProject:${to.name}",
                displayName?.let { "$targetProject:$it" },
            ).distinct(),
            text = null,
            data = mapOf(
                "sourceBuild" to from.searchDocumentData(),
                "targetBuild" to to.searchDocumentData(),
                "qualifier" to qualifier,
            ).asJson(),
            updatedAt = from.signature.time,
        )
    }

    companion object {
        const val SEARCH_RESULT_TYPE = "build-link"

        /**
         * Page size to get all the links of a build
         */
        private const val ALL = Int.MAX_VALUE / 2
    }
}
