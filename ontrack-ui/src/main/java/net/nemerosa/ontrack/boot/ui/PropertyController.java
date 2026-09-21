package net.nemerosa.ontrack.boot.ui;

import tools.jackson.databind.JsonNode;
import net.nemerosa.ontrack.model.Ack;
import net.nemerosa.ontrack.model.structure.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * UI end point for the management of properties.
 */
@RestController
@RequestMapping("/rest/properties")
public class PropertyController extends AbstractProjectEntityController {

    private final PropertyService propertyService;

    @Autowired
    public PropertyController(PropertyService propertyService, StructureService structureService) {
        super(structureService);
        this.propertyService = propertyService;
    }

    /**
     * Gets the list of properties for a given entity and for the current user.
     *
     * @param entityType Entity type
     * @param id         Entity ID
     * @return List of properties
     */
    @GetMapping("{entityType}/{id}")
    public List<Property<?>> getProperties(@PathVariable ProjectEntityType entityType, @PathVariable ID id) {
        ProjectEntity entity = getEntity(entityType, id);
        return propertyService.getProperties(entity);
    }

    /**
     * Gets the value for a given property for an entity. If the property is not set, a non-null
     * {@link net.nemerosa.ontrack.model.structure.Property} is returned but is marked as
     * {@linkplain net.nemerosa.ontrack.model.structure.Property#isEmpty() empty}.
     * If the property is not opened for viewing, the call could be rejected with an
     * authorization exception.
     *
     * @param entityType       Type of the entity to get the edition form for
     * @param id               ID of the entity to get the edition form for
     * @param propertyTypeName Fully qualified name of the property to get the form for
     * @return A response that defines the property
     */
    @RequestMapping(value = "{entityType}/{id}/{propertyTypeName}/view", method = RequestMethod.GET)
    public Property<?> getPropertyValue(@PathVariable ProjectEntityType entityType, @PathVariable ID id, @PathVariable String propertyTypeName) {
        return propertyService.getProperty(getEntity(entityType, id), propertyTypeName);
    }

    /**
     * Edits the value of a property.
     *
     * @param entityType       Type of the entity to edit
     * @param id               ID of the entity to edit
     * @param propertyTypeName Fully qualified name of the property to edit
     * @param data             Raw JSON data for the property value
     */
    @RequestMapping(value = "{entityType}/{id}/{propertyTypeName}/edit", method = RequestMethod.PUT)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Ack editProperty(@PathVariable ProjectEntityType entityType, @PathVariable ID id, @PathVariable String propertyTypeName, @RequestBody JsonNode data) {
        return propertyService.editProperty(
                getEntity(entityType, id),
                propertyTypeName,
                data
        );
    }

    /**
     * Deletes the value of a property.
     *
     * @param entityType       Type of the entity to edit
     * @param id               ID of the entity to edit
     * @param propertyTypeName Fully qualified name of the property to delete
     */
    @RequestMapping(value = "{entityType}/{id}/{propertyTypeName}/edit", method = RequestMethod.DELETE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Ack deleteProperty(@PathVariable ProjectEntityType entityType, @PathVariable ID id, @PathVariable String propertyTypeName) {
        return propertyService.deleteProperty(
                getEntity(entityType, id),
                propertyTypeName
        );
    }

}
