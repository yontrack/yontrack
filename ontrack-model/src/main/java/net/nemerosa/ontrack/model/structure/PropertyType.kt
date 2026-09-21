package net.nemerosa.ontrack.model.structure

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.model.extension.Extension
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.security.SecurityService
import org.apache.commons.lang3.Strings

/**
 * Defines the type for a property.
 *
 * @param <T> Type of object supported by this type
 */
interface PropertyType<T> : Extension {

    /**
     * Display name for this property
     */
    val name: String

    /**
     * Description for this property
     */
    val description: String

    /**
     * List of entity types this property applies to.
     */
    val supportedEntityTypes: Set<ProjectEntityType>

    /**
     * Edition policy.
     *
     *
     * Can this property be directly edited by a used on the given
     * associated entity.
     *
     * @param entity          Entity where to edit the property
     * @param securityService The access to the security layer
     * @return Authorization policy for this entity
     */
    fun canEdit(entity: ProjectEntity, securityService: SecurityService): Boolean

    /**
     * Defines the authorization policy for viewing this property.
     *
     * @param entity          Entity where to view the property
     * @param securityService The access to the security layer
     * @return Authorization policy for this entity
     */
    fun canView(entity: ProjectEntity, securityService: SecurityService): Boolean

    /**
     * Creation of a property value from a value. Should perform validation.
     */
    fun of(value: T): Property<T>

    /**
     * Gets the JSON representation of a property value
     */
    fun forStorage(value: T): JsonNode

    /**
     * Parses the client JSON representation for a property value. This method
     * *MUST* perform validation before accepting the value.
     */
    fun fromClient(node: JsonNode): T

    /**
     * Parses the storage JSON representation for a property value.
     */
    fun fromStorage(node: JsonNode): T

    /**
     * Parses the JSON representation for a property value and creates the property value directly.
     *
     * @see .fromStorage
     * @see .of
     */
    fun of(node: JsonNode): Property<T> {
        return of(fromStorage(node))
    }

    /**
     * Replaces a value by another one by transforming each string of the value into another one.
     *
     * @param value               Value to replace
     * @param replacementFunction Replacement function to used to transform each string into a new one
     * @return Transformed value
     */
    fun replaceValue(value: T, replacementFunction: (String) -> String): T

    /**
     * Checks if the property `value` contains the given search token.
     *
     * @param value         Value to search into
     * @param propertyValue Search token
     * @return `true` is found
     */
    fun containsValue(value: T, propertyValue: String): Boolean {
        return Strings.CI.contains(value.toString(), propertyValue)
    }

    val typeName: String
        /**
         * Type name for this property type.
         */
        get() = javaClass.getName()

    /**
     * Copy/clones the `value` defined for the `sourceEntity` for being suitable
     * in the `targetEntity` after applying the replacement function.
     *
     *
     * By default, just applies the textual replacements.
     *
     * @param sourceEntity  Owner of the property to copy
     * @param value         Property value to copy
     * @param targetEntity  Entity to associate the new property with
     * @param replacementFn Replacement function for textual values
     * @see .replaceValue
     */
    fun copy(
        sourceEntity: ProjectEntity,
        value: T,
        targetEntity: ProjectEntity,
        replacementFn: (String) -> String
    ): T {
        return replaceValue(value, replacementFn)
    }

    /**
     * This method is called when the property value changes (created or updated) for an entity
     *
     * @param entity Entity for which the property is changed
     * @param value  New value
     */
    fun onPropertyChanged(entity: ProjectEntity, value: T) {
    }

    /**
     * This method is called when the property is deleted for an entity
     *
     * @param entity   Entity for which the property is deleted
     * @param oldValue Old value
     */
    fun onPropertyDeleted(entity: ProjectEntity, oldValue: T) {
    }

    /**
     * Gets the additional SQL criteria to add to a search.
     *
     * @param token Token to look for this property type
     * @return Search arguments (or `null` if no search is possible)
     * @see PropertySearchArguments
     */
    fun getSearchArguments(token: String): PropertySearchArguments? {
        return null
    }

    /**
     * Additional decorations for a property, which will be available as additional information
     * for display or through the API.
     * @param value Property value
     * @return Additional decorations if any.
     */
    fun getPropertyDecorations(@Suppress("unused") value: T): Map<String, *> {
        return emptyMap<String, Any>()
    }

    /**
     * JSON schema type for this property.
     */
    fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType

}
