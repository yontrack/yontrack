package net.nemerosa.ontrack.extension.notifications.subscriptions

import net.nemerosa.ontrack.extension.notifications.AbstractNotificationTestSupport
import net.nemerosa.ontrack.extension.notifications.mock.MockNotificationChannelConfig
import net.nemerosa.ontrack.it.AgentTestSupport
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.security.AgentIdentifiers
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.PromotionLevel
import net.nemerosa.ontrack.model.structure.PromotionRun
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Subscriptions selecting the events on their actor, with the `actor:` and `agent:` keywords: the
 * actor is the one of the event, as recorded when it was posted, and survives its dispatch.
 */
class ActorKeywordsSubscriptionIT : AbstractNotificationTestSupport() {

    @Autowired
    private lateinit var agentTestSupport: AgentTestSupport

    private fun agent(): AgentTestSupport.TestAgent =
        agentTestSupport.registerAgent(owner = asAdmin { doCreateAccount() })

    /**
     * The [agent] promotes the build, the system acting on its behalf: the agent policy is not the
     * point here.
     */
    private fun Build.promoteAsAgent(agent: AgentTestSupport.TestAgent, promotionLevel: PromotionLevel) {
        agentTestSupport.withToken(agent.token) {
            securityService.asAdmin {
                structureService.newPromotionRun(
                    PromotionRun.of(this, promotionLevel, securityService.currentSignature, null)
                )
            }
        }
    }

    private fun Branch.subscribeToPromotions(keywords: String): String {
        val target = uid("t_")
        eventSubscriptionService.subscribe(
            name = uid("p"),
            channel = mockNotificationChannel,
            channelConfig = MockNotificationChannelConfig(target),
            projectEntity = this,
            keywords = keywords,
            origin = "test",
            contentTemplate = null,
            EventFactory.NEW_PROMOTION_RUN
        )
        return target
    }

    @Test
    fun `A subscription on actor-agent fires for the promotion of an agent and not for the one of a person`() {
        val agent = agent()
        asAdmin {
            project {
                branch {
                    val pl = promotionLevel()
                    val target = subscribeToPromotions("actor:agent")
                    build {
                        // A person promotes --> no message
                        promote(pl)
                        assertNull(mockNotificationChannel.messages[target], "No message for a person's promotion")
                    }
                    build {
                        // An agent promotes --> one message
                        promoteAsAgent(agent, pl)
                        assertEquals(
                            listOf("Build $name has been promoted to ${pl.name} for branch ${branch.name} in ${project.name}."),
                            mockNotificationChannel.messages[target]?.toList(),
                            "One message for the agent's promotion",
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `A subscription on actor-human fires for the promotion of a person and not for the one of an agent`() {
        val agent = agent()
        asAdmin {
            project {
                branch {
                    val pl = promotionLevel()
                    val target = subscribeToPromotions("actor:human")
                    build {
                        promoteAsAgent(agent, pl)
                        assertNull(mockNotificationChannel.messages[target], "No message for an agent's promotion")
                    }
                    build {
                        promote(pl)
                        assertEquals(1, mockNotificationChannel.messages[target]?.size, "One message for a person's promotion")
                    }
                }
            }
        }
    }

    @Test
    fun `A subscription on one agent, combined with a promotion level, fires for this agent only`() {
        val claude = agent()
        val codex = agent()
        val slug = AgentIdentifiers.slug(claude.account.email)!!
        asAdmin {
            project {
                branch {
                    val silver = promotionLevel()
                    val gold = promotionLevel()
                    val target = subscribeToPromotions("agent:$slug ${gold.name}")
                    build {
                        promoteAsAgent(codex, gold)
                        promoteAsAgent(claude, silver)
                        promote(gold)
                        assertNull(mockNotificationChannel.messages[target], "No message")
                        promoteAsAgent(claude, gold)
                        assertEquals(1, mockNotificationChannel.messages[target]?.size, "One message for the agent and the level")
                    }
                }
            }
        }
    }

}
