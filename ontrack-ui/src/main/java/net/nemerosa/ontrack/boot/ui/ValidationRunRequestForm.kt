package net.nemerosa.ontrack.boot.ui

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.model.structure.PropertyCreationRequest
import net.nemerosa.ontrack.model.structure.ServiceConfiguration

/**
 * Form sent from a REST client to create a validation run for a build
 * and validation stamp.
 */
class ValidationRunRequestForm(
        val description: String? = null,
        val validationRunStatusId: String? = null,
        val validationStampName: String? = null,
        val validationStampData: ValidationRunRequestFormData? = null,
        val properties: List<PropertyCreationRequest> = listOf()
) {
    val actualValidationStampName: String
        get() = validationStampName ?: validationStampData?.id
        ?: throw IllegalStateException("Validation stamp name is required")
}

/**
 * This contains the mapping to the validation and any associated data.
 *
 * Note that it must map of the [ServiceConfiguration] class because of the
 * way the form sent to the client is built.
 *
 * @property id Name of the validation stamp.
 * @property type Type of data (only provided by some calls, will default to the one associated
 * with the validation stamp)
 * @property data Data to associate with the run
 */
class ValidationRunRequestFormData(
        val id: String,
        val type: String? = null,
        val data: JsonNode? = null
)
