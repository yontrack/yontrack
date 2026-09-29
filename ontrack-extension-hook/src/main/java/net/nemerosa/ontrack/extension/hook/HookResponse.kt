package net.nemerosa.ontrack.extension.hook

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import net.nemerosa.ontrack.common.api.APIDescription

/**
 * Data returned by a hook.
 *
 * The unstructured `info` field was removed in V6 (#1922), in favour of [infoLink]. It is ignored
 * when reading the hook records stored before.
 */
@APIDescription("Data returned by a hook.")
@JsonIgnoreProperties("info")
data class HookResponse(
        /**
         * Type of response
         */
        @APIDescription("Type of response")
        val type: HookResponseType,
        /**
         * Structured additional information
         */
        @APIDescription("Structured additional information")
        val infoLink: HookInfoLink?
)
