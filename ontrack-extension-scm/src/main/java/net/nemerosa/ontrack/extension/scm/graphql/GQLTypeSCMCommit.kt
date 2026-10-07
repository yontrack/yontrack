package net.nemerosa.ontrack.extension.scm.graphql

import graphql.schema.GraphQLObjectType
import net.nemerosa.ontrack.extension.scm.changelog.SCMCommit
import net.nemerosa.ontrack.extension.scm.changelog.assistants.SCMCommitAssistant
import net.nemerosa.ontrack.extension.scm.changelog.assistants.SCMCommitAssistantService
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.support.dateField
import net.nemerosa.ontrack.graphql.support.getTypeDescription
import net.nemerosa.ontrack.graphql.support.listField
import net.nemerosa.ontrack.graphql.support.stringField
import net.nemerosa.ontrack.model.annotations.getPropertyDescription
import org.springframework.stereotype.Component

@Component
class GQLTypeSCMCommit(
    private val scmCommitAssistantService: SCMCommitAssistantService,
) : GQLType {

    override fun getTypeName(): String = SCMCommit::class.java.simpleName

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description(getTypeDescription(SCMCommit::class))
            .stringField(SCMCommit::id)
            .stringField(SCMCommit::shortId)
            .stringField(SCMCommit::author)
            .stringField(SCMCommit::authorEmail)
            .stringField(SCMCommit::message)
            .stringField(SCMCommit::link)
            .dateField(
                name = SCMCommit::timestamp.name,
                description = getPropertyDescription(SCMCommit::timestamp),
                nullable = false
            )
            .field(
                listField(
                    type = SCMCommitAssistant::class,
                    name = "assistants",
                    description = "Assistants (agent kinds) which helped write the commit, read from its trailers, its author and its committer according to the agent markers settings. Empty when no assistant is recognised.",
                ) { env ->
                    scmCommitAssistantService.getAssistants(env.getSource<SCMCommit>()!!)
                }
            )
            .build()
}
