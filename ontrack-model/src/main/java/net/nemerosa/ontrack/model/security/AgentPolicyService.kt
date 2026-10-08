package net.nemerosa.ontrack.model.security

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.common.api.APIName
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.PromotionLevel

/**
 * What the [agent policy][AgentPolicy] lets the **current agent** do, so that an agent knows which
 * gates it may pass before acting, instead of discovering them by being refused.
 *
 * Every method reads the policy of the agent authenticated for the call. For a person, or for the
 * system acting on somebody's behalf, there is no agent policy.
 */
interface AgentPolicyService {

    /**
     * The policy of the current agent on a project.
     *
     * @param project Name of the project
     * @return The policy, or `null` when the current user is not an agent
     * @throws net.nemerosa.ontrack.model.exceptions.ProjectNotFoundException When the agent cannot
     * see the project
     */
    fun getAgentPolicy(project: String): AgentProjectPolicy?

    /**
     * The promotion levels the current agent may promote to on a project: the levels which admit
     * agents, on the enabled branches, provided its owner may promote on the project.
     *
     * @param project Project
     * @param branch Name of a branch, to restrict the levels to this branch
     * @return The levels, empty for a person
     */
    fun getAdmittedPromotionLevels(project: Project, branch: String?): List<AgentPolicyPromotionLevel>

    /**
     * The slots of a project where the current agent may start a deployment pipeline: the slots
     * which admit agents, provided its owner may start a pipeline in them.
     *
     * @param project Project
     * @return The slots, empty for a person
     */
    fun getAdmittedSlots(project: Project): List<AgentPolicySlot>

    /**
     * Does the [promotionLevel] admit agents?
     */
    fun isAgentsAdmitted(promotionLevel: PromotionLevel): Boolean

    /**
     * Name of the person accountable for the [agent], to whom the agent turns when the policy stops
     * it.
     */
    fun getOwnerName(agent: Account): String
}

/**
 * Policy of an agent on a project.
 *
 * @property project Project the policy is about
 * @property owner Full name of the owner of the agent
 * @property canRecordEvidence Whether the agent may create builds and validation runs on the project
 */
@APIName("AgentPolicy")
@APIDescription(
    "What the current agent may do on a project: the rights of its owner, narrowed by the agent policy. " +
            "Read it before acting rather than discovering the policy by being refused."
)
data class AgentProjectPolicy(
    val project: Project,
    val owner: String,
    val canRecordEvidence: Boolean,
)

/**
 * A promotion level the agent may promote to.
 *
 * @property id ID of the promotion level
 * @property branch Name of the branch of the promotion level
 * @property name Name of the promotion level
 */
@APIDescription("A promotion level which admits agents, and on which the owner of the agent may promote")
data class AgentPolicyPromotionLevel(
    @APIDescription("ID of the promotion level")
    val id: Int,
    @APIDescription("Name of the branch of the promotion level")
    val branch: String,
    @APIDescription("Name of the promotion level")
    val name: String,
)

/**
 * A slot where the agent may start a deployment pipeline.
 *
 * @property id ID of the slot
 * @property environment Name of the environment of the slot
 * @property qualifier Qualifier of the slot, empty for the default one
 * @property manualApproval Whether a manual approval rule will still stop the agent
 */
@APIDescription(
    "A slot which admits agents, and where the owner of the agent may start a deployment pipeline"
)
data class AgentPolicySlot(
    @APIDescription("ID of the slot")
    val id: String,
    @APIDescription("Name of the environment of the slot")
    val environment: String,
    @APIDescription("Qualifier of the slot, empty for the default slot of the project in the environment")
    val qualifier: String,
    @APIDescription(
        "True when the slot has a manual approval rule: the agent may start a pipeline, but a person must " +
                "approve it - an agent never approves"
    )
    val manualApproval: Boolean,
)
