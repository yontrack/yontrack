package net.nemerosa.ontrack.model.security

import net.nemerosa.ontrack.model.structure.ProjectEntity

inline fun <reified F : ProjectFunction> SecurityService.isProjectFunctionGranted(e: ProjectEntity) =
        isProjectFunctionGranted(e, F::class.java)

inline fun <reified F : GlobalFunction> SecurityService.isGlobalFunctionGranted() =
        isGlobalFunctionGranted(F::class.java)

/**
 * The registered agent authenticated for this call, if any.
 *
 * `null` for a person, and for the system acting on somebody's behalf (`asAdmin`): auto-promotion,
 * for example, runs as the system, and is gated by the auto-promotion rules, not by the agent policy.
 */
val SecurityService.currentAgent: Account?
    get() = (currentUser as? AccountAuthenticatedUser)?.account?.takeIf { it.isAgent }
