package net.nemerosa.ontrack.kdsl.spec.admin

import com.apollographql.apollo.api.Optional
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.UserAgentPolicyQuery
import net.nemerosa.ontrack.kdsl.connector.graphqlConnector
import net.nemerosa.ontrack.kdsl.spec.Ontrack

/**
 * What the current agent may do on a project.
 *
 * @property owner Full name of the owner of the agent
 * @property canRecordEvidence Whether the agent may create builds and validation runs
 * @property promotionLevels Promotion levels the agent may promote to
 * @property slots Slots where the agent may start a deployment pipeline
 */
data class AgentPolicy(
    val owner: String,
    val canRecordEvidence: Boolean,
    val promotionLevels: List<AgentPolicyPromotionLevel>,
    val slots: List<AgentPolicySlot>,
)

/**
 * A promotion level the agent may promote to.
 */
data class AgentPolicyPromotionLevel(
    val id: Int,
    val branch: String,
    val name: String,
)

/**
 * A slot where the agent may start a deployment pipeline.
 *
 * @property manualApproval Whether a manual approval will still stop the agent
 */
data class AgentPolicySlot(
    val id: String,
    val environment: String,
    val qualifier: String,
    val manualApproval: Boolean,
)

/**
 * The policy of the current agent on a [project], with the promotion levels restricted to a [branch]
 * if given.
 *
 * @return The policy, `null` when the client is not connected as an agent
 */
fun Ontrack.agentPolicy(project: String, branch: String? = null): AgentPolicy? =
    graphqlConnector.query(
        UserAgentPolicyQuery(
            project = project,
            branch = Optional.presentIfNotNull(branch),
        )
    )?.user?.agentPolicy?.let { policy ->
        AgentPolicy(
            owner = policy.owner,
            canRecordEvidence = policy.canRecordEvidence,
            promotionLevels = policy.promotionLevels.map {
                AgentPolicyPromotionLevel(id = it.id, branch = it.branch, name = it.name)
            },
            slots = policy.slots.map {
                AgentPolicySlot(
                    id = it.id,
                    environment = it.environment,
                    qualifier = it.qualifier,
                    manualApproval = it.manualApproval,
                )
            },
        )
    }
