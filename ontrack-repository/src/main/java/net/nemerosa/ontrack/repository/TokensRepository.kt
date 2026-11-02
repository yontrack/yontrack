package net.nemerosa.ontrack.repository

import net.nemerosa.ontrack.model.security.Account
import net.nemerosa.ontrack.model.structure.Token
import java.time.LocalDateTime

/**
 * Access to the `TOKENS` table.
 */
interface TokensRepository {

    /**
     * Invalidates a token for the account designated by its [id].
     *
     * @param id Account ID
     * @param name Name of the token
     * @return Previous token or `null` if not existing.
     */
    fun invalidate(id: Int, name: String): String?

    /**
     * Saves a token.
     *
     * @param id ID of the account
     * @param name Name of the token
     * @param token Token to save
     * @param time Creation time
     * @param until End of the validity time
     * @param readOnly Is the token read-only?
     */
    fun save(id: Int, name: String, token: String, time: LocalDateTime, until: LocalDateTime?, readOnly: Boolean)

    /**
     * Gets a named token for an account.
     *
     * Note that the [Token.validUntil] property is always set to `null`.
     */
    fun getTokenForAccount(account: Account, name: String): Token?

    /**
     * Gets the account ID which is associated with this token, if any.
     */
    fun findAccountByToken(token: String): Pair<Int, Token>?

    /**
     * Revokes all tokens
     *
     * @return Number of tokens having been revoked
     */
    fun revokeAll(): Int

    /**
     * Gets all the tokens of an account.
     */
    fun getTokens(account: Account): List<Token>

    /**
     * Invalidates all the tokens for an account
     */
    fun invalidateAll(accountId: Int): List<String>

    /**
     * Given a token, updates its last used date
     */
    fun updateLastUsed(token: Token, lastUsed: LocalDateTime)

    /**
     * Given a token, updates its validity
     */
    fun updateValidUntil(token: Token, validUntil: LocalDateTime)

}