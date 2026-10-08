package net.nemerosa.ontrack.extension.environments.ui

import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLTypeReference
import net.nemerosa.ontrack.extension.environments.SlotAdmissionRuleData
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.schema.GQLTypeSignatureActor
import net.nemerosa.ontrack.graphql.support.jsonField
import net.nemerosa.ontrack.graphql.support.localDateTimeField
import net.nemerosa.ontrack.graphql.support.stringField
import net.nemerosa.ontrack.model.annotations.getAPITypeDescription
import org.springframework.stereotype.Component

@Component
class GQLTypeSlotAdmissionRuleData : GQLType {

    override fun getTypeName(): String = SlotAdmissionRuleData::class.java.simpleName

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description(getAPITypeDescription(SlotAdmissionRuleData::class))
            .stringField(SlotAdmissionRuleData::user)
            .localDateTimeField(SlotAdmissionRuleData::timestamp)
            .jsonField(SlotAdmissionRuleData::data)
            .field {
                it.name("actor")
                    .description("The agent behind the data, null for a person")
                    .type(GraphQLTypeReference(GQLTypeSignatureActor.SIGNATURE_ACTOR))
            }
            .build()
}