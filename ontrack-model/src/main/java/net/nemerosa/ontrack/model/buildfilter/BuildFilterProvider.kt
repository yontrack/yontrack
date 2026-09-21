package net.nemerosa.ontrack.model.buildfilter

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.model.pagination.PaginatedList
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.ID

interface BuildFilterProvider<T> {

    /**
     * Type
     */
    val type: String

    /**
     * Display name
     */
    val name: String

    /**
     * If this method returns `true`, there is no need to configure the filter.
     */
    val isPredefined: Boolean

    /**
     * Gets the form for a new filter on the given branch
     */
    fun newFilterForm(branchId: ID): BuildFilterForm

    /**
     * Gets the form for a pre-filled filter
     *
     * @param branchId Branch to filter
     * @param data     Filter data
     * @return Form
     */
    fun getFilterForm(branchId: ID, data: T): BuildFilterForm

    /**
     * Performs the filtering
     */
    fun filterBranchBuilds(branch: Branch, data: T?): List<Build>

    /**
     * Performs the filtering with some pagination
     */
    fun filterBranchBuildsWithPagination(branch: Branch, data: T?, offset: Int, size: Int): PaginatedList<Build> {
        return PaginatedList.create(
            filterBranchBuilds(branch, data),
            offset,
            size
        )
    }

    /**
     * Parses the filter data, provided as JSON, into an actual filter data object, when possible.
     *
     * @param data Filter data, as JSON
     * @return Filter data object, or null when not possible to parse
     */
    fun parse(data: JsonNode): T?

    fun withData(data: T?) = BuildFilterProviderData(
        this,
        data
    )

    /**
     * Validates the data for this type.
     *
     * @param branch Branch used for the validation
     * @param data   Filter data
     * @return Error message or `null` if OK
     */
    fun validateData(branch: Branch, data: T?): String? {
        return null
    }
}