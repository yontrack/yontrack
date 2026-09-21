package net.nemerosa.ontrack.model.buildfilter;

import tools.jackson.databind.JsonNode;
import net.nemerosa.ontrack.model.Ack;
import net.nemerosa.ontrack.model.exceptions.BuildFilterNotFoundException;
import net.nemerosa.ontrack.model.exceptions.BuildFilterNotLoggedException;
import net.nemerosa.ontrack.model.structure.Branch;
import net.nemerosa.ontrack.model.structure.ID;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Management of build filters.
 */
public interface BuildFilterService {

    /**
     * Gets the default filter to use when none is defined.
     */
    BuildFilterProviderData<?> defaultFilterProviderData();

    /**
     * Gets the list of forms to create new filters
     *
     * @param branchId Branch to get the forms for
     * @return List of forms
     */
    Collection<BuildFilterForm> getBuildFilterForms(ID branchId);

    /**
     * Builds a last promotion filter
     */
    BuildFilterProviderData<?> lastPromotedBuildsFilterData();

    /**
     * Builds a standard build filter
     */
    StandardFilterProviderDataBuilder standardFilterProviderData(int count);

    /**
     * Builds a standard build filter given its parameters as a JSON node
     */
    BuildFilterProviderData<?> standardFilterProviderData(JsonNode node);

    /**
     * Gets the list of all existing filters.
     *
     * @param branchId Branch to get the filters for
     * @return List of filters
     */
    Collection<BuildFilterResource<?>> getBuildFilters(ID branchId);

    /**
     * Gets a build filter provider for the given type.
     *
     * @param filterType Qualified type for the filter
     * @param parameters Parameters for the filter
     * @return Actual filter to use
     * @throws BuildFilterProviderNotFoundException    If the type cannot be resolved
     * @throws BuildFilterProviderDataParsingException If the parameters cannot be parsed
     */
    <T> BuildFilterProviderData<T> getBuildFilterProviderData(String filterType, JsonNode parameters);

    /**
     * Gets a build filter provider for the given type.
     *
     * @param filterType Qualified type for the filter
     * @param parameters Parameters for the filter
     * @return Actual filter to use
     * @throws BuildFilterProviderNotFoundException    If the type cannot be resolved
     * @throws BuildFilterProviderDataParsingException If the parameters cannot be parsed
     */
    <T> BuildFilterProviderData<T> getBuildFilterProviderData(String filterType, T parameters);

    /**
     * Validates the data for the given type.
     *
     * @param branch     Branch used for the validation
     * @param filterType Qualified type for the filter
     * @param parameters Parameters for the filter
     * @return Error message or <code>null</code> if OK
     */
    @Nullable
    String validateBuildFilterProviderData(Branch branch, String filterType, JsonNode parameters);

    /**
     * Gets the form to edit an existing filter.
     *
     * @param branchId Branch to get the form on
     * @param name     name of the filter on the branch
     * @return An edition form
     * @throws BuildFilterNotFoundException  If the filter is not defined
     * @throws BuildFilterNotLoggedException If the user is not logged
     */
    BuildFilterForm getEditionForm(ID branchId, String name) throws BuildFilterNotFoundException, BuildFilterNotLoggedException;

    /**
     * Saves a filter for a branch. This method does nothing if the user is not logged,
     * or if the filter type is not found, or if the parameters are not valid.
     *
     * @param branchId   Branch to save the filter for
     * @param shared     If the filter must be shared in the branch (needs specific authorisation)
     * @param name       Name of the filter
     * @param type       Type of the filter
     * @param parameters Parameters for the filter
     */
    Ack saveFilter(ID branchId, boolean shared, String name, String type, JsonNode parameters);

    /**
     * Deletes a filter for a branch. This method does nothing if the user is not logged,
     * or if the filter is not found.
     *
     * @param branchId Branch to save the filter for
     * @param name     Name of the filter
     */
    Ack deleteFilter(ID branchId, String name);

    /**
     * Copies all the filters from one branch to another
     */
    void copyToBranch(ID sourceBranchId, ID targetBranchId);
}
