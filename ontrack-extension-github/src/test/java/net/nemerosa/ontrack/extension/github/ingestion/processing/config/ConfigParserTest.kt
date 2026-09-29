package net.nemerosa.ontrack.extension.github.ingestion.processing.config

import net.nemerosa.ontrack.extension.github.ingestion.config.model.*
import net.nemerosa.ontrack.extension.github.ingestion.config.parser.ConfigParser
import net.nemerosa.ontrack.extension.github.ingestion.config.parser.ConfigParsingException
import net.nemerosa.ontrack.extension.github.ingestion.config.parser.ConfigVersionException
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ConfigParserTest {

    @Test
    fun `V2 configuration`() {
        test(
            """
                version: v2
                jobs:
                    validationPrefix: false
                    mappings:
                        - name: Job name
                          validation: my-job
                          description: My description
                steps:
                    mappings:
                        - name: Step name
                          validation: my-validation
                          validationPrefix: false
                          description: My description
            """
        ) {
            assertEquals("v2", it.version)

            assertEquals(false, it.jobs.validationPrefix)
            assertEquals(1, it.jobs.mappings.size)
            assertEquals("Job name", it.jobs.mappings.first().name)
            assertEquals("my-job", it.jobs.mappings.first().validation)
            assertEquals("My description", it.jobs.mappings.first().description)

            assertEquals(1, it.steps.mappings.size)
            assertEquals("Step name", it.steps.mappings.first().name)
            assertEquals("my-validation", it.steps.mappings.first().validation)
            assertEquals(false, it.steps.mappings.first().validationPrefix)
            assertEquals("My description", it.steps.mappings.first().description)

            assertEquals(IngestionConfigVSNameNormalization.DEFAULT, it.vsNameNormalization)
        }
    }

    @Test
    fun `V2 configuration with legacy VS name normalization`() {
        test(
            """
                version: v2
                jobs:
                    validationPrefix: false
                    mappings:
                        - name: Job name
                          validation: my-job
                          description: My description
                steps:
                    mappings:
                        - name: Step name
                          validation: my-validation
                          validationPrefix: false
                          description: My description
                vs-name-normalization: LEGACY
            """
        ) {
            assertEquals("v2", it.version)

            assertEquals(false, it.jobs.validationPrefix)
            assertEquals(1, it.jobs.mappings.size)
            assertEquals("Job name", it.jobs.mappings.first().name)
            assertEquals("my-job", it.jobs.mappings.first().validation)
            assertEquals("My description", it.jobs.mappings.first().description)

            assertEquals(1, it.steps.mappings.size)
            assertEquals("Step name", it.steps.mappings.first().name)
            assertEquals("my-validation", it.steps.mappings.first().validation)
            assertEquals(false, it.steps.mappings.first().validationPrefix)
            assertEquals("My description", it.steps.mappings.first().description)

            assertEquals(IngestionConfigVSNameNormalization.LEGACY, it.vsNameNormalization)
        }
    }

    @Test
    fun `Configuration without a version is read as V2`() {
        test(
            """
                jobs:
                    validationPrefix: false
                    mappings:
                        - name: Job name
                          validation: my-job
                steps:
                    mappings:
                        - name: Step name
                          validation: my-validation
            """
        ) {
            assertEquals("v2", it.version)
            assertEquals(false, it.jobs.validationPrefix)
            assertEquals("my-job", it.jobs.mappings.single().validation)
            assertEquals("my-validation", it.steps.mappings.single().validation)
            assertEquals(IngestionConfigVSNameNormalization.DEFAULT, it.vsNameNormalization)
        }
    }

    @Test
    fun `Empty configuration is the default one`() {
        test("") {
            assertEquals(IngestionConfig(), it)
        }
    }

    @Test
    fun `V1 configuration is no longer supported`() {
        val ex = assertFailsWith<ConfigParsingException> {
            ConfigParser.parseYaml(
                """
                    version: v1
                    jobs:
                        validationPrefix: false
                """.trimIndent()
            )
        }
        val cause = assertIs<ConfigVersionException>(ex.cause)
        assertEquals(
            "Unsupported version for the ingestion configuration: v1. Use version v2 instead.",
            cause.message
        )
    }

    @Test
    fun `V0 configuration is no longer supported`() {
        assertFailsWith<ConfigParsingException> {
            ConfigParser.parseYaml(
                """
                    jobs:
                        - name: Job name
                          validation: my-job
                          description: My description
                    steps:
                        - name: Step name
                          validation: my-validation
                          validationJobPrefix: false
                          description: My description
                """.trimIndent()
            )
        }
    }

    @Test
    fun `Rendering a configuration as Yaml`() {
        val yaml = ConfigParser.toYaml(
            IngestionConfig(
                steps = IngestionConfigSteps(
                    mappings = listOf(
                        StepIngestionConfigValidation(
                            name = "Some step",
                            validation = "some-validation"
                        )
                    )
                ),
                jobs = IngestionConfigJobs(
                    mappings = listOf(
                        JobIngestionConfigValidation(
                            name = "Some job",
                            validation = "some-job"
                        )
                    )
                ),
            )
        )
        assertEquals(
            """
                ---
                vs-name-normalization: "DEFAULT"
                version: "v2"
                workflows:
                  filter:
                    includes: ".*"
                    excludes: ""
                  validations:
                    enabled: true
                    filter:
                      includes: ".*"
                      excludes: ""
                    prefix: "workflow-"
                    suffix: ""
                  events:
                  - "push"
                  branchFilter:
                    includes: ".*"
                    excludes: ""
                  includePRs: true
                  buildIdStrategy:
                    id: null
                    config: null
                jobs:
                  filter:
                    includes: ".*"
                    excludes: ""
                  validationPrefix: true
                  mappings:
                  - name: "Some job"
                    validation: "some-job"
                    description: null
                steps:
                  filter:
                    includes: ""
                    excludes: ".*"
                  mappings:
                  - name: "Some step"
                    validation: "some-validation"
                    description: null
                    validationPrefix: null
                setup:
                  validations: []
                  promotions: []
                  project:
                    includes: "main"
                    excludes: ""
                    casc: null
                  branch:
                    includes: "main"
                    excludes: ""
                    casc: null
                tagging:
                  strategies: []
                  commit-property: true
            """.trimIndent().trim(),
            yaml.trim()
        )
    }

    @Test
    fun `Failing on parsing error`() {
        val yaml = """
            workflows:
                excludes: "not-useful"
        """.trimIndent()
        assertFailsWith<ConfigParsingException> {
            ConfigParser.parseYaml(yaml)
        }
    }

    fun test(
        yaml: String,
        code: (config: IngestionConfig) -> Unit,
    ) {
        val config = ConfigParser.parseYaml(yaml.trimIndent())
        code(config)
    }

}