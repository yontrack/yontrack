package net.nemerosa.ontrack.extension.scorecard.graphql

import graphql.Scalars.GraphQLString
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLTypeReference
import net.nemerosa.ontrack.extension.scorecard.service.Scorecard
import net.nemerosa.ontrack.extension.scorecard.service.ScorecardSet
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.support.listType
import org.springframework.stereotype.Component

@Component
class GQLTypeScorecard : GQLType {

    override fun getTypeName(): String = Scorecard::class.java.simpleName

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description("The readings of one project, in every set it is in")
            .field {
                it.name(Scorecard::sets.name)
                    .description("Sets the project is in: the set with no estate first, always present")
                    .type(listType(GraphQLTypeReference(ScorecardSet::class.java.simpleName)))
            }
            .build()
}

@Component
class GQLTypeScorecardSet : GQLType {

    override fun getTypeName(): String = ScorecardSet::class.java.simpleName

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description("The readings of a project in one set: with no estate, or for one estate it belongs to")
            .field {
                it.name("name")
                    .description("Name of the set: Project for the set with no estate")
                    .type(GraphQLNonNull(GraphQLString))
                    .dataFetcher { env -> env.getSource<ScorecardSet>()!!.set.name }
            }
            .field {
                it.name(ScorecardSet::readings.name)
                    .description("Latest snapshot of each reading of the set, in the catalogue order. Empty until the readings are computed.")
                    .type(listType(GraphQLTypeReference(GQLTypeReading.READING)))
            }
            .build()
}
