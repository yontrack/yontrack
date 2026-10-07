package net.nemerosa.ontrack.model.security

/**
 * An authenticated account, with its authorisations and groups.
 *
 * For an agent, [authorisations] and [groups] are those of its **owner**, and every check is
 * narrowed by the [agent policy][AgentPolicy]: an agent never holds a function its owner does not
 * hold, nor a function the policy does not allow.
 */
class AccountAuthenticatedUser(
    override val account: Account,
    val authorisations: Authorisations,
    override val groups: List<AuthorizedGroup>,
    override val assignedGroups: List<AccountGroup>,
    override val mappedGroups: List<AccountGroup>,
    override val idpGroups: List<String>,
) : AuthenticatedUser {

    override fun isGranted(fn: Class<out GlobalFunction>): Boolean =
        isAllowedByAgentPolicy(fn) &&
                (authorisations.isGranted(fn) || groups.any { it.isGranted(fn) })

    override fun isGranted(projectId: Int, fn: Class<out ProjectFunction>): Boolean =
        isAllowedByAgentPolicy(fn) &&
                (authorisations.isGranted(projectId, fn) || groups.any { it.isGranted(projectId, fn) })

    /**
     * A person is not concerned by the agent policy.
     */
    fun isAllowedByAgentPolicy(fn: Class<*>): Boolean =
        !account.isAgent || AgentPolicy.isGranted(fn)

    override fun getName(): String = account.email

}
