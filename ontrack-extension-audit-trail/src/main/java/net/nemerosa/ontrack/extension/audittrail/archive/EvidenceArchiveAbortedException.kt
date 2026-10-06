package net.nemerosa.ontrack.extension.audittrail.archive

/**
 * An evidence archive could not be completed once its writing started: its response can no longer
 * change its status, and is aborted.
 *
 * @param buildId ID of the build of the archive
 * @param cause Why the archive could not be completed
 */
class EvidenceArchiveAbortedException(buildId: Int, cause: Throwable) :
    RuntimeException("The evidence archive of build $buildId was aborted: ${cause.message}", cause)
