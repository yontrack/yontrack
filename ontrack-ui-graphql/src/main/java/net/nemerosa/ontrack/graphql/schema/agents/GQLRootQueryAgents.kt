package net.nemerosa.ontrack.graphql.schema.agents

import graphql.schema.GraphQLFieldDefinition
import net.nemerosa.ontrack.graphql.schema.GQLRootQuery
import net.nemerosa.ontrack.graphql.schema.GQLTypeAccount
import net.nemerosa.ontrack.graphql.support.intArgument
import net.nemerosa.ontrack.graphql.support.listType
import net.nemerosa.ontrack.graphql.support.stringArgument
import net.nemerosa.ontrack.model.security.AgentService
import net.nemerosa.ontrack.model.structure.ID
import org.springframework.stereotype.Component

/**
 * List of the registered agents visible to the current user.
 */
@Component
class GQLRootQueryAgents(
    private val gqlTypeAccount: GQLTypeAccount,
    private val agentService: AgentService,
) : GQLRootQuery {

    override fun getFieldDefinition(): GraphQLFieldDefinition =
        GraphQLFieldDefinition.newFieldDefinition()
            .name("agents")
            .description(
                "Registered agents: every agent for an administrator, only their own agents for any other user. " +
                        "An agent is an account of kind AGENT, owned by a person, acting with its own tokens."
            )
            .argument(
                stringArgument(
                    ARG_OWNER,
                    "Restricts the list to the agents of this owner, given by email",
                )
            )
            .argument(
                intArgument(
                    ARG_ID,
                    "Restricts the list to the agent with this ID",
                )
            )
            .type(listType(gqlTypeAccount.typeRef))
            .dataFetcher { env ->
                val owner: String? = env.getArgument(ARG_OWNER)
                val id: Int? = env.getArgument(ARG_ID)
                if (id != null) {
                    listOfNotNull(
                        agentService.findAgent(ID.of(id))
                            ?.takeIf { owner.isNullOrBlank() || it.owner?.email == owner }
                    )
                } else {
                    agentService.getAgents(owner)
                }
            }
            .build()

    companion object {
        private const val ARG_OWNER = "owner"
        private const val ARG_ID = "id"
    }
}
