package net.nemerosa.ontrack.extension.scm.changelog.assistants

/**
 * Computation of the assisted change of a build.
 *
 * It carries the build only: the computation reads its current commit and previous build.
 *
 * @property buildId ID of the build
 */
data class AssistedChangeQueuePayload(
    val buildId: Int,
)
