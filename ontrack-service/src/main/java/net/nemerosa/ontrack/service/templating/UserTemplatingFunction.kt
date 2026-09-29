package net.nemerosa.ontrack.service.templating

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.model.docs.Documentation
import net.nemerosa.ontrack.model.docs.DocumentationExampleCode
import net.nemerosa.ontrack.model.events.EventRenderer
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.templating.TemplatingFunction
import net.nemerosa.ontrack.model.templating.TemplatingMisconfiguredConfigParamException
import net.nemerosa.ontrack.model.templating.TemplatingSourceConfig
import org.springframework.stereotype.Component

@Component
@APIDescription("Displays the current user")
@Documentation(UserTemplatingFunctionParameters::class)
@DocumentationExampleCode(
    """
       #.user 
    """
)
class UserTemplatingFunction(
    private val securityService: SecurityService,
) : TemplatingFunction {

    override val id: String = "user"

    override fun render(
        config: TemplatingSourceConfig,
        context: Map<String, Any>,
        renderer: EventRenderer,
        expressionResolver: (expression: String) -> String
    ): String {
        val field = config.getString(UserTemplatingFunctionParameters::field.name)
            ?.let { parseField(it) }
            ?: UserTemplatingFunctionField.EMAIL
        val account = securityService.currentUser?.account
        return if (account == null) {
            ""
        } else when (field) {
            UserTemplatingFunctionField.DISPLAY -> account.fullName
            UserTemplatingFunctionField.EMAIL -> account.email
        }
    }

    private fun parseField(value: String): UserTemplatingFunctionField =
        UserTemplatingFunctionField.entries.find { it.name.equals(value, ignoreCase = true) }
            ?: throw TemplatingMisconfiguredConfigParamException(
                UserTemplatingFunctionParameters::field.name,
                "Unknown field: $value. Use one of: ${
                    UserTemplatingFunctionField.entries.joinToString(", ") { it.name.lowercase() }
                }."
            )
}
