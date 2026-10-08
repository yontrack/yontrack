package net.nemerosa.ontrack.kdsl.acceptance.tests.agents

import net.nemerosa.ontrack.kdsl.acceptance.tests.AbstractACCDSLTestSupport
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.uid
import net.nemerosa.ontrack.kdsl.spec.Ontrack
import net.nemerosa.ontrack.kdsl.spec.admin.agents
import net.nemerosa.ontrack.kdsl.spec.extension.agents.FEATURE_AGENTS
import net.nemerosa.ontrack.kdsl.spec.extension.agents.nonAgentEvidence
import net.nemerosa.ontrack.kdsl.spec.extension.license.isLicensedFeatureEnabled
import net.nemerosa.ontrack.kdsl.spec.withToken
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * *Evidence from non-agents only* on a validation stamp (#2030), with the licence enabling the agent
 * governance: an agent token is refused on the restricted stamp, and accepted on another one.
 */
class ACCNonAgentEvidence : AbstractACCDSLTestSupport() {

    /**
     * A client acting as an agent, whose owner has the automation role.
     */
    private fun agent(): Ontrack {
        lateinit var token: String
        withUser(globalRole = "AUTOMATION") {
            token = ontrack.agents.register(
                slug = uid("acc-").lowercase(),
                displayName = "Claude",
                tool = "Claude Code",
            ).generateToken("ci")
        }
        return ontrack.withToken(token)
    }

    @Test
    fun `An agent is refused on the restricted stamp and accepted on another one`() {
        assertTrue(ontrack.isLicensedFeatureEnabled(FEATURE_AGENTS), "The licence enables the agent governance")
        val asAgent = agent()
        project {
            branch {
                validationStamp("CI")
                val review = validationStamp("REVIEW")
                review.nonAgentEvidence = true
                assertTrue(review.nonAgentEvidence, "Restricted to non-agents")

                val build = build { this }
                val agentBuild = assertNotNull(asAgent.findBuildByName(project.name, name, build.name))

                // Refused on the restricted stamp
                val refusal = try {
                    agentBuild.validate("REVIEW", status = "PASSED")
                    fail("Expected a refusal")
                } catch (any: Exception) {
                    any.message ?: ""
                }
                assertTrue(
                    refusal.contains("evidence on REVIEW must come from a non-agent actor"),
                    "Refused: $refusal"
                )
                // Accepted on another one
                agentBuild.validate("CI", status = "PASSED")

                val runs = assertNotNull(ontrack.findBuildByName(project.name, name, build.name))
                assertEquals(1, runs.getValidationRuns("CI").size, "Run of the agent on CI")
                assertTrue(runs.getValidationRuns("REVIEW").isEmpty(), "No run of the agent on REVIEW")

                // A person is accepted on the restricted stamp
                build.validate("REVIEW", status = "PASSED")
                assertEquals(1, runs.getValidationRuns("REVIEW").size, "Run of the person on REVIEW")
            }
        }
    }
}
