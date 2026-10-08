package net.nemerosa.ontrack.extension.agents.activity

import java.time.LocalDateTime

/**
 * Aggregates over the assisted change of the builds, stored as their
 * [AssistedChangeProperty][net.nemerosa.ontrack.extension.scm.changelog.assistants.AssistedChangeProperty].
 */
interface AssistedBuildsRepository {

    /**
     * Counts the builds created since a time, on some projects, by their assisted change. The builds
     * are counted by the database, never loaded.
     *
     * @param from Builds created at or after this time (UTC)
     * @param projects IDs of the projects of the builds - no count for an empty list
     * @return Counts
     */
    fun countAssistedBuilds(from: LocalDateTime, projects: Collection<Int>): AssistedBuildsCounts
}
