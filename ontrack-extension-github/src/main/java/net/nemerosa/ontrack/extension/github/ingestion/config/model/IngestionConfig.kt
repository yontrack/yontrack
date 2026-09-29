package net.nemerosa.ontrack.extension.github.ingestion.config.model

import com.fasterxml.jackson.annotation.JsonProperty
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.common.api.APIName
import net.nemerosa.ontrack.extension.github.ingestion.config.model.tagging.IngestionTaggingConfig

/**
 * Configuration for the ingestion.
 *
 * @property workflows Configuration for the ingestion of the workflows
 * @property jobs Configuration for the ingestion of the jobs
 * @property steps Configuration for the ingestion of the steps
 * @property setup Setup of Ontrack resources
 * @property tagging Configuration for the tag ingestion
 */
@APIName("GitHubIngestionConfig")
data class IngestionConfig(
    @APIDescription("Version of the configuration")
    val version: String = V2_VERSION,
    @APIDescription("Configuration for the ingestion of the workflows")
    val workflows: IngestionConfigWorkflows = IngestionConfigWorkflows(),
    @APIDescription("Configuration for the ingestion of the jobs")
    val jobs: IngestionConfigJobs = IngestionConfigJobs(),
    @APIDescription("Configuration for the ingestion of the steps")
    val steps: IngestionConfigSteps = IngestionConfigSteps(),
    @APIDescription("Setup of Ontrack resources")
    val setup: IngestionConfigSetup = IngestionConfigSetup(),
    @APIDescription("Configuration for the tag ingestion")
    val tagging: IngestionTaggingConfig = IngestionTaggingConfig(),
    @JsonProperty("vs-name-normalization")
    @APIDescription("Defines the way a computed name must be normalized before it can be used as a validation stamp name.")
    val vsNameNormalization: IngestionConfigVSNameNormalization = IngestionConfigVSNameNormalization.DEFAULT,
) {
    companion object {
        const val V2_VERSION = "v2"
    }

    /**
     * Gets the validation stamp name for a given job and step
     */
    fun getValidationStampName(job: String, step: String?): String =
        if (step == null) {
            val jobConfig = findJobValidationConfig(job)
            val baseName = jobConfig?.validation ?: job
            normalizeValidationStampName(baseName)
        } else {
            val stepConfig = findStepValidationConfig(step)
            // Step contribution
            val stepValidation = stepConfig?.validation ?: step
            // Job contribution
            // The configuration at job level always takes precedence unless overridden at step level
            val jobContribution = if ((jobs.validationPrefix && stepConfig?.validationPrefix != false) ||
                (!jobs.validationPrefix && stepConfig?.validationPrefix == true)
            ) {
                val jobConfig = findJobValidationConfig(job)
                val jobValidation = jobConfig?.validation ?: job
                "$jobValidation-$stepValidation"
            } else {
                stepValidation
            }
            // Normalization
            normalizeValidationStampName(jobContribution)
        }

    /**
     * Normalizes a name before it can be used as a validation stamp name.
     */
    fun normalizeValidationStampName(name: String): String =
        vsNameNormalization.normalization(name)

    /**
     * Gets the validation stamp description for a given job and step
     */
    fun getValidationStampDescription(job: String, step: String?): String =
        if (step != null) {
            val cfg = findStepValidationConfig(step)
            cfg?.description ?: step
        } else {
            val cfg = findJobValidationConfig(job)
            cfg?.description ?: job
        }

    private fun findStepValidationConfig(step: String): StepIngestionConfigValidation? =
        steps.mappings.find { it.name == step }

    private fun findJobValidationConfig(job: String): JobIngestionConfigValidation? =
        jobs.mappings.find { it.name == job }
}