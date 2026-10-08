package net.nemerosa.ontrack.extension.agents.evidence

import net.nemerosa.ontrack.extension.agents.license.AgentsLicensedFeatureProvider.Companion.FEATURE_AGENTS
import net.nemerosa.ontrack.extension.license.DevLicenseService
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.it.AgentTestSupport
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.ValidationRun
import net.nemerosa.ontrack.model.structure.ValidationRunRequest
import net.nemerosa.ontrack.model.structure.ValidationRunService
import net.nemerosa.ontrack.model.structure.ValidationRunStatus
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID
import net.nemerosa.ontrack.model.structure.ValidationStamp
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * *Evidence from non-agents only* on a validation stamp (#2030): with the licence, an agent may
 * neither create a run on the stamp, by any entry point, nor change the status of one of its runs.
 */
class NonAgentEvidenceIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var devLicenseService: DevLicenseService

    @Autowired
    private lateinit var agentTestSupport: AgentTestSupport

    @Autowired
    private lateinit var validationRunService: ValidationRunService

    private fun <T> withoutAgentsLicence(code: () -> T): T {
        devLicenseService.setFeatureEnabled(FEATURE_AGENTS, false)
        return try {
            code()
        } finally {
            devLicenseService.setFeatureEnabled(FEATURE_AGENTS, true)
        }
    }

    /**
     * An agent whose owner may record evidence everywhere.
     */
    private fun agent() = agentTestSupport.registerAgent(
        owner = asAdmin { doCreateAccountWithGlobalRole(Roles.GLOBAL_AUTOMATION) },
    )

    private fun <T> asAgent(code: () -> T): T = agentTestSupport.withToken(agent().token, code = code)

    private fun ValidationStamp.nonAgentEvidence(enabled: Boolean = true) {
        setProperty(this, NonAgentEvidencePropertyType::class.java, NonAgentEvidenceProperty(enabled))
    }

    private fun message(stamp: ValidationStamp) = "evidence on ${stamp.name} must come from a non-agent actor"

    private fun Build.newRun(stamp: ValidationStamp): ValidationRun =
        structureService.newValidationRun(
            this,
            ValidationRunRequest(
                validationStampName = stamp.name,
                validationRunStatusId = ValidationRunStatusID.STATUS_FAILED,
            )
        )

    private fun Build.runs(stamp: ValidationStamp): List<ValidationRun> = asAdmin {
        structureService.getValidationRunsForBuildAndValidationStamp(id, stamp.id, 0, 10)
    }

    private fun ValidationRun.explain(): ValidationRun =
        structureService.newValidationRunStatus(
            this,
            ValidationRunStatus(
                ID.NONE,
                securityService.currentSignature,
                ValidationRunStatusID.STATUS_EXPLAINED,
                "Explained",
            )
        )

    /**
     * Creates a run through a GraphQL mutation, and returns its user error message, if any.
     */
    private fun graphQLRun(mutation: String, input: String): String? {
        var error: String? = null
        run(
            """
                mutation {
                    $mutation(input: {$input}) {
                        errors { message }
                    }
                }
            """
        ) { data ->
            error = data.path(mutation).path("errors").firstOrNull()?.path("message")?.asText()
        }
        return error
    }

    private fun Build.graphQLEntryPoints(stamp: ValidationStamp): Map<String, String?> = mapOf(
        "createValidationRun" to graphQLRun(
            "createValidationRun",
            """project: "${project.name}", branch: "${branch.name}", build: "$name", validationStamp: "${stamp.name}", validationRunStatus: "PASSED""""
        ),
        "createValidationRunById" to graphQLRun(
            "createValidationRunById",
            """buildId: $id, validationStamp: "${stamp.name}", validationRunStatus: "PASSED""""
        ),
        "validateBuildWithNumber" to graphQLRun(
            "validateBuildWithNumber",
            """project: "${project.name}", branch: "${branch.name}", build: "$name", validation: "${stamp.name}", value: 10, status: "PASSED""""
        ),
        "validateBuildByIdWithNumber" to graphQLRun(
            "validateBuildByIdWithNumber",
            """id: $id, validation: "${stamp.name}", value: 10, status: "PASSED""""
        ),
    )

    @Test
    fun `An agent is refused a run on the stamp, through each entry point`() {
        asAdmin {
            project {
                branch {
                    val review = validationStamp("REVIEW")
                    review.nonAgentEvidence()
                    build {
                        val errors = asAgent {
                            // Service
                            val ex = assertFailsWith<NonAgentEvidenceException> { newRun(review) }
                            assertEquals(message(review), ex.message)
                            // The system acting on behalf of the agent, like an ingestion
                            val sys = assertFailsWith<NonAgentEvidenceException> {
                                securityService.asAdmin { newRun(review) }
                            }
                            assertEquals(message(review), sys.message)
                            // GraphQL
                            graphQLEntryPoints(review)
                        }
                        errors.forEach { (mutation, error) ->
                            assertEquals(message(review), error, "Refused by $mutation")
                        }
                        assertTrue(runs(review).isEmpty(), "No run on the record")
                    }
                }
            }
        }
    }

    @Test
    fun `A human is accepted on the restricted stamp`() {
        asAdmin {
            project {
                branch {
                    val review = validationStamp("REVIEW")
                    review.nonAgentEvidence()
                    build {
                        val accepted = asAccountWithGlobalRole(Roles.GLOBAL_AUTOMATION) {
                            graphQLEntryPoints(review).forEach { (mutation, error) ->
                                assertNull(error, "Accepted by $mutation")
                            }
                            newRun(review)
                        }
                        assertEquals(5, runs(review).size)
                        assertNull(accepted.lastStatus.signature.actor, "Recorded by a person")
                    }
                }
            }
        }
    }

    @Test
    fun `An agent is refused a status change on the stamp, a human is accepted`() {
        asAdmin {
            project {
                branch {
                    val review = validationStamp("REVIEW")
                    review.nonAgentEvidence()
                    build {
                        val failed = validate(review, ValidationRunStatusID.STATUS_FAILED)
                        // Service
                        val ex = asAgent {
                            assertFailsWith<NonAgentEvidenceException> { failed.explain() }
                        }
                        assertEquals(message(review), ex.message)
                        // GraphQL
                        val error = asAgent {
                            var error: String? = null
                            run(
                                """
                                    mutation {
                                        changeValidationRunStatus(input: {
                                            validationRunId: ${failed.id},
                                            validationRunStatusId: "EXPLAINED",
                                            description: "Explained"
                                        }) {
                                            errors { message }
                                        }
                                    }
                                """
                            ) { data ->
                                error = data.path("changeValidationRunStatus").path("errors")
                                    .firstOrNull()?.path("message")?.asText()
                            }
                            error
                        }
                        assertEquals(message(review), error)
                        assertEquals(
                            ValidationRunStatusID.FAILED,
                            asAdmin { structureService.getValidationRun(failed.id) }.lastStatus.statusID.id,
                            "Status unchanged"
                        )
                        // A human
                        asAccountWithGlobalRole(Roles.GLOBAL_AUTOMATION) {
                            failed.explain()
                        }
                        assertEquals(
                            ValidationRunStatusID.EXPLAINED,
                            asAdmin { structureService.getValidationRun(failed.id) }.lastStatus.statusID.id,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `The system acting on behalf of an agent may not change the data of a run on the stamp`() {
        asAdmin {
            project {
                branch {
                    val review = validationStamp("REVIEW")
                    review.nonAgentEvidence()
                    build {
                        val passed = validate(review)
                        val ex = asAgent {
                            assertFailsWith<NonAgentEvidenceException> {
                                securityService.asAdmin {
                                    validationRunService.updateValidationRunData(passed, null)
                                }
                            }
                        }
                        assertEquals(message(review), ex.message)
                    }
                }
            }
        }
    }

    @Test
    fun `Licence off - the agent is accepted`() {
        asAdmin {
            project {
                branch {
                    val review = validationStamp("REVIEW")
                    review.nonAgentEvidence()
                    build {
                        val explained = withoutAgentsLicence {
                            asAgent { newRun(review).explain() }
                        }
                        assertNotNull(explained.lastStatus.signature.actor, "Recorded by the agent")
                        assertEquals(1, runs(review).size)
                    }
                }
            }
        }
    }

    @Test
    fun `A stamp without the property, or with the property switched off, accepts the agent`() {
        asAdmin {
            project {
                branch {
                    val ci = validationStamp("CI")
                    val review = validationStamp("REVIEW")
                    review.nonAgentEvidence(enabled = false)
                    build {
                        asAgent {
                            newRun(ci)
                            newRun(review).explain()
                        }
                        assertEquals(1, runs(ci).size)
                        assertEquals(1, runs(review).size)
                    }
                }
            }
        }
    }

    @Test
    fun `An agent cannot remove the restriction`() {
        asAdmin {
            project {
                branch {
                    val review = validationStamp("REVIEW")
                    review.nonAgentEvidence()
                    // The agent policy denies ProjectConfig to agents
                    asAgent {
                        assertFails {
                            propertyService.deleteProperty(review, NonAgentEvidencePropertyType::class.java)
                        }
                    }
                    assertNotNull(getProperty(review, NonAgentEvidencePropertyType::class.java))
                }
            }
        }
    }

    @Test
    fun `Setting and deleting the property through GraphQL`() {
        asAdmin {
            project {
                branch {
                    val review = validationStamp("REVIEW")
                    run(
                        """
                            mutation {
                                setValidationStampNonAgentEvidencePropertyById(input: {id: ${review.id}}) {
                                    errors { message }
                                }
                            }
                        """
                    ) { data ->
                        checkGraphQLUserErrors(data, "setValidationStampNonAgentEvidencePropertyById")
                    }
                    assertEquals(
                        NonAgentEvidenceProperty(enabled = true),
                        getProperty(review, NonAgentEvidencePropertyType::class.java)
                    )
                    run(
                        """
                            mutation {
                                deleteValidationStampNonAgentEvidencePropertyById(input: {id: ${review.id}}) {
                                    errors { message }
                                }
                            }
                        """
                    ) { data ->
                        checkGraphQLUserErrors(data, "deleteValidationStampNonAgentEvidencePropertyById")
                    }
                    assertNull(getProperty(review, NonAgentEvidencePropertyType::class.java))
                }
            }
        }
    }
}
