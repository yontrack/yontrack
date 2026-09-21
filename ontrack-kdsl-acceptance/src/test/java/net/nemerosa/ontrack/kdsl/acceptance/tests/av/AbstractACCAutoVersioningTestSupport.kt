package net.nemerosa.ontrack.kdsl.acceptance.tests.av

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.kdsl.acceptance.tests.ACCProperties
import net.nemerosa.ontrack.kdsl.acceptance.tests.AbstractACCDSLTestSupport
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.waitUntil
import net.nemerosa.ontrack.kdsl.spec.Branch
import net.nemerosa.ontrack.kdsl.spec.Project
import net.nemerosa.ontrack.kdsl.spec.extension.av.AutoVersioningSettings
import net.nemerosa.ontrack.kdsl.spec.extension.av.autoVersioning
import net.nemerosa.ontrack.kdsl.spec.extension.notifications.notifications
import net.nemerosa.ontrack.kdsl.spec.settings.settings
import org.junit.jupiter.api.BeforeEach

abstract class AbstractACCAutoVersioningTestSupport : AbstractACCDSLTestSupport() {

    @BeforeEach
    fun init() {
        ontrack.autoVersioning.audit.purge()
    }

    protected fun withAutoVersioning(
        enabled: Boolean = true,
        code: () -> Unit,
    ) {
        val old = ontrack.settings.autoVersioning.get()
        try {
            ontrack.settings.autoVersioning.set(
                AutoVersioningSettings(
                    enabled = enabled,
                )
            )
            code()
        } finally {
            ontrack.settings.autoVersioning.set(old)
        }
    }

    protected fun branchWithPromotion(
        name: String = "main",
        promotion: String,
        code: Branch.() -> Unit = {},
    ): Branch = project {
        branch(name = name) {
            promotion(name = promotion)
            code()
            this
        }
    }

    protected fun waitForAutoVersioningCompletion(
        timeout: Long = ACCProperties.AutoVersioning.autoVersioningCompletion,
    ) {
        AutoVersioningUtils.waitForAutoVersioningCompletion(ontrack, timeout = timeout)
    }

    protected fun checkMostRecentStateOfAutoVersioningAuditForSourceAndTargetBranch(
        sourceProject: Project,
        targetBranch: Branch,
        expectedMostRecentState: String?,
        expectedData: Map<String, String> = emptyMap(),
    ) {
        waitUntil(
            task = "Latest state for ${sourceProject.name}/${targetBranch.name} is $expectedMostRecentState",
            initial = 1_000L,
            timeout = 120_000L,
        ) {
            // Check result
            val entry = ontrack.autoVersioning.audit.entries(
                size = 1,
                source = sourceProject.name,
                project = targetBranch.project.name,
                branch = targetBranch.name
            ).firstOrNull()
            entry?.let {
                // Checks its most recent state
                if (expectedMostRecentState != null && expectedMostRecentState == it.mostRecentState.state) {
                    // Checks the data
                    if (expectedData.isNotEmpty()) {
                        expectedData.all { (key, valueRegex) ->
                            val actualValue: JsonNode? = it.mostRecentState.data[key]
                            actualValue != null && actualValue.asText().matches(valueRegex.toRegex())
                        }
                    } else {
                        true
                    }
                } else {
                    false
                }
            } ?: false
        }
    }

    protected fun displayNotifications(group: String, expectedMessage: String? = null): () -> Unit = {
        println("Notifications for group $group:")
        ontrack.notifications.inMemory.group(group).forEach {
            println(">>>>>>>>>>>>>>>>")
            println(it)
            println("<<<<<<<<<<<<<<<<")
        }
        if (!expectedMessage.isNullOrBlank()) {
            println("Expected message:")
            println(expectedMessage)
        }
    }

}