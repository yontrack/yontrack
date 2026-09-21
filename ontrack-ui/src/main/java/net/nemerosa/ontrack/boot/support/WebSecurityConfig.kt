package net.nemerosa.ontrack.boot.support

import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest
import org.springframework.boot.actuate.endpoint.web.WebServerNamespace
import org.springframework.boot.web.server.context.WebServerApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtTypeValidator
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.util.matcher.OrRequestMatcher
import org.springframework.security.web.util.matcher.RequestMatcher
import org.springframework.web.context.support.WebApplicationContextUtils

@Configuration
class WebSecurityConfig(
    private val webSecurityFilter: WebSecurityFilter,
    private val tokenSecurityFilter: TokenSecurityFilter,
    private val ontrackConfigProperties: OntrackConfigProperties,
) {

    private val logger: Logger = LoggerFactory.getLogger(WebSecurityConfig::class.java)

    /**
     * Any request served by the separate management server - the child context Spring Boot starts
     * when `management.server.port` differs from `server.port`.
     */
    private val managementServerRequest = RequestMatcher { request ->
        WebServerApplicationContext.hasServerNamespace(
            WebApplicationContextUtils.getWebApplicationContext(request.servletContext),
            WebServerNamespace.MANAGEMENT.value,
        )
    }

    /**
     * Management end points are accessible on a separate port without any authentication needed.
     *
     * The chain covers the whole management server, not only its exposed end points: otherwise a
     * request for an end point which is not exposed falls through to the API chain and answers
     * `401`, telling the caller the end point exists behind authentication, instead of `404` (#1772).
     */
    @Bean
    fun actuatorWebSecurity(http: HttpSecurity): SecurityFilterChain {
        http.securityMatcher(OrRequestMatcher(EndpointRequest.toAnyEndpoint(), managementServerRequest))
            .authorizeHttpRequests { requests ->
                requests.anyRequest().permitAll()
            }
            .csrf { it.disable() }
        return http.build()
    }

    /**
     * API login
     */
    @Bean
    fun apiWebSecurity(http: HttpSecurity): SecurityFilterChain {
        http {
            authorizeHttpRequests {
                authorize("/hook/secured/**", permitAll)
                authorize(anyRequest, authenticated)
            }
            csrf { disable() }
            // Answers the CORS preflights before authentication, using the mappings of WebConfig (#1771)
            cors { }
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
            oauth2ResourceServer {
                jwt { }
            }
            addFilterAfter<BearerTokenAuthenticationFilter>(tokenSecurityFilter)
            addFilterAfter<TokenSecurityFilter>(webSecurityFilter)
        }
        return http.build()
    }

    /**
     * Custom JWT `typ`, accepted in place of the standard `JWT`.
     *
     * Spring Boot adds every `OAuth2TokenValidator<Jwt>` bean to the validators of the JWT decoder
     * it configures, and Spring Security then leaves out its default `JwtTypeValidator.jwt()`.
     */
    @Bean
    @ConditionalOnExpression("'\${ontrack.config.security.authorization.jwt.typ:}'.trim() != ''")
    fun jwtTypeValidator(): OAuth2TokenValidator<Jwt> {
        val typ = ontrackConfigProperties.security.authorization.jwt.typ.trim()
        logger.info("Using a custom JWT `typ`: $typ")
        return JwtTypeValidator(typ)
    }

}