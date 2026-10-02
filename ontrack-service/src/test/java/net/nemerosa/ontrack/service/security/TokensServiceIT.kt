package net.nemerosa.ontrack.service.security

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.format
import net.nemerosa.ontrack.model.security.Account
import net.nemerosa.ontrack.model.security.Actor
import net.nemerosa.ontrack.model.security.ActorVia
import net.nemerosa.ontrack.model.security.AccountManagement
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.TokenOptions
import net.nemerosa.ontrack.model.structure.TokensService
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.context.SecurityContextHolder
import java.time.Duration
import java.util.*
import kotlin.test.*

class TokensServiceIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var tokensService: TokensService

    @Test
    fun `Getting a token returns none when no token is set`() {
        asUser {
            assertNull(tokensService.getCurrentToken(uid("ot_")))
        }
    }

    @Test
    fun `Getting the current token when a token is set`() {
        asUser {
            val token = tokensService.generateNewToken(TokenOptions(name = "test"))
            assertNotNull(tokensService.getCurrentToken("test"), "Could find the token") {
                assertEquals(token.value, it.value)
                assertNotNull(it.creation)
                assertNull(it.validUntil)
            }
        }
    }

    @Test
    fun `Getting the current token with a validity period being set`() {
        asUser {
            withCustomTokenValidityDuration(Duration.ofDays(1)) {
                tokensService.generateNewToken(TokenOptions(name = "test"))
            }
            assertNotNull(tokensService.getCurrentToken("test")) {
                assertTrue(it.value.isNotBlank())
                assertNotNull(it.creation)
                assertNotNull(it.validUntil) { until ->
                    assertTrue(until > it.creation, "Validity is set and in the future of the creation period")
                }
            }
        }
    }

    @Test
    fun `Revoking a token`() {
        asUser {
            tokensService.generateNewToken(TokenOptions("test"))
            assertNotNull(tokensService.getCurrentToken("test"))
            tokensService.revokeToken("test")
            assertNull(tokensService.getCurrentToken("test"))
        }
    }

    @Test
    fun `Unknown token`() {
        val t = tokensService.findAccountByToken(UUID.randomUUID().toString())
        assertNull(t, "Token not found")
    }

    @Test
    fun `Bound user`() {
        asUser {
            val accountId = securityService.currentUser?.account?.id()
                ?: fail("No current account")
            tokensService.generateNewToken(TokenOptions("test"))
            val token = tokensService.getCurrentToken("test")!!.value
            val t = tokensService.findAccountByToken(token)
            assertNotNull(t, "Token found") {
                assertEquals(token, it.token.value)
                assertNotNull(it.token.creation)
                assertNull(it.token.validUntil)
                assertEquals(accountId, it.account.id(), "Same account")
            }
        }
    }

    @Test
    fun `Token not found when invalidated`() {
        asUser {
            tokensService.generateNewToken(TokenOptions("test"))
            val token = tokensService.getCurrentToken("test")!!.value
            val t = tokensService.findAccountByToken(token)
            assertNotNull(t, "Token found")
            // Invalidates the token
            tokensService.revokeToken("test")
            val tt = tokensService.findAccountByToken(token)
            assertNull(tt)
        }
    }

    @Test
    fun `Get the token of an account`() {
        asUser {
            val token = tokensService.generateNewToken(TokenOptions("test"))
            // Gets the account ID
            val accountId = securityService.currentUser?.account?.id()
                ?: fail("No current account")
            asUserWith<AccountManagement> {
                val result = tokensService.getTokens(accountId).find { it.name == "test" }
                assertNotNull(result) {
                    assertEquals(token.value, it.value)
                }
            }
        }
    }

    @Test
    fun `Revoke an account`() {
        asUser {
            tokensService.generateNewToken(TokenOptions("test"))
            val accountId = securityService.currentUser?.account?.id()
                ?: fail("No current account")
            asUserWith<AccountManagement> {
                tokensService.revokeAllTokens(accountId)
            }
            assertNull(tokensService.getCurrentToken("test"))
        }
    }

    @Test
    fun `Revoke all tokens`() {
        val accounts = (1..3).map { accountWithToken() }
        asAdmin {
            // Checks that they all have tokens
            accounts.forEach { account ->
                val tokens = tokensService.getTokens(account)
                assertTrue(tokens.isNotEmpty(), "Tokens are set")
            }
            // Revokes all tokens
            asUserWith<AccountManagement> {
                val count = tokensService.revokeAll()
                assertTrue(count >= 3)
            }
            // Checks that all tokens are gone
            accounts.forEach { account ->
                val tokens = tokensService.getTokens(account)
                assertTrue(tokens.isEmpty(), "Tokens are gone")
            }
        }
    }

    @Test
    fun `Changing the validity of a token to a shorter one with unlimited defaults`() {
        asUser {
            val id = securityService.currentUser?.account?.id()
                ?: fail("No current account")
            asAdmin {
                val t = tokensService.generateToken(
                    accountId = id,
                    options = TokenOptions(
                        name = "test",
                        validity = Duration.ofDays(14),
                        forceUnlimited = false,
                    )
                )
                assertNotNull(t.validUntil) {
                    assertFalse(t.isValid(Time.now() + Duration.ofDays(15)))
                }
            }
        }
    }

    @Test
    fun `Generating a token with default duration`() {
        withCustomTokenValidityDuration(Duration.ofDays(14)) {
            asUser {
                val id = securityService.currentUser?.account?.id()
                    ?: fail("No current account")
                asAdmin {
                    val t = tokensService.generateToken(
                        accountId = id,
                        options = TokenOptions(
                            name = "test",
                            validity = null,
                            forceUnlimited = false,
                        )
                    )
                    assertNotNull(t.validUntil) {
                        assertFalse(t.isValid(Time.now() + Duration.ofDays(15)))
                    }
                }
            }
        }
    }

    @Test
    fun `Generating a token with unlimited duration`() {
        withCustomTokenValidityDuration(Duration.ofDays(14)) {
            asUser {
                val id = securityService.currentUser?.account?.id()
                    ?: fail("No current account")
                asAdmin {
                    val t = tokensService.generateToken(
                        accountId = id,
                        options = TokenOptions(
                            name = "test",
                            validity = null,
                            forceUnlimited = true,
                        )
                    )
                    assertNull(t.validUntil, "Unlimited token")
                }
            }
        }
    }

    @Test
    fun `Getting an account for a given token set the last used date`() {
        asUser {
            val token = tokensService.generateNewToken(TokenOptions("test"))
            assertNull(token.lastUsed, "Last used date not set on creation")
            // Getting the account for this token
            val tokenAccount = tokensService.findAccountByToken(token.value)
            assertNotNull(tokenAccount, "Account found")
            // Getting the tokens for this account
            asAdmin {
                val firstToken = tokensService.getTokens(tokenAccount.account).first()
                assertNotNull(firstToken.lastUsed, "Last used date has been set")
            }
        }
    }

    @Test
    fun `Using a token for the security context keeps its name as the actor`() {
        val (email, token) = asUser {
            val token = tokensService.generateNewToken(TokenOptions("pipeline"))
            securityService.currentUser?.account?.email to token.value
        }
        val actor = withEmptySecurityContext {
            assertTrue(tokensService.useTokenForSecurityContext(token))
            securityService.currentActor
        }
        assertEquals(
            Actor(account = email!!, via = ActorVia.TOKEN, tokenName = "pipeline"),
            actor,
        )
        assertFalse(actor.asJson().format().contains(token), "The actor never carries the value of the token")
    }

    @Test
    fun `Using a token for the security context of a webhook`() {
        val (email, token) = asUser {
            val token = tokensService.generateNewToken(TokenOptions("hook"))
            securityService.currentUser?.account?.email to token.value
        }
        val actor = withEmptySecurityContext {
            assertTrue(tokensService.useTokenForSecurityContext(token, ActorVia.WEBHOOK))
            securityService.currentActor
        }
        assertEquals(
            Actor(account = email!!, via = ActorVia.WEBHOOK, tokenName = "hook"),
            actor,
        )
    }

    @Test
    fun `An invalid token sets no actor`() {
        val actor = withEmptySecurityContext {
            assertFalse(tokensService.useTokenForSecurityContext(UUID.randomUUID().toString()))
            securityService.currentActor
        }
        assertNull(actor)
    }

    private fun <T> withEmptySecurityContext(code: () -> T): T {
        val oldContext = SecurityContextHolder.getContext()
        return try {
            SecurityContextHolder.setContext(SecurityContextHolder.createEmptyContext())
            code()
        } finally {
            SecurityContextHolder.setContext(oldContext)
        }
    }

    private fun accountWithToken(): Account {
        return asUser {
            tokensService.generateNewToken(TokenOptions("test"))
            val id = securityService.currentUser?.account?.id()
                ?: fail("No current account")
            asAdmin {
                accountService.getAccount(ID.of(id))
            }
        }
    }

    private fun <T> withCustomTokenValidityDuration(duration: Duration, code: () -> T): T {
        val old = ontrackConfigProperties.security.tokens.validity
        return try {
            ontrackConfigProperties.security.tokens.validity = duration
            code()
        } finally {
            ontrackConfigProperties.security.tokens.validity = old
        }
    }

}