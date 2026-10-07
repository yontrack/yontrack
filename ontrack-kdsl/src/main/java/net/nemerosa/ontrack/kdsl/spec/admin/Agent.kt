package net.nemerosa.ontrack.kdsl.spec.admin

import net.nemerosa.ontrack.kdsl.connector.Connected
import net.nemerosa.ontrack.kdsl.connector.Connector
import net.nemerosa.ontrack.kdsl.connector.graphql.checkData
import net.nemerosa.ontrack.kdsl.connector.graphql.convert
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.DeleteAgentMutation
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.GenerateAgentTokenMutation
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.RevokeAgentTokenMutation
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.TransferAgentMutation
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.fragment.AgentFragment
import net.nemerosa.ontrack.kdsl.connector.graphqlConnector

/**
 * A registered agent: an account of kind `AGENT`, owned by a person, acting with its own tokens.
 *
 * @property id ID of the agent
 * @property email Identifier of the agent, `<slug>[agent]`
 * @property displayName Display name of the agent
 * @property tool Tool behind the agent
 * @property description Optional description
 * @property owner Email of the owner
 */
class Agent(
    connector: Connector,
    val id: Int,
    val email: String,
    val displayName: String,
    val tool: String?,
    val description: String?,
    val owner: String?,
) : Connected(connector) {

    /**
     * Slug of the agent.
     */
    val slug: String get() = email.removeSuffix("[agent]")

    /**
     * Generates a named token for this agent. Owner or administrator only.
     *
     * @param name Name of the token, unique for the agent
     * @return Value of the token — the only time it can be read
     */
    fun generateToken(name: String): String =
        graphqlConnector.mutate(
            GenerateAgentTokenMutation(id, name)
        ) {
            it?.generateAgentToken?.payloadUserErrors?.convert()
        }
            ?.checkData { it.generateAgentToken?.token?.value }
            ?: error("Did not get back the value of the generated token $name")

    /**
     * Revokes a named token of this agent. Owner or administrator only.
     */
    fun revokeToken(name: String) {
        graphqlConnector.mutate(
            RevokeAgentTokenMutation(id, name)
        ) {
            it?.revokeAgentToken?.payloadUserErrors?.convert()
        }
    }

    /**
     * Transfers this agent to another owner. Administrator only.
     *
     * @param owner Email of the new owner, a person
     * @return The transferred agent
     */
    fun transfer(owner: String): Agent =
        graphqlConnector.mutate(
            TransferAgentMutation(id, owner)
        ) {
            it?.transferAgent?.payloadUserErrors?.convert()
        }
            ?.checkData { it.transferAgent?.agent?.agentFragment }
            ?.toAgent(connector)
            ?: error("Did not get back the transferred agent")

    /**
     * Deletes this agent and its tokens. Owner or administrator only.
     */
    fun delete() {
        graphqlConnector.mutate(
            DeleteAgentMutation(id)
        ) {
            it?.deleteAgent?.payloadUserErrors?.convert()
        }
    }
}

/**
 * Converts the GraphQL fragment into an [Agent].
 */
fun AgentFragment.toAgent(connector: Connector) = Agent(
    connector = connector,
    id = id.toInt(),
    email = email,
    displayName = fullName,
    tool = agentTool,
    description = agentDescription,
    owner = owner?.email,
)
