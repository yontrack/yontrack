package net.nemerosa.ontrack.model.structure

import net.nemerosa.ontrack.model.security.ActorVia
import org.springframework.security.access.AccessDeniedException

fun TokensService.checkTokenForSecurityContext(
    token: String,
    message: String,
    via: ActorVia = ActorVia.TOKEN,
) {
    if (!useTokenForSecurityContext(token, via)) {
        throw AccessDeniedException(message)
    }
}
