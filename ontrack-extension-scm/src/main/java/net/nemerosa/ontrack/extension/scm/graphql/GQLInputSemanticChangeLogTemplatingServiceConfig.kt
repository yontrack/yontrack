package net.nemerosa.ontrack.extension.scm.graphql

import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLInputType
import graphql.schema.GraphQLType
import graphql.schema.GraphQLTypeReference
import net.nemerosa.ontrack.extension.scm.changelog.SemanticChangeLogTemplatingServiceConfig
import net.nemerosa.ontrack.graphql.schema.GQLInputType
import net.nemerosa.ontrack.graphql.schema.booleanInputField
import net.nemerosa.ontrack.graphql.schema.intInputField
import net.nemerosa.ontrack.graphql.schema.optionalStringListInputField
import net.nemerosa.ontrack.graphql.schema.stringListInputField
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.annotations.getAPITypeName
import org.springframework.stereotype.Component

@Component
class GQLInputSemanticChangeLogTemplatingServiceConfig : GQLInputType<SemanticChangeLogTemplatingServiceConfig> {

    private val typeName: String = SemanticChangeLogTemplatingServiceConfig::class.java.simpleName

    override fun getTypeRef(): GraphQLTypeReference = GraphQLTypeReference(typeName)

    override fun createInputType(dictionary: MutableSet<GraphQLType>): GraphQLInputType =
        GraphQLInputObjectType.newInputObject()
            .name(typeName)
            .description(getAPITypeName(SemanticChangeLogTemplatingServiceConfig::class))
            .field(stringListInputField(SemanticChangeLogTemplatingServiceConfig::dependencies, nullable = true))
            .field(booleanInputField(SemanticChangeLogTemplatingServiceConfig::allQualifiers, nullable = true))
            .field(
                booleanInputField(
                    SemanticChangeLogTemplatingServiceConfig::defaultQualifierFallback,
                    nullable = true
                )
            )
            .field(booleanInputField(SemanticChangeLogTemplatingServiceConfig::issues, nullable = true))
            .field(
                optionalStringListInputField(
                    SemanticChangeLogTemplatingServiceConfig::sections.name,
                    "List of type=title sections"
                )
            )
            .field(stringListInputField(SemanticChangeLogTemplatingServiceConfig::exclude, nullable = true))
            .field(booleanInputField(SemanticChangeLogTemplatingServiceConfig::emojis, nullable = true))
            .field(intInputField(SemanticChangeLogTemplatingServiceConfig::commitsMaxLength, nullable = true))
            .build()


    override fun convert(argument: Any?): SemanticChangeLogTemplatingServiceConfig =
        argument?.asJson()?.parse() ?: SemanticChangeLogTemplatingServiceConfig()

}