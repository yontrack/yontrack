package net.nemerosa.ontrack.extension.findings.graphql

import graphql.schema.GraphQLInputType
import graphql.schema.GraphQLType
import graphql.schema.GraphQLTypeReference
import net.nemerosa.ontrack.extension.findings.query.FindingFilter
import net.nemerosa.ontrack.graphql.schema.GQLInputType
import net.nemerosa.ontrack.graphql.support.GraphQLBeanConverter
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import org.springframework.stereotype.Component

@Component
class GQLInputFindingFilter : GQLInputType<FindingFilter> {

    override fun createInputType(dictionary: MutableSet<GraphQLType>): GraphQLInputType =
        GraphQLBeanConverter.asInputType(
            type = FindingFilter::class,
            dictionary = dictionary,
        )

    override fun convert(argument: Any?): FindingFilter =
        argument?.asJson()?.parse() ?: FindingFilter()

    override fun getTypeRef() = GraphQLTypeReference(FindingFilter::class.java.simpleName)
}
