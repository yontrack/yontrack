package net.nemerosa.ontrack.extension.notifications.http.mock

import net.nemerosa.ontrack.extension.notifications.NotificationsConfigProperties
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode
import java.time.Clock

/**
 * Mock deployment API, the target of the `http` channel in the KDSL acceptance tests and in the demo.
 *
 * - `GET` on its root describes it, for the connection test of an HTTP configuration pointing at it.
 * - `POST deployments` starts a deployment, and answers `201` with the deployment, `RUNNING`, and its
 *   `Location`. The body is optional; it may carry `outcome` (`SUCCEEDED` by default, or `FAILED`) and
 *   `durationSeconds` (a few seconds by default), and anything else, which is ignored.
 * - `GET deployments/{id}` returns the deployment: `RUNNING` until its duration has elapsed, then its
 *   outcome. An unknown id is `404`.
 *
 * It is stateless (see [MockDeployment]), and it is there only when
 * [NotificationsConfigProperties.HTTP_MOCK_ENABLED] is set: otherwise, its paths are those of no end
 * point - refused without an authentication, `404` with one. Where it is enabled, it answers without any
 * authentication (`WebSecurityConfig.mockDeploymentWebSecurity` in `ontrack-ui`), as an HTTP
 * configuration whose authentication is `none` calls it; [GQLRootQueryHttpMockDeploymentsUrl] tells
 * where the backend reaches it.
 */
@RestController
@RequestMapping(MockDeploymentController.PATH)
@ConditionalOnProperty(
    name = [NotificationsConfigProperties.HTTP_MOCK_ENABLED],
    havingValue = "true",
    matchIfMissing = false
)
class MockDeploymentController(
    /**
     * Context path of the backend, which the `Location` of a deployment starts with.
     */
    @Value("\${server.servlet.context-path:}")
    private val contextPath: String,
) {

    /**
     * Time of the deployments. Only the tests replace it.
     */
    internal var clock: Clock = Clock.systemUTC()

    /**
     * Root of the API, which is what the connection test of an HTTP configuration pointing at it reads
     * (a `GET` on its base URL): it answers rather than leaving it to an unknown path.
     */
    @GetMapping("")
    fun root() = MockDeploymentRoot(
        deployments = "${contextPath.trimEnd('/')}$PATH/deployments",
    )

    @PostMapping("/deployments")
    fun create(
        @RequestBody(required = false) body: JsonNode?,
    ): ResponseEntity<Any> {
        val (outcome, durationSeconds) = try {
            parseRequest(body)
        } catch (ex: MockDeploymentRequestException) {
            return ResponseEntity.badRequest().body(mapOf("message" to ex.message))
        }
        val deployment = MockDeployment.create(
            now = clock.instant(),
            durationSeconds = durationSeconds,
            outcome = outcome,
        )
        return ResponseEntity.status(HttpStatus.CREATED)
            .header(HttpHeaders.LOCATION, "${contextPath.trimEnd('/')}$PATH/deployments/${deployment.id}")
            // Always running when it is created: what is started is polled for its outcome
            .body(deployment.toResponse(MockDeploymentStatus.RUNNING))
    }

    @GetMapping("/deployments/{id}")
    fun get(@PathVariable id: String): ResponseEntity<MockDeploymentResponse> {
        val deployment = MockDeployment.parse(id)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(deployment.toResponse(deployment.statusAt(clock.instant())))
    }

    private fun MockDeployment.toResponse(status: MockDeploymentStatus) = MockDeploymentResponse(
        id = id,
        status = status,
        startedAt = startedAt.toString(),
        durationSeconds = durationSeconds,
    )

    private fun parseRequest(body: JsonNode?): Pair<MockDeploymentOutcome, Int> {
        if (body == null || body.isNull || body.isMissingNode) {
            return MockDeploymentOutcome.SUCCEEDED to MockDeployment.DEFAULT_DURATION_SECONDS
        }
        if (!body.isObject) {
            throw MockDeploymentRequestException("The body must be a JSON object.")
        }

        val outcomeNode = body.path(MockDeploymentRequest.OUTCOME)
        val outcome = if (outcomeNode.isNull || outcomeNode.isMissingNode) {
            MockDeploymentOutcome.SUCCEEDED
        } else {
            val text = outcomeNode.takeIf { it.isString }?.asString()
            MockDeploymentOutcome.entries.firstOrNull { it.name.equals(text, ignoreCase = true) }
                ?: throw MockDeploymentRequestException(
                    "The outcome must be one of ${MockDeploymentOutcome.entries.joinToString(", ")}."
                )
        }

        val durationNode = body.path(MockDeploymentRequest.DURATION_SECONDS)
        val durationSeconds = if (durationNode.isNull || durationNode.isMissingNode) {
            MockDeployment.DEFAULT_DURATION_SECONDS
        } else if (durationNode.isIntegralNumber && durationNode.canConvertToInt() &&
            durationNode.intValue() in 0..MockDeployment.MAX_DURATION_SECONDS
        ) {
            durationNode.intValue()
        } else {
            throw MockDeploymentRequestException(
                "The duration must be a whole number of seconds, between 0 and ${MockDeployment.MAX_DURATION_SECONDS}."
            )
        }

        return outcome to durationSeconds
    }

    companion object {
        /**
         * Root path of the mock deployment API, under the root of the backend.
         */
        const val PATH = "/extension/notifications/http/mock"
    }
}

/**
 * Root of the mock deployment API.
 *
 * @property deployments Path of the deployments
 */
data class MockDeploymentRoot(
    val deployments: String,
)

/**
 * Fields of the body of `POST deployments`.
 */
private object MockDeploymentRequest {
    const val OUTCOME = "outcome"
    const val DURATION_SECONDS = "durationSeconds"
}

private class MockDeploymentRequestException(message: String) : RuntimeException(message)

/**
 * Deployment, as the mock deployment API returns it.
 *
 * @property id Id of the deployment
 * @property status Status of the deployment: `RUNNING`, `SUCCEEDED` or `FAILED`
 * @property startedAt Creation time, in ISO-8601
 * @property durationSeconds Time the deployment is running for
 */
data class MockDeploymentResponse(
    val id: String,
    val status: MockDeploymentStatus,
    val startedAt: String,
    val durationSeconds: Int,
)
