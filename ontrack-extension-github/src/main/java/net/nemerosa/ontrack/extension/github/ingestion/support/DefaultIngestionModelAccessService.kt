package net.nemerosa.ontrack.extension.github.ingestion.support

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.general.ReleasePropertyType
import net.nemerosa.ontrack.extension.general.validation.CHMLValidationDataType
import net.nemerosa.ontrack.extension.general.validation.MetricsValidationDataType
import net.nemerosa.ontrack.extension.general.validation.TestSummaryValidationDataType
import net.nemerosa.ontrack.extension.general.validation.ThresholdPercentageValidationDataType
import net.nemerosa.ontrack.extension.git.model.ConfiguredBuildGitCommitLink
import net.nemerosa.ontrack.extension.git.property.GitBranchConfigurationProperty
import net.nemerosa.ontrack.extension.git.property.GitBranchConfigurationPropertyType
import net.nemerosa.ontrack.extension.git.support.GitCommitPropertyCommitLink
import net.nemerosa.ontrack.extension.github.ingestion.processing.*
import net.nemerosa.ontrack.extension.github.ingestion.processing.events.WorkflowRun
import net.nemerosa.ontrack.extension.github.ingestion.processing.model.IPullRequest
import net.nemerosa.ontrack.extension.github.ingestion.processing.model.Repository
import net.nemerosa.ontrack.extension.github.ingestion.processing.model.getProjectName
import net.nemerosa.ontrack.extension.github.ingestion.settings.GitHubIngestionSettings
import net.nemerosa.ontrack.extension.github.model.GitHubEngineConfiguration
import net.nemerosa.ontrack.extension.github.property.GitHubProjectConfigurationProperty
import net.nemerosa.ontrack.extension.github.property.GitHubProjectConfigurationPropertyType
import net.nemerosa.ontrack.extension.github.service.GitHubConfigurationService
import net.nemerosa.ontrack.extension.github.workflow.BuildGitHubWorkflowRun
import net.nemerosa.ontrack.extension.github.workflow.BuildGitHubWorkflowRunProperty
import net.nemerosa.ontrack.extension.github.workflow.BuildGitHubWorkflowRunPropertyType
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.model.structure.NameDescription.Companion.nd
import net.nemerosa.ontrack.model.support.NoConfig
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.jvm.optionals.getOrNull

@Service
@Transactional
class DefaultIngestionModelAccessService(
    private val structureService: StructureService,
    private val gitHubConfigurationService: GitHubConfigurationService,
    private val propertyService: PropertyService,
    private val cachedSettingsService: CachedSettingsService,
    private val gitCommitPropertyCommitLink: GitCommitPropertyCommitLink,
    private val validationDataTypeService: ValidationDataTypeService,
    private val ingestionImageService: IngestionImageService,
    private val buildGitHubWorkflowRunPropertyType: BuildGitHubWorkflowRunPropertyType,
) : IngestionModelAccessService {

    override fun getOrCreateProject(repository: Repository, configuration: String?): Project {
        val settings = cachedSettingsService.getCachedSettings(GitHubIngestionSettings::class.java)
        val name = getProjectName(
            owner = repository.owner.login,
            repository = repository.name,
            orgProjectPrefix = settings.orgProjectPrefix,
        )
        val project = structureService.findProjectByName(name)
            .getOrNull()
            ?: structureService.newProject(
                Project.of(
                    nd(
                        name = name,
                        description = repository.description,
                    )
                )
            )
        // Setup the Git configuration for this project
        setupProjectGitHubConfiguration(project, repository, settings, configuration)
        // OK
        return project
    }

    private fun setupProjectGitHubConfiguration(
        project: Project,
        repository: Repository,
        settings: GitHubIngestionSettings,
        configurationName: String?,
    ) {
        if (!propertyService.hasProperty(project, GitHubProjectConfigurationPropertyType::class.java)) {
            val configuration = findGitHubEngineConfiguration(repository, configurationName)
            // Project property if not already defined
            propertyService.editProperty(
                project,
                GitHubProjectConfigurationPropertyType::class.java,
                GitHubProjectConfigurationProperty(
                    configuration = configuration,
                    repository = repository.fullName,
                    indexationInterval = settings.indexationInterval,
                    issueServiceConfigurationIdentifier = settings.issueServiceIdentifier,
                )
            )
        }
    }

    override fun findGitHubEngineConfiguration(
        repository: Repository,
        configurationName: String?,
    ): GitHubEngineConfiguration = if (!configurationName.isNullOrBlank()) {
        gitHubConfigurationService.findConfiguration(configurationName)
            ?: throw GitHubConfigProvidedNameNotFoundException(configurationName)
    } else {
        // Gets the list of GH configs
        val configurations = gitHubConfigurationService.configurations
        // If no configuration, error
        if (configurations.isEmpty()) {
            throw NoGitHubConfigException()
        }
        // If only 1 config, use it
        else if (configurations.size == 1) {
            val candidate = configurations.first()
            // Checks the URL
            if (repository.htmlUrl.startsWith(candidate.url)) {
                candidate
            } else {
                throw GitHubConfigURLMismatchException(repository.htmlUrl)
            }
        }
        // If several configurations, select it based on the URL
        else {
            val candidates = configurations.filter {
                repository.htmlUrl.startsWith(it.url)
            }
            if (candidates.isEmpty()) {
                throw GitHubConfigURLNoMatchException(repository.htmlUrl)
            } else if (candidates.size == 1) {
                candidates.first()
            } else {
                throw GitHubConfigURLSeveralMatchesException(repository.htmlUrl)
            }
        }
    }

    override fun findBranchByRef(project: Project, ref: String, pullRequest: IPullRequest?): Branch? {
        val branchName = if (pullRequest != null) {
            "PR-${pullRequest.number}"
        } else if (ref.startsWith(REFS_TAGS_PREFIX)) {
            error("Creating branch from tag is not supported: $ref")
        } else {
            NameDescription.escapeName(ref).take(Branch.NAME_MAX_LENGTH)
        }
        return structureService.findBranchByName(project.name, branchName).getOrNull()
    }

    override fun getBranchIfExists(
        repository: Repository,
        headBranch: String,
        pullRequest: IPullRequest?,
    ): Branch? {
        val settings = cachedSettingsService.getCachedSettings(GitHubIngestionSettings::class.java)
        val name = getProjectName(
            owner = repository.owner.login,
            repository = repository.name,
            orgProjectPrefix = settings.orgProjectPrefix,
        )
        val project = structureService.findProjectByName(name).getOrNull()
        return if (project != null) {
            val (branchName, _) = getBranchNames(headBranch, pullRequest)
            structureService.findBranchByName(project.name, branchName).getOrNull()
        } else {
            null
        }
    }

    private data class BranchNames(
        val name: String,
        val gitBranch: String,
    )

    private fun getBranchNames(
        headBranch: String,
        pullRequest: IPullRequest?,
    ): BranchNames = if (pullRequest != null) {
        val key = "PR-${pullRequest.number}"
        BranchNames(key, key)
    } else if (headBranch.startsWith(REFS_TAGS_PREFIX)) {
        error("Creating branch from tag is not supported: $headBranch")
    } else {
        val branchName = NameDescription.escapeName(headBranch).take(Branch.NAME_MAX_LENGTH)
        BranchNames(branchName, headBranch)
    }

    override fun getOrCreateBranch(
        project: Project,
        headBranch: String,
        pullRequest: IPullRequest?,
    ): Branch {
        val (branchName, gitBranch) = getBranchNames(headBranch, pullRequest)
        val branch = structureService.findBranchByName(project.name, branchName)
            .getOrNull()
            ?: structureService.newBranch(
                Branch.of(
                    project,
                    nd(
                        name = branchName,
                        description = "$headBranch branch",
                    )
                )
            )
        // Setup the Git configuration for this branch
        if (!propertyService.hasProperty(branch, GitBranchConfigurationPropertyType::class.java)) {
            propertyService.editProperty(
                branch,
                GitBranchConfigurationPropertyType::class.java,
                GitBranchConfigurationProperty(
                    branch = gitBranch,
                    buildCommitLink = ConfiguredBuildGitCommitLink(
                        gitCommitPropertyCommitLink,
                        NoConfig.INSTANCE
                    ).toServiceConfiguration(),
                    override = false,
                    buildTagInterval = 0,
                )
            )
        }
        // OK
        return branch
    }

    override fun findProjectFromRepository(repository: Repository): Project? {
        // Gets the general ingestion settings
        val settings = cachedSettingsService.getCachedSettings(GitHubIngestionSettings::class.java)
        // Gets the project name from the repository
        val projectName = getProjectName(
            owner = repository.owner.login,
            repository = repository.name,
            orgProjectPrefix = settings.orgProjectPrefix,
        )
        // Getting the project using its name
        return structureService.findProjectByName(projectName).getOrNull()
    }

    override fun setBuildRunId(
        build: Build,
        workflowRun: WorkflowRun,
    ) {
        val link = BuildGitHubWorkflowRun(
            runId = workflowRun.id,
            url = workflowRun.htmlUrl,
            name = workflowRun.name,
            runNumber = workflowRun.runNumber,
            running = workflowRun.conclusion == null,
            event = workflowRun.event,
        )
        val property = propertyService.getPropertyValue(build, BuildGitHubWorkflowRunPropertyType::class.java)
        if (property != null) {
            val workflows = property.workflows.toMutableList()
            val changed = BuildGitHubWorkflowRun.edit(workflows, link)
            if (changed) {
                propertyService.editProperty(
                    build,
                    BuildGitHubWorkflowRunPropertyType::class.java,
                    BuildGitHubWorkflowRunProperty(
                        workflows = workflows,
                    )
                )
            }
        } else {
            propertyService.editProperty(
                build,
                BuildGitHubWorkflowRunPropertyType::class.java,
                BuildGitHubWorkflowRunProperty(
                    workflows = listOf(link),
                )
            )
        }
    }

    override fun findBuildByRunId(repository: Repository, runId: Long): Build? {
        // Gets the project
        val project = findProjectFromRepository(repository) ?: return null
        // Getting the build using its Run ID property
        return propertyService.findByEntityTypeAndSearchArguments(
            ProjectEntityType.BUILD,
            BuildGitHubWorkflowRunPropertyType::class,
            buildGitHubWorkflowRunPropertyType.getSearchArguments(runId.toString()),
        ).map {
            structureService.getBuild(it)
        }.firstOrNull {
            it.project.id == project.id
        }
    }

    override fun findBuildByBuildName(repository: Repository, buildName: String): Build? {
        // Gets the project
        val project = findProjectFromRepository(repository) ?: return null
        // Searching
        return structureService.buildSearch(
            projectId = project.id,
            form = BuildSearchForm(
                buildName = buildName,
                buildExactMatch = true,
            )
        ).firstOrNull { it.project.id == project.id }
    }

    override fun findBuildByBuildLabel(repository: Repository, buildLabel: String): Build? {
        // Gets the project
        val project = findProjectFromRepository(repository) ?: return null
        // Searching
        return structureService.buildSearch(
            projectId = project.id,
            form = BuildSearchForm(
                property = ReleasePropertyType::class.java.name,
                propertyValue = buildLabel,
            )
        ).firstOrNull { it.project.id == project.id }
    }

    override fun setupValidationStamp(
        branch: Branch,
        vsName: String,
        vsDescription: String?,
        dataType: String?,
        dataTypeConfig: JsonNode?,
        image: String?,
    ): ValidationStamp {
        // Data type
        val actualDataTypeConfig: ValidationDataTypeConfig<Any>? = if (!dataType.isNullOrBlank()) {
            // Shortcut resolution
            val dataTypeId: String = when (dataType) {
                "test-summary" -> TestSummaryValidationDataType::class.java.name
                "metrics" -> MetricsValidationDataType::class.java.name
                "percentage" -> ThresholdPercentageValidationDataType::class.java.name
                "chml" -> CHMLValidationDataType::class.java.name
                else -> dataType
            }
            // Parsing of the configuration
            validationDataTypeService.validateValidationDataTypeConfig(dataTypeId, dataTypeConfig)
        } else {
            null
        }
        // Image
        val imageDocument = image?.run { ingestionImageService.downloadImage(branch.project, this) }
        // Getting the existing validation stamp
        val existing = structureService.findValidationStampByName(branch.project.name, branch.name, vsName).getOrNull()
        val saved = if (existing != null) {
            // Adapt description if need be
            if (vsDescription != null && vsDescription != existing.description) {
                val adapted = existing.withDescription(vsDescription).run {
                    if (actualDataTypeConfig != null) {
                        withDataType(actualDataTypeConfig)
                    } else {
                        this
                    }
                }
                structureService.saveValidationStamp(
                    adapted
                )
                adapted
            } else {
                // Done
                existing
            }
        } else {
            val vs = ValidationStamp.of(
                branch,
                nd(vsName, vsDescription),
            ).run {
                if (actualDataTypeConfig != null) {
                    withDataType(actualDataTypeConfig)
                } else {
                    this
                }
            }
            structureService.newValidationStamp(
                vs
            )
        }
        // Image setup
        if (imageDocument != null) {
            structureService.setValidationStampImage(saved.id, imageDocument)
        }
        // OK
        return saved
    }

    override fun setupPromotionLevel(
        branch: Branch,
        plName: String,
        plDescription: String?,
        image: String?,
    ): PromotionLevel {
        val existing = structureService.findPromotionLevelByName(branch.project.name, branch.name, plName).getOrNull()
        val imageDocument = image?.run { ingestionImageService.downloadImage(branch.project, this) }
        val pl = if (existing != null) {
            // Adapt description if need be
            if (plDescription != null && plDescription != existing.description) {
                val adapted = existing.withDescription(plDescription)
                structureService.savePromotionLevel(
                    adapted
                )
                adapted
            } else {
                // Done
                existing
            }
        } else {
            structureService.newPromotionLevel(
                PromotionLevel.of(
                    branch,
                    nd(plName, plDescription)
                )
            )
        }
        // Image setup
        if (imageDocument != null) {
            structureService.setPromotionLevelImage(pl.id, imageDocument)
        }
        // OK
        return pl
    }
}