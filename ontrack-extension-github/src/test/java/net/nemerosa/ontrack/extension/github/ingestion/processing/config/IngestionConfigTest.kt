package net.nemerosa.ontrack.extension.github.ingestion.processing.config

import net.nemerosa.ontrack.extension.github.ingestion.config.model.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class IngestionConfigTest {

    @Test
    fun `Job validation stamp name without customization`() {
        assertEquals(
            "job",
            IngestionConfig().getValidationStampName("job", null)
        )
    }

    @Test
    fun `Job validation stamp name with customization without validation`() {
        assertEquals(
            "job",
            IngestionConfig(
                jobs = IngestionConfigJobs(
                    mappings = listOf(
                        JobIngestionConfigValidation(name = "job")
                    )
                )
            ).getValidationStampName("job", null)
        )
    }

    @Test
    fun `Job validation stamp name with customization with validation`() {
        assertEquals(
            "job-validation",
            IngestionConfig(
                jobs = IngestionConfigJobs(
                    mappings = listOf(
                        JobIngestionConfigValidation(name = "job", validation = "job-validation"),
                    )
                )
            ).getValidationStampName("job", null)
        )
    }

    @Test
    fun `Step validation stamp name without customization`() {
        assertEquals(
            "Job name-Step name",
            IngestionConfig().getValidationStampName("Job name", "Step name")
        )
    }

    @Test
    fun `Step validation stamp name with customization of validation`() {
        assertEquals(
            "Job name-step-validation",
            IngestionConfig(
                steps = IngestionConfigSteps(
                    mappings = listOf(
                        StepIngestionConfigValidation(name = "Step name", validation = "step-validation")
                    )
                )
            ).getValidationStampName("Job name", "Step name")
        )
    }

    @Test
    fun `Step validation stamp name with customization of validation and job name`() {
        assertEquals(
            "job-validation-step-validation",
            IngestionConfig(
                jobs = IngestionConfigJobs(
                    mappings = listOf(
                        JobIngestionConfigValidation(name = "Job name", validation = "job-validation"),
                    )
                ),
                steps = IngestionConfigSteps(
                    mappings = listOf(
                        StepIngestionConfigValidation(name = "Step name", validation = "step-validation")
                    )
                )
            ).getValidationStampName("Job name", "Step name")
        )
    }

    @Test
    fun `Step validation stamp name with customization of validation and no job prefix`() {
        assertEquals(
            "step-validation",
            IngestionConfig(
                steps = IngestionConfigSteps(
                    mappings = listOf(
                        StepIngestionConfigValidation(
                            name = "Step name",
                            validation = "step-validation",
                            validationPrefix = false
                        )
                    )
                )
            ).getValidationStampName("Job name", "Step name")
        )
    }

    @Test
    fun `Step validation stamp name with customization of job prefix at job level`() {
        assertEquals(
            "Step name",
            IngestionConfig(
                jobs = IngestionConfigJobs(validationPrefix = false),
                steps = IngestionConfigSteps(
                    mappings = listOf(
                        StepIngestionConfigValidation(name = "Step name")
                    )
                )
            ).getValidationStampName("Job name", "Step name")
        )
    }

    @Test
    fun `Step validation stamp name with customization of job prefix at job level and overridden at step level`() {
        assertEquals(
            "Job name-Step name",
            IngestionConfig(
                jobs = IngestionConfigJobs(validationPrefix = false),
                steps = IngestionConfigSteps(
                    mappings = listOf(
                        StepIngestionConfigValidation(name = "Step name", validationPrefix = true)
                    )
                )
            ).getValidationStampName("Job name", "Step name")
        )
    }

    @Test
    fun `Step validation stamp name with no job prefix`() {
        assertEquals(
            "Step name",
            IngestionConfig(
                jobs = IngestionConfigJobs(validationPrefix = false),
            ).getValidationStampName("Job name", "Step name")
        )
    }

    @Test
    fun `Job validation stamp description without customization`() {
        assertEquals(
            "Job name",
            IngestionConfig().getValidationStampDescription("Job name", null)
        )
    }

    @Test
    fun `Job validation stamp description with customization but no description`() {
        assertEquals(
            "Job name",
            IngestionConfig(
                jobs = IngestionConfigJobs(
                    mappings = listOf(
                        JobIngestionConfigValidation(name = "Job name"),
                    )
                ),
            ).getValidationStampDescription("Job name", null)
        )
    }

    @Test
    fun `Job validation stamp description with customization of description`() {
        assertEquals(
            "Job description",
            IngestionConfig(
                jobs = IngestionConfigJobs(
                    mappings = listOf(
                        JobIngestionConfigValidation(name = "Job name", description = "Job description"),
                    )
                ),
            ).getValidationStampDescription("Job name", null)
        )
    }

    @Test
    fun `Step validation stamp description without customization`() {
        assertEquals(
            "Step name",
            IngestionConfig().getValidationStampDescription("Job name", "Step name")
        )
    }

    @Test
    fun `Step validation stamp description with customization but no description`() {
        assertEquals(
            "Step name",
            IngestionConfig(
                steps = IngestionConfigSteps(
                    mappings = listOf(
                        StepIngestionConfigValidation(name = "Step name")
                    )
                )
            ).getValidationStampDescription("Job name", "Step name")
        )
    }

    @Test
    fun `Step validation stamp description with customization of description`() {
        assertEquals(
            "Step description",
            IngestionConfig(
                steps = IngestionConfigSteps(
                    mappings = listOf(
                        StepIngestionConfigValidation(name = "Step name", description = "Step description")
                    )
                )
            ).getValidationStampDescription("Job name", "Step name")
        )
    }

}