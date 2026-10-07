package net.nemerosa.ontrack.model.security

import net.nemerosa.ontrack.model.structure.PromotionLevel

/**
 * Says whether a promotion level admits agents, that is, whether an agent may promote a build to it.
 *
 * Implemented by the *Agents admitted* property of the general extension. Without any
 * implementation, no promotion level admits agents: the [agent policy][AgentPolicy] denies by
 * default.
 */
interface PromotionLevelAgentAdmission {

    /**
     * Does the [promotionLevel] admit agents?
     */
    fun isAgentsAdmitted(promotionLevel: PromotionLevel): Boolean

}
