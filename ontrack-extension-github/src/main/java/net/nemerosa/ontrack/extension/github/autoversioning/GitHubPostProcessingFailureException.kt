package net.nemerosa.ontrack.extension.github.autoversioning

import net.nemerosa.ontrack.common.BaseException
import net.nemerosa.ontrack.extension.av.postprocessing.PostProcessingFailureException

/**
 * Failure of a GitHub post-processing once its workflow run has been launched: the run
 * concluding without success, the wait timing out, or an error while polling it.
 *
 * Carrying the [link] to the run is what makes the auto-versioning emit
 * `auto-versioning-post-processing-error` rather than the generic `auto-versioning-error`.
 *
 * @param runUrl URL of the GitHub Actions workflow run
 * @param cause Original failure
 */
class GitHubPostProcessingFailureException(
    runUrl: String,
    cause: Exception,
) : BaseException(
    cause,
    "GitHub post processing workflow run at $runUrl failed: ${cause.message ?: cause::class.java.name}"
), PostProcessingFailureException {

    override val link: String = runUrl
}
