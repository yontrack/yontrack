package net.nemerosa.ontrack.boot.support

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import net.nemerosa.ontrack.boot.ui.UIErrorMessage
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.format
import net.nemerosa.ontrack.model.security.ReadOnlyTokenException
import net.nemerosa.ontrack.model.security.SecurityService
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Refuses to a read-only token every call which is not a read, right after [TokenSecurityFilter]:
 * any method but `GET`, `HEAD` and `OPTIONS`.
 *
 * The [readPosts] are the only exemptions, and match exact paths: nothing below them is exempted, and
 * anything which is not exactly one of them, however it would be routed, is refused.
 */
@Component
class ReadOnlyTokenSecurityFilter(
    private val securityService: SecurityService,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val token = securityService.currentUser?.readOnlyTokenName
        if (token != null && !isRead(request)) {
            val message = ReadOnlyTokenException.request(token, request.method, path(request)).message ?: ""
            response.status = HttpServletResponse.SC_FORBIDDEN
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.characterEncoding = Charsets.UTF_8.name()
            response.writer.write(UIErrorMessage(HttpServletResponse.SC_FORBIDDEN, message).asJson().format())
            return
        }
        filterChain.doFilter(request, response)
    }

    private fun isRead(request: HttpServletRequest): Boolean =
        request.method in readMethods || (request.method == "POST" && path(request) in readPosts)

    private fun path(request: HttpServletRequest): String =
        request.requestURI.removePrefix(request.contextPath)

    companion object {

        private val readMethods = setOf("GET", "HEAD", "OPTIONS")

        /**
         * The `POST` end points which are really reads. A mutation sent to `/graphql` is refused by
         * the GraphQL layer.
         */
        private val readPosts = setOf("/graphql", "/rest/search")
    }

}
