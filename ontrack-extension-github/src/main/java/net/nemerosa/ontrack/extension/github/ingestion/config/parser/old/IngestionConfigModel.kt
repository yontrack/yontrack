package net.nemerosa.ontrack.extension.github.ingestion.config.parser.old

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.NullNode
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.common.api.APIName
import net.nemerosa.ontrack.extension.github.ingestion.config.model.*
import net.nemerosa.ontrack.extension.github.ingestion.config.model.support.FilterConfig
import net.nemerosa.ontrack.extension.github.ingestion.config.model.tagging.IngestionTaggingConfig

/**
 * Configuration for the ingestion.
 *
 * @param general General settings
 * @param steps List of specific step configurations
 * @param jobs List of specific job configurations
 * @param jobsFilter Filtering on the jobs
 * @param stepsFilter Filtering on the steps
 * @param validations Validation stamps configuration
 * @param promotions Auto promotion configuration
 */
@APIName("GitHubIngestionConfig")
@APIDescription("Configuration for the ingestion of GitHub Actions workflows.")
@Deprecated("Use the V2 model")
data class IngestionConfigOld(
    @APIDescription("General settings")
    val general: OldIngestionConfigGeneral = OldIngestionConfigGeneral(),
    @APIDescription("List of specific step configurations")
    val steps: List<OldStepConfig> = emptyList(),
    @APIDescription("List of specific job configurations")
    val jobs: List<OldJobConfig> = emptyList(),
    @APIDescription("Filtering on the jobs")
    val jobsFilter: FilterConfig = FilterConfig(),
    @APIDescription("Filtering on the steps")
    val stepsFilter: FilterConfig = FilterConfig(),
    @APIDescription("Validation stamps configuration")
    val validations: List<OldValidationConfig> = emptyList(),
    @APIDescription("Auto promotion configuration")
    val promotions: List<OldPromotionConfig> = emptyList(),
    @APIDescription("Run validations")
    val runs: OldIngestionRunConfig = OldIngestionRunConfig(),
    @APIDescription("Workflows ingestion")
    val workflows: OldIngestionWorkflowConfig = OldIngestionWorkflowConfig(),
    @APIDescription("Casc configurations")
    val casc: OldIngestionCascConfig = OldIngestionCascConfig(),
    @APIDescription("Tagging configuration")
    val tagging: IngestionTaggingConfig = IngestionTaggingConfig(),
) {
    fun convert() = IngestionConfig(
        version = "v0",
        workflows = IngestionConfigWorkflows(
            filter = workflows.filter,
        ),
        jobs = IngestionConfigJobs(
            filter = jobsFilter,
            validationPrefix = general.validationJobPrefix ?: true,
            mappings = jobs.map { old ->
                JobIngestionConfigValidation(
                    name = old.name,
                    validation = old.validation,
                    description = old.description,
                )
            }
        ),
        steps = IngestionConfigSteps(
            filter = stepsFilter,
            mappings = steps.map { old ->
                StepIngestionConfigValidation(
                    name = old.name,
                    validation = old.validation,
                    description = old.description,
                    validationPrefix = old.validationJobPrefix,
                )
            }
        ),
        setup = IngestionConfigSetup(
            validations = validations.map { old ->
                IngestionConfigCascValidation(
                    name = old.name,
                    description = old.description,
                    dataType = old.dataType?.run {
                        IngestionConfigCascValidationType(
                            type = type,
                            config = config,
                        )
                    },
                    image = old.image,
                )
            },
            promotions = promotions.map { old ->
                IngestionConfigCascPromotion(
                    name = old.name,
                    description = old.description,
                    validations = old.validations,
                    promotions = old.promotions,
                    include = old.include,
                    exclude = old.exclude,
                    image = old.image,
                )
            },
            project = IngestionConfigCascSetup(
                includes = casc.project.includes,
                excludes = casc.project.excludes,
                casc = casc.project.casc,
            ),
            branch = IngestionConfigCascSetup(
                includes = casc.branch.includes,
                excludes = casc.branch.excludes,
                casc = casc.branch.casc,
            )
        ),
        tagging = tagging,
    )
}

/**
 * General settings
 *
 * @param skipJobs Must jobs be considered as validations?
 * @param validationJobPrefix Must we use the job name as a prefix to the validation stamp?
 */
@APIName("GitHubIngestionConfigGeneral")
@APIDescription("General settings")
@Deprecated("Use the V1 model")
data class OldIngestionConfigGeneral(
    @APIDescription("Must jobs be considered as validations?")
    val skipJobs: Boolean = true,
    @APIDescription("Must we use the job name as a prefix to the validation stamp?")
    val validationJobPrefix: Boolean? = null,
)

/**
 * Run configuration
 */
@APIName("GitHubIngestionConfigRun")
@APIDescription("Settings for the workflow run level")
@Deprecated("Use the V1 model")
data class OldIngestionRunConfig(
    @APIDescription("Should we consider the runs to create a validation run?")
    val enabled: Boolean? = null,
    @APIDescription("Filter on the run names")
    val filter: FilterConfig = FilterConfig(),
)

/**
 * Workflow configuration
 */
@APIName("GitHubIngestionConfigWorkflow")
@APIDescription("Settings for the workflows ingestion")
@Deprecated("Use the V1 model")
data class OldIngestionWorkflowConfig(
    @APIDescription("Filter on the workflow names")
    val filter: FilterConfig = FilterConfig(),
)

/**
 * Step configuration
 *
 * @param name Exact name of the step in the workflow
 * @param validation Name of the validation stamp to use (instead of a generated one)
 * @param validationJobPrefix Must we use the job name as a prefix to the validation stamp?
 * @param description Description for the validation stamp
 */
@APIName("GitHubIngestionStepConfig")
@Deprecated("Use the V1 model")
data class OldStepConfig(
    @APIDescription("Exact name of the step in the workflow")
    val name: String,
    @APIDescription("Name of the validation stamp to use (instead of a generated one)")
    val validation: String? = null,
    @APIDescription("Must we use the job name as a prefix to the validation stamp?")
    val validationJobPrefix: Boolean? = null,
    @APIDescription("Description for the validation stamp")
    val description: String? = null,
)

/**
 * Job level configuration
 *
 * @param name Exact name of the job in the workflow
 * @param validation Name of the validation stamp to use (instead of a generated one)
 * @param description Description for the validation stamp
 * @param validationJobPrefix Must we use the job name as a prefix to the validation stamp?
 */
@APIName("GitHubIngestionJobConfig")
@Deprecated("Use the V1 model")
data class OldJobConfig(
    @APIDescription("Exact name of the job in the workflow")
    val name: String,
    @APIDescription("Name of the validation stamp to use (instead of a generated one)")
    val validation: String? = null,
    @APIDescription("Description for the validation stamp")
    val description: String? = null,
    @APIDescription("Must we use the job name as a prefix to the validation stamp?")
    val validationJobPrefix: Boolean? = null,
)

/**
 * Validation stamp configuration.
 */
@APIName("GitHubIngestionValidationConfig")
@Deprecated("Use the V1 model")
data class OldValidationConfig(
    @APIDescription("Unique name for the validation stamp in the branch")
    val name: String,
    @APIDescription("Optional description")
    val description: String? = null,
    @APIDescription("Data type")
    val dataType: OldValidationTypeConfig? = null,
    @APIDescription("Reference to the image to set")
    val image: String? = null,
)

/**
 * Data type for a validation.
 */
@APIName("GitHubIngestionValidationTypeConfig")
@Deprecated("Use the V1 model")
data class OldValidationTypeConfig(
    @APIDescription("FQCN or shortcut for the data type")
    val type: String,
    @APIDescription("Data type configuration")
    val config: JsonNode? = null,
)

/**
 * Promotion configuration
 *
 * @param name Unique name for the promotion in the branch
 */
@APIName("GitHubIngestionPromotionConfig")
@Deprecated("Use the V1 model")
data class OldPromotionConfig(
    @APIDescription("Unique name for the promotion in the branch")
    val name: String,
    @APIDescription("Optional description")
    val description: String? = null,
    @APIDescription("List of validations triggering this promotion. Important: these names are the names of the validations after step name resolution.")
    val validations: List<String> = emptyList(),
    @APIDescription("List of promotions triggering this promotion")
    val promotions: List<String> = emptyList(),
    @APIDescription("Regular expression to include validation stamps by name")
    val include: String? = null,
    @APIDescription("Regular expression to exclude validation stamps by name")
    val exclude: String? = null,
    @APIDescription("Reference to the image to set")
    val image: String? = null,
)

/**
 * List of CasC nodes for the projects & branches
 */
@APIDescription("List of CasC nodes for the projects & branches")
@Deprecated("Use the V1 model")
data class OldIngestionCascConfig(
    @APIDescription("Casc for the project")
    val project: OldIngestionCascBranchConfig = OldIngestionCascBranchConfig(),
    @APIDescription("Casc for the branch")
    val branch: OldIngestionCascBranchConfig = OldIngestionCascBranchConfig(),
)

/**
 * CasC node for the project
 */
@APIDescription("CasC node for the project")
@Deprecated("Use the V1 model")
data class OldIngestionCascBranchConfig(
    @APIDescription("Regular expression for the branches which can setup the entity")
    val includes: String = "main",
    @APIDescription("Regular expression to exclude branches")
    val excludes: String = "",
    @APIDescription("Casc configuration for the project")
    val casc: JsonNode = NullNode.instance,
)