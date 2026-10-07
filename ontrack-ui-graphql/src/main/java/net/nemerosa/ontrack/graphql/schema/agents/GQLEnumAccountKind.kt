package net.nemerosa.ontrack.graphql.schema.agents

import graphql.schema.GraphQLEnumType
import graphql.schema.GraphQLTypeReference
import net.nemerosa.ontrack.graphql.schema.GQLEnum
import net.nemerosa.ontrack.model.security.AccountKind
import org.springframework.stereotype.Component

/**
 * Kinds of accounts, each value with its description.
 */
@Component
class GQLEnumAccountKind : GQLEnum {

    override fun getTypeRef(): GraphQLTypeReference = GraphQLTypeReference(AccountKind::class.java.simpleName)

    override fun createEnum(): GraphQLEnumType = GraphQLEnumType.newEnum()
        .name(AccountKind::class.java.simpleName)
        .description("Kind of an account: a person, or a registered agent")
        .apply {
            AccountKind.entries.forEach { kind ->
                value(kind.name, kind.name, kind.description)
            }
        }
        .build()
}
