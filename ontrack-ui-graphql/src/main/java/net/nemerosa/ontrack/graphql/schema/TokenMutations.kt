package net.nemerosa.ontrack.graphql.schema

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.graphql.support.TypedMutationProvider
import net.nemerosa.ontrack.model.structure.Token
import net.nemerosa.ontrack.model.structure.TokenOptions
import net.nemerosa.ontrack.model.structure.TokensService
import org.springframework.stereotype.Component

@Component
class TokenMutations(
    private val tokensService: TokensService,
) : TypedMutationProvider() {
    override val mutations: List<Mutation> = listOf(
        simpleMutation(
            name = "generateToken",
            description = "Generate a new token",
            input = GenerateTokenInput::class,
            outputName = "token",
            outputDescription = "Generated token",
            outputType = Token::class,
        ) { input ->
            tokensService.generateNewToken(
                TokenOptions(
                    name = input.name,
                    readOnly = input.readOnly ?: false,
                )
            )
        },
        unitMutation<RevokeTokenInput>(
            name = "revokeToken",
            description = "Revoke the given token",
        ) { input ->
            tokensService.revokeToken(input.name)
        },
        unitMutation<RevokeAccountTokensInput>(
            name = "revokeAccountTokens",
            description = "Revoke all tokens of the given account",
        ) { input ->
            tokensService.revokeAllTokens(input.accountId)
        },
        unitNoInputMutation(
            name = "revokeAllTokens",
            description = "Revokes all tokens",
        ) {
            tokensService.revokeAll()
        }
    )
}

data class GenerateTokenInput(
    @APIDescription("Name of the token, unique for the account")
    val name: String,
    @APIDescription("Is the token read-only: it reads what its account can read, and changes nothing. Never changes afterwards. False when not set.")
    val readOnly: Boolean? = null,
)

data class RevokeTokenInput(
    val name: String,
)

data class RevokeAccountTokensInput(
    val accountId: Int,
)

