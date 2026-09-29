package net.nemerosa.ontrack.extension.scm.service

import net.nemerosa.ontrack.extension.scm.model.SCMFileChangeFilter
import net.nemerosa.ontrack.extension.scm.model.SCMFileChangeFilters
import net.nemerosa.ontrack.model.security.ProjectConfig
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.EntityStore
import net.nemerosa.ontrack.model.structure.Project
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class SCMFileChangeFilterServiceImpl(
    private val entityStore: EntityStore,
    private val securityService: SecurityService,
) : SCMFileChangeFilterService {

    override fun loadSCMFileChangeFilters(project: Project): SCMFileChangeFilters =
        entityStore.findByName(
            project,
            STORE,
            EntityStore.DEFAULT_NAME,
            SCMFileChangeFilters::class
        ) ?: SCMFileChangeFilters.create()

    override fun save(project: Project, filter: SCMFileChangeFilter) {
        securityService.checkProjectFunction(project, ProjectConfig::class.java)
        val config = loadSCMFileChangeFilters(project).run {
            save(filter)
        }
        // Saves the store back
        entityStore.store(project, STORE, EntityStore.DEFAULT_NAME, config)
    }

    override fun delete(project: Project, name: String) {
        securityService.checkProjectFunction(project, ProjectConfig::class.java)
        entityStore.findByName(project, STORE, EntityStore.DEFAULT_NAME, SCMFileChangeFilters::class)
            ?.let { filters ->
                entityStore.store(project, STORE, EntityStore.DEFAULT_NAME, filters.remove(name))
            }
    }

    companion object {
        private val STORE: String = SCMFileChangeFilters::class.java.name
    }
}