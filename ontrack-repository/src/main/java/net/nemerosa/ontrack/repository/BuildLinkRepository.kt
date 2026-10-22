package net.nemerosa.ontrack.repository

import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.BuildLink
import net.nemerosa.ontrack.model.structure.Project

interface BuildLinkRepository {

    /**
     * Counts the downstream links of a build.
     *
     * @return Number of links per project ID of the target builds
     */
    fun getCountQualifiedBuildsUsedBy(build: Build): Map<Int, Int>

    /**
     * Counts the upstream links of a build.
     *
     * @return Number of links per project ID of the source builds
     */
    fun getCountQualifiedBuildsUsing(build: Build): Map<Int, Int>

    fun getQualifiedBuildsUsedBy(build: Build): List<BuildLink>
    fun getQualifiedBuildsUsing(build: Build): List<BuildLink>

    /**
     * Creates a link, or keeps it if it exists already.
     *
     * @return `true` if the link did not exist before
     */
    fun createBuildLink(fromBuild: Build, toBuild: Build, qualifier: String): Boolean

    /**
     * Deletes a link, if it exists.
     *
     * @return `true` if the link existed and has been deleted
     */
    fun deleteBuildLink(fromBuild: Build, toBuild: Build, qualifier: String): Boolean

    fun isLinkedTo(build: Build, project: String, buildPattern: String? = null, qualifier: String? = null): Boolean
    fun isLinkedTo(build: Build, targetBuild: Build, qualifier: String? = null): Boolean
    fun isLinkedFrom(build: Build, project: String, buildPattern: String? = null, qualifier: String? = null): Boolean

    /**
     * Loops over ALL the build links. Use this method with care, mostly for external indexation.
     */
    fun forEachBuildLink(code: (from: Build, to: Build, qualifier: String) -> Unit)

    /**
     * Loops over all the build links to the builds of a project. Use this method with care, mostly
     * for external indexation.
     */
    fun forEachBuildLinkTo(project: Project, code: (from: Build, to: Build, qualifier: String) -> Unit)

}