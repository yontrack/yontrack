package net.nemerosa.ontrack.extension.environments.service

import net.nemerosa.ontrack.extension.environments.*
import net.nemerosa.ontrack.extension.environments.security.EnvironmentList
import net.nemerosa.ontrack.extension.environments.security.SlotView
import net.nemerosa.ontrack.extension.environments.storage.EnvironmentMatrixRepository
import net.nemerosa.ontrack.extension.environments.storage.SlotRepository
import net.nemerosa.ontrack.model.security.SecurityService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * The matrix, in four queries and no loop over cells.
 *
 * In order: the number of matching projects, the ids of one page of them, the slots of that page,
 * and the slots themselves. Everything after that is arithmetic on what is already in memory - and
 * the operational readings of the cells (`blocked`, `behind`, what is deployed, what is on its way)
 * are not read here at all: they are fields of `Slot`, resolved by batch loaders when and only when
 * the caller asks for them. A matrix query that selects nothing but project and environment names
 * therefore touches no deployment table.
 *
 * **What `totalProjects` counts.** The filters run in SQL, which does not know the caller's project
 * permissions, so the total counts what the *filter* matches while the rows are then narrowed to
 * what the caller may see. A user who cannot view some of the matching projects sees a total larger
 * than the rows they get. Narrowing it exactly would mean pushing the ACL into SQL, which Yontrack
 * does nowhere; the alternative - counting by reading every matching project - is the fan-out this
 * whole service exists to avoid.
 */
@Service
@Transactional(readOnly = true)
class EnvironmentMatrixServiceImpl(
    private val environmentMatrixRepository: EnvironmentMatrixRepository,
    private val slotRepository: SlotRepository,
    private val securityService: SecurityService,
) : EnvironmentMatrixService {

    override fun matrix(
        filter: EnvironmentMatrixFilter,
        offset: Int,
        size: Int,
    ): EnvironmentMatrix {
        val accountId = securityService.currentUser?.account?.id()
        val hasFavourites = environmentMatrixRepository.hasFavourites(accountId)

        // The same gate as every other slot reading: without it there is no matrix to show, and
        // answering "no projects" is the truthful answer rather than an error.
        if (!securityService.isGlobalFunctionGranted(EnvironmentList::class.java)) {
            return EnvironmentMatrix(
                environments = emptyList(),
                projects = emptyList(),
                totalProjects = 0,
                offset = offset,
                size = size,
                hasFavourites = hasFavourites,
            )
        }

        val totalProjects = environmentMatrixRepository.countProjects(filter, accountId)
        val projectIds = environmentMatrixRepository.findProjectIds(
            filter = filter,
            accountId = accountId,
            offset = offset,
            size = size,
        )

        val slotIds = environmentMatrixRepository.findSlotIdsByProjects(
            projectIds = projectIds,
            tags = filter.onTags,
            environmentNames = filter.onEnvironments,
            activity = filter.onActivity,
        )
        val slots = slotRepository.findSlotsByIds(slotIds)
            .filter { securityService.isSlotAccessible<SlotView>(it) }

        val slotsByProject = slots.groupBy { it.project.id() }

        val projects = projectIds.mapNotNull { projectId ->
            val projectSlots = slotsByProject[projectId]
            // A project whose every slot is either filtered out by the tags or invisible to the
            // caller has no row at all: an empty row would be a line of blank cells under a name.
            if (projectSlots.isNullOrEmpty()) {
                null
            } else {
                EnvironmentMatrixProject(
                    project = projectSlots.first().project,
                    rows = rows(projectSlots),
                )
            }
        }

        return EnvironmentMatrix(
            environments = columns(slots),
            projects = projects,
            totalProjects = totalProjects,
            offset = offset,
            size = size,
            hasFavourites = hasFavourites,
        )
    }

    /**
     * One row per qualifier, the default one first.
     *
     * The empty qualifier leads because it *is* the project row on screen: the others are drawn
     * nested under it, and a nesting whose first child is the parent reads wrong.
     */
    private fun rows(projectSlots: List<Slot>): List<EnvironmentMatrixRow> =
        projectSlots
            .groupBy { it.qualifier }
            .map { (qualifier, qualifierSlots) ->
                EnvironmentMatrixRow(
                    qualifier = qualifier,
                    slots = qualifierSlots.sortedBy { it.environment.order },
                )
            }
            .sortedWith(compareBy({ it.qualifier.isNotEmpty() }, { it.qualifier }))

    /**
     * The columns: the environments actually holding a slot on this page, by order.
     *
     * Derived from the slots rather than read from the environment table, which is what makes
     * "columns with no slot among the visible rows are hidden" true by construction - including
     * under a tag filter, which has already removed the slots of the environments it excludes.
     */
    private fun columns(slots: List<Slot>): List<Environment> =
        slots.map { it.environment }
            .distinctBy { it.id }
            .sortedBy { it.order }

}
