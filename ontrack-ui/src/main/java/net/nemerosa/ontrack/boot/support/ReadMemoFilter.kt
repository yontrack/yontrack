package net.nemerosa.ontrack.boot.support

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import net.nemerosa.ontrack.model.support.ReadMemo
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties
import org.springframework.core.Ordered
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Each HTTP request is a unit of work, with its [ReadMemo]: what it reads from the database - its
 * settings, for a start - is read once for the request, its authentication included, and fresh for
 * the next one (ADR 0021).
 *
 * Run just before the security filters, so that the authentication of the request reads its
 * security settings through the memo too.
 */
@Component
class ReadMemoFilter : OncePerRequestFilter(), Ordered {

    override fun getOrder(): Int = SecurityFilterProperties.DEFAULT_FILTER_ORDER - 1

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        ReadMemo.unitOfWork {
            filterChain.doFilter(request, response)
        }
    }
}
