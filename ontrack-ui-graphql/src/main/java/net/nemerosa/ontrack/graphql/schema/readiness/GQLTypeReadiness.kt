package net.nemerosa.ontrack.graphql.schema.readiness

import graphql.schema.GraphQLObjectType
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.support.GraphQLBeanConverter
import net.nemerosa.ontrack.model.readiness.Readiness
import org.springframework.stereotype.Component

/**
 * What a build still lacks to reach a promotion level or a slot.
 */
@Component
class GQLTypeReadiness : GQLType {

    override fun getTypeName(): String = Readiness::class.java.simpleName

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLBeanConverter.asObjectType(Readiness::class, cache)
}
