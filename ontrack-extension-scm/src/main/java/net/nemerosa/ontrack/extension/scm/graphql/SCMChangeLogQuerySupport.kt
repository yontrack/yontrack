package net.nemerosa.ontrack.extension.scm.graphql

import kotlinx.coroutines.runBlocking
import net.nemerosa.ontrack.extension.scm.changelog.DependencyLink
import net.nemerosa.ontrack.extension.scm.changelog.SCMChangeLog
import net.nemerosa.ontrack.extension.scm.changelog.SCMChangeLogService
import net.nemerosa.ontrack.model.structure.Build

/**
 * Computes a change log between two boundaries, whatever the way these boundaries have been
 * identified (build IDs or build names).
 *
 * The boundaries are inverted when needed, so that "to" is always the most recent build.
 *
 * @param from Build boundary
 * @param to Build boundary
 * @param projects List of projects to follow one by one for a deep change log. Each item is either
 *                 a project name, or a project name and a qualifier separated by a colon (:).
 */
internal fun SCMChangeLogService.getChangeLogForBoundaries(
    from: Build,
    to: Build,
    projects: List<String>?,
): SCMChangeLog? {
    var buildFrom = from
    var buildTo = to

    // Inverting the boundaries so that "buildTo" is the most recent
    if (buildTo.signature.time < buildFrom.signature.time) {
        val tmp = buildFrom
        buildFrom = buildTo
        buildTo = tmp
    }

    return runBlocking {
        getChangeLog(
            from = buildFrom,
            to = buildTo,
            dependencies = projects
                ?.map { DependencyLink.parse(it) }
                ?: emptyList(),
        )
    }
}
