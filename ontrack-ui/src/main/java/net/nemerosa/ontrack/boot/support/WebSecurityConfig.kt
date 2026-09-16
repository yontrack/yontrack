package net.nemerosa.ontrack.boot.support

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor
import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest
import org.springframework.boot.actuate.endpoint.web.WebServerNamespace
import org.springframework.boot.web.context.WebServerApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.util.matcher.OrRequestMatcher
import org.springframework.security.web.util.matcher.RequestMatcher
import org.springframework.web.context.support.WebApplicationContextUtils
import java.util.function.Supplier

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
    fun apiWebSecurity(
        http: HttpSecurity,
        jwtDecoder: JwtDecoder,
    ): SecurityFilterChain {
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
                jwt {
                    if (ontrackConfigProperties.security.authorization.jwt.typ.isNotBlank()) {
                        this.jwtDecoder = customJwtDecoder(jwtDecoder, ontrackConfigProperties.security.authorization.jwt.typ)
                    }
                }
            }
            addFilterAfter<BearerTokenAuthenticationFilter>(tokenSecurityFilter)
            addFilterAfter<TokenSecurityFilter>(webSecurityFilter)
        }
        return http.build()
    }

    private fun customJwtDecoder(jwtDecoder: JwtDecoder, typ: String): JwtDecoder {
        if (jwtDecoder is NimbusJwtDecoder) {
            @Suppress("UNCHECKED_CAST")
            val jwtProcessor = jwtDecoder::class
                .java
                .getDeclaredField("jwtProcessor")
                .apply { isAccessible = true }
                .get(jwtDecoder) as ConfigurableJWTProcessor<SecurityContext>
            logger.info("Using a custom JWT `typ`: $typ")
            jwtProcessor.jwsTypeVerifier = DefaultJOSEObjectTypeVerifier(
                setOf(
                    JOSEObjectType(typ)
                )
            )
            return jwtDecoder
        } else if (jwtDecoder is SupplierJwtDecoder) {
            @Suppress("UNCHECKED_CAST")
            val delegate = jwtDecoder::class
                .java
                .getDeclaredField("delegate")
                .apply { isAccessible = true }
                .get(jwtDecoder) as Supplier<JwtDecoder>
            val delegateDecoder = delegate.get()
            return customJwtDecoder(delegateDecoder, typ)
        } else {
            return jwtDecoder
        }
    }

}