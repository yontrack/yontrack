package net.nemerosa.ontrack.extension.kubernetes.autoversioning

import net.nemerosa.ontrack.common.BaseException
import net.nemerosa.ontrack.extension.av.postprocessing.PostProcessingFailureException
import net.nemerosa.ontrack.extension.av.retry.AutoVersioningRetryableException

/**
 * The post-processing configuration is refused: invalid, or outside an allow-list.
 */
class KubernetesPostProcessingConfigException(message: String) : BaseException(message)

/**
 * The Kubernetes API failed while the Job was being created. Nothing ran: the auto-versioning
 * order may succeed if it is run again later.
 */
class KubernetesPostProcessingTransientException(
    cause: Exception,
) : BaseException(
    cause,
    "Kubernetes post processing job could not be created: ${cause.message ?: cause::class.java.name}"
), AutoVersioningRetryableException

/**
 * A Job ran and did not succeed.
 *
 * There is no page to link to: [link] is empty, and the message carries what a link would have
 * shown.
 *
 * @property namespace Namespace of the Job
 * @property job Name of the Job
 * @property step Failing step (`clone`, `run` or `push`), `null` when it could not be told
 * @property exitCode Exit code of the failing step, `null` on a timeout or when unknown
 * @property timedOut `true` when the Job ran out of time
 * @property logs Tail of the logs of the failing step, empty when they could not be read
 */
class KubernetesPostProcessingFailureException(
    val namespace: String,
    val job: String,
    val step: String?,
    val exitCode: Int?,
    val timedOut: Boolean,
    val logs: String,
) : BaseException(message(namespace, job, step, exitCode, timedOut, logs)), PostProcessingFailureException {

    override val link: String = ""

    companion object {
        fun message(
            namespace: String,
            job: String,
            step: String?,
            exitCode: Int?,
            timedOut: Boolean,
            logs: String,
        ): String {
            val where = "Kubernetes post processing job $namespace/$job"
            val what = when {
                timedOut && step != null -> "timed out in the `$step` step"
                timedOut -> "timed out"
                step != null && exitCode != null -> "failed in the `$step` step with exit code $exitCode"
                step != null -> "failed in the `$step` step"
                else -> "failed"
            }
            return if (logs.isBlank()) {
                "$where $what."
            } else {
                "$where $what. Last log lines:\n${logs.trimEnd()}"
            }
        }
    }
}
