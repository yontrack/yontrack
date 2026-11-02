package net.nemerosa.ontrack.extension.av.dispatcher

import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.BuildLink
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.StructureService
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
import kotlin.jvm.optionals.getOrNull

@Component
class LinkVersionSource(
    private val applicationContext: ApplicationContext,
    private val structureService: StructureService,
) : VersionSource {

    override val id: String = "link"

    private val versionSourceFactory: VersionSourceFactory by lazy {
        applicationContext.getBean(VersionSourceFactory::class.java)
    }

    override fun getVersion(build: Build, config: String?): String {
        val (project, qualifier, subVersion) = parseConfig(config)
        // Gets the list of linked builds
        val links = structureService.getBuildDependencyLinks(build) {
            it.build.project.name == project && it.qualifier == qualifier
        }.pageItems
        if (links.isEmpty()) {
            throw VersionSourceNoVersionException("Cannot find linked builds for $project:$qualifier")
        } else if (links.size > 1) {
            throw VersionSourceNoVersionException("More than 1 link returned for $project:$qualifier: ${links.size}")
        } else {
            val linkedBuild = links.first().build
            // Version source to use
            val versionSource = subVersion ?: DefaultVersionSource.ID
            // Getting the version source
            return versionSourceFactory.getBuildVersion(linkedBuild, versionSource)
        }
    }

    /**
     * We cannot blindly check the whole tree of dependencies, that'd be too costly.
     *
     * Instead, we can navigate until the target project, get its build according to
     * the version.
     *
     * From there, we get the build in the source project linked to the target build.
     */
    override fun getBuildFromVersion(sourceProject: Project, config: String?, version: String): Build? {
        val (project, qualifier, subVersion) = parseConfig(config)
        val targetProject = structureService.findProjectByName(project).getOrNull()
            ?: throw VersionSourceNoVersionException("Cannot find project $project")
        val targetBuild = versionSourceFactory.getBuildWithVersion(
            sourceProject = targetProject,
            versionSource = subVersion,
            version = version,
        ) ?: return null
        // Gets the latest source build linked to this target build
        return structureService.getBuildConsumerLinks(targetBuild) { sourceBuildLink ->
            sourceBuildLink.build.project.name == sourceProject.name && sourceBuildLink.qualifier == qualifier
        }.pageItems.firstOrNull()?.build
    }

    data class LinkVersionSourceConfig(
        val project: String,
        val qualifier: String,
        val subVersion: String?,
    )

    companion object {

        private val regex: Regex = "^([a-zA-Z0-9_-]+)(?:/([a-zA-Z0-9_-]+))?(?:->(.*))?$".toRegex()

        fun parseConfig(config: String?): LinkVersionSourceConfig {
            if (config.isNullOrBlank()) {
                throw VersionSourceConfigException("Config cannot be null or blank for a `link` version source.")
            } else {
                val m = regex.matchEntire(config)
                if (m != null) {
                    return LinkVersionSourceConfig(
                        project = m.groupValues[1],
                        qualifier = m.groupValues[2].takeIf { it.isNotBlank() } ?: BuildLink.DEFAULT,
                        subVersion = m.groupValues[3].takeIf { it.isNotBlank() },
                    )
                } else {
                    throw VersionSourceConfigException("Config for the `link` version source could not be parsed: $config")
                }
            }
        }
    }

}