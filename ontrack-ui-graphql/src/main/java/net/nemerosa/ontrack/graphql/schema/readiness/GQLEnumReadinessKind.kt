package net.nemerosa.ontrack.graphql.schema.readiness

import graphql.schema.GraphQLEnumType
import graphql.schema.GraphQLTypeReference
import net.nemerosa.ontrack.graphql.schema.GQLEnum
import net.nemerosa.ontrack.model.readiness.ReadinessKind
import org.springframework.stereotype.Component

/**
 * Kinds of the missing conditions of a readiness, each value with its description.
 */
@Component
class GQLEnumReadinessKind : GQLEnum {

    override fun getTypeRef(): GraphQLTypeReference = GraphQLTypeReference(ReadinessKind::class.java.simpleName)

    override fun createEnum(): GraphQLEnumType = GraphQLEnumType.newEnum()
        .name(ReadinessKind::class.java.simpleName)
        .description("Kind of a condition a build still lacks to reach a promotion level or a slot")
        .apply {
            ReadinessKind.entries.forEach { kind ->
                value(kind.name, kind.name, kind.description)
            }
        }
        .build()
}
