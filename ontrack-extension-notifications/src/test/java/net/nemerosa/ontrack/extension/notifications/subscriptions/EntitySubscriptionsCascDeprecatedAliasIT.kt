package net.nemerosa.ontrack.extension.notifications.subscriptions

import io.micrometer.core.instrument.MeterRegistry
import net.nemerosa.ontrack.extension.casc.CascService
import net.nemerosa.ontrack.extension.notifications.AbstractNotificationTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.it.deprecatedUsages
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.deprecation.DeprecationSurface
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.toProjectEntityID
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

/**
 * The deprecated `channel-config` alias of the entity subscriptions in CasC.
 */
@AsAdminTest
class EntitySubscriptionsCascDeprecatedAliasIT : AbstractNotificationTestSupport() {

    @Autowired
    private lateinit var cascService: CascService

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    private fun cascEntitySubscription(project: Project, target: String, channelConfigField: String): Double =
        meterRegistry.deprecatedUsages(
            DeprecationSurface.CASC,
            "ontrack.extensions.notifications.entity-subscriptions.subscriptions.channel-config"
        ) {
            asAdmin {
                cascService.runYaml(
                    """
                        ontrack:
                            extensions:
                                notifications:
                                    entity-subscriptions:
                                        - entity:
                                            project: ${project.name}
                                          subscriptions:
                                            - name: test
                                              events:
                                                - new_promotion_run
                                              channel: mock
                                              $channelConfigField:
                                                target: "$target"
                    """.trimIndent()
                )
            }
        }

    private fun channelConfig(project: Project) = asAdmin {
        eventSubscriptionService.filterSubscriptions(
            EventSubscriptionFilter(
                entity = project.toProjectEntityID(),
                origin = "casc",
            )
        ).pageItems.single().channelConfig
    }

    @Test
    fun `Entity subscription using the channelConfig field reports no deprecation`() {
        val target = uid("t")
        project {
            assertEquals(0.0, cascEntitySubscription(this, target, "channelConfig"))
            assertEquals(mapOf("target" to target).asJson(), channelConfig(this))
        }
    }

    @Test
    fun `Entity subscription using the deprecated channel-config alias`() {
        val target = uid("t")
        project {
            assertEquals(
                1.0,
                cascEntitySubscription(this, target, "channel-config"),
                "The alias is reported as deprecated"
            )
            assertEquals(mapOf("target" to target).asJson(), channelConfig(this), "The alias still works")
        }
    }
}
