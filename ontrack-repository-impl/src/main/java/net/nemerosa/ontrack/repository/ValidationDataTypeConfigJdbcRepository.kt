package net.nemerosa.ontrack.repository

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.model.structure.ValidationDataType
import net.nemerosa.ontrack.model.structure.ValidationDataTypeConfig
import net.nemerosa.ontrack.model.structure.ValidationDataTypeService
import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import javax.sql.DataSource

@Repository
class ValidationDataTypeConfigJdbcRepository(
    dataSource: DataSource,
    private val validationDataTypeService: ValidationDataTypeService
) : AbstractJdbcRepository(dataSource), ValidationDataTypeConfigRepository {

    private val logger: Logger = LoggerFactory.getLogger(ValidationDataTypeConfigJdbcRepository::class.java)

    override fun <C> readValidationDataTypeConfig(rs: ResultSet): ValidationDataTypeConfig<C>? {
        val id: String? = rs.getString("DATA_TYPE_ID")
        val json: JsonNode? = readJson(rs, "DATA_TYPE_CONFIG")
        if (id == null || id.isBlank()) {
            return null
        } else {
            val validationDataType: ValidationDataType<C, Any>? = validationDataTypeService.getValidationDataType(id)
            return if (validationDataType != null) {
                // Parsing
                val config = validationDataType.configFromJson(json)
                // OK
                ValidationDataTypeConfig(
                    validationDataType.descriptor,
                    config
                )
            } else {
                logger.warn("Cannot find validation data type for ID = " + id)
                null
            }
        }
    }
}