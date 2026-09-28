package net.nemerosa.ontrack.extension.github.autoversioning

import net.nemerosa.ontrack.common.BaseException
import net.nemerosa.ontrack.extension.av.retry.AutoVersioningRetryableException

/**
 * Transient failure to launch the GitHub post-processing workflow: its dispatch kept failing on
 * GitHub errors, or its run could not be found in time. The auto-versioning order may succeed if
 * it is run again later.
 *
 * @param cause Original failure
 */
class GitHubPostProcessingTransientException(
    cause: Exception,
) : BaseException(
    cause,
    "GitHub post processing workflow could not be launched: ${cause.message ?: cause::class.java.name}"
), AutoVersioningRetryableException
