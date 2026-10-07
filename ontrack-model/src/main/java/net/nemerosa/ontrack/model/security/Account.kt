package net.nemerosa.ontrack.model.security

import com.fasterxml.jackson.annotation.JsonIgnore
import net.nemerosa.ontrack.model.structure.Entity
import net.nemerosa.ontrack.model.structure.ID
import java.io.Serializable

/**
 * An account: a person or a registered agent.
 *
 * @property id ID of the account
 * @property fullName Full name of a person, display name of an agent
 * @property email Email of a person, `<slug>[agent]` identifier of an agent (see [AgentIdentifiers])
 * @property role Security role
 * @property kind Person or agent
 * @property owner For an agent, the human account accountable for it - only its ID, full name and
 * email are set, never its own owner
 * @property agentTool For an agent, the tool behind it (Claude Code, Codex, ...)
 * @property agentDescription For an agent, an optional description
 */
data class Account(
    override val id: ID,
    val fullName: String,
    val email: String,
    val role: SecurityRole,
    val kind: AccountKind = AccountKind.HUMAN,
    val owner: Account? = null,
    val agentTool: String? = null,
    val agentDescription: String? = null,
) : Entity, Serializable {

    companion object {

        fun user(fullName: String, email: String) =
            Account(
                id = ID.NONE,
                fullName = fullName,
                email = email,
                role = SecurityRole.USER,
            )

        @JvmStatic
        fun of(fullName: String, email: String, role: SecurityRole) =
            Account(
                id = ID.NONE,
                fullName = fullName,
                email = email,
                role = role,
            )

        /**
         * A new agent account, not saved yet.
         *
         * @param slug Slug of the agent, see [AgentIdentifiers.SLUG_REGEX]
         * @param displayName Display name of the agent
         * @param owner Human account owning the agent
         * @param tool Tool behind the agent
         * @param description Optional description
         */
        fun agent(
            slug: String,
            displayName: String,
            owner: Account,
            tool: String,
            description: String?,
        ) = Account(
            id = ID.NONE,
            fullName = displayName,
            email = AgentIdentifiers.identifier(slug),
            role = SecurityRole.USER,
            kind = AccountKind.AGENT,
            owner = owner.asOwner(),
            agentTool = tool,
            agentDescription = description,
        )

    }

    /**
     * Is this account a registered agent?
     */
    @get:JsonIgnore
    val isAgent: Boolean get() = kind == AccountKind.AGENT

    fun withId(id: ID): Account = copy(id = id)

    fun withFullName(fullName: String): Account = copy(fullName = fullName)

    fun update(input: AccountInput) =
        copy(
            fullName = input.fullName,
            email = input.email,
        )

    /**
     * Only the ID, full name and email of this account, to be used as the owner of an agent.
     */
    fun asOwner() = Account(
        id = id,
        fullName = fullName,
        email = email,
        role = SecurityRole.USER,
    )

    fun asPermissionTarget() =
        PermissionTarget(
            type = PermissionTargetType.ACCOUNT,
            id = id(),
            name = email,
            description = fullName
        )

}
