package net.nemerosa.ontrack.extension.artifactory.service

import net.nemerosa.ontrack.extension.artifactory.ArtifactoryConfProperties
import net.nemerosa.ontrack.extension.artifactory.client.ArtifactoryClient
import net.nemerosa.ontrack.extension.artifactory.client.ArtifactoryClientFactory
import net.nemerosa.ontrack.extension.artifactory.configuration.ArtifactoryConfiguration
import net.nemerosa.ontrack.extension.artifactory.configuration.ArtifactoryConfigurationService
import net.nemerosa.ontrack.extension.artifactory.property.ArtifactoryPromotionSyncProperty
import net.nemerosa.ontrack.extension.artifactory.property.ArtifactoryPromotionSyncPropertyType
import net.nemerosa.ontrack.job.*
import net.nemerosa.ontrack.job.orchestrator.JobOrchestratorSupplier
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.model.support.AbstractBranchJob
import net.nemerosa.ontrack.model.support.ConfigurationServiceListener
import org.apache.commons.lang3.Strings
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.regex.Pattern

@Service
class ArtifactoryPromotionSyncServiceImpl(
    private val structureService: StructureService,
    private val propertyService: PropertyService,
    private val artifactoryClientFactory: ArtifactoryClientFactory,
    private val configurationService: ArtifactoryConfigurationService,
    private val artifactoryConfProperties: ArtifactoryConfProperties,
    private val securityService: SecurityService,
) : ArtifactoryPromotionSyncService, JobOrchestratorSupplier, ConfigurationServiceListener<ArtifactoryConfiguration> {

    private val logger = LoggerFactory.getLogger(ArtifactoryPromotionSyncServiceImpl::class.java)

    init {
        configurationService.addConfigurationServiceListener(this)
    }

    override val jobRegistrations: Collection<JobRegistration>
        get() = if (artifactoryConfProperties.buildSyncDisabled) {
            emptyList()
        } else if (configurationService.configurations.isEmpty()) {
            // No Artifactory configuration is created, so it's not even needed to look for configured branches
            emptyList()
        } else {
            securityService.asAdmin {
                val jobs = mutableListOf<JobRegistration>()
                // Getting all branches having the sync property
                propertyService.forEachEntityWithProperty(ArtifactoryPromotionSyncPropertyType::class) { projectEntityID: ProjectEntityID, _ ->
                    if (projectEntityID.type == ProjectEntityType.BRANCH) {
                        val branch = structureService.getBranch(ID.of(projectEntityID.id))
                        if (!branch.isDisabled) {
                            jobs += scheduleArtifactoryBuildSync(branch)
                        }
                    }
                }
                // OK
                jobs
            }
        }

    fun scheduleArtifactoryBuildSync(branch: Branch): JobRegistration {
        val property = propertyService.getPropertyValue(branch, ArtifactoryPromotionSyncPropertyType::class.java)
            ?: error("Cannot find sync. property on branch ${branch.id()}")
        return JobRegistration.of(getBranchSyncJob(branch)).everyMinutes(property.interval.toLong())
    }

    private fun getBranchSyncJobKey(branch: Branch): JobKey {
        return ARTIFACTORY_BUILD_SYNC_JOB.getKey(branch.id.toString())
    }

    private fun getBranchSyncJob(branch: Branch): Job {
        return propertyService.getProperty(branch, ArtifactoryPromotionSyncPropertyType::class.java).value
            ?.let { _: ArtifactoryPromotionSyncProperty ->
                object : AbstractBranchJob(
                    structureService, branch
                ) {
                    override fun getKey(): JobKey =
                        getBranchSyncJobKey(branch)

                    override fun getTask(): JobRun =
                        JobRun { runListener: JobRunListener -> sync(branch, runListener) }

                    override fun getDescription(): String =
                        "Synchronisation of promotions with Artifactory for branch ${branch.project.name}/${branch.name}"

                    override fun isValid(): Boolean =
                        super.isValid() &&
                                propertyService.hasProperty(branch, ArtifactoryPromotionSyncPropertyType::class.java)
                }
            }
            ?: throw IllegalStateException("Branch not configured for Artifactory")
    }

    override fun sync(branch: Branch, listener: JobRunListener) {
        // Gets the sync properties
        val syncProperty = propertyService.getPropertyValue(branch, ArtifactoryPromotionSyncPropertyType::class.java)
            ?: error("Cannot find sync. property on branch ${branch.id()}")
        val buildName = syncProperty.buildName
        val buildNameFilter = syncProperty.buildNameFilter
        val configuration: ArtifactoryConfiguration = syncProperty.configuration
        val log =
            "Sync branch ${branch.project.name}/${branch.name} with Artifactory build $buildName ($buildNameFilter)"
        logger.info("[artifactory-sync] {}", log)
        listener.message(log)
        // Build name filter
        val buildNamePattern = Pattern.compile(
            Strings.CS.replace(Strings.CS.replace(buildNameFilter, ".", "\\."), "*", ".*")
        )
        // Gets an Artifactory client
        val client = artifactoryClientFactory.getClient(configuration)
        // Gets all the build numbers for the specified build name
        val buildNumbers = client.getBuildNumbers(buildName)
            // ... and filter them
            .filter { name: String -> buildNamePattern.matcher(name).matches() }
        // Synchronises the promotion levels for each build
        for (buildNumber in buildNumbers) {
            syncBuild(branch, buildName, buildNumber, client, listener)
        }
    }

    override fun syncBuild(
        branch: Branch,
        artifactoryBuildName: String,
        buildName: String,
        client: ArtifactoryClient,
        listener: JobRunListener,
    ) {
        // Looks for the build
        val buildOpt = structureService.findBuildByName(
            branch.project.name,
            branch.name,
            buildName
        )
        if (buildOpt.isPresent) {
            // Log
            val log = String.format(
                "Sync branch %s/%s for Artifactory build %s",
                branch.project.name,
                branch.name,
                buildName
            )
            logger.debug("[artifactory-sync] {}", log)
            listener.message(log)
            // Gets the build information from Artifactory
            val buildInfo = client.getBuildInfo(artifactoryBuildName, buildName)
            // Gets the list of statuses
            val statuses = client.getStatuses(buildInfo)
            // For all statuses
            for (artifactoryStatus in statuses) {
                val statusName = artifactoryStatus.name
                // Looks for an existing promotion level with the same name on the branch
                val promotionLevelOpt = structureService.findPromotionLevelByName(
                    branch.project.name,
                    branch.name,
                    statusName
                )
                if (promotionLevelOpt.isPresent) {
                    // Looks for an existing promotion run for the build
                    val runOpt = structureService.getLastPromotionRunForBuildAndPromotionLevel(
                        buildOpt.get(),
                        promotionLevelOpt.get()
                    )
                    if (!runOpt.isPresent) {
                        // No existing promotion, we can promote safely
                        logger.info(
                            "[artifactory-sync] Promote {}/{}/{} to {}",
                            branch.project.name,
                            branch.name,
                            buildName,
                            statusName
                        )
                        // Actual promotion
                        structureService.newPromotionRun(
                            PromotionRun.of(
                                buildOpt.get(),
                                promotionLevelOpt.get(),
                                Signature.of(artifactoryStatus.user).withTime(artifactoryStatus.timestamp),
                                "Promoted from Artifactory"
                            )
                        )
                    }
                }
            }
        }
    }

    companion object {
        private val ARTIFACTORY_BUILD_SYNC_JOB = JobCategory.of("artifactory").withName("Artifactory")
            .getType("build-sync").withName("Artifactory Build synchronisation")
    }
}