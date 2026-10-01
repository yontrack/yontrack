package net.nemerosa.ontrack.graphql.schema

import graphql.schema.GraphQLObjectType
import net.nemerosa.ontrack.graphql.support.GQLScalarJSON
import net.nemerosa.ontrack.model.structure.ValidationDataTypeConfig
import net.nemerosa.ontrack.model.structure.ValidationDataTypeService
import org.springframework.stereotype.Component

/**
 * GraphQL type for [ValidationDataTypeConfig].
 */
@Component
class GQLTypeValidationDataTypeConfig(
    private val validationDataTypeDescriptor: GQLTypeValidationDataTypeDescriptor,
    private val validationDataTypeService: ValidationDataTypeService,
) : GQLType {

    override fun getTypeName() = ValidationDataTypeConfig::class.simpleName

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description("Configuration for the data type associated with a validation stamp")
            .field {
                it.name("descriptor")
                    .description("Descriptor for the validation data type")
                    .type(validationDataTypeDescriptor.typeRef)
            }
            .field {
                it.name("config")
                    .description("Configuration object")
                    .type(GQLScalarJSON.INSTANCE)
            }
            .field {
                it.name("formConfig")
                    .description("Configuration object, in the shape expected by the edition forms and the mutations")
                    .type(GQLScalarJSON.INSTANCE)
                    .dataFetcher { env ->
                        val config: ValidationDataTypeConfig<*> = env.getSource()!!
                        validationDataTypeService.getServiceConfigurationForConfig(config)?.data
                    }
            }
            .build()
}