package net.nemerosa.ontrack.model.security

import net.nemerosa.ontrack.model.structure.PromotionLevel
import org.springframework.security.access.AccessDeniedException

/**
 * Refusal of an action to an agent by the [agent policy][AgentPolicy].
 *
 * It is an [AccessDeniedException], so that it is handled as any other refusal, but its message
 * always says why: an agent which discovers its limits by a bare 403 is an agent which retries.
 */
class AgentPolicyException(message: String) : AccessDeniedException(message) {

    companion object {

        private fun refusal(agent: Account, action: String, reason: String? = null) =
            AgentPolicyException(
                "agent ${agent.email} may not $action${reason?.let { ": $it" } ?: ""} (agent policy)"
            )

        /**
         * The function is not in the allowlist of the policy.
         */
        fun function(agent: Account, fn: Class<*>) = refusal(agent, fn.simpleName)

        /**
         * The promotion level does not admit agents.
         */
        fun promotionLevel(agent: Account, promotionLevel: PromotionLevel) = refusal(
            agent = agent,
            action = "promote to ${promotionLevel.name}",
            reason = "the promotion level does not admit agents",
        )

        /**
         * The slot does not admit agents.
         *
         * @param action What the agent tried, like "start a pipeline"
         * @param slot Full name of the slot
         */
        fun slot(agent: Account, action: String, slot: String) = refusal(
            agent = agent,
            action = "$action on $slot",
            reason = "the slot does not admit agents",
        )
    }

}
