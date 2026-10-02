package net.nemerosa.ontrack.extension.hook

import io.micrometer.core.instrument.MeterRegistry
import net.nemerosa.ontrack.extension.api.ExtensionManager
import net.nemerosa.ontrack.extension.hook.metrics.*
import net.nemerosa.ontrack.extension.hook.records.HookRecordService
import net.nemerosa.ontrack.model.metrics.time
import net.nemerosa.ontrack.model.security.ActorVia
import net.nemerosa.ontrack.model.structure.TokensService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.annotation.*

/**
 * Generic hook controller
 */
@RestController
class HookController(
    private val extensionManager: ExtensionManager,
    private val meterRegistry: MeterRegistry,
    private val hookRecordService: HookRecordService,
    private val tokensService: TokensService,
) {

    private val logger: Logger = LoggerFactory.getLogger(HookController::class.java)

    /**
     * Unique entrypoint for the hooks.
     */
    @PostMapping("/hook/secured/{hook}")
    fun hook(
        @PathVariable hook: String,
        @RequestBody body: String,
        @RequestParam parameters: Map<String, String>,
        @RequestHeader headers: Map<String, String>,
    ): HookResponse {

        // Request representation
        val request = HookRequest(body, parameters, headers)
        val recordId = hookRecordService.onReceived(hook, request)

        // Getting the extension
        val endpoint = extensionManager.getExtensions(HookEndpointExtension::class.java)
            .find { it.id == hook }
        if (endpoint == null) {
            meterRegistry.hookUndefined(hook)
            hookRecordService.onUndefined(recordId)
            throw HookNotFoundException(hook)
        }

        // Checking if the endpoint is enabled
        if (!endpoint.enabled) {
            meterRegistry.hookDisabled(hook)
            hookRecordService.onDisabled(recordId)
            return hookDisabled()
        }

        // Checking the access
        try {
            val token = endpoint.checkAccess(request)
                .takeIf { it.isNotBlank() }
                ?: throw AccessDeniedException("No token was provided by the hook: $hook")
            if (!tokensService.useTokenForSecurityContext(token, ActorVia.WEBHOOK)) {
                throw AccessDeniedException("Token provided by the hook is denied: $hook")
            }
        } catch (any: Exception) {
            meterRegistry.hookAccessDenied(hook)
            hookRecordService.onDenied(recordId)
            throw any
        }

        // Processing
        return try {
            val result =
                meterRegistry.time(HookMetrics.time, "hook" to hook) {
                    endpoint.process(recordId, request)
                } ?: error("Processing did not return any result")
            meterRegistry.hookSuccess(hook)
            hookRecordService.onSuccess(recordId, result)
            result
        } catch (any: Exception) {
            meterRegistry.hookError(hook)
            logger.error("[$hook] Uncaught error while processing a hook", any)
            hookRecordService.onError(recordId, any)
            throw any
        }
    }
}