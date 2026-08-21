package net.nemerosa.ontrack.extension.api

import net.nemerosa.ontrack.model.extension.Extension
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.BuildDisplayNameProperty
import net.nemerosa.ontrack.model.structure.Project

/**
 * Extension which allows to render the display name for
 * a [Build].
 */
interface BuildDisplayNameExtension : Extension {

    /**
     * Returns a name according to the build configuration or null if not available.
     */
    fun getBuildDisplayName(build: Build): String?

    /**
     * Property holding the display name, when the display name is stored as a property.
     *
     * When available, this allows the display name to be resolved by the database, for example
     * when filtering builds on their display name. Defaults to `null`, meaning that the display
     * name provided by this extension cannot be resolved by the database.
     */
    val displayNameProperty: BuildDisplayNameProperty? get() = null

    /**
     * Checks if the build MUST return a name according to its configuration.
     */
    fun mustProvideBuildName(build: Build): Boolean

    /**
     * Inside a given [project], tries to find a build by using its display name or its name.
     *
     * If [onlyDisplayName] is `true`, only the display name will be used.
     */
    fun findBuildByDisplayName(
        project: Project,
        name: String,
        onlyDisplayName: Boolean,
    ): Build?

    /**
     * Sets the display name for a build.
     *
     * If [override] is `false`, the display name will be set only if not already set.
     *
     * @return `true` if the display name was set
     */
    fun setDisplayName(build: Build, displayName: String, override: Boolean): Boolean

}