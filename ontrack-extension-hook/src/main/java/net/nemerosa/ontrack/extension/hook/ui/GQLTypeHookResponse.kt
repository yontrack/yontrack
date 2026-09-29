package net.nemerosa.ontrack.extension.hook.ui

import graphql.schema.GraphQLObjectType
import net.nemerosa.ontrack.extension.hook.HookResponse
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.support.enumField
import net.nemerosa.ontrack.graphql.support.field
import net.nemerosa.ontrack.graphql.support.getTypeDescription
import org.springframework.stereotype.Component

@Component
class GQLTypeHookResponse : GQLType {

    override fun getTypeName(): String = HookResponse::class.java.simpleName

    override fun createType(cache: GQLTypeCache): GraphQLObjectType = GraphQLObjectType.newObject()
            .name(typeName)
            .description(getTypeDescription(HookResponse::class))
            .enumField(HookResponse::type)
            .field(HookResponse::infoLink)
            .build()
}