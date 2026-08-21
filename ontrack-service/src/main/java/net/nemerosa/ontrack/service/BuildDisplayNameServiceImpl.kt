package net.nemerosa.ontrack.service

import net.nemerosa.ontrack.extension.api.BuildDisplayNameExtension
import net.nemerosa.ontrack.extension.api.ExtensionManager
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.BuildDisplayNameProperty
import net.nemerosa.ontrack.model.structure.BuildDisplayNameService
import net.nemerosa.ontrack.model.structure.Project
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class BuildDisplayNameServiceImpl(
    private val extensionManager: ExtensionManager
) : BuildDisplayNameService {

    private val extensions: Collection<BuildDisplayNameExtension> by lazy {
        extensionManager.getExtensions(BuildDisplayNameExtension::class.java)
    }

    override val displayNameProperty: BuildDisplayNameProperty?
        get() = extensions.firstOrNull()?.displayNameProperty

    override fun getFirstBuildDisplayName(build: Build): String? {
        return extensions.map { it.getBuildDisplayName(build) }.firstOrNull()
    }

    override fun findBuildByDisplayName(project: Project, name: String, onlyDisplayName: Boolean): Build? {
        return extensions.firstNotNullOfOrNull { it.findBuildByDisplayName(project, name, onlyDisplayName) }
    }

    override fun setDisplayName(
        build: Build,
        displayName: String,
        override: Boolean
    ) {
        extensions.firstOrNull { it.setDisplayName(build, displayName, override) }
    }
}