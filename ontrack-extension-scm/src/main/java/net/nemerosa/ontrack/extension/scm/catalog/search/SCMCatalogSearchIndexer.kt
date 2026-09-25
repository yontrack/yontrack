package net.nemerosa.ontrack.extension.scm.catalog.search

import net.nemerosa.ontrack.extension.scm.SCMExtensionConfigProperties
import net.nemerosa.ontrack.extension.scm.SCMExtensionFeature
import net.nemerosa.ontrack.extension.scm.catalog.CatalogLinkService
import net.nemerosa.ontrack.extension.scm.catalog.SCMCatalog
import net.nemerosa.ontrack.extension.scm.catalog.SCMCatalogAccessFunction
import net.nemerosa.ontrack.extension.scm.catalog.SCMCatalogEntry
import net.nemerosa.ontrack.job.Schedule
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.security.GlobalFunction
import net.nemerosa.ontrack.model.structure.*
import org.springframework.stereotype.Component

/**
 * Search documents for the entries of the SCM catalog: one per entry, found by its repository.
 *
 * The entries belong to no project - a catalog entry may be linked to a project or not - and only
 * the users granted the [SCMCatalogAccessFunction], which the SCM catalog itself requires, see
 * them.
 *
 * The catalog and its links to the projects are collected daily, outside any transaction: the
 * documents are rebuilt daily as well.
 */
@Component
class SCMCatalogSearchIndexer(
    extensionFeature: SCMExtensionFeature,
    private val scmCatalog: SCMCatalog,
    private val catalogLinkService: CatalogLinkService,
    private val scmExtensionConfigProperties: SCMExtensionConfigProperties,
) : SearchDocumentIndexer {

    override val searchResultType = SearchResultType(
        feature = extensionFeature.featureDescription,
        id = SCM_CATALOG_SEARCH_RESULT_TYPE,
        name = "SCM Catalog",
        description = "Indexed SCM repository, which might be associated or not with an Ontrack project",
        order = SearchResultType.ORDER_PROPERTIES + 100,
    )

    override val indexerName: String = "SCM Catalog"

    override val globalFunction: Class<out GlobalFunction> = SCMCatalogAccessFunction::class.java

    override val indexerSchedule: Schedule =
        if (scmExtensionConfigProperties.catalog.enabled) {
            Schedule.EVERY_DAY
        } else {
            Schedule.NONE
        }

    /**
     * All the entries of the catalog, none when the catalog is disabled: its documents are then
     * deleted by the rebuild.
     */
    override fun indexAll(processor: (SearchDocument) -> Unit) {
        if (!scmExtensionConfigProperties.catalog.enabled) {
            return
        }
        scmCatalog.catalogEntries.forEach { entry ->
            processor(entry.asSearchDocument(catalogLinkService.getLinkedProject(entry)))
        }
    }

    private fun SCMCatalogEntry.asSearchDocument(project: Project?): SearchDocument {
        val title: String
        val description: String
        if (project != null) {
            title = "${project.name} ($repository)"
            description = "Project ${project.name} associated with SCM $repository ($scm @ $config)"
        } else {
            title = repository
            description = "SCM $repository ($scm @ $config) not associated with any project"
        }
        return SearchDocument(
            type = SCM_CATALOG_SEARCH_RESULT_TYPE,
            key = key,
            projectId = null,
            entity = null,
            title = title,
            identifiers = listOf(repository, repository.substringAfterLast('/')).distinct(),
            text = description,
            data = mapOf(
                SEARCH_RESULT_SCM_CATALOG_ENTRY to this,
                SearchResult.SEARCH_RESULT_PROJECT to project?.searchDocumentData(),
            ).asJson(),
            updatedAt = lastActivity ?: timestamp,
        )
    }

    companion object {
        const val SCM_CATALOG_SEARCH_RESULT_TYPE = "scm-catalog"
        const val SEARCH_RESULT_SCM_CATALOG_ENTRY = "scmCatalogEntry"
    }
}
