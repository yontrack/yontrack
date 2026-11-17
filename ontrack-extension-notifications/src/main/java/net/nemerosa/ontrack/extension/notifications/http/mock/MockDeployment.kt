package net.nemerosa.ontrack.extension.notifications.http.mock

import java.security.SecureRandom
import java.time.Instant

/**
 * Deployment of the mock deployment API.
 *
 * It is **stateless**: its creation time, its duration and its outcome are encoded in its id, so that any
 * backend replica answers for it, and none keeps anything in memory (ADR 0021). The id is
 * `<creation time in epoch milliseconds>-<duration in seconds>-<succeeded|failed>-<nonce>`, the nonce
 * telling apart two deployments created at the same time.
 *
 * @property id Id of the deployment, which encodes all of it
 * @property startedAt Creation time
 * @property durationSeconds Time the deployment is running for
 * @property outcome Status of the deployment once it is over
 */
data class MockDeployment(
    val id: String,
    val startedAt: Instant,
    val durationSeconds: Int,
    val outcome: MockDeploymentOutcome,
) {

    /**
     * Status of the deployment at a given time: running until its duration has elapsed, then its outcome.
     */
    fun statusAt(now: Instant): MockDeploymentStatus =
        if (now.isBefore(startedAt.plusSeconds(durationSeconds.toLong()))) {
            MockDeploymentStatus.RUNNING
        } else {
            outcome.status
        }

    companion object {

        /**
         * Default time a deployment is running for: one polling interval, at least, of the `http` channel.
         */
        const val DEFAULT_DURATION_SECONDS = 5

        /**
         * Longest time a deployment can run for.
         */
        const val MAX_DURATION_SECONDS = 3600

        private val random = SecureRandom()

        private val idRegex = Regex("^(\\d{1,15})-(\\d{1,4})-(succeeded|failed)-([0-9a-f]{8})$")

        /**
         * Creates a deployment.
         *
         * @param now Creation time
         * @param durationSeconds Time the deployment is running for, between 0 and [MAX_DURATION_SECONDS]
         * @param outcome Status of the deployment once it is over
         * @param nonce Eight lowercase hexadecimal characters, random by default
         */
        fun create(
            now: Instant,
            durationSeconds: Int,
            outcome: MockDeploymentOutcome,
            nonce: String = randomNonce(),
        ): MockDeployment {
            require(durationSeconds in 0..MAX_DURATION_SECONDS) {
                "The duration must be between 0 and $MAX_DURATION_SECONDS seconds."
            }
            val startedAt = Instant.ofEpochMilli(now.toEpochMilli())
            return MockDeployment(
                id = "${startedAt.toEpochMilli()}-$durationSeconds-${outcome.name.lowercase()}-$nonce",
                startedAt = startedAt,
                durationSeconds = durationSeconds,
                outcome = outcome,
            )
        }

        /**
         * Reads a deployment from its id.
         *
         * @return The deployment, or `null` when the id is not one of a deployment
         */
        fun parse(id: String): MockDeployment? {
            val match = idRegex.matchEntire(id) ?: return null
            val (millis, duration, outcome, _) = match.destructured
            val durationSeconds = duration.toInt()
            if (durationSeconds > MAX_DURATION_SECONDS) {
                return null
            }
            return MockDeployment(
                id = id,
                startedAt = Instant.ofEpochMilli(millis.toLong()),
                durationSeconds = durationSeconds,
                outcome = MockDeploymentOutcome.valueOf(outcome.uppercase()),
            )
        }

        private fun randomNonce(): String = "%08x".format(random.nextInt())
    }
}

/**
 * Status of a deployment of the mock deployment API, as its API returns it.
 */
enum class MockDeploymentStatus {
    RUNNING,
    SUCCEEDED,
    FAILED,
}

/**
 * Status of a deployment of the mock deployment API once it is over.
 */
enum class MockDeploymentOutcome(val status: MockDeploymentStatus) {
    SUCCEEDED(MockDeploymentStatus.SUCCEEDED),
    FAILED(MockDeploymentStatus.FAILED),
}
