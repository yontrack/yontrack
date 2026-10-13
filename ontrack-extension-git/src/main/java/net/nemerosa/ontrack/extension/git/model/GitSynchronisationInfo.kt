package net.nemerosa.ontrack.extension.git.model

import net.nemerosa.ontrack.git.model.GitBranchInfo
import net.nemerosa.ontrack.git.model.GitSynchronisationStatus
import java.time.LocalDateTime

/**
 * Information about a Git repository and the ways to synchronise it.
 */
class GitSynchronisationInfo(

        /**
         * Type of Git provided (github, basic, etc.)
         *
         * @see GitConfiguration
         */
        val type: String,

        /**
         * Name of the configuration
         */
        val name: String,

        /**
         * Remote URL
         */
        val remote: String,

        /**
         * Indexation interval
         */
        val indexationInterval: Int,

        /**
         * General status, from the indexation job, the same on every replica (#2085): running,
         * never run, or idle
         */
        val status: GitSynchronisationStatus,

        /**
         * Synchronisation status, index of commits per branch. If no branch is present, it
         * means that the repository was never synched.
         */
        val branches: List<GitBranchInfo>,

        /**
         * Start of the last run of the indexation job, if any
         */
        val lastRunDate: LocalDateTime? = null,

        /**
         * Error of the last run of the indexation job, if it failed
         */
        val lastError: String? = null,

)
