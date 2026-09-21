package net.nemerosa.ontrack.repository.support

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.model.exceptions.*
import net.nemerosa.ontrack.model.structure.*
import org.springframework.stereotype.Service

@Service
class ValidationDataTypeServiceImpl(
        private val types: List<ValidationDataType<*, *>>
) : ValidationDataTypeService {

    override fun <C, T> getValidationDataType(id: String): ValidationDataType<C, T>? {
        @Suppress("UNCHECKED_CAST")
        return types.find { it::class.java.name == id } as? ValidationDataType<C, T>?
    }

    override fun getAllTypes(): List<ValidationDataType<*, *>> = types

    override fun <C> getServiceConfigurationForConfig(config: ValidationDataTypeConfig<C>?): ServiceConfiguration? {
        if (config != null) {
            // Gets the type
            val validationDataType = getValidationDataType<C, Any>(config.descriptor.id)
                    ?: throw ValidationRunDataInputException("Cannot find any data type for ID `${config.descriptor.id}`")
            // Converts the typed data into JSON for the client
            val json: JsonNode? = config.config?.let { validationDataType.configToFormJson(it) }
            // OK
            return ServiceConfiguration(
                    config.descriptor.id,
                    json
            )
        } else {
            return null
        }
    }

    override fun <C, T> validateData(
            typedData: ValidationRunData<T>?,
            config: ValidationDataTypeConfig<C>?,
            status: ValidationRunStatusID?
    ): ValidationRunDataWithStatus<T> {
        if (config == null) {
            // OK, there might be data, there might be not,
            // We do not validate...
            // ... but status is therefore required
            if (status == null) {
                if (typedData == null) {
                    throw ValidationRunDataStatusRequiredBecauseNoDataException()
                } else {
                    throw ValidationRunDataStatusRequiredBecauseNoDataTypeException()
                }
            } else {
                return ValidationRunDataWithStatus(
                        typedData, // Might defined... or not. No matter here.
                        status
                )
            }
        } else if (typedData == null) {
            // No data as input. OK as long as the status is provided
            if (status == null) {
                throw ValidationRunDataStatusRequiredBecauseNoDataException()
            } else {
                return ValidationRunDataWithStatus(
                        null,
                        status
                )
            }
        } else if (typedData.descriptor.id != config.descriptor.id) {
            // Different type of data
            throw ValidationRunDataMismatchException(
                    actualId = typedData.descriptor.id,
                    expectedId= config.descriptor.id
            )
        } else {
            // Gets the type
            val validationDataType = getValidationDataType<C, T>(typedData.descriptor.id)
                    ?: throw ValidationRunDataTypeNotFoundException(typedData.descriptor.id)
            // Validation
            val validatedData: T = validationDataType.validateData(config.config, typedData.data)
            // Computing the status
            val computedStatus: ValidationRunStatusID? = validationDataType.computeStatus(config.config, validatedData)
            // Final status
            val finalStatus: ValidationRunStatusID =
                    when {
                    // Provided status has priority
                        status != null -> status
                    // But it can be computed
                        computedStatus != null -> computedStatus
                    // Else, we consider it valid
                        else -> ValidationRunStatusID.STATUS_PASSED
                    }
            // OK
            return ValidationRunDataWithStatus(
                    ValidationRunData(
                            config.descriptor,
                            validatedData
                    ),
                    finalStatus
            )
        }
    }

}
