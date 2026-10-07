package net.nemerosa.ontrack.service.security

import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AgentTestSupport
import net.nemerosa.ontrack.model.security.*
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.NameDescription
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.ValidationRunRequest
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID
import net.nemerosa.ontrack.model.structure.ValidationStamp
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.AccessDeniedException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The effective rights of an agent: its owner's, narrowed by the agent policy.
 *
 * The owner is a project owner, who may do everything on the project but delete it: creating builds,
 * validation runs and stamps, and editing the project. (A project manager holds neither `BuildCreate`
 * nor `ProjectEdit`, so it could not show the narrowing.)
 */
class AgentPolicyIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var agentTestSupport: AgentTestSupport

    private fun <T> AgentTestSupport.TestAgent.act(code: () -> T): T =
        agentTestSupport.withToken(token, code = code)

    private fun projectOwnerAgent(project: Project): Pair<Account, AgentTestSupport.TestAgent> {
        val owner = asAdmin { doCreateAccountWithProjectRole(project, Roles.PROJECT_OWNER) }
        return owner to agentTestSupport.registerAgent(owner = owner)
    }

    private fun AgentTestSupport.TestAgent.createBuild(branch: Branch, name: String): Build = act {
        structureService.newBuild(Build.of(branch, NameDescription.nd(name, ""), securityService.currentSignature))
    }

    private fun AgentTestSupport.TestAgent.validate(build: Build, vs: ValidationStamp) = act {
        structureService.newValidationRun(
            build,
            ValidationRunRequest(
                validationStampName = vs.name,
                validationRunStatusId = ValidationRunStatusID.STATUS_PASSED,
            )
        )
    }

    @Test
    fun `An agent of a project owner records builds and validation runs`() {
        asAdmin {
            project {
                val (_, agent) = projectOwnerAgent(this)
                branch {
                    val vs = validationStamp()
                    val build = agent.createBuild(this, uid("b-"))
                    assertEquals(agent.account.email, build.signature.user.name)
                    val run = agent.validate(build, vs)
                    assertEquals(agent.account.email, run.lastStatus.signature.user.name)
                }
            }
        }
    }

    @Test
    fun `An agent of a project owner cannot create a validation stamp, with a message saying why`() {
        asAdmin {
            project {
                val (_, agent) = projectOwnerAgent(this)
                branch {
                    val ex = assertFailsWith<AgentPolicyException> {
                        agent.act {
                            structureService.newValidationStamp(
                                ValidationStamp.of(this, NameDescription.nd(uid("vs-"), ""))
                            )
                        }
                    }
                    assertEquals(
                        "agent ${agent.account.email} may not ValidationStampCreate (agent policy)",
                        ex.message
                    )
                }
            }
        }
    }

    @Test
    fun `An agent of a project owner cannot edit the project`() {
        asAdmin {
            project {
                val (_, agent) = projectOwnerAgent(this)
                assertFailsWith<AgentPolicyException> {
                    agent.act {
                        structureService.saveProject(this.withDescription("Changed by an agent"))
                    }
                }
                agent.act {
                    assertTrue(securityService.isProjectFunctionGranted(id(), ProjectView::class.java))
                    assertTrue(securityService.isProjectFunctionGranted(id(), BuildCreate::class.java))
                    assertFalse(securityService.isProjectFunctionGranted(id(), ProjectEdit::class.java))
                    assertFalse(securityService.isProjectFunctionGranted(id(), ProjectConfig::class.java))
                    assertFalse(securityService.isProjectFunctionGranted(id(), BuildDelete::class.java))
                    assertFalse(securityService.isProjectFunctionGranted(id(), PromotionRunDelete::class.java))
                }
            }
        }
    }

    @Test
    fun `An agent has no right on a project its owner cannot see`() {
        val other = asAdmin { project() }
        asAdmin {
            project {
                val (_, agent) = projectOwnerAgent(this)
                withNoGrantViewToAll {
                    agent.act {
                        assertFalse(securityService.isProjectFunctionGranted(other.id(), ProjectView::class.java))
                        assertFalse(securityService.isProjectFunctionGranted(other.id(), BuildCreate::class.java))
                    }
                }
            }
        }
    }

    @Test
    fun `Demoting the owner removes the rights of the agent immediately`() {
        asAdmin {
            project {
                val (owner, agent) = projectOwnerAgent(this)
                branch {
                    agent.createBuild(this, uid("b-"))
                    // Demoting the owner
                    asAdmin {
                        accountService.deleteProjectPermission(project.id, PermissionTargetType.ACCOUNT, owner.id())
                    }
                    withNoGrantViewToAll {
                        assertFailsWith<AccessDeniedException> {
                            agent.createBuild(this, uid("b-"))
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `An agent does not get the rights of a group of its owner beyond the policy`() {
        asAdmin {
            project {
                val group = doCreateAccountGroupWithGlobalRole(Roles.GLOBAL_ADMINISTRATOR)
                val owner = doCreateAccount(group)
                val agent = agentTestSupport.registerAgent(owner = owner)
                agent.act {
                    assertTrue(securityService.isGlobalFunctionGranted(ProjectList::class.java), "From the group")
                    assertTrue(securityService.isProjectFunctionGranted(id(), BuildCreate::class.java), "From the group")
                    assertFalse(securityService.isGlobalFunctionGranted(ProjectCreation::class.java), "Policy")
                    assertFalse(securityService.isGlobalFunctionGranted(AccountManagement::class.java), "Policy")
                    assertFalse(securityService.isProjectFunctionGranted(id(), ProjectDelete::class.java), "Policy")
                }
            }
        }
    }
}
