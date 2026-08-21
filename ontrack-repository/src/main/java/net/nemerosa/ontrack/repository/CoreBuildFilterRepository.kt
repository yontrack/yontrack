package net.nemerosa.ontrack.repository

import net.nemerosa.ontrack.model.pagination.PaginatedList
import net.nemerosa.ontrack.model.structure.*
import java.util.*

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
