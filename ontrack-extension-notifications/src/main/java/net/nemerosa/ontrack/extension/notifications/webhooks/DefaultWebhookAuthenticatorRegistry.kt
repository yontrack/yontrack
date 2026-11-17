package net.nemerosa.ontrack.extension.notifications.webhooks

import net.nemerosa.ontrack.extension.notifications.http.auth.HttpAuthenticator
import net.nemerosa.ontrack.extension.notifications.http.auth.HttpAuthenticators
import net.nemerosa.ontrack.extension.notifications.http.auth.NoneHttpAuthenticator
import org.springframework.stereotype.Component

@Component
class DefaultWebhookAuthenticatorRegistry : WebhookAuthenticatorRegistry {

    override val authenticators: List<HttpAuthenticator<*>> =
        HttpAuthenticators.authenticators.filter { it.type != NoneHttpAuthenticator.TYPE }

    private val index = authenticators.associateBy { it.type }

    override fun findWebhookAuthenticator(type: String): HttpAuthenticator<*>? = index[type]

}
