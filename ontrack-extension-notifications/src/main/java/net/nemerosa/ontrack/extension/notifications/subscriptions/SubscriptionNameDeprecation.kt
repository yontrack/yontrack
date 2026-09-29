package net.nemerosa.ontrack.extension.notifications.subscriptions

import net.nemerosa.ontrack.model.deprecation.DeprecationService
import net.nemerosa.ontrack.model.deprecation.DeprecationSurface

/**
 * Reports a subscription created without a name, which V6 no longer accepts.
 */
fun DeprecationService.namelessSubscription(surface: DeprecationSurface) {
    deprecatedUsage(
        surface = surface,
        item = "subscription without name",
        message = "Removed in V6. Use a named subscription instead. See #1927",
    )
}
