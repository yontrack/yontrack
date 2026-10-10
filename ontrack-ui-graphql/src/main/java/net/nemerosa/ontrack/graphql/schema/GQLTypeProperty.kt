package net.nemerosa.ontrack.graphql.schema

import graphql.Scalars
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLObjectType
import net.nemerosa.ontrack.graphql.support.GQLScalarJSON
import net.nemerosa.ontrack.model.structure.Property
import org.springframework.stereotype.Component

/**
 * Description of a [Property].
 */
@Component
class GQLTypeProperty(
        private val propertyType: GQLTypePropertyType,
) : GQLType {

    override fun getTypeName(): String = PROPERTY

    override fun createType(cache: GQLTypeCache): GraphQLObjectType {
        return GraphQLObjectType.newObject()
                .name(PROPERTY) // Type
                .field(
                        GraphQLFieldDefinition.newFieldDefinition()
                                .name("type")
                                .description("Property type")
                                .type(propertyType.typeRef)
                                .dataFetcher { env ->
                                    val property: Property<*> = env.getSource()!!
                                    property.typeDescriptor
                                }
                                .build()
                ) // Value
                .field(
                        GraphQLFieldDefinition.newFieldDefinition()
                                .name("value")
                                .description("JSON representation of the value")
                                .type(GQLScalarJSON.INSTANCE)
                                .build()
                ) // Editable
                .field(
                        GraphQLFieldDefinition.newFieldDefinition()
                                .name("editable")
                                .description("True is the field is editable")
                                .type(Scalars.GraphQLBoolean)
                                .dataFetcher { env ->
                                    val property: Property<*> = env.getSource()!!
                                    property.editable
                                }
                                .build()
                ) // Error
                .field(
                        GraphQLFieldDefinition.newFieldDefinition()
                                .name("error")
                                .description("When the stored value of the property cannot be read, the reason why. The value is then null. Null when the property is fine.")
                                .type(Scalars.GraphQLString)
                                .dataFetcher { env ->
                                    val property: Property<*> = env.getSource()!!
                                    property.error
                                }
                                .build()
                ) // OK
                .build()
    }

    companion object {
        const val PROPERTY = "Property"
    }
}
