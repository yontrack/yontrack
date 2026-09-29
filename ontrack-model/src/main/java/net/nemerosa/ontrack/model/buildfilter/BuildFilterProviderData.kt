package net.nemerosa.ontrack.model.buildfilter

import net.nemerosa.ontrack.model.pagination.PaginatedList
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Build

/**
 * [BuildFilterProvider] with its associated data.
 *
 * @param <T> Type of data for the filter provider
 */
class BuildFilterProviderData<T>(
    /**
     * Provider service
     */
    val provider: BuildFilterProvider<T>,
    /**
     * Data
     */
    val data: T?,
) {

    /**
     * Launches the filter
     */
    fun filterBranchBuilds(branch: Branch): List<Build> {
        return provider.filterBranchBuilds(branch, data)
    }

    /**
     * Filter with pagination
     */
    fun filterBranchBuildsWithPagination(branch: Branch, offset: Int, size: Int): PaginatedList<Build> {
        return provider.filterBranchBuildsWithPagination(branch, data, offset, size)
    }
}