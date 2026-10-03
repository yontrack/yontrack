package net.nemerosa.ontrack.extension.audittrail.tampering

import net.nemerosa.ontrack.extension.audittrail.AbstractAuditTrailITSupport
import net.nemerosa.ontrack.extension.audittrail.AuditTrailConfigProperties
import net.nemerosa.ontrack.extension.audittrail.service.TrailService
import net.nemerosa.ontrack.extension.audittrail.ui.DemoTamperingController
import net.nemerosa.ontrack.extension.audittrail.verification.TrailVerificationProblemType
import net.nemerosa.ontrack.extension.audittrail.verification.TrailVerificationService
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.exceptions.InputException
import net.nemerosa.ontrack.model.exceptions.NotFoundException
import net.nemerosa.ontrack.model.message.GlobalMessageService
import net.nemerosa.ontrack.model.message.MessageType
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.model.structure.Build
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.AccessDeniedException
import org.springframework.test.context.TestPropertySource
import kotlin.test.assertEquals

/**
 * The demonstration tampering, switched on: a global administrator rewrites the payload of one
 * entry, nothing is recomputed, and the verification of the trail breaks at that entry.
 *
 * Its own Spring context, the property being set for this class only.
 */
@TestPropertySource(
    properties = [
        "${AuditTrailConfigProperties.DEMO_TAMPERING_PREFIX}.enabled=true",
    ]
)
class DemoTamperingIT : AbstractAuditTrailITSupport() {

    @Autowired
    private lateinit var demoTamperingController: DemoTamperingController

    @Autowired
    private lateinit var trailService: TrailService

    @Autowired
    private lateinit var trailVerificationService: TrailVerificationService

    @Autowired
    private lateinit var globalMessageService: GlobalMessageService

    /**
     * A build with build.created, validation.run and promotion.added.
     */
    private fun trailedBuild(code: Build.() -> Unit) {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp()
                    val pl = promotionLevel()
                    build {
                        validate(vs)
                        promote(pl)
                        code()
                    }
                }
            }
        }
    }

    private val forged = """{"validationStamp":"forged","status":"PASSED"}"""

    @Test
    fun `Rewriting the payload of an entry breaks the verification at that entry`() {
        trailedBuild {
            assertEquals(true, trailVerificationService.verify(this).chainIntact)

            val view = demoTamperingController.rewritePayload(id(), 2, forged.parseAsJson())
            assertEquals(2, view.seq)
            assertEquals(forged.parseAsJson(), view.payload)

            val verification = trailVerificationService.verify(this)
            assertEquals(false, verification.chainIntact)
            assertEquals(2, verification.firstBrokenSeq)
            assertEquals(
                listOf(2 to TrailVerificationProblemType.HASH),
                verification.problems.map { it.seq to it.type },
            )
            // The endorsements sign the stored hashes, which did not change
            assertEquals(true, verification.endorsementsValid)
        }
    }

    @Test
    fun `Nothing but the payload of the entry changes`() {
        trailedBuild {
            val before = trailService.getEntries(this)
            val endorsementsBefore = trailService.getEndorsements(this)

            demoTamperingController.rewritePayload(id(), 2, forged.parseAsJson())

            val after = trailService.getEntries(this)
            assertEquals(forged.parseAsJson(), after[1].payload)
            assertEquals(before[1].copy(payload = after[1].payload), after[1], "Hash, previous hash, actor and time kept")
            assertEquals(listOf(before[0], before[2]), listOf(after[0], after[2]), "Other entries untouched")
            assertEquals(endorsementsBefore, trailService.getEndorsements(this), "Endorsements untouched")
        }
    }

    @Test
    fun `Permanent error message while the switch is on`() {
        val messages = globalMessageService.globalMessages.filter {
            it.content == "This instance allows trail tampering for demonstration: its trails prove nothing."
        }
        assertEquals(listOf(MessageType.ERROR), messages.map { it.type })
    }

    @Test
    fun `Only a global administrator may tamper with a trail`() {
        trailedBuild {
            val build = this
            assertThrows<AccessDeniedException> {
                asGlobalRole(Roles.GLOBAL_AUTOMATION).call {
                    demoTamperingController.rewritePayload(build.id(), 2, forged.parseAsJson())
                }
            }
            assertThrows<AccessDeniedException> {
                asUserWithView(build).call {
                    demoTamperingController.rewritePayload(build.id(), 2, forged.parseAsJson())
                }
            }
            assertEquals(true, trailVerificationService.verify(build).chainIntact, "Trail untouched")
        }
    }

    @Test
    fun `The new payload must be a JSON object a trail accepts`() {
        trailedBuild {
            assertThrows<InputException> {
                demoTamperingController.rewritePayload(id(), 2, """{"coverage":0.85}""".parseAsJson())
            }
            assertThrows<InputException> {
                demoTamperingController.rewritePayload(id(), 2, listOf("forged").asJson())
            }
            assertEquals(true, trailVerificationService.verify(this).chainIntact, "Trail untouched")
        }
    }

    @Test
    fun `No entry at this position`() {
        trailedBuild {
            assertThrows<NotFoundException> {
                demoTamperingController.rewritePayload(id(), 4, forged.parseAsJson())
            }
        }
    }
}
