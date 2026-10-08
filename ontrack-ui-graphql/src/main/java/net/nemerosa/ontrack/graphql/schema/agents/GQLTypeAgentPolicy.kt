package net.nemerosa.ontrack.graphql.schema.agents

import graphql.Scalars.GraphQLBoolean
import graphql.Scalars.GraphQLString
import graphql.schema.GraphQLObjectType
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.support.GraphQLBeanConverter
import net.nemerosa.ontrack.graphql.support.listType
import net.nemerosa.ontrack.graphql.support.stringArgument
import net.nemerosa.ontrack.graphql.support.toNotNull
import net.nemerosa.ontrack.model.annotations.getAPITypeDescription
import net.nemerosa.ontrack.model.annotations.getAPITypeName
import net.nemerosa.ontrack.model.security.AgentPolicyPromotionLevel
import net.nemerosa.ontrack.model.security.AgentPolicyService
import net.nemerosa.ontrack.model.security.AgentPolicySlot
import net.nemerosa.ontrack.model.security.AgentProjectPolicy
import org.springframework.stereotype.Component

/**
 * What the current agent may do on a project - `user.agentPolicy`.
 */
@Component
class GQLTypeAgentPolicy(
    private val agentPolicyService: AgentPolicyService,
) : GQLType {

    override fun getTypeName(): String = getAPITypeName(AgentProjectPolicy::class)

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description(getAPITypeDescription(AgentProjectPolicy::class))
            .field {
                it.name(AgentProjectPolicy::owner.name)
                    .description("Full name of the owner of the agent: the person to ask when the policy stops the agent")
                    .type(GraphQLString.toNotNull())
            }
            .field {
                it.name(AgentProjectPolicy::canRecordEvidence.name)
                    .description("True when the agent may create builds and validation runs on the project")
                    .type(GraphQLBoolean.toNotNull())
            }
            .field {
                it.name("promotionLevels")
                    .description(
                        "Promotion levels of the enabled branches of the project which admit agents, and on which " +
                                "the owner of the agent may promote. Empty when the owner may not promote."
                    )
                    .argument(stringArgument(ARG_BRANCH, "Name of a branch, to restrict the levels to this branch"))
                    .type(
                        // Non-null list of non-null items
                        listType(
                            cache.getOrCreate(getAPITypeName(AgentPolicyPromotionLevel::class)) {
                                GraphQLBeanConverter.asObjectType(AgentPolicyPromotionLevel::class, cache)
                            }
                        )
                    )
                    .dataFetcher { env ->
                        val policy: AgentProjectPolicy = env.getSource()!!
                        agentPolicyService.getAdmittedPromotionLevels(policy.project, env.getArgument(ARG_BRANCH))
                    }
            }
            .field {
                it.name("slots")
                    .description(
                        "Slots of the project which admit agents, and where the owner of the agent may start a " +
                                "deployment pipeline, each one saying whether a manual approval will still stop the agent"
                    )
                    .type(
                        // Non-null list of non-null items
                        listType(
                            cache.getOrCreate(getAPITypeName(AgentPolicySlot::class)) {
                                GraphQLBeanConverter.asObjectType(AgentPolicySlot::class, cache)
                            }
                        )
                    )
                    .dataFetcher { env ->
                        val policy: AgentProjectPolicy = env.getSource()!!
                        agentPolicyService.getAdmittedSlots(policy.project)
                    }
            }
            .build()

    companion object {
        private const val ARG_BRANCH = "branch"
    }
}
