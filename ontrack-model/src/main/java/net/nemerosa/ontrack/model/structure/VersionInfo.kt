package net.nemerosa.ontrack.model.structure

import java.time.LocalDateTime

/**
 * Information about the version of the application.
 */
data class VersionInfo(
    /**
     * Display version. Example: 2.3 or master or feature/158-my-feature
     */
    val display: String,
    /**
     * Full version string, including the build number
     */
    val full: String,
    /**
     * Git branch the build was produced from
     */
    val branch: String,
    /**
     * Build number
     */
    val build: String,
    /**
     * Associated commit (hash)
     */
    val commit: String
)
