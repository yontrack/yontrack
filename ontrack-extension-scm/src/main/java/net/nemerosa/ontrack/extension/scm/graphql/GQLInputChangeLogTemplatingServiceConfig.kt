package net.nemerosa.ontrack.extension.scm.graphql

import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLInputType
import graphql.schema.GraphQLType
import graphql.schema.GraphQLTypeReference
import net.nemerosa.ontrack.extension.scm.changelog.ChangeLogTemplatingServiceConfig
import net.nemerosa.ontrack.graphql.schema.*
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.annotations.getAPITypeName
import org.springframework.stereotype.Component

@Component
class GQLInputChangeLogTemplatingServiceConfig : GQLInputType<ChangeLogTemplatingServiceConfig> {

    private val typeName: String = ChangeLogTemplatingServiceConfig::class.java.simpleName

    override fun getTypeRef(): GraphQLTypeReference = GraphQLTypeReference(typeName)

    override fun createInputType(dictionary: MutableSet<GraphQLType>): GraphQLInputType =
        GraphQLInputObjectType.newInputObject()
            .name(typeName)
            .description(getAPITypeName(ChangeLogTemplatingServiceConfig::class))
            .field(stringInputField(ChangeLogTemplatingServiceConfig::empty, nullable = true))
            .field(stringListInputField(ChangeLogTemplatingServiceConfig::dependencies, nullable = true))
            .field(booleanInputField(ChangeLogTemplatingServiceConfig::title, nullable = true))
            .field(booleanInputField(ChangeLogTemplatingServiceConfig::allQualifiers, nullable = true))
            .field(booleanInputField(ChangeLogTemplatingServiceConfig::defaultQualifierFallback, nullable = true))
            .field(enumInputField(ChangeLogTemplatingServiceConfig::commitsOption, nullable = true))
            .field(intInputField(ChangeLogTemplatingServiceConfig::commitsMaxLength, nullable = true))
            .build()

    override fun convert(argument: Any?): ChangeLogTemplatingServiceConfig =
        argument?.asJson()?.parse() ?: ChangeLogTemplatingServiceConfig()

}