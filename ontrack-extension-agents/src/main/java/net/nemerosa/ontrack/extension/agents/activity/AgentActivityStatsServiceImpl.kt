package net.nemerosa.ontrack.extension.agents.activity

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.agents.license.AgentsLicense
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.events.EventFilter
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.StructureService
import net.nemerosa.ontrack.repository.EventRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class AgentActivityStatsServiceImpl(
    private val agentsLicense: AgentsLicense,
    private val structureService: StructureService,
    private val eventRepository: EventRepository,
    private val assistedBuildsRepository: AssistedBuildsRepository,
) : AgentActivityStatsService {

    override fun getAgentActivityStats(filter: AgentActivityStatsFilter): AgentActivityStats {
        agentsLicense.checkAgents()
        val window = filter.window.coerceIn(1, AgentActivityStatsFilter.MAX_WINDOW)
        val from = Time.now.minusDays(window.toLong())
        // The projects the user can see
        val visibleProjects = structureService.projectList
        // ... narrowed by the filter
        val projects = narrowedProjects(visibleProjects, filter)
        // Actions of the agents, by type
        val counts = eventRepository.countAgentEventsByType(
            filter = EventFilter(
                from = from,
                eventTypes = BUILD_EVENT_TYPES + PROMOTION_EVENT_TYPES + DEPLOYMENT_EVENT_TYPES,
                actor = EventFilter.ACTOR_AGENT,
            ),
            projects = projects,
            // An event linking a project the user cannot see is not counted
            visibleProjects = visibleProjects.map { it.id() },
        )
        // Assisted builds
        val assisted = assistedBuildsRepository.countAssistedBuilds(from, projects)
        return AgentActivityStats(
            window = window,
            from = from,
            builds = counts.sumOf(BUILD_EVENT_TYPES),
            promotions = counts.sumOf(PROMOTION_EVENT_TYPES),
            deployments = counts.sumOf(DEPLOYMENT_EVENT_TYPES),
            assistedBuilds = assisted.assisted,
            knownBuilds = assisted.known,
            unknownBuilds = assisted.unknown,
        )
    }

    private fun Map<String, Int>.sumOf(types: List<String>) = types.sumOf { this[it] ?: 0 }

    /**
     * IDs of the visible projects carrying all the labels of the filter, and among the projects of the
     * filter if any.
     */
    private fun narrowedProjects(visibleProjects: List<Project>, filter: AgentActivityStatsFilter): List<Int> {
        val labels = filter.labels.map { it.trim() }.filter { it.isNotEmpty() }
        val names = filter.projects.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val projects = if (labels.isEmpty()) {
            visibleProjects
        } else {
            // Only the visible projects
            structureService.findProjects(null, labels)
        }
        return projects
            .filter { names.isEmpty() || it.name in names }
            .map { it.id() }
    }

    companion object {
        /**
         * Events of the builds created by agents
         */
        val BUILD_EVENT_TYPES = listOf(EventFactory.NEW_BUILD.id)

        /**
         * Events of the promotions by agents
         */
        val PROMOTION_EVENT_TYPES = listOf(EventFactory.NEW_PROMOTION_RUN.id)

        /**
         * Events of the deployments by agents: a slot pipeline started, starting its deployment, and
         * deployed - the event types of the environments extension
         */
        val DEPLOYMENT_EVENT_TYPES = listOf(
            "slot-pipeline-creation",
            "slot-pipeline-deploying",
            "slot-pipeline-deployed",
        )
    }
}
