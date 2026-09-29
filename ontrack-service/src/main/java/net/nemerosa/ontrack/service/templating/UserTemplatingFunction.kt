package net.nemerosa.ontrack.service.templating

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.model.deprecation.DeprecationService
import net.nemerosa.ontrack.model.deprecation.DeprecationSurface
import net.nemerosa.ontrack.model.docs.Documentation
import net.nemerosa.ontrack.model.docs.DocumentationExampleCode
import net.nemerosa.ontrack.model.events.EventRenderer
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.templating.TemplatingFunction
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
    private val deprecationService: DeprecationService,
) : TemplatingFunction {

    override val id: String = "user"

    override fun render(
        config: TemplatingSourceConfig,
        context: Map<String, Any>,
        renderer: EventRenderer,
        expressionResolver: (expression: String) -> String
    ): String {
        val field = config.getString(UserTemplatingFunctionParameters::field.name)
            ?.let { UserTemplatingFunctionField.valueOf(it.uppercase()) }
            ?: UserTemplatingFunctionField.EMAIL
        @Suppress("DEPRECATION")
        if (field == UserTemplatingFunctionField.NAME) {
            deprecationService.deprecatedUsage(
                surface = DeprecationSurface.TEMPLATING,
                item = "#.user?field=name",
                message = "Removed in V6. Use field=email instead. See #1920",
            )
        }
        val account = securityService.currentUser?.account
        return if (account == null) {
            ""
        } else when (field) {
            UserTemplatingFunctionField.NAME -> account.email
            UserTemplatingFunctionField.DISPLAY -> account.fullName
            UserTemplatingFunctionField.EMAIL -> account.email
        }
    }
}
