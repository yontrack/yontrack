package net.nemerosa.ontrack.boot.support

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import net.nemerosa.ontrack.model.structure.TokensConstants
import net.nemerosa.ontrack.model.structure.TokensService
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Authenticates a request carrying an API token (`X-Ontrack-Token`).
 *
 * The authentication is kept in the request attributes, as the JWT one is, so that it survives the
 * error dispatch: a call to a path which does not exist then answers `404` to the caller of a
 * token, as to any other authenticated caller, instead of `401` (#1969).
 */
@Component
class TokenSecurityFilter(
    private val tokensService: TokensService,
) : OncePerRequestFilter() {

    private val securityContextRepository = RequestAttributeSecurityContextRepository()

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val token = request.getHeader(TokensConstants.HTTP_ONTRACK_TOKEN)
        if (!token.isNullOrBlank()) {
            val success = tokensService.useTokenForSecurityContext(token)
            if (!success) {
                response.status = HttpServletResponse.SC_UNAUTHORIZED
                response.writer.write("Invalid API token")
                return
            }
            securityContextRepository.saveContext(SecurityContextHolder.getContext(), request, response)
        }
        filterChain.doFilter(request, response)
    }

}