package net.nemerosa.ontrack.extension.jenkins.mock

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import net.nemerosa.ontrack.extension.jenkins.JenkinsConfiguration
import net.nemerosa.ontrack.extension.jenkins.client.AbstractJenkinsClient
import net.nemerosa.ontrack.extension.jenkins.client.JenkinsBuild
import net.nemerosa.ontrack.extension.jenkins.client.JenkinsInfo
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

class MockJenkinsClient(
    configuration: JenkinsConfiguration,
) : AbstractJenkinsClient(
    url = configuration.url,
) {

    /**
     * Concurrent: the nodes of a workflow run in parallel, and two of them may build jobs of the
     * same mock Jenkins at once.
     */
    val jobs: MutableMap<String, MockJenkinsJob> = ConcurrentHashMap()

    override val info: JenkinsInfo
        get() = TODO("Not yet implemented")

    override fun runJob(
        job: String,
        parameters: Map<String, String>,
        retries: Int,
        retriesDelaySeconds: Int,
        buildFeedback: (build: JenkinsBuild) -> Unit,
    ): JenkinsBuild {
        val build = jobs.computeIfAbsent(job) { MockJenkinsJob(job) }.build(parameters)
        val result = parameters[PARAM_RESULT] ?: "SUCCESS"
        val waiting: Long? = parameters[PARAM_WAITING]?.toLong()?.let(::clampWaiting)
        if (waiting != null) {
            // Creating the ongoing build
            val ongoingBuild = JenkinsBuild(
                id = build.number.toString(),
                building = true,
                url = "$url/$job/${build.number}",
                result = null,
            )
            // Feedback
            buildFeedback(ongoingBuild)
            // Waiting
            runBlocking {
                delay(waiting)
            }
        }
        return JenkinsBuild(
            id = build.number.toString(),
            building = false,
            url = "$url/$job/${build.number}",
            result = result,
        )
    }

    override fun fireAndForgetJob(job: String, parameters: Map<String, String>): URI? {
        if (parameters["queued"] == "false") {
            return null
        } else {
            jobs.computeIfAbsent(job) { MockJenkinsJob(job) }.build(parameters)
            return URI("$url/queue/$job")
        }
    }

    companion object {
        /**
         * Ceiling of the `waiting` parameter: enabled outside of the `dev` profile, a build must not
         * be able to park the thread running it indefinitely.
         */
        const val MAX_WAITING_MS = 60_000L

        fun clampWaiting(waiting: Long): Long = waiting.coerceIn(0L, MAX_WAITING_MS)

        private const val PARAM_RESULT = "result"
        private const val PARAM_WAITING = "waiting"
    }
}