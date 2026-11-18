package net.nemerosa.ontrack.kdsl.acceptance.tests.workflows

import net.nemerosa.ontrack.common.waitFor
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.uid
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.ProjectEntityType
import net.nemerosa.ontrack.kdsl.spec.Build
import net.nemerosa.ontrack.kdsl.spec.extension.notifications.notifications
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ACCDSLWorkflowDependenciesPromotion : AbstractACCDSLWorkflowsTestSupport() {

    @Test
    fun `A workflow promotes the unqualified and qualified links of a build`() {
        val gold = uid("GOLD-")

        fun component(name: String): Build {
            var component: Build? = null
            project {
                branch {
                    promotion(gold)
                    component = build(name) { this }
                }
            }
            return component!!
        }

        val unqualified = component("unqualified")
        val moduleX = component("module-x")
        val moduleY = component("module-y")
        val ignored = component("ignored")

        project {
            branch {
                val pl = promotion("pl-source")
                pl.subscribe(
                    name = uid("s"),
                    channel = "workflow",
                    channelConfig = mapOf(
                        "workflow" to WorkflowTestSupport.yamlWorkflowToJson(
                            """
                                name: Promoting the dependencies
                                nodes:
                                  - id: promotion
                                    executorId: notification
                                    timeout: 30
                                    interval: 1
                                    data:
                                      channel: yontrack-dependencies-promotion
                                      channelConfig:
                                        qualifier: "|module-.*"
                                        promotion: $gold
                                        waitForPromotion: true
                                        waitForPromotionTimeout: 20s
                            """.trimIndent()
                        )
                    ),
                    keywords = null,
                    contentTemplate = null,
                    events = listOf("new_promotion_run"),
                )

                build("source") {
                    linkTo(unqualified)
                    linkTo(moduleX, "module-x")
                    linkTo(moduleY, "module-y")
                    linkTo(ignored, "other")

                    val sourceRun = promote(pl.name)

                    waitFor(
                        message = "Waiting for the workflow notification to be completed",
                        interval = 500.milliseconds,
                        timeout = 40.seconds,
                    ) {
                        ontrack.notifications.notificationRecords(
                            channel = "workflow",
                            projectEntityType = ProjectEntityType.PROMOTION_RUN,
                            projectEntityId = sourceRun.id.toInt(),
                        ).firstOrNull()
                    } until { record ->
                        record.result.type == "OK"
                    }

                    listOf(unqualified, moduleX, moduleY).forEach { target ->
                        assertEquals(
                            1,
                            target.getPromotionRunsForPromotionLevel(gold).size,
                            "${target.name} has been promoted",
                        )
                    }
                    assertEquals(
                        0,
                        ignored.getPromotionRunsForPromotionLevel(gold).size,
                        "The link which does not match is not promoted",
                    )
                }
            }
        }
    }
}
