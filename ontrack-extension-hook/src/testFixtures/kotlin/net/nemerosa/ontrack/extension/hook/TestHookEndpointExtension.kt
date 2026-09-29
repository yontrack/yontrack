package net.nemerosa.ontrack.extension.hook

import net.nemerosa.ontrack.extension.api.support.TestExtensionFeature
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.it.SecurityTestSupport
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Component

@Component
class TestHookEndpointExtension(
    extension: TestExtensionFeature,
    private val testHookInfoLinkExtension: TestHookInfoLinkExtension,
    private val securityTestSupport: SecurityTestSupport,
) : AbstractExtension(extension), HookEndpointExtension {

    override var enabled: Boolean = true
    var denied: Boolean = false
    var error: Boolean = false

    private var token: String = ""

    override val id: String = "test"

    fun provisionToken(token: String?) {
        if (token.isNullOrBlank()) {
            this.token = securityTestSupport.provisionToken()
        } else {
            this.token = token
        }
    }

    override fun checkAccess(request: HookRequest): String {
        if (denied) {
            throw AccessDeniedException("Hook refused the connection.")
        }
        return token
    }

    override fun process(recordId: String, request: HookRequest): HookResponse {
        if (error) {
            throw RuntimeException("Error during the processing.")
        } else {
            return HookResponse(
                type = HookResponseType.PROCESSED,
                infoLink = testHookInfoLinkExtension.createHookInfoLink(
                    "Processing: ${request.body}"
                ),
            )
        }
    }

}