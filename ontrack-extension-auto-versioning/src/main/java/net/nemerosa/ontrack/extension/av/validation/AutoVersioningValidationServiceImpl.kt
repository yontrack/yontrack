package net.nemerosa.ontrack.extension.av.validation

import net.nemerosa.ontrack.extension.av.config.AutoVersioningConfigurationService
import net.nemerosa.ontrack.extension.av.config.AutoVersioningSourceConfig
import net.nemerosa.ontrack.extension.av.config.AutoVersioningTargetFileService
import net.nemerosa.ontrack.extension.av.dispatcher.VersionSourceFactory
import net.nemerosa.ontrack.extension.av.dispatcher.getBuildVersion
import net.nemerosa.ontrack.extension.av.dispatcher.getBuildWithVersion
import net.nemerosa.ontrack.extension.av.settings.AutoVersioningSettings
import net.nemerosa.ontrack.extension.scm.service.SCMDetector
import net.nemerosa.ontrack.model.buildfilter.BuildFilterService
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import net.nemerosa.ontrack.model.structure.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.jvm.optionals.getOrNull

@Service
@Transactional
class AutoVersioningValidationServiceImpl(
    private val autoVersionConfigurationService: AutoVersioningConfigurationService,
    private val structureService: StructureService,
    private val autoVersioningValidationDataType: AutoVersioningValidationDataType,
    private val scmDetector: SCMDetector,
    private val autoVersioningTargetFileService: AutoVersioningTargetFileService,
    private val buildFilterService: BuildFilterService,
    private val cachedSettingsService: CachedSettingsService,
    private val versionSourceFactory: VersionSourceFactory,
) : AutoVersioningValidationService {

    override fun checkAndValidate(build: Build): List<AutoVersioningValidationData> {
        val config = autoVersionConfigurationService.getAutoVersioning(build.branch)
        return config?.configurations
            ?.filter { !it.validationStamp.isNullOrBlank() }
            ?.mapNotNull { checkAndValidate(build, it) }
            ?: emptyList()
    }

    private fun checkAndValidate(build: Build, config: AutoVersioningSourceConfig): AutoVersioningValidationData? {
        // Validation stamp name
        val validationStampName = getActualValidationStampName(config)
        return if (!validationStampName.isNullOrBlank()) {
            val sourceProject = structureService.findProjectByName(config.sourceProject).getOrNull()
                ?: return null
            // Gets the version information
            val start = System.currentTimeMillis()
            val time: Long
            val (current, last) = try {
                getVersionInfo(build, config, sourceProject)
            } finally {
                time = System.currentTimeMillis() - start
            }
            // Validation
            val data = AutoVersioningValidationData(
                project = config.sourceProject,
                version = current?.version ?: "",
                latestVersion = last?.version ?: "",
                path = config.targetPath,
                time = time
            )
            // Validation stamp creation with type
            val validationStamp = setupValidationStamp(build.branch, validationStampName)
            if (validationStamp.dataType?.descriptor?.id != autoVersioningValidationDataType.descriptor.id) {
                structureService.saveValidationStamp(
                    validationStamp.withDataType(
                        ValidationDataTypeConfig(
                            autoVersioningValidationDataType.descriptor,
                            null
                        )
                    )
                )
            }
            // Validation of the build
            structureService.newValidationRun(
                build,
                ValidationRunRequest(
                    validationStampName = validationStampName,
                    data = data
                )
            )
            // Creation of the build link
            val settings = cachedSettingsService.getCachedSettings(AutoVersioningSettings::class.java)
            val buildLinkCreation = config.buildLinkCreation
            if (settings.buildLinks
                && current != null
                && (buildLinkCreation == null || buildLinkCreation)
            ) {
                val links = structureService.getBuildDependencyLinks(build)
                val existingLink = links.pageItems.find {
                    it.qualifier == config.qualifier && it.build.project.name == config.sourceProject
                }
                if (existingLink == null) {
                    // Source project
                    structureService.createBuildLink(
                        fromBuild = build,
                        toBuild = current.build,
                        qualifier = config.qualifier ?: BuildLink.DEFAULT,
                    )
                }
            }
            // OK
            data
        } else {
            null
        }
    }

    /**
     * Ignoring the setup of the project for the automated creation of validation stamps here.
     */
    private fun setupValidationStamp(
        branch: Branch,
        validationStampName: String,
    ): ValidationStamp =
        structureService.setupValidationStamp(branch, validationStampName, "Auto created for auto versioning check")

    private fun getActualValidationStampName(config: AutoVersioningSourceConfig): String? =
        if (config.validationStamp.isNullOrBlank()) {
            null
        } else if (config.validationStamp == "auto") {
            "auto-versioning-${NameDescription.escapeName(config.sourceProject)}"
        } else {
            config.validationStamp
        }

    private fun getVersionInfo(build: Build, config: AutoVersioningSourceConfig, sourceProject: Project) = VersionInfo(
        getCurrentVersion(build, config, sourceProject),
        getLastVersion(build.branch, config, sourceProject)
    )

    private fun getCurrentVersion(
        build: Build,
        config: AutoVersioningSourceConfig,
        sourceProject: Project
    ): BuildVersionInfo? {
        // Using build links first
        val link = structureService.getBuildDependencyLinks(build, 0, 1) {
            it.build.project.name == config.sourceProject &&
                    (config.qualifier == null || config.qualifier == it.qualifier)
        }.pageItems.firstOrNull()?.build
        return if (link != null) {
            val linkedVersion = link.run {
                versionSourceFactory.getBuildVersion(this, config)
            }
            BuildVersionInfo(link, linkedVersion)
        } else {
            val scmVersion = readCurrentVersion(build.branch, config)
                ?: return null
            // Given the stored version, get the build from the source project
            versionSourceFactory.getBuildWithVersion(sourceProject, config, scmVersion)?.let {
                BuildVersionInfo(
                    build = it,
                    version = scmVersion,
                )
            }
        }
    }

    private fun readCurrentVersion(branch: Branch, config: AutoVersioningSourceConfig): String? {
        val scm = scmDetector.getSCM(branch.project) ?: return null
        val scmBranch: String = scm.getSCMBranch(branch) ?: return null
        // Using the first path of the default path config
        val defaultPath = config.defaultPath
        val targetPath = defaultPath.paths.first()
        val lines = scm.download(scmBranch, targetPath)
            ?.toString(Charsets.UTF_8)
            ?.lines()
            ?: emptyList()
        return autoVersioningTargetFileService.readVersion(defaultPath, lines)
    }

    private fun getLastVersion(
        eligibleTargetBranch: Branch,
        config: AutoVersioningSourceConfig,
        sourceProject: Project
    ): BuildVersionInfo? {
        // Gets the latest eligible branch for the source project
        val sourceBranch = sourceProject.run {
            autoVersionConfigurationService.getLatestBranch(
                eligibleTargetBranch,
                sourceProject,
                config
            )
        }
        // Gets the latest build for this branch having the corresponding promotion
        val sourceBuild = sourceBranch?.run {
            buildFilterService.standardFilterProviderData(1)
                .withWithPromotionLevel(config.sourcePromotion)
                .build()
                .filterBranchBuilds(sourceBranch)
                .firstOrNull()
        }
        // Source build found
        return if (sourceBuild != null) {
            val version = sourceBuild.run {
                versionSourceFactory.getBuildVersion(this, config)
            }
            BuildVersionInfo(sourceBuild, version)
        } else {
            null
        }
    }

}