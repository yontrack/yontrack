package net.nemerosa.ontrack.model.security

/**
 * An authenticated account, with its authorisations and groups.
 *
 * For an agent, [authorisations] and [groups] are those of its **owner**, and every check is
 * narrowed by the [agent policy][AgentPolicy]: an agent never holds a function its owner does not
 * hold, nor a function the policy does not allow.
 *
 * Authenticated through a read-only token, every check is further narrowed by the
 * [read-only token policy][ReadOnlyTokenPolicy].
 *
 * @property readOnlyTokenName Name of the read-only token this user is authenticated with, `null` otherwise
 */
class AccountAuthenticatedUser(
    override val account: Account,
    val authorisations: Authorisations,
    override val groups: List<AuthorizedGroup>,
    override val assignedGroups: List<AccountGroup>,
    override val mappedGroups: List<AccountGroup>,
    override val idpGroups: List<String>,
    override val readOnlyTokenName: String? = null,
) : AuthenticatedUser {

    override fun isGranted(fn: Class<out GlobalFunction>): Boolean =
        isAllowedByAgentPolicy(fn) && isAllowedByReadOnlyToken(fn) &&
                (authorisations.isGranted(fn) || groups.any { it.isGranted(fn) })

    override fun isGranted(projectId: Int, fn: Class<out ProjectFunction>): Boolean =
        isAllowedByAgentPolicy(fn) && isAllowedByReadOnlyToken(fn) &&
                (authorisations.isGranted(projectId, fn) || groups.any { it.isGranted(projectId, fn) })

    /**
     * A person is not concerned by the agent policy.
     */
    fun isAllowedByAgentPolicy(fn: Class<*>): Boolean =
        !account.isAgent || AgentPolicy.isGranted(fn)

    /**
     * Without a read-only token, this policy does not apply.
     */
    private fun isAllowedByReadOnlyToken(fn: Class<*>): Boolean =
        readOnlyTokenName == null || ReadOnlyTokenPolicy.isGranted(fn)

    override fun getName(): String = account.email

}
