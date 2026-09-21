package net.nemerosa.ontrack.model.structure

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.model.Ack
import net.nemerosa.ontrack.model.exceptions.PropertyTypeNotFoundException
import java.util.function.BiFunction
import java.util.function.Predicate
import kotlin.reflect.KClass

/**
 * Management of properties.
 */
interface PropertyService {

    /**
     * List of all property types
     */
    val propertyTypes: List<PropertyType<*>>

    /**
     * Gets a property type using its name
     *
     * @param propertyTypeName Fully qualified name of the property type
     * @param <T>              Type of property
     * @return Property type
     * @throws PropertyTypeNotFoundException If not found
     */
    @Throws(PropertyTypeNotFoundException::class)
    fun <T> getPropertyTypeByName(propertyTypeName: String): PropertyType<T>

    /**
     * List of property values for a given entity and for the current user.
     *
     * @param entity Entity
     * @return List of properties for this entity
     */
    fun getProperties(entity: ProjectEntity): List<Property<*>>

    /**
     * Gets the value for a given property for an entity. If the property is not set, a non-null
     * [net.nemerosa.ontrack.model.structure.Property] is returned but is marked as
     * [empty][net.nemerosa.ontrack.model.structure.Property.isEmpty].
     * If the property is not opened for viewing, the call could be rejected with an
     * authorization exception.
     *
     * @param entity           Entity to get the edition form for
     * @param propertyTypeName Fully qualified name of the property to get the property for
     * @return A response that defines the property
     */
    fun <T> getProperty(entity: ProjectEntity, propertyTypeName: String): Property<T>

    /**
     * Same than [.getProperty] but using the class of
     * the property type.
     *
     * @param entity            Entity to get the edition form for
     * @param propertyTypeClass Class of the property type to get the property for
     * @return A response that defines the property
     */
    fun <T> getProperty(entity: ProjectEntity, propertyTypeClass: Class<out PropertyType<T>>): Property<T>

    /**
     * This method is better suited for testing but can also be used in most of other cases
     *
     * @param entity            Entity to get the edition form for
     * @param propertyTypeClass Class of the property type to get the property for
     * @return A response that defines the property
     */
    fun <T> getPropertyValue(entity: ProjectEntity, propertyTypeClass: Class<out PropertyType<T>>): T?

    /**
     * Edits the value of a property.
     *
     * @param entity           Entity to edit
     * @param propertyTypeName Fully qualified name of the property to edit
     * @param data             Raw JSON data for the property value
     */
    fun editProperty(entity: ProjectEntity, propertyTypeName: String, data: JsonNode): Ack

    /**
     * Edits the value of a property.
     *
     * @param entity       Entity to edit
     * @param propertyType The type of the property to edit
     * @param data         Property value
     */
    fun <T> editProperty(entity: ProjectEntity, propertyType: Class<out PropertyType<T>>, data: T): Ack

    /**
     * Deletes the value of a property.
     *
     * @param entity           Type of the entity to edit
     * @param propertyTypeName Fully qualified name of the property to delete
     */
    fun deleteProperty(entity: ProjectEntity, propertyTypeName: String): Ack

    /**
     * Deletes the value of a property.
     *
     * @param entity       Type of the entity to edit
     * @param propertyType Class of the property to delete
     */
    fun <T> deleteProperty(entity: ProjectEntity, propertyType: Class<out PropertyType<T>>): Ack =
            deleteProperty(entity, propertyType.name)

    /**
     * Searches for all entities with the corresponding property value.
     */
    fun <T> searchWithPropertyValue(
            propertyTypeClass: Class<out PropertyType<T>>,
            entityLoader: BiFunction<ProjectEntityType, ID, ProjectEntity>,
            predicate: Predicate<T>
    ): Collection<ProjectEntity>

    /**
     * Finds an item using its search key.
     */
    fun <T> findBuildByBranchAndSearchkey(branchId: ID, propertyType: Class<out PropertyType<T>>, searchKey: String): ID?

    /**
     * Finds a list of entities based on their type, a property and a search key.
     */
    fun <T> findByEntityTypeAndSearchkey(entityType: ProjectEntityType, propertyType: Class<out PropertyType<T>>, searchKey: String): List<ID>

    /**
     * Finds a list of entities based on their type, a property and a list of search arguments.
     */
    fun <T> findByEntityTypeAndSearchArguments(entityType: ProjectEntityType, propertyType: KClass<out PropertyType<T>>, searchArguments: PropertySearchArguments?): List<ID>

    /**
     * Tests if a property is defined.
     */
    fun <T> hasProperty(entity: ProjectEntity, propertyTypeClass: Class<out PropertyType<T>>): Boolean

    /**
     * Copy/clones the `property` into the `targetEntity` after applying the replacement function.
     *
     * @param sourceEntity  Owner of the property to copy
     * @param property      Property to copy
     * @param targetEntity  Entity to associate the new property with
     * @param replacementFn Replacement function for textual values
     * @param <T>           Type of the property
    </T> */
    fun <T> copyProperty(sourceEntity: ProjectEntity, property: Property<T>, targetEntity: ProjectEntity, replacementFn: (String) -> String)

    /**
     * Loops over all the properties of a given type.
     */
    fun <T> forEachEntityWithProperty(propertyTypeClass: KClass<out PropertyType<T>>, consumer: (ProjectEntityID, T) -> Unit)

    /**
     * Loops over all the properties of a given type.
     */
    fun <T> forEachEntityWithProperty(propertyType: PropertyType<T>, consumer: (ProjectEntityID, T) -> Unit)

}