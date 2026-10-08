package net.nemerosa.ontrack.extension.environments.ui

import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLTypeReference
import net.nemerosa.ontrack.extension.environments.SlotPipelineChange
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.schema.GQLTypeSignatureActor
import net.nemerosa.ontrack.graphql.support.enumField
import net.nemerosa.ontrack.graphql.support.field
import net.nemerosa.ontrack.graphql.support.localDateTimeField
import net.nemerosa.ontrack.graphql.support.stringField
import org.springframework.stereotype.Component

@Component
class GQLTypeSlotPipelineChange : GQLType {

    override fun getTypeName(): String = SlotPipelineChange::class.java.simpleName

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description("Change happening to a pipeline")
            .stringField(SlotPipelineChange::id)
            .field(SlotPipelineChange::pipeline)
            .stringField(SlotPipelineChange::user)
            .localDateTimeField(SlotPipelineChange::timestamp)
            .enumField(SlotPipelineChange::type)
            .enumField(SlotPipelineChange::status)
            .stringField(SlotPipelineChange::message)
            .stringField(SlotPipelineChange::overrideMessage)
            .field {
                it.name("actor")
                    .description("The agent behind the change, null for a person")
                    .type(GraphQLTypeReference(GQLTypeSignatureActor.SIGNATURE_ACTOR))
            }
            .build()

}