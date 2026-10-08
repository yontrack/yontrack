package net.nemerosa.ontrack.extension.agents.activity

import net.nemerosa.ontrack.extension.agents.AgentsExtensionFeature
import net.nemerosa.ontrack.extension.agents.license.AgentsLicense
import net.nemerosa.ontrack.extension.api.UserMenuItemExtension
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.model.support.CoreUserMenuGroups
import net.nemerosa.ontrack.model.support.UserMenuItem
import org.springframework.stereotype.Component

/**
 * *Latest agent actions*, `/extension/agents/actions`, in the information group of the user menu, when
 * the licence allows the agent governance (#2035). Every user reads it, restricted to the projects they
 * can see.
 */
@Component
class AgentActionsUserMenuItemExtension(
    agentsExtensionFeature: AgentsExtensionFeature,
    private val agentsLicense: AgentsLicense,
) : AbstractExtension(agentsExtensionFeature), UserMenuItemExtension {

    override val items: List<UserMenuItem>
        get() = listOfNotNull(
            UserMenuItem(
                groupId = CoreUserMenuGroups.INFORMATION,
                extension = feature,
                id = ID,
                name = NAME,
            ).takeIf {
                agentsLicense.agentsEnabled
            }
        )

    companion object {
        /**
         * ID of the menu item, and path of the page under the extension
         */
        const val ID = "actions"

        /**
         * Name of the menu item
         */
        const val NAME = "Latest agent actions"
    }
}
