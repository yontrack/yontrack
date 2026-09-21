package net.nemerosa.ontrack.boot.ui;

import jakarta.validation.Valid;
import net.nemerosa.ontrack.model.Ack;
import net.nemerosa.ontrack.model.buildfilter.BuildFilterInput;
import net.nemerosa.ontrack.model.buildfilter.BuildFilterResource;
import net.nemerosa.ontrack.model.buildfilter.BuildFilterService;
import net.nemerosa.ontrack.model.structure.ID;
import org.apache.commons.lang3.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Management of build filters for the branches.
 */
@RestController
@RequestMapping("/rest")
public class BuildFilterController {

    private final BuildFilterService buildFilterService;

    @Autowired
    public BuildFilterController(BuildFilterService buildFilterService) {
        this.buildFilterService = buildFilterService;
    }

    /**
     * Returns the list of existing filters for this branch and the current user.
     *
     * @param branchId ID of the branch to get the filter for.
     */
    @RequestMapping(value = "branches/{branchId}/filters", method = RequestMethod.GET)
    public List<BuildFilterResource<?>> buildFilters(@PathVariable ID branchId) {
        return buildFilterService.getBuildFilters(branchId).stream().toList();
    }

    /**
     * Creating a filter
     */
    @RequestMapping(value = "branches/{branchId}/filters", method = RequestMethod.POST)
    public ResponseEntity<Ack> createFilter(@PathVariable ID branchId, @RequestBody @Valid BuildFilterInput input) {
        return ResponseEntity.ok(
                buildFilterService.saveFilter(branchId, input.isShared(), input.getName(), input.getType(), input.getData())
        );
    }

    /**
     * Saving a filter
     */
    @RequestMapping(value = "branches/{branchId}/filters/{name}", method = RequestMethod.PUT)
    public ResponseEntity<Ack> saveFilter(@PathVariable ID branchId, @PathVariable String name, @RequestBody @Valid BuildFilterInput input) {
        if (!Strings.CS.equals(name, input.getName())) {
            throw new IllegalArgumentException("The input name must be identical to the one in the URI.");
        }
        return ResponseEntity.ok(
                buildFilterService.saveFilter(branchId, input.isShared(), name, input.getType(), input.getData())
        );
    }

    /**
     * Deletes a filter
     */
    @RequestMapping(value = "branches/{branchId}/filters/{name}", method = RequestMethod.DELETE)
    public ResponseEntity<Ack> deleteFilter(@PathVariable ID branchId, @PathVariable String name) {
        return ResponseEntity.ok(
                buildFilterService.deleteFilter(branchId, name)
        );
    }
}
