package net.nemerosa.ontrack.kdsl.acceptance.tests.audittrail

import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.kdsl.acceptance.tests.AbstractACCDSLTestSupport
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.uid
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.AuditTrailStorageState
import net.nemerosa.ontrack.kdsl.connector.support.DefaultConnector
import net.nemerosa.ontrack.kdsl.spec.Ontrack
import net.nemerosa.ontrack.kdsl.spec.changeStatus
import net.nemerosa.ontrack.kdsl.spec.delete
import net.nemerosa.ontrack.kdsl.spec.extension.audittrail.auditTrailStorageState
import net.nemerosa.ontrack.kdsl.spec.extension.audittrail.trail
import net.nemerosa.ontrack.kdsl.spec.extension.environments.environments
import net.nemerosa.ontrack.kdsl.spec.generateToken
import net.nemerosa.ontrack.kdsl.spec.globalMessages
import net.nemerosa.ontrack.kdsl.spec.revokeToken
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What the demo seed records in the trail of its builds, through the KDSL (#1970): the name of the
 * token a pipeline posts through, a status changed by somebody else, a validation stamp deleted
 * with its runs, and an admission rule overridden for a deployment.
 */
class ACCDSLAuditTrailActors : AbstractACCDSLTestSupport() {

    @Test
    fun `The name of the token a build is created through is the actor of its entries`() {
        val tokenName = uid("ci-")
        val value = ontrack.generateToken(tokenName)
        try {
            val pipeline = Ontrack(DefaultConnector(url = ontrack.connector.url, token = value))
            project {
                branch {
                    val build = pipeline.findBranchByName(project.name, name)!!.createBuild(uid("b-"))
                    val trail = assertNotNull(build.trail)
                    val actor = trail.entries.first().actor
                    assertEquals("token", actor.path("via").asString())
                    assertEquals(tokenName, actor.path("tokenName").asString())
                }
            }
        } finally {
            ontrack.revokeToken(tokenName)
        }
        // Revoked: nothing is seen through it any more
        val revoked = Ontrack(DefaultConnector(url = ontrack.connector.url, token = value))
        assertTrue(runCatching { revoked.projects() }.getOrNull().isNullOrEmpty(), "The token is revoked")
    }

    @Test
    fun `A failed run fixed afterwards records its new status and its comment`() {
        project {
            branch {
                val vs = validationStamp()
                build {
                    val run = validate(vs.name, status = "FAILED")
                    run.changeStatus("FIXED", "Known flaky test.")

                    val entry = assertNotNull(trail).entries.last()
                    assertEquals("validation.status", entry.type)
                    assertEquals("FIXED", entry.payload.path("status").asString())
                    assertEquals("Known flaky test.", entry.payload.path("description").asString())
                }
            }
        }
    }

    @Test
    fun `A deleted validation stamp leaves a cascade entry in the trail of every build it ran on`() {
        project {
            branch {
                val vs = validationStamp()
                val builds = (1..2).map { index ->
                    build(name = "$index") {
                        validate(vs.name, status = "PASSED")
                        this
                    }
                }
                vs.delete()

                builds.forEach { build ->
                    val entry = assertNotNull(build.trail).entries.last()
                    assertEquals("validation.deleted", entry.type)
                    assertEquals("cascade/validation-stamp-deleted", entry.payload.path("reason").asString())
                }
            }
        }
    }

    @Test
    fun `An admission rule overridden for a deployment is recorded in the trail of its build`() {
        val application = project { this }
        val branch = application.branch { this }
        val environment = ontrack.environments.createEnvironment(name = uid("env-"), order = 0)
        val slot = environment.createSlot(project = application)
        slot.addAdmissionRule(
            ruleId = "manual",
            ruleConfig = mapOf("message" to "Approve?").asJson(),
            name = "approval",
        )
        val build = branch.build(name = "1.0.0") { this }

        slot.createPipeline(build = build)
            .overrideRule("approval", "Approved elsewhere.")
            .startDeploying()
            .finishDeployment()

        val types = assertNotNull(build.trail).entries.map { it.type }
        assertEquals(
            listOf("deployment.created", "deployment.rule-overridden", "deployment.running", "deployment.done"),
            types.dropWhile { it != "deployment.created" },
        )
        assertFailsWith<IllegalStateException> {
            slot.createPipeline(build = build).overrideRule("nothing", "No such rule.")
        }
    }

    @Test
    fun `The state of the evidence storage and the global messages are readable`() {
        assertEquals(AuditTrailStorageState.OK, ontrack.auditTrailStorageState)
        assertTrue(ontrack.globalMessages.all { it.content.isNotBlank() })
    }
}
