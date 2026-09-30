package net.nemerosa.ontrack.extension.environments.ui

import graphql.schema.GraphQLObjectType
import net.nemerosa.ontrack.extension.environments.SlotDeploymentCheck
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.support.booleanField
import net.nemerosa.ontrack.graphql.support.enumField
import net.nemerosa.ontrack.graphql.support.stringField
import org.springframework.stereotype.Component

@Component
class GQLTypeSlotDeploymentCheck : GQLType {

    override fun getTypeName(): String = SlotDeploymentCheck::class.java.simpleName

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description("Result for the check of the admission for a pipeline")
            .booleanField(SlotDeploymentCheck::ok)
            .enumField(
                SlotDeploymentCheck::state,
                "OK, still pending - it blocks, but is expected to pass, like a running workflow - or failed"
            )
            .stringField(SlotDeploymentCheck::reason)
            .build()
}