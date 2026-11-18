package net.nemerosa.ontrack.extension.notifications.core

import net.nemerosa.ontrack.model.pagination.PaginatedList
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.BuildLink
import net.nemerosa.ontrack.model.structure.StructureService
import org.springframework.stereotype.Component

/**
 * Build to promote, with the qualifiers of the links which selected it.
 */
data class YontrackDependenciesPromotionTarget(
    val build: Build,
    val qualifiers: List<String>,
)

/**
 * Selects the builds to promote among the direct dependencies of a build.
 */
@Component
class YontrackDependenciesPromotionTargetSelector(
    private val structureService: StructureService,
) {

    /**
     * @param source Build whose direct links are read. It is never a target.
     * @param project Regular expression matching the whole name of the project of the linked build. Blank or `null`
     * means no filter.
     * @param qualifier Regular expression matching the whole qualifier of the link. `null` means no filter, and an
     * empty expression selects the unqualified links only.
     */
    fun selectTargets(
        source: Build,
        project: String?,
        qualifier: String?,
    ): List<YontrackDependenciesPromotionTarget> {
        val projectRegex = project?.takeIf { it.isNotBlank() }?.toRegex()
        val qualifierRegex = qualifier?.toRegex()
        return readLinks(source)
            .filter { link ->
                link.build.id() != source.id() &&
                        (projectRegex == null || projectRegex.matches(link.build.project.name)) &&
                        (qualifierRegex == null || qualifierRegex.matches(link.qualifier))
            }
            .groupBy { link -> link.build.id() }
            .values
            .map { links ->
                YontrackDependenciesPromotionTarget(
                    build = links.first().build,
                    qualifiers = links.map { it.qualifier }.distinct(),
                )
            }
    }

    private fun readLinks(source: Build): List<BuildLink> =
        generateSequence(readPage(source, 0)) { page ->
            page.pageInfo.nextPage?.let { readPage(source, it.offset) }
        }.flatMap { it.pageItems }.toList()

    private fun readPage(source: Build, offset: Int): PaginatedList<BuildLink> =
        structureService.getBuildDependencyLinks(build = source, offset = offset, size = PAGE_SIZE)

    private companion object {
        const val PAGE_SIZE = 100
    }
}
