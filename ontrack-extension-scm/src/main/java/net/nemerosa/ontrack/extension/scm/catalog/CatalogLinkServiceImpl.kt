package net.nemerosa.ontrack.extension.scm.catalog

import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.format
import net.nemerosa.ontrack.model.structure.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class CatalogLinkServiceImpl(
        private val scmCatalog: SCMCatalog,
        private val scmCatalogProviders: List<SCMCatalogProvider>,
        private val structureService: StructureService,
        private val entityStore: EntityStore,
) : CatalogLinkService {

    private val logger: Logger = LoggerFactory.getLogger(CatalogLinkService::class.java)

    override fun computeCatalogLinks() {
        val projects = structureService.projectList
        val providers = scmCatalogProviders.associateBy { it.id }
        val catalogEntries = scmCatalog.catalogEntries
        val allCatalogKeys = catalogEntries.map { it.key }.toSet()
        val leftOverKeys = catalogEntries.map { it.key }.toMutableSet()
        catalogEntries.forEach {
            if (computeCatalogLink(it, projects, providers)) {
                leftOverKeys.remove(it.key)
            }
        }
        // Cleanup
        projects.forEach { project ->
            val value = getLinkedKey(project)
            if (!value.isNullOrBlank() && (value in leftOverKeys || value !in allCatalogKeys)) {
                logger.debug("Catalog entry $value --> ${project.name} is obsolete.")
                entityStore.deleteByName(project, STORE, EntityStore.DEFAULT_NAME)
            }
        }
    }

    override fun getSCMCatalogEntry(project: Project): SCMCatalogEntry? =
            getLinkedKey(project)
                    ?.run { scmCatalog.getCatalogEntry(this) }

    override fun getLinkedProject(entry: SCMCatalogEntry): Project? =
            findLinkedProjectId(entry)?.run {
                assert(type == ProjectEntityType.PROJECT)
                structureService.getProject(ID.of(id))
            }

    override fun isLinked(entry: SCMCatalogEntry): Boolean =
            findLinkedProjectId(entry) != null

    override fun isOrphan(project: Project): Boolean =
            getLinkedKey(project) == null

    private fun computeCatalogLink(
            entry: SCMCatalogEntry,
            projects: List<Project>,
            providers: Map<String, SCMCatalogProvider>
    ): Boolean {
        logger.debug("Catalog entry ${entry.key}")
        // Gets a provider for this entry
        val provider = providers[entry.scm]
        // For all projects
        if (provider != null) {
            projects.forEach { project ->
                // Is that a match?
                if (provider.matches(entry, project)) {
                    logger.debug("Catalog entry ${entry.key} --> ${project.name}")
                    // Stores the link
                    storeLink(project, entry)
                    // OK
                    return true
                }
            }
        }
        // Not linked
        return false
    }

    override fun storeLink(project: Project, entry: SCMCatalogEntry) {
        entityStore.store(
            project,
            STORE,
            EntityStore.DEFAULT_NAME,
            entry.key
        )
    }

    private fun getLinkedKey(project: Project): String? =
            entityStore.findByName(project, STORE, EntityStore.DEFAULT_NAME, String::class)

    private fun findLinkedProjectId(entry: SCMCatalogEntry): ProjectEntityID? =
            entityStore.findEntities(
                    type = ProjectEntityType.PROJECT,
                    store = STORE,
                    filter = EntityStoreFilter(
                            jsonFilter = "DATA = CAST(:key AS JSONB)",
                            jsonFilterCriterias = mapOf("key" to entry.key.asJson().format()),
                    ),
            ).firstOrNull()

    companion object {
        private val STORE: String = CatalogLinkService::class.java.name
    }

}