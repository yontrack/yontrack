package net.nemerosa.ontrack.extension.hook

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.graphql.support.JSONType

/**
 * Data returned by a hook.
 */
@APIDescription("Data returned by a hook.")
data class HookResponse(
        /**
         * Type of response
         */
        @APIDescription("Type of response")
        val type: HookResponseType,
        /**
         * Additional information (non structured, will typically be rendered as JSON)
         */
        @APIDescription("Additional information (non structured, will typically be rendered as JSON)")
        @Deprecated("Removed in V6. Use infoLink instead. See #1922")
        @JSONType
        val info: Any? = null,
        /**
         * Structured additional information
         */
        @APIDescription("Structured additional information")
        val infoLink: HookInfoLink?
)
