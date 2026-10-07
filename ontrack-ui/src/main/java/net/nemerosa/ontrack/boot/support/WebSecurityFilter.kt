package net.nemerosa.ontrack.boot.support

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import net.nemerosa.ontrack.model.security.Account
import net.nemerosa.ontrack.model.security.AccountLoginService
import net.nemerosa.ontrack.model.security.Actor
import net.nemerosa.ontrack.model.security.ActorAgent
import net.nemerosa.ontrack.model.security.AgentIdentifiers
import net.nemerosa.ontrack.model.security.ActorJwt
import net.nemerosa.ontrack.model.security.ActorVia
import net.nemerosa.ontrack.model.security.AuthenticationUserService
import net.nemerosa.ontrack.model.structure.TokenAuthenticationToken
import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class WebSecurityFilter(
    private val ontrackConfigProperties: OntrackConfigProperties,
    private val accountLoginService: AccountLoginService,
    private val authenticationUserService: AuthenticationUserService,
) : OncePerRequestFilter() {

    private val log: Logger = LoggerFactory.getLogger(WebSecurityFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authentication = SecurityContextHolder.getContext().authentication
        if (authentication != null && authentication.isAuthenticated) {
            // An agent never logs in through the identity provider: a JWT claiming an agent identifier is refused
            if (authentication is JwtAuthenticationToken) {
                val email = jwtEmail(authentication)
                if (!email.isNullOrBlank() && AgentIdentifiers.isAgentIdentifier(email)) {
                    log.warn("JWT refused because its email is an agent identifier: {}", email)
                    SecurityContextHolder.clearContext()
                    response.sendError(
                        HttpServletResponse.SC_UNAUTHORIZED,
                        "An agent never logs in through the identity provider."
                    )
                    return
                }
            }
            when (authentication) {
                is JwtAuthenticationToken -> accountFromJwt(authentication)?.let { account ->
                    authenticationUserService.asUser(account, actorFromJwt(account, authentication))
                }

                is TokenAuthenticationToken -> authentication.account.let { account ->
                    authenticationUserService.asUser(
                        account,
                        Actor(account = account.email, via = ActorVia.TOKEN, agent = ActorAgent.of(account)),
                    )
                }
            }
        }
        filterChain.doFilter(request, response)
    }

    /**
     * Actor of a JWT: the UI when the token was issued to one of its clients, a JWT otherwise.
     */
    private fun actorFromJwt(account: Account, jwtAuthenticationToken: JwtAuthenticationToken): Actor {
        val token = jwtAuthenticationToken.token
        val azp = token.getClaimAsString("azp")
        val ui = !azp.isNullOrBlank() && azp in ontrackConfigProperties.security.authorization.jwt.uiClients
        return Actor(
            account = account.email,
            via = if (ui) ActorVia.UI else ActorVia.JWT,
            jwt = ActorJwt(
                iss = token.getClaimAsString("iss"),
                sub = token.subject,
            ),
        )
    }

    private fun accountFromJwt(jwtAuthenticationToken: JwtAuthenticationToken): Account? {
        val debug = ontrackConfigProperties.security.authorization.jwt.debug
        if (debug) {
            jwtAuthenticationToken.token.claims.forEach { (key, value) ->
                log.debug("JWT claim {}: {}", key, value)
            }
        }
        val email = jwtEmail(jwtAuthenticationToken)
        if (debug) log.debug("JWT email {}", email)
        if (email.isNullOrBlank()) {
            if (debug) log.debug("JWT email not set - not authenticated")
            return null
        } else {
            var fullName = jwtAuthenticationToken.token.getClaim<String>("name")
            if (debug) log.debug("JWT full name {}", fullName)
            if (fullName.isNullOrBlank()) {
                val givenName = jwtAuthenticationToken.token.getClaim<String>("given_name")
                val familyName = jwtAuthenticationToken.token.getClaim<String>("family_name")
                if (debug) log.debug("JWT given name {}", givenName)
                if (debug) log.debug("JWT family name {}", familyName)
                if (!givenName.isNullOrBlank() && !familyName.isNullOrBlank()) {
                    fullName = "$givenName $familyName"
                }
            }
            if (fullName.isNullOrBlank()) {
                if (debug) log.debug("JWT no name found - using email")
                fullName = email
            }
            if (debug) log.debug("JWT full name {}", fullName)

            val groupsClaim = ontrackConfigProperties.security.authorization.jwt.claims.groups
                .takeIf { it.isNotBlank() }
                ?: "groups"
            val groups = jwtAuthenticationToken.token.getClaim<List<String>>(groupsClaim)
                ?: emptyList()

            return accountLoginService.login(email, fullName, groups)
        }
    }

    private fun jwtEmail(jwtAuthenticationToken: JwtAuthenticationToken): String? =
        getClaim(
            jwtAuthenticationToken,
            defaultClaimName = "email",
            customClaimName = ontrackConfigProperties.security.authorization.jwt.claims.email,
        )

    private fun getClaim(
        jwtAuthenticationToken: JwtAuthenticationToken,
        defaultClaimName: String,
        customClaimName: String? = null,
    ): String? {
        val value: String? = jwtAuthenticationToken.token.getClaim<String>(defaultClaimName)
        return if (value.isNullOrBlank()) {
            if (!customClaimName.isNullOrBlank()) {
                jwtAuthenticationToken.token.getClaim<String>(customClaimName)
            } else {
                null
            }
        } else {
            value
        }
    }
}