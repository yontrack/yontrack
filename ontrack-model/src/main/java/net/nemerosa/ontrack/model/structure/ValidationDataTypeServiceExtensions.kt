package net.nemerosa.ontrack.model.structure

import tools.jackson.databind.JsonNode

/**
 * Given a [data type id][ValidationDataType] and its configuration as JSON, returns
 * a validated and typed configuration for this type.
 */
fun <C> ValidationDataTypeService.validateValidationDataTypeConfig(
    dataType: String?,
    dataTypeConfig: JsonNode?
): ValidationDataTypeConfig<C>? {
    return if (dataType.isNullOrBlank()) {
        null
    } else {
        val type = getValidationDataType<C, Any>(dataType)
        if (type != null) {
            // Parsing without exception
            return ValidationDataTypeConfig(
                type.descriptor,
                type.fromConfigForm(dataTypeConfig)
            )
        } else {
            null
        }
    }
}