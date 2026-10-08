package net.nemerosa.ontrack.extension.scm.graphql

import graphql.Scalars.GraphQLBoolean
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import net.nemerosa.ontrack.extension.scm.changelog.assistants.AssistedChangeProperty
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.support.enumField
import net.nemerosa.ontrack.graphql.support.getTypeDescription
import net.nemerosa.ontrack.graphql.support.intField
import net.nemerosa.ontrack.graphql.support.stringField
import net.nemerosa.ontrack.graphql.support.stringListField
import org.springframework.stereotype.Component

/**
 * GraphQL type of the [assisted change][AssistedChangeProperty] of a build.
 */
@Component
class GQLTypeAssistedChange : GQLType {

    override fun getTypeName(): String = "AssistedChange"

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description(getTypeDescription(AssistedChangeProperty::class))
            .field {
                it.name("assisted")
                    .description("The build is assisted: there is at least one assistant.")
                    .type(GraphQLNonNull(GraphQLBoolean))
                    .dataFetcher { env ->
                        val value: AssistedChangeProperty = env.getSource()!!
                        value.assisted
                    }
            }
            .enumField(AssistedChangeProperty::basis)
            .stringField(AssistedChangeProperty::unknownReason)
            .stringListField(AssistedChangeProperty::assistants)
            .intField(AssistedChangeProperty::assistedCommits)
            .intField(AssistedChangeProperty::totalCommits)
            .stringListField(AssistedChangeProperty::sessionLinks)
            .intField(AssistedChangeProperty::previousBuildId)
            .build()
}
