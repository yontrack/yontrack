package net.nemerosa.ontrack.extension.scm.graphql

import graphql.schema.GraphQLObjectType
import net.nemerosa.ontrack.extension.scm.changelog.assistants.SCMCommitAssistant
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.support.getTypeDescription
import net.nemerosa.ontrack.graphql.support.listField
import net.nemerosa.ontrack.graphql.support.stringField
import org.springframework.stereotype.Component

@Component
class GQLTypeSCMCommitAssistant : GQLType {

    override fun getTypeName(): String = SCMCommitAssistant::class.java.simpleName

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description(getTypeDescription(SCMCommitAssistant::class))
            .stringField(SCMCommitAssistant::name)
            .listField(SCMCommitAssistant::markers)
            .stringField(SCMCommitAssistant::sessionLink)
            .build()
}
