package net.nemerosa.ontrack.model.security

import net.nemerosa.ontrack.model.Ack
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.Token

/**
 * Management of the registered agents: accounts of kind [AGENT][AccountKind.AGENT], each owned by a
 * human account and acting with its own tokens.
 *
 * Any user registers and manages their own agents; an administrator (holding [AccountManagement])
 * manages every agent, and is the only one to transfer one.
 *
 * An agent has no rights of its own: it is created without groups nor ACLs, and its effective
 * rights are empty until the agent policy gives it some.
 */
interface AgentService {

    /**
     * Registers a new agent.
     *
     * @param input Slug, display name, tool, description and optional owner
     * @return The registered agent
     */
    fun registerAgent(input: AgentRegistrationInput): Account

    /**
     * Updates the display name, the tool and the description of an agent. The slug never changes.
     * Owner or administrator only.
     */
    fun updateAgent(agentId: ID, input: AgentUpdateInput): Account

    /**
     * Transfers an agent to another human owner. Administrator only.
     *
     * @param agentId ID of the agent
     * @param owner Email of the new owner
     * @return The transferred agent
     */
    fun transferAgent(agentId: ID, owner: String): Account

    /**
     * Deletes an agent and its tokens. Its name stays on every signature. Owner or administrator only.
     */
    fun deleteAgent(agentId: ID): Ack

    /**
     * Generates a named token for an agent, under the usual validity rules of the instance.
     * Owner or administrator only.
     *
     * @return The token, whose value is available only this once
     */
    fun generateAgentToken(agentId: ID, name: String): Token

    /**
     * Revokes a named token of an agent. Owner or administrator only.
     */
    fun revokeAgentToken(agentId: ID, name: String)

    /**
     * Revokes all the tokens of an agent. Owner or administrator only.
     */
    fun revokeAllAgentTokens(agentId: ID)

    /**
     * Gets the agents visible to the current user: all of them for an administrator, only their own
     * for anybody else.
     *
     * @param owner Restricts the list to the agents of this owner, given by email
     */
    fun getAgents(owner: String? = null): List<Account>

    /**
     * Gets an agent visible to the current user, or `null`.
     */
    fun findAgent(agentId: ID): Account?

    /**
     * Gets the agents owned by an account, when visible to the current user.
     */
    fun getAgentsOwnedBy(account: Account): List<Account>

}

/**
 * Registration of an agent.
 *
 * @property slug Slug of the agent, matching [AgentIdentifiers.SLUG_REGEX]; its identifier is `<slug>[agent]`
 * @property displayName Display name of the agent
 * @property tool Tool behind the agent - see [AgentIdentifiers.TOOLS]
 * @property description Optional description
 * @property owner Email of the owner. The current user when not set; only an administrator can set
 * another owner.
 */
data class AgentRegistrationInput(
    val slug: String,
    val displayName: String,
    val tool: String,
    val description: String? = null,
    val owner: String? = null,
)

/**
 * Update of an agent.
 *
 * @property displayName Display name of the agent
 * @property tool Tool behind the agent
 * @property description Optional description
 */
data class AgentUpdateInput(
    val displayName: String,
    val tool: String,
    val description: String? = null,
)
