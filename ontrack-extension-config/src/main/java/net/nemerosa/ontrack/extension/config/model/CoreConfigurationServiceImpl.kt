package net.nemerosa.ontrack.extension.config.model

import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.api.ExtensionManager
import net.nemerosa.ontrack.extension.config.ci.CIConfigPRNotSupportedException
import net.nemerosa.ontrack.extension.config.ci.engine.CIEngine
import net.nemerosa.ontrack.extension.config.extensions.CIConfigExtension
import net.nemerosa.ontrack.extension.config.extensions.CIConfigExtensionNotFoundException
import net.nemerosa.ontrack.extension.config.extensions.ProjectCIConfigExtension
import net.nemerosa.ontrack.extension.config.license.ConfigurationLicense
import net.nemerosa.ontrack.extension.config.scm.SCMEngine
import net.nemerosa.ontrack.model.events.PlainEventRenderer
import net.nemerosa.ontrack.model.security.*
import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.model.templating.EnvTemplatingRenderable
import net.nemerosa.ontrack.model.templating.TemplatingService
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.jvm.optionals.getOrNull

@Service
class CoreConfigurationServiceImpl(
    private val configurationLicense: ConfigurationLicense,
    private val securityService: SecurityService,
    private val structureService: StructureService,
    private val propertyService: PropertyService,
    private val buildDisplayNameService: BuildDisplayNameService,
    private val validationDataTypeService: ValidationDataTypeService,
    private val promotionLevelConfigurators: List<PromotionLevelConfigurator>,
    private val extensionManager: ExtensionManager,
    private val templatingService: TemplatingService,
) : CoreConfigurationService {

    private val ciExtensions: Map<String, CIConfigExtension<*>> by lazy {
        extensionManager.getExtensions(CIConfigExtension::class.java).associateBy { it.id }
    }

    private val projectCiExtensions: Collection<ProjectCIConfigExtension> by lazy {
        extensionManager.getExtensions(ProjectCIConfigExtension::class.java)
    }

    override fun configureProject(
        input: ConfigurationInput,
        configuration: ProjectConfiguration,
        ciEngine: CIEngine,
        scmEngine: SCMEngine,
        env: Map<String, String>
    ): Project {
        configurationLicense.checkConfigurationFeatureEnabled()
        val projectName = configuration.projectName
            ?: ciEngine.getProjectName(env)
            ?: throw CoreConfigurationException("Could not get the project name from the environment")

        val existingProject = securityService.asAdmin { structureService.findProjectByName(projectName) }.getOrNull()
        val project = if (existingProject != null) {
            if (securityService.isProjectFunctionGranted<ProjectConfig>(existingProject)) {
                existingProject
            } else {
                throw CoreConfigurationException("Project $projectName already exists but you do not have the `PROJECT_CONFIG` permission.")
            }
        } else {
            structureService.newProject(Project.of(NameDescription(name = projectName, description = null)))
        }

        // Check for configuration
        securityService.checkProjectFunction(project, ProjectConfig::class.java)

        // Configuration of the project SCM (using the SCM engine)
        scmEngine.configureProject(project, configuration, env, projectName, ciEngine)

        // Auto-validations & auto-promotions
        projectCiExtensions.forEach { extension ->
            extension.configureProject(project, configuration)
        }

        // Configuration of properties
        configureProperties(
            entity = project,
            defaults = configuration.properties,
            env = env,
        )

        configureExtensions(
            entity = project,
            defaults = configuration.extensions,
        )

        return project
    }

    override fun configureBranch(
        project: Project,
        input: ConfigurationInput,
        configuration: BranchConfiguration,
        ciEngine: CIEngine,
        scmEngine: SCMEngine,
        env: Map<String, String>
    ): Branch {
        configurationLicense.checkConfigurationFeatureEnabled()
        securityService.checkProjectFunction(project, BranchCreate::class.java)
        securityService.checkProjectFunction(project, BranchEdit::class.java)

        val rawBranchName = ciEngine.getBranchName(env)
            ?: throw CoreConfigurationException("Could not get the branch name from the environment")

        if (scmEngine.isBranchPR(rawBranchName, env)) {
            throw CIConfigPRNotSupportedException()
        }

        val branchName = scmEngine.normalizeBranchName(rawBranchName)

        val branch = structureService.findBranchByName(project.name, branchName).getOrNull()
            ?: structureService.newBranch(
                Branch.of(
                    project,
                    NameDescription(name = branchName, description = null)
                )
            )

        configureValidations(branch, configuration.validations)
        configurePromotions(branch, configuration.promotions)

        // Configuration of the branch SCM (using the SCM engine)
        scmEngine.configureBranch(branch, configuration, env, rawBranchName)

        configureProperties(
            entity = branch,
            defaults = configuration.properties,
            env = env,
        )

        configureExtensions(
            entity = branch,
            defaults = configuration.extensions,
        )

        return branch
    }

    override fun configureBuild(
        branch: Branch,
        input: ConfigurationInput,
        configuration: BuildConfiguration,
        ciEngine: CIEngine,
        scmEngine: SCMEngine,
        env: Map<String, String>
    ): Build {
        configurationLicense.checkConfigurationFeatureEnabled()
        securityService.checkProjectFunction(branch, BuildCreate::class.java)
        securityService.checkProjectFunction(branch, BuildConfig::class.java)

        val buildName = getBuildName(configuration, ciEngine, env)

        val build = structureService.findBuildByName(branch.project.name, branch.name, buildName).getOrNull()
            ?: structureService.newBuild(
                Build.of(
                    branch,
                    NameDescription(name = buildName, description = null),
                    securityService.currentSignature,
                )
            )

        // Configuration of the build SCM (using the SCM engine)
        scmEngine.configureBuild(build, configuration, env, ciEngine)
        // Configuration of the build using the CI engine
        ciEngine.configureBuild(build, configuration, env)

        configureProperties(
            entity = build,
            defaults = configuration.properties,
            env = env,
        )

        configureExtensions(
            entity = build,
            defaults = configuration.extensions,
        )

        // Build display name
        configureBuildDisplayName(build, configuration, ciEngine, env)

        // OK
        return build
    }

    private fun configureValidations(
        branch: Branch,
        validations: List<ValidationStampConfiguration>,
    ) {
        validations.forEach { config ->
            val vs = structureService.setupValidationStamp(
                branch = branch,
                validationStampName = config.name,
                validationStampDescription = config.description,
            )
            val validationStampDataConfiguration = config.validationStampDataConfiguration
            if (validationStampDataConfiguration != null) {
                structureService.saveValidationStamp(
                    vs.withDataType(
                        validationDataTypeService.validateValidationDataTypeConfig<Any>(
                            validationStampDataConfiguration.type,
                            validationStampDataConfiguration.data
                        )
                    )
                )
            } else {
                structureService.saveValidationStamp(
                    vs.withDataType(null)
                )
            }
        }
    }

    private fun configurePromotions(
        branch: Branch,
        promotions: List<PromotionLevelConfiguration>,
    ) {
        promotions.forEach { config ->
            val pl = structureService.findPromotionLevelByName(
                project = branch.project.name,
                branch = branch.name,
                promotionLevel = config.name
            ).getOrNull() ?: structureService.newPromotionLevel(
                PromotionLevel.of(
                    branch,
                    NameDescription(name = config.name, description = config.description)
                )
            )
            // Fields
            structureService.setPromotionLevelFields(pl.id, config.fields)
            // Auto promotion property is not accessible through the API (general extension not visible)
            promotionLevelConfigurators.forEach { configurator ->
                configurator.configure(pl, config)
            }
        }
    }

    private fun configureBuildDisplayName(
        build: Build,
        configuration: BuildConfiguration,
        ciEngine: CIEngine,
        env: Map<String, String>
    ) {
        val version = ciEngine.getBuildVersion(env)
        if (!version.isNullOrBlank()) {
            buildDisplayNameService.setDisplayName(build = build, displayName = version, override = false)
        }
    }

    private fun getBuildName(
        configuration: BuildConfiguration,
        ciEngine: CIEngine,
        env: Map<String, String>
    ): String {
        val buildNameTemplate = configuration.buildName
            ?.takeIf { it.isNotBlank() }
        return if (!buildNameTemplate.isNullOrBlank()) {
            renderBuildNameTemplate(
                template = buildNameTemplate,
                env = env,
            )
        } else {
            val suffix = ciEngine.getBuildSuffix(env)
            val timestampUtc = Instant.now()
                .atZone(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
            if (suffix.isNullOrBlank()) {
                timestampUtc
            } else {
                "$timestampUtc-$suffix"
            }
        }
    }

    private fun templating(env: Map<String, String>): (text: String) -> String = { text ->
        templatingService.render(
            template = text,
            context = mapOf(
                "env" to EnvTemplatingRenderable(env),
            ),
            renderer = PlainEventRenderer.INSTANCE,
        )
    }

    private fun renderBuildNameTemplate(template: String, env: Map<String, String>): String {
        return templating(env)(template)
    }

    private fun configureProperties(
        entity: ProjectEntity,
        defaults: List<PropertyConfiguration>,
        env: Map<String, String>,
    ) {
        defaults.forEach { config ->
            val type = propertyService.getPropertyTypeByName<Any>(config.type)
            configureProperty(entity, type, config.data, env)
        }
    }

    private fun <T> configureProperty(
        entity: ProjectEntity,
        type: PropertyType<T>,
        data: JsonNode,
        env: Map<String, String>
    ) {
        val value = type.fromClient(data)
        val templatedValue = type.replaceValue(value, templating(env))
        propertyService.editProperty(
            entity,
            type::class.java,
            templatedValue
        )
    }

    private fun configureExtensions(
        entity: ProjectEntity,
        defaults: List<ExtensionConfiguration>,
    ) {
        defaults.forEach { config ->
            val extension = ciExtensions[config.id]
                ?: throw CIConfigExtensionNotFoundException(id = config.id)
            configureExtension(entity, extension, config.data)
        }
    }

    private fun <T> configureExtension(
        entity: ProjectEntity,
        extension: CIConfigExtension<T>,
        data: JsonNode
    ) {
        val parsedData = extension.parseData(data)
        extension.configure(
            entity = entity,
            data = parsedData,
        )
    }

}