package net.nemerosa.ontrack.graphql.schema.agents

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.graphql.schema.Mutation
import net.nemerosa.ontrack.graphql.support.TypedMutationProvider
import net.nemerosa.ontrack.model.security.Account
import net.nemerosa.ontrack.model.security.AgentRegistrationInput
import net.nemerosa.ontrack.model.security.AgentService
import net.nemerosa.ontrack.model.security.AgentUpdateInput
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.Token
import org.springframework.stereotype.Component

/**
 * Management of the registered agents.
 */
@Component
class AgentMutations(
    private val agentService: AgentService,
) : TypedMutationProvider() {

    override val mutations: List<Mutation> = listOf(

        simpleMutation(
            name = "registerAgent",
            description = "Registers an agent. Any user registers agents for themselves; only an administrator " +
                    "registers one for another owner. The agent is created with no rights of its own.",
            input = RegisterAgentInput::class,
            outputName = "agent",
            outputDescription = "Registered agent",
            outputType = Account::class,
        ) { input ->
            agentService.registerAgent(
                AgentRegistrationInput(
                    slug = input.slug,
                    displayName = input.displayName,
                    tool = input.tool,
                    description = input.description,
                    owner = input.owner,
                )
            )
        },

        simpleMutation(
            name = "updateAgent",
            description = "Updates the display name, the tool and the description of an agent. Its slug never " +
                    "changes. Owner or administrator only.",
            input = UpdateAgentInput::class,
            outputName = "agent",
            outputDescription = "Updated agent",
            outputType = Account::class,
        ) { input ->
            agentService.updateAgent(
                ID.of(input.id),
                AgentUpdateInput(
                    displayName = input.displayName,
                    tool = input.tool,
                    description = input.description,
                )
            )
        },

        simpleMutation(
            name = "transferAgent",
            description = "Transfers an agent to another owner, who must be a person. Administrator only.",
            input = TransferAgentInput::class,
            outputName = "agent",
            outputDescription = "Transferred agent",
            outputType = Account::class,
        ) { input ->
            agentService.transferAgent(ID.of(input.id), input.owner)
        },

        unitMutation<DeleteAgentInput>(
            name = "deleteAgent",
            description = "Deletes an agent and its tokens. Its name stays on every signature. Owner or " +
                    "administrator only.",
        ) { input ->
            agentService.deleteAgent(ID.of(input.id))
        },

        simpleMutation(
            name = "generateAgentToken",
            description = "Generates a named token for an agent, under the usual validity rules of the " +
                    "instance. The value of the token is returned only this once. Owner or administrator only.",
            input = GenerateAgentTokenInput::class,
            outputName = "token",
            outputDescription = "Generated token, with its value",
            outputType = Token::class,
        ) { input ->
            agentService.generateAgentToken(ID.of(input.id), input.name)
        },

        unitMutation<RevokeAgentTokenInput>(
            name = "revokeAgentToken",
            description = "Revokes a named token of an agent. Owner or administrator only.",
        ) { input ->
            agentService.revokeAgentToken(ID.of(input.id), input.name)
        },

        unitMutation<RevokeAllAgentTokensInput>(
            name = "revokeAllAgentTokens",
            description = "Revokes all the tokens of an agent. Owner or administrator only.",
        ) { input ->
            agentService.revokeAllAgentTokens(ID.of(input.id))
        },
    )
}

@APIDescription("Registration of an agent")
data class RegisterAgentInput(
    @APIDescription("Slug of the agent: 1 to 32 lowercase letters, digits or dashes. The agent is identified as `<slug>[agent]`, and the slug never changes.")
    val slug: String,
    @APIDescription("Display name of the agent")
    val displayName: String,
    @APIDescription("Tool behind the agent: Claude Code, Codex, Copilot, Devin, Other, or any other name")
    val tool: String,
    @APIDescription("Optional description of the agent")
    val description: String? = null,
    @APIDescription("Email of the owner of the agent. The current user when not set; only an administrator can give another owner.")
    val owner: String? = null,
)

@APIDescription("Update of an agent")
data class UpdateAgentInput(
    @APIDescription("ID of the agent")
    val id: Int,
    @APIDescription("Display name of the agent")
    val displayName: String,
    @APIDescription("Tool behind the agent")
    val tool: String,
    @APIDescription("Optional description of the agent")
    val description: String? = null,
)

@APIDescription("Transfer of an agent to another owner")
data class TransferAgentInput(
    @APIDescription("ID of the agent")
    val id: Int,
    @APIDescription("Email of the new owner, a person")
    val owner: String,
)

@APIDescription("Deletion of an agent")
data class DeleteAgentInput(
    @APIDescription("ID of the agent")
    val id: Int,
)

@APIDescription("Generation of a token for an agent")
data class GenerateAgentTokenInput(
    @APIDescription("ID of the agent")
    val id: Int,
    @APIDescription("Name of the token, unique for the agent")
    val name: String,
)

@APIDescription("Revocation of a token of an agent")
data class RevokeAgentTokenInput(
    @APIDescription("ID of the agent")
    val id: Int,
    @APIDescription("Name of the token")
    val name: String,
)

@APIDescription("Revocation of all the tokens of an agent")
data class RevokeAllAgentTokensInput(
    @APIDescription("ID of the agent")
    val id: Int,
)
