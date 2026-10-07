package net.nemerosa.ontrack.extension.general

import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.it.AgentTestSupport
import net.nemerosa.ontrack.model.security.AgentPolicyException
import net.nemerosa.ontrack.model.security.PromotionLevelAgentAdmission
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.PromotionLevel
import net.nemerosa.ontrack.model.structure.PromotionRun
import net.nemerosa.ontrack.model.structure.ValidationRunRequest
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.AccessDeniedException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The *Agents admitted* property on promotion levels: an agent promotes only on a level which admits
 * agents, and only if its owner can promote.
 */
class AgentsAdmittedPropertyIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var agentTestSupport: AgentTestSupport

    @Autowired
    private lateinit var promotionLevelAgentAdmission: PromotionLevelAgentAdmission

    private fun agentOf(ownerGlobalRole: String): AgentTestSupport.TestAgent =
        agentTestSupport.registerAgent(owner = asAdmin { doCreateAccountWithGlobalRole(ownerGlobalRole) })

    private fun AgentTestSupport.TestAgent.promote(build: Build, pl: PromotionLevel): PromotionRun =
        agentTestSupport.withToken(token) {
            structureService.newPromotionRun(PromotionRun.of(build, pl, securityService.currentSignature, null))
        }

    private fun PromotionLevel.admitAgents() {
        asAdmin {
            setProperty(this, AgentsAdmittedPropertyType::class.java, AgentsAdmittedProperty())
        }
    }

    @Test
    fun `An agent cannot promote on a level without Agents admitted`() {
        val agent = agentOf(Roles.GLOBAL_AUTOMATION)
        asAdmin {
            project {
                branch {
                    val pl = promotionLevel("GOLD")
                    build {
                        val ex = assertFailsWith<AgentPolicyException> {
                            agent.promote(this, pl)
                        }
                        assertEquals(
                            "agent ${agent.account.email} may not promote to GOLD: the promotion level does not admit agents (agent policy)",
                            ex.message,
                        )
                        assertTrue(
                            structureService.getPromotionRunsForBuildAndPromotionLevel(this, pl).isEmpty(),
                            "Not promoted"
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `An agent promotes on a level with Agents admitted`() {
        val agent = agentOf(Roles.GLOBAL_AUTOMATION)
        asAdmin {
            project {
                branch {
                    val pl = promotionLevel("GOLD")
                    pl.admitAgents()
                    build {
                        val run = agent.promote(this, pl)
                        assertEquals(agent.account.email, run.signature.user.name)
                        assertEquals(
                            1,
                            structureService.getPromotionRunsForBuildAndPromotionLevel(this, pl).size,
                            "Promoted"
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `An agent does not promote on a level with Agents admitted when its owner cannot promote`() {
        val agent = agentOf(Roles.GLOBAL_READ_ONLY)
        asAdmin {
            project {
                branch {
                    val pl = promotionLevel("GOLD")
                    pl.admitAgents()
                    build {
                        val ex = assertFailsWith<AccessDeniedException> {
                            agent.promote(this, pl)
                        }
                        assertFalse(ex is AgentPolicyException, "Refused because of the owner, not of the policy")
                    }
                }
            }
        }
    }

    @Test
    fun `A level with Agents admitted set to false does not admit agents`() {
        asAdmin {
            project {
                branch {
                    val pl = promotionLevel("GOLD")
                    assertFalse(promotionLevelAgentAdmission.isAgentsAdmitted(pl), "No property")
                    setProperty(pl, AgentsAdmittedPropertyType::class.java, AgentsAdmittedProperty(admitted = false))
                    assertFalse(promotionLevelAgentAdmission.isAgentsAdmitted(pl), "Not admitted")
                    pl.admitAgents()
                    assertTrue(promotionLevelAgentAdmission.isAgentsAdmitted(pl), "Admitted")
                }
            }
        }
    }

    @Test
    fun `Auto promotion is not gated by Agents admitted`() {
        val agent = agentOf(Roles.GLOBAL_AUTOMATION)
        asAdmin {
            project {
                branch {
                    val vs = validationStamp("CI")
                    val pl = promotionLevel("GOLD")
                    setProperty(
                        pl,
                        AutoPromotionPropertyType::class.java,
                        AutoPromotionProperty(listOf(vs), "", "", emptyList())
                    )
                    build {
                        agentTestSupport.withToken(agent.token) {
                            structureService.newValidationRun(
                                this,
                                ValidationRunRequest(
                                    validationStampName = vs.name,
                                    validationRunStatusId = ValidationRunStatusID.STATUS_PASSED,
                                )
                            )
                        }
                        assertEquals(
                            1,
                            structureService.getPromotionRunsForBuildAndPromotionLevel(this, pl).size,
                            "Promoted by the system"
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `Setting and deleting the Agents admitted property through GraphQL`() {
        asAdmin {
            project {
                branch {
                    val pl = promotionLevel("GOLD")
                    run(
                        """
                            mutation {
                                setPromotionLevelAgentsAdmittedPropertyById(input: {id: ${pl.id}}) {
                                    errors { message }
                                }
                            }
                        """
                    ) { data ->
                        checkGraphQLUserErrors(data, "setPromotionLevelAgentsAdmittedPropertyById")
                    }
                    assertNotNull(getProperty(pl, AgentsAdmittedPropertyType::class.java)) {
                        assertTrue(it.admitted)
                    }
                    run(
                        """
                            mutation {
                                deletePromotionLevelAgentsAdmittedPropertyById(input: {id: ${pl.id}}) {
                                    errors { message }
                                }
                            }
                        """
                    ) { data ->
                        checkGraphQLUserErrors(data, "deletePromotionLevelAgentsAdmittedPropertyById")
                    }
                    assertNull(getProperty(pl, AgentsAdmittedPropertyType::class.java))
                }
            }
        }
    }
}
