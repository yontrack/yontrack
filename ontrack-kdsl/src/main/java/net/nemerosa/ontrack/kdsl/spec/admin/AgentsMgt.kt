package net.nemerosa.ontrack.kdsl.spec.admin

import com.apollographql.apollo.api.Optional
import net.nemerosa.ontrack.kdsl.connector.Connected
import net.nemerosa.ontrack.kdsl.connector.Connector
import net.nemerosa.ontrack.kdsl.connector.graphql.checkData
import net.nemerosa.ontrack.kdsl.connector.graphql.convert
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.AgentsQuery
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.RegisterAgentMutation
import net.nemerosa.ontrack.kdsl.connector.graphqlConnector
import net.nemerosa.ontrack.kdsl.spec.Ontrack

/**
 * Management of the registered agents.
 */
class AgentsMgt(connector: Connector) : Connected(connector) {

    /**
     * Registers an agent.
     *
     * @param slug Slug of the agent: 1 to 32 lowercase letters, digits or dashes
     * @param displayName Display name of the agent
     * @param tool Tool behind the agent
     * @param description Optional description
     * @param owner Email of the owner. The current user when not set; only an administrator can
     * give another owner.
     * @return The registered agent
     */
    fun register(
        slug: String,
        displayName: String = slug,
        tool: String = "Other",
        description: String? = null,
        owner: String? = null,
    ): Agent =
        graphqlConnector.mutate(
            RegisterAgentMutation(
                slug,
                displayName,
                tool,
                Optional.presentIfNotNull(description),
                Optional.presentIfNotNull(owner),
            )
        ) {
            it?.registerAgent?.payloadUserErrors?.convert()
        }
            ?.checkData { it.registerAgent?.agent?.agentFragment }
            ?.toAgent(connector)
            ?: error("Did not get back the registered agent $slug")

    /**
     * Agents visible to the current user: every agent for an administrator, their own otherwise.
     *
     * @param owner Restricts the list to the agents of this owner, given by email
     */
    fun list(owner: String? = null): List<Agent> =
        graphqlConnector.query(
            AgentsQuery(
                Optional.presentIfNotNull(owner),
                Optional.absent(),
            )
        )?.agents?.map { it.agentFragment.toAgent(connector) } ?: emptyList()

    /**
     * Gets an agent by ID, when visible to the current user.
     */
    fun findById(id: Int): Agent? =
        graphqlConnector.query(
            AgentsQuery(
                Optional.absent(),
                Optional.present(id),
            )
        )?.agents?.firstOrNull()?.agentFragment?.toAgent(connector)
}

/**
 * Management of the registered agents.
 */
val Ontrack.agents: AgentsMgt get() = AgentsMgt(connector)
