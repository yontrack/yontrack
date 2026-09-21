package net.nemerosa.ontrack.model.structure

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.model.extension.Extension
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder

/**
 * Definition and validation of some arbitrary data associated with [ValidationStamp]s
 * and [ValidationRun]s.
 *
 * @param C Configuration data associated with this type.
 * @param T Type of data associated with this type.
 */
interface ValidationDataType<C, T> : Extension {

    /**
     * Display name for this type
     */
    val displayName: String

    /**
     * Translates the configuration data into JSON
     */
    fun configToJson(config: C): JsonNode

    /**
     * Translates some JSON into configuration data
     * @return The data or `null` if the JSON cannot be translated. The implementation can also choose
     * to throw an exception instead.
     */
    fun configFromJson(node: JsonNode?): C?

    /**
     * JSON for the client (must be mapped to the fields of the form
     * defined by [getConfigForm].
     */
    fun configToFormJson(config: C?): JsonNode?

    /**
     * Creates the config object from the JSON returned by a form edition
     *
     * @param node JSON returned by the form edition
     * @return Configuration
     */
    fun fromConfigForm(node: JsonNode?): C?

    /**
     * Translates the data into JSON
     */
    fun toJson(data: T): JsonNode

    /**
     * Translates some JSON into data
     * @return The data or `null` if the JSON cannot be translated. The implementation can also choose
     * to throw an exception instead.
     */
    fun fromJson(node: JsonNode): T?

    /**
     * Creates the data object from the JSON returned by a form edition
     *
     * @param node JSON returned by the form edition
     * @return Data
     */
    fun fromForm(node: JsonNode?): T?

    /**
     * Computes the status from some data
     *
     * @param config Configuration to use
     * @param data Data associated with a run
     * @return Status or `null` if no status can be determined
     */
    fun computeStatus(config: C?, data: T): ValidationRunStatusID?

    /**
     * Validates some data according to its type and configuration.
     *
     * @param config Configuration associated to the type
     * @param data Data to validate
     * @return Validated data
     */
    fun validateData(config: C?, data: T?): T

    /**
     * Gets some metrics about this data. Return `null` if not applicable.
     */
    fun getMetrics(data: T): Map<String, *>?

    /**
     * Creates the JSON type for the configuration
     *
     * @param jsonTypeBuilder Builder for the JSON types
     */
    fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType

    /**
     * Descriptor
     */
    val descriptor
        get() = ValidationDataTypeDescriptor(
            feature.featureDescription,
            this::class.qualifiedName!!,
            displayName
        )
}
