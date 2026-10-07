package net.nemerosa.ontrack.service.security

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.model.security.*
import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import net.nemerosa.ontrack.repository.TokensRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional
class TokensServiceImpl(
    private val tokensRepository: TokensRepository,
    private val securityService: SecurityService,
    private val tokenGenerator: TokenGenerator,
    private val ontrackConfigProperties: OntrackConfigProperties,
    private val accountService: AccountService,
    private val authenticationUserService: AuthenticationUserService,
) : TokensService {

    override fun getCurrentToken(name: String): Token? {
        // Gets the current account
        val account = securityService.currentUser?.account
        // Gets the token of this account
        return account?.let {
            tokensRepository.getTokenForAccount(account, name)
        }
    }

    override fun generateNewToken(options: TokenOptions): Token {
        // Gets the current account
        val account = securityService.currentUser?.account
            ?: throw TokenGenerationNoAccountException()
        // Checking if a token with the same name already exists
        val existing = tokensRepository.getTokenForAccount(account, options.name)
        if (existing != null) {
            throw TokenGenerationNameAlreadyExistsException(options.name)
        }
        // Generates a new token
        return securityService.asAdmin { generateToken(account.id(), options) }
    }

    override fun generateToken(accountId: Int, options: TokenOptions): Token {
        securityService.checkGlobalFunction(AccountManagement::class.java)
        // Generates a new token
        val token = tokenGenerator.generateToken()
        // Validity
        val systemValidity = ontrackConfigProperties.security.tokens.validity
        val actualValidity = if (options.forceUnlimited) {
            options.validity
        } else {
            options.validity ?: systemValidity
        }
        // Token object
        val creation = Time.now()
        val validUntil = if (actualValidity != null && !actualValidity.isZero && !actualValidity.isNegative) {
            creation + actualValidity
        } else {
            null
        }
        val tokenObject = Token(options.name, token, creation, validUntil, lastUsed = null)
        // Saves the token...
        tokensRepository.save(
            id = accountId,
            name = options.name,
            token = token,
            time = tokenObject.creation,
            until = tokenObject.validUntil
        )
        // ... and returns it
        return tokenObject
    }

    override fun revokeToken(name: String) {
        // Gets the current account
        val account = securityService.currentUser?.account
        // Revokes its token
        account?.apply {
            tokensRepository.invalidate(id(), name)
        }
    }

    override fun getTokens(account: Account): List<Token> {
        securityService.checkGlobalFunction(AccountManagement::class.java)
        return tokensRepository.getTokens(account)
    }

    override fun getTokens(accountId: Int): List<Token> {
        return getTokens(accountService.getAccount(ID.of(accountId)))
    }

    override fun isValid(token: String): Boolean = tokensRepository
        .findAccountByToken(token)
        ?.let { (_, result) ->
            result.isValid()
        }
        ?: false

    override fun useTokenForSecurityContext(
        token: String,
        via: ActorVia,
        agentSession: AgentSessionHeaders?,
    ): Boolean {
        val tokenAccount = findAccountByToken(token)
        return if (tokenAccount == null || !tokenAccount.token.valid) {
            false
        } else {
            // The session headers are read for an agent only
            val agent = ActorAgent.of(tokenAccount.account)
            authenticationUserService.asUser(
                account = tokenAccount.account,
                actor = Actor(
                    account = tokenAccount.account.email,
                    via = via,
                    tokenName = tokenAccount.token.name,
                    agent = agent,
                    agentSession = agent?.let { agentSession?.parse() },
                ),
            )
            true
        }
    }

    override fun findAccountByToken(token: String, refTime: LocalDateTime): TokenAccount? {
        // Find the account ID
        val result = tokensRepository.findAccountByToken(token)
        return result?.let { (accountId, token) ->
            // Updating the "last used" date
            tokensRepository.updateLastUsed(token, refTime)
            // OK, returning the account AND the token
            TokenAccount(
                securityService.asAdmin {
                    accountService.getAccount(ID.of(accountId))
                },
                token.withLastUsed(refTime)
            )
        }
    }

    override fun revokeAll(): Int {
        securityService.checkGlobalFunction(AccountManagement::class.java)
        return tokensRepository.revokeAll()
    }

    override fun revokeToken(accountId: Int, name: String) {
        securityService.checkGlobalFunction(AccountManagement::class.java)
        tokensRepository.invalidate(accountId, name)
    }

    override fun revokeAllTokens(accountId: Int) {
        securityService.checkGlobalFunction(AccountManagement::class.java)
        tokensRepository.invalidateAll(accountId)
    }
}