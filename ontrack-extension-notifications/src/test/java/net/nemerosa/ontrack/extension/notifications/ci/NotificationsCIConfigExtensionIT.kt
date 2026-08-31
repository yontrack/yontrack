package net.nemerosa.ontrack.extension.notifications.ci

import net.nemerosa.ontrack.extension.config.ConfigTestSupport
import net.nemerosa.ontrack.extension.config.EnvFixtures
import net.nemerosa.ontrack.extension.notifications.AbstractNotificationTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class NotificationsCIConfigExtensionIT : AbstractNotificationTestSupport() {

    /**
     * One name per test instance (JUnit builds a new one per method), so no two tests - and no two
     * modules of the same CI shard - configure the same project. See #1657.
     */
    private val configuredProjectName = uid("cfg-")


    @Autowired
    private lateinit var configTestSupport: ConfigTestSupport

    @Test
    @AsAdminTest
    fun `Setup of notifications with some conditions`() {
        val branch = configTestSupport.configureBranch(
            yaml = """
                version: v1
                configuration:
                  defaults:
                    branch:
                      promotions:
                        BRONZE: {}
                        RELEASE: {}
                      notificationsConfig:
                        notifications:
                          - name: On validation error
                            events:
                              - new_validation_run
                            keywords: failed
                            channel: mock
                            channelConfig:
                              target: "#notifications"
                            contentTemplate: "Build ${'$'}{build} has failed on ${'$'}{validationStamp}."
                          - name: On BRONZE
                            promotion: BRONZE
                            events:
                              - new_promotion_run
                            channel: mock
                            channelConfig:
                              target: "#notifications"
                            contentTemplate: "Build ${'$'}{build} has been promoted to ${'$'}{promotionLevel}."
                          - name: On RELEASE
                            promotion: RELEASE
                            events:
                              - new_promotion_run
                            channel: mock
                            channelConfig:
                              target: "#internal-releases"
                            contentTemplate: |
                              Yontrack ${'$'}{build} has been released.
                              
                              ${'$'}{promotionRun.changelog?title=true}
                  custom:
                    configs:
                      - conditions:
                          - name: branch
                            config: '^release\/\d+\.\d+$'
                        branch:
                          notificationsConfig:
                            notifications:
                              - name: On RELEASE
                                channelConfig:
                                  target: "#releases"
            """.trimIndent(),
            ci = "generic",
            scm = "mock",
            env = EnvFixtures.generic(configuredProjectName, scmBranch = "release/5.0"),
        )

        // Branch name
        assertEquals("release-5.0", branch.name)

        // Promotions
        val bronze = structureService.findPromotionLevelByName(branch.project.name, branch.name, "BRONZE").get()
        val release = structureService.findPromotionLevelByName(branch.project.name, branch.name, "RELEASE").get()

        // Notifications at the branch level
        assertNotNull(
            eventSubscriptionService.findSubscriptionByName(branch, "On validation error"),
            "On validation error subscription found"
        ) {
            assertEquals("failed", it.keywords)
            assertEquals(setOf("new_validation_run"), it.events)
            assertEquals("mock", it.channel)
            assertEquals("#notifications", it.channelConfig["target"].asText())
            assertEquals(
                "Build ${'$'}{build} has failed on ${'$'}{validationStamp}.",
                it.contentTemplate
            )
        }

        // Notifications at the BRONZE promotion level
        assertNotNull(
            eventSubscriptionService.findSubscriptionByName(bronze, "On BRONZE"),
            "On BRONZE subscription found"
        ) {
            assertEquals(null, it.keywords)
            assertEquals(setOf("new_promotion_run"), it.events)
            assertEquals("mock", it.channel)
            assertEquals("#notifications", it.channelConfig["target"].asText())
            assertEquals(
                "Build ${'$'}{build} has been promoted to ${'$'}{promotionLevel}.",
                it.contentTemplate
            )
        }

        // Notifications at the RELEASE promotion level
        assertNotNull(
            eventSubscriptionService.findSubscriptionByName(release, "On RELEASE"),
            "On RELEASE subscription found"
        ) {
            assertEquals(null, it.keywords)
            assertEquals(setOf("new_promotion_run"), it.events)
            assertEquals("mock", it.channel)
            assertEquals("#releases", it.channelConfig["target"].asText())
        }
    }

}