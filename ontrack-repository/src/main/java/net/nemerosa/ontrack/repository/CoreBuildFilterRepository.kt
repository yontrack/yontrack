package net.nemerosa.ontrack.repository

import net.nemerosa.ontrack.model.pagination.PaginatedList
import net.nemerosa.ontrack.model.structure.*
import java.time.LocalDateTime
import java.util.*

/**
 * A bound of a search in build order: a build, or, once it has been purged, a time on the creation
 * of the builds.
 */
sealed interface BuildBound {
    data class ById(val buildId: Int) : BuildBound
    data class ByTime(val time: LocalDateTime) : BuildBound
}

/**
 * Standard filter query.
 */
interface CoreBuildFilterRepository {

    /**
     * Performs a standard filter at project level.
     */
    fun projectSearch(
        project: Project,
        form: BuildSearchForm,
        helper: CoreBuildFilterRepositoryHelper,
    ): List<Build>

    fun standardFilter(
        branch: Branch,
        data: StandardBuildFilterData,
        displayNameProperty: BuildDisplayNameProperty?,
        propertyTypeAccessor: (String) -> PropertyType<*>
    ): List<Build>

    fun standardFilterPagination(
        branch: Branch,
        data: StandardBuildFilterData,
        offset: Int,
        size: Int,
        displayNameProperty: BuildDisplayNameProperty?,
        propertyTypeAccessor: (String) -> PropertyType<*>,
    ): PaginatedList<Build>

    /**
     * The first build of a branch, in build order, which matches the criteria of a filter testing a
     * single build, between two bounds. Unlike [standardFilter], which selects from the newest build
     * backwards, this finds the first match after a given build.
     *
     * The `count` of the filter is ignored, since a limit would lose the build looked for, and its
     * `since*` fields are left out, since their result moves with the head of the branch.
     *
     * @param from Inclusive lower bound
     * @param until Exclusive upper bound, if any
     * @return The build, `null` when none matches between the bounds
     * @throws CoreBuildFilterInvalidException When the filter is not valid: a stamp which does not
     * exist on the branch, an invalid regular expression...
     */
    fun firstMatchingBuild(
        branch: Branch,
        data: StandardBuildFilterData,
        from: BuildBound,
        until: BuildBound?,
        displayNameProperty: BuildDisplayNameProperty?,
        propertyTypeAccessor: (String) -> PropertyType<*>,
    ): Build?

    fun nameFilter(
        branch: Branch,
        fromBuild: String?,
        toBuild: String?,
        withPromotionLevel: String?,
        count: Int
    ): List<Build>

    fun lastBuild(branch: Branch, sinceBuild: String?, withPromotionLevel: String?): Optional<Build>

    fun between(branch: Branch, from: String?, to: String?): List<Build>

}
