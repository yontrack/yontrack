package net.nemerosa.ontrack.extension.av.workflows

import net.nemerosa.ontrack.extension.av.config.AutoApprovalMode
import net.nemerosa.ontrack.extension.av.config.AutoVersioningPushMode
import net.nemerosa.ontrack.extension.av.config.AutoVersioningSourceConfigPath
import net.nemerosa.ontrack.json.asJson
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AutoVersioningWorkflowNodeExecutorDataTest {

    @Test
    fun `Resolving keeps all the fields`() {
        val data = fullData()
        assertEquals(
            data,
            data.resolve { it },
            "Resolving with an identity templating returns an identical object"
        )
    }

    @Test
    fun `Resolving keeps the version rule`() {
        val data = fullData()
        val resolved = data.resolve { it }
        assertEquals("semver", resolved.versionRule)
        assertEquals(
            "ACCEPT",
            resolved.versionRuleConfig?.path("onUnparseable")?.asText(),
        )
    }

    @Test
    fun `Resolving templates the project, branch and version`() {
        val resolved = fullData().resolve { "$it-resolved" }
        assertEquals("prj-resolved", resolved.targetProject)
        assertEquals("main-resolved", resolved.targetBranch)
        assertEquals("1.0.0-resolved", resolved.targetVersion)
    }

    /**
     * Data with every field set to a non-default value, so that a field dropped
     * by [AutoVersioningWorkflowNodeExecutorData.resolve] shows up as a difference.
     */
    private fun fullData() = AutoVersioningWorkflowNodeExecutorData(
        targetProject = "prj",
        targetBranch = "main",
        targetVersion = "1.0.0",
        targetPath = "gradle.properties",
        targetRegex = "version = (.*)",
        targetProperty = "version",
        targetPropertyRegex = "(.*)",
        targetPropertyType = "npm",
        autoApproval = false,
        upgradeBranchPattern = "feature/version-<version>",
        postProcessing = "mock",
        postProcessingConfig = mapOf("some" to "config").asJson(),
        versionRule = "semver",
        versionRuleConfig = mapOf("onUnparseable" to "ACCEPT").asJson(),
        validationStamp = "AUTO_VERSIONING",
        autoApprovalMode = AutoApprovalMode.CLIENT,
        reviewers = listOf("dcoraboeuf"),
        prTitleTemplate = "title",
        prBodyTemplate = "body",
        prBodyTemplateFormat = "markdown",
        additionalPaths = listOf(
            AutoVersioningSourceConfigPath(
                path = "other.properties",
                regex = null,
                property = "version",
                propertyRegex = null,
                propertyType = null,
                versionSource = null,
            )
        ),
        pushMode = AutoVersioningPushMode.PUSH,
    )

}
