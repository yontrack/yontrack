package net.nemerosa.ontrack.extension.av.workflows

import com.fasterxml.jackson.databind.JsonNode
import io.mockk.mockk
import net.nemerosa.ontrack.extension.av.versionrules.AutoVersioningVersionRule
import net.nemerosa.ontrack.extension.av.versionrules.AutoVersioningVersionRuleRegistry
import net.nemerosa.ontrack.extension.av.versionrules.SemVerVersionRule
import net.nemerosa.ontrack.extension.av.versionrules.VersionRuleConfigException
import net.nemerosa.ontrack.extension.av.versionrules.VersionRuleNotFoundException
import net.nemerosa.ontrack.extension.workflows.registry.WorkflowParser
import net.nemerosa.ontrack.json.asJson
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

/**
 * Validation of the data of an `auto-versioning` workflow node.
 *
 * A typo in the ID of a _safety_ rule, or a configuration the rule cannot parse, must be rejected when
 * the workflow is saved, not when the node runs: the run the rule was meant to guard is the worst
 * possible moment to discover that the guard is not usable.
 */
class AutoVersioningWorkflowNodeExecutorValidationTest {

    private val semVerVersionRule = SemVerVersionRule(mockk())

    private val registry = object : AutoVersioningVersionRuleRegistry {
        @Suppress("UNCHECKED_CAST")
        override fun <T> findVersionRuleById(id: String): AutoVersioningVersionRule<T>? =
            allVersionRules.find { it.id == id } as? AutoVersioningVersionRule<T>?

        override val allVersionRules: List<AutoVersioningVersionRule<*>> = listOf(semVerVersionRule)
    }

    private val executor = AutoVersioningWorkflowNodeExecutor(
        extensionFeature = mockk(),
        autoVersioningProcessingService = mockk(),
        structureService = mockk(),
        securityService = mockk(),
        eventTemplatingService = mockk(),
        serializableEventService = mockk(),
        autoVersioningAuditService = mockk(),
        autoVersioningVersionRuleRegistry = registry,
    )

    @Test
    fun `No version rule is accepted`() {
        executor.validate(data().asJson())
    }

    @Test
    fun `A blank version rule is accepted`() {
        executor.validate(data(versionRule = "   ").asJson())
    }

    @Test
    fun `A known version rule is accepted`() {
        executor.validate(data(versionRule = "semver").asJson())
    }

    @Test
    fun `A known version rule with a valid configuration is accepted`() {
        executor.validate(
            data(
                versionRule = "semver",
                versionRuleConfig = mapOf("onUnparseable" to "ACCEPT").asJson(),
            ).asJson()
        )
    }

    @Test
    fun `An unknown version rule is rejected`() {
        assertFailsWith<VersionRuleNotFoundException> {
            executor.validate(data(versionRule = "no-such-rule").asJson())
        }
    }

    @Test
    fun `A known version rule with an unparseable configuration is rejected`() {
        assertFailsWith<VersionRuleConfigException> {
            executor.validate(
                data(
                    versionRule = "semver",
                    versionRuleConfig = mapOf("onUnparseable" to "NO_SUCH_POLICY").asJson(),
                ).asJson()
            )
        }
    }

    private fun data(
        versionRule: String? = null,
        versionRuleConfig: JsonNode? = null,
    ) = AutoVersioningWorkflowNodeExecutorData(
        targetProject = "target",
        targetBranch = "main",
        targetVersion = "1.0.0",
        targetPath = "gradle.properties",
        targetProperty = "version",
        versionRule = versionRule,
        versionRuleConfig = versionRuleConfig,
    )


    /**
     * The node data as a user writes it in a workflow YAML, so that the shape the documentation shows is
     * the shape the validation accepts.
     */
    @Test
    fun `A version rule declared in a workflow YAML is validated`() {
        assertFailsWith<VersionRuleNotFoundException> {
            executor.validate(yamlNodeData(versionRule = "no-such-rule", versionRuleConfig = "{}"))
        }
        assertFailsWith<VersionRuleConfigException> {
            executor.validate(yamlNodeData(versionRule = "semver", versionRuleConfig = "{onUnparseable: NO_SUCH_POLICY}"))
        }
        executor.validate(yamlNodeData(versionRule = "semver", versionRuleConfig = "{onUnparseable: ACCEPT}"))
    }

    private fun yamlNodeData(versionRule: String, versionRuleConfig: String): JsonNode =
        WorkflowParser.parseYamlWorkflow(
            """
                name: Deployment
                nodes:
                  - id: av
                    executorId: auto-versioning
                    data:
                        targetProject: target
                        targetBranch: main
                        targetPath: gradle.properties
                        targetProperty: version
                        targetVersion: 1.0.0
                        versionRule: $versionRule
                        versionRuleConfig: $versionRuleConfig
            """.trimIndent()
        ).getNode("av").data

}
