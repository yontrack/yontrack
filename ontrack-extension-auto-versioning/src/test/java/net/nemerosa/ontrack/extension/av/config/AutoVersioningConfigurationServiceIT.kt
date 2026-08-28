package net.nemerosa.ontrack.extension.av.config

import net.nemerosa.ontrack.extension.av.AbstractAutoVersioningTestSupport
import net.nemerosa.ontrack.extension.av.AutoVersioningTestFixtures
import net.nemerosa.ontrack.extension.av.event.AutoVersioningEvents
import net.nemerosa.ontrack.extension.av.versionrules.VersionRuleNotFoundException
import net.nemerosa.ontrack.extension.notifications.mock.MockNotificationChannel
import net.nemerosa.ontrack.extension.notifications.mock.MockNotificationChannelConfig
import net.nemerosa.ontrack.extension.notifications.subscriptions.EventSubscription
import net.nemerosa.ontrack.extension.notifications.subscriptions.EventSubscriptionFilter
import net.nemerosa.ontrack.extension.notifications.subscriptions.EventSubscriptionService
import net.nemerosa.ontrack.extension.notifications.subscriptions.subscribe
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.toProjectEntityID
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@AsAdminTest
internal class AutoVersioningConfigurationServiceIT : AbstractAutoVersioningTestSupport() {

    @Autowired
    protected lateinit var mockNotificationChannel: MockNotificationChannel

    @Autowired
    protected lateinit var eventSubscriptionService: EventSubscriptionService

    @Test
    fun `Getting the branches configured to a promotion`() {
        withThreeDependencies { target, dep1, _, _ ->
            val targets = autoVersioningConfigurationService.getBranchesConfiguredFor(dep1.project.name, "GOLD")
            assertEquals(
                listOf(target),
                targets
            )
        }
    }

    @Test
    fun `Setting a configuration with an unknown version rule is rejected`() {
        asAdmin {
            project {
                branch {
                    assertFailsWith<VersionRuleNotFoundException> {
                        autoVersioningConfigurationService.setupAutoVersioning(
                            this,
                            AutoVersioningConfig(
                                configurations = listOf(
                                    AutoVersioningTestFixtures.sourceConfig(
                                        versionRule = "no-such-rule",
                                    )
                                )
                            )
                        )
                    }
                    assertNull(
                        autoVersioningConfigurationService.getAutoVersioning(this),
                        "Configuration has not been saved"
                    )
                }
            }
        }
    }

    @Test
    fun `Setting a configuration with a known version rule is accepted`() {
        asAdmin {
            project {
                branch {
                    autoVersioningConfigurationService.setupAutoVersioning(
                        this,
                        AutoVersioningConfig(
                            configurations = listOf(
                                AutoVersioningTestFixtures.sourceConfig(
                                    versionRule = "semver",
                                    versionRuleConfig = mapOf("onUnparseable" to "ACCEPT").asJson(),
                                )
                            )
                        )
                    )
                    val config = assertNotNull(
                        autoVersioningConfigurationService.getAutoVersioning(this),
                        "Configuration has been saved"
                    )
                    val source = config.configurations.first()
                    assertEquals("semver", source.versionRule)
                    assertEquals(
                        "ACCEPT",
                        source.versionRuleConfig?.path("onUnparseable")?.asText(),
                        "Version rule configuration has been kept after the deserialization"
                    )
                }
            }
        }
    }

    @Test
    fun `Using &same expression in source branch returns the same branch as the promoted build`() {
        asAdmin {
            project {
                branch {
                    val sourceBranch = this

                    // Additional branches for adding some noise
                    project.branch()

                    val sourceConfig = AutoVersioningTestFixtures.sourceConfig(
                        sourceProject = sourceBranch.project.name,
                        sourceBranch = "&same",
                    )

                    project {
                        branch(name = sourceBranch.name) {
                            val targetBranch = this

                            val branch = autoVersioningConfigurationService.getLatestBranch(
                                eligibleTargetBranch = targetBranch,
                                project = sourceBranch.project,
                                config = sourceConfig,
                            )

                            assertEquals(sourceBranch, branch, "Using the same branch")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Using &same expression in source branch does not return a match is no branch with the same name is present`() {
        asAdmin {
            project {
                branch {
                    val sourceBranch = this

                    // Additional branches for adding some noise
                    project.branch()

                    val sourceConfig = AutoVersioningTestFixtures.sourceConfig(
                        sourceProject = sourceBranch.project.name,
                        sourceBranch = "&same",
                    )

                    project {
                        branch(name = "another-name") { // Using another name
                            val targetBranch = this

                            val branch = autoVersioningConfigurationService.getLatestBranch(
                                eligibleTargetBranch = targetBranch,
                                project = sourceBranch.project,
                                config = sourceConfig,
                            )

                            assertNull(branch, "Not finding a branch with the same name")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Saving and retrieving a configuration`() {
        asAdmin {
            project {
                branch {
                    assertNull(
                        autoVersioningConfigurationService.getAutoVersioning(this),
                        "No auto versioning initially"
                    )
                    val config = AutoVersioningTestFixtures.sampleConfig()
                    autoVersioningConfigurationService.setupAutoVersioning(this, config)
                    val saved = autoVersioningConfigurationService.getAutoVersioning(this)
                    assertEquals(config, saved)
                }
            }
        }
    }

    @Test
    fun `Deleting a configuration`() {
        asAdmin {
            project {
                branch {
                    val config = AutoVersioningTestFixtures.sampleConfig()
                    autoVersioningConfigurationService.setupAutoVersioning(this, config)
                    autoVersioningConfigurationService.setupAutoVersioning(this, null)
                    val finalOne = autoVersioningConfigurationService.getAutoVersioning(this)
                    assertNull(finalOne, "Configuration has been deleted")
                }
            }
        }
    }

    @Test
    fun `Registering notifications for AV`() {
        registerAVNotifications { code ->
            asAdmin {
                code()
            }
        }
    }

    @Test
    fun `Registering notifications for non super admin`() {
        registerAVNotifications { code ->
            asAccountWithGlobalRole(Roles.GLOBAL_AUTOMATION) {
                code()
            }
        }
    }

    private fun registerAVNotifications(
        roleFn: (() -> Unit) -> Unit,
    ) {
        val target = uid("t")
        val source = project {
            branch("main")
        }
        project {
            branch {
                // Setting an AV configuration with notifications
                val config = AutoVersioningConfig(
                    listOf(
                        AutoVersioningTestFixtures.sourceConfig(
                            sourceProject = source.name,
                            sourceBranch = "main",
                            notifications = listOf(
                                AutoVersioningNotification(
                                    channel = "mock",
                                    config = mapOf(
                                        "target" to target
                                    ).asJson(),
                                    notificationTemplate = null,
                                )
                            )
                        )
                    )
                )

                roleFn {
                    autoVersioningConfigurationService.setupAutoVersioning(this, config)
                }

                // Checks that the subscriptions are saved
                val subscriptions = eventSubscriptionService.filterSubscriptions(
                    EventSubscriptionFilter(
                        entity = toProjectEntityID(),
                        origin = "auto-versioning",
                    )
                ).pageItems
                assertEquals(
                    listOf(
                        EventSubscription(
                            name = "Auto versioning 1",
                            projectEntity = this,
                            events = setOf(
                                "auto-versioning-error",
                                "auto-versioning-success",
                                "auto-versioning-post-processing-error",
                                "auto-versioning-pr-merge-timeout-error",
                                "auto-versioning-rejected",
                            ),
                            keywords = "${source.name} ${project.name} $name",
                            channel = "mock",
                            channelConfig = mapOf(
                                "target" to target,
                            ).asJson(),
                            disabled = false,
                            origin = "auto-versioning",
                            contentTemplate = null,
                        )
                    ),
                    subscriptions
                )
            }
        }
    }

    @Test
    fun `Changing the notifications for AV`() {
        asAdmin {
            val target = uid("t")
            val source = project {
                branch("main")
            }
            project {
                branch {
                    // Setting an AV configuration with notifications
                    val config = AutoVersioningConfig(
                        listOf(
                            AutoVersioningTestFixtures.sourceConfig(
                                sourceProject = source.name,
                                sourceBranch = "main",
                                notifications = listOf(
                                    AutoVersioningNotification(
                                        channel = "mock",
                                        config = mapOf(
                                            "target" to target
                                        ).asJson(),
                                        scope = listOf(
                                            AutoVersioningNotificationScope.SUCCESS
                                        ),
                                        notificationTemplate = null,
                                    )
                                )
                            ),
                            AutoVersioningTestFixtures.sourceConfig(
                                sourceProject = source.name,
                                sourceBranch = "main",
                                notifications = listOf(
                                    AutoVersioningNotification(
                                        channel = "mock",
                                        config = mapOf(
                                            "target" to target
                                        ).asJson(),
                                        scope = listOf(
                                            AutoVersioningNotificationScope.ERROR
                                        ),
                                        notificationTemplate = null,
                                    )
                                )
                            )
                        )
                    )
                    autoVersioningConfigurationService.setupAutoVersioning(this, config)

                    // Checks that the subscriptions are saved
                    val subscriptions = eventSubscriptionService.filterSubscriptions(
                        EventSubscriptionFilter(
                            entity = toProjectEntityID(),
                            origin = "auto-versioning",
                        )
                    ).pageItems
                    assertEquals(
                        listOf(
                            EventSubscription(
                                projectEntity = this,
                                name = "Auto versioning 1",
                                events = setOf(
                                    "auto-versioning-success",
                                ),
                                keywords = "${source.name} ${project.name} $name",
                                channel = "mock",
                                channelConfig = mapOf(
                                    "target" to target,
                                ).asJson(),
                                disabled = false,
                                origin = "auto-versioning",
                                contentTemplate = null,
                            ),
                            EventSubscription(
                                projectEntity = this,
                                name = "Auto versioning 2",
                                events = setOf(
                                    "auto-versioning-error",
                                    "auto-versioning-post-processing-error",
                                ),
                                keywords = "${source.name} ${project.name} $name",
                                channel = "mock",
                                channelConfig = mapOf(
                                    "target" to target,
                                ).asJson(),
                                disabled = false,
                                origin = "auto-versioning",
                                contentTemplate = null,
                            ),
                        ).toSet(),
                        subscriptions.toSet()
                    )

                    // Changing the notifications
                    val newConfig = AutoVersioningConfig(
                        listOf(
                            AutoVersioningTestFixtures.sourceConfig(
                                sourceProject = source.name,
                                sourceBranch = "main",
                                notifications = listOf(
                                    AutoVersioningNotification(
                                        channel = "mock",
                                        config = mapOf(
                                            "target" to target, // <- no change
                                        ).asJson(),
                                        scope = listOf(
                                            AutoVersioningNotificationScope.SUCCESS
                                        ),
                                        notificationTemplate = null,
                                    )
                                )
                            ),
                            AutoVersioningTestFixtures.sourceConfig(
                                sourceProject = source.name,
                                sourceBranch = "main",
                                notifications = listOf(
                                    AutoVersioningNotification(
                                        channel = "mock",
                                        config = mapOf(
                                            "target" to "$target-error", // <- change
                                        ).asJson(),
                                        scope = listOf(
                                            AutoVersioningNotificationScope.ERROR
                                        ),
                                        notificationTemplate = null,
                                    )
                                )
                            ),
                            // VVV New subscription
                            AutoVersioningTestFixtures.sourceConfig(
                                sourceProject = source.name,
                                sourceBranch = "main",
                                notifications = listOf(
                                    AutoVersioningNotification(
                                        channel = "mock",
                                        config = mapOf(
                                            "target" to "$target-timeout",
                                        ).asJson(),
                                        scope = listOf(
                                            AutoVersioningNotificationScope.PR_TIMEOUT
                                        ),
                                        notificationTemplate = null,
                                    )
                                )
                            ),
                        )
                    )
                    autoVersioningConfigurationService.setupAutoVersioning(this, newConfig)

                    // Checks that the subscriptions are saved
                    val newSubscriptions = eventSubscriptionService.filterSubscriptions(
                        EventSubscriptionFilter(
                            entity = toProjectEntityID(),
                            origin = "auto-versioning",
                        )
                    ).pageItems
                    assertEquals(
                        listOf(
                            EventSubscription(
                                projectEntity = this,
                                name = "Auto versioning 1",
                                events = setOf(
                                    "auto-versioning-success",
                                ),
                                keywords = "${source.name} ${project.name} $name",
                                channel = "mock",
                                channelConfig = mapOf(
                                    "target" to target,
                                ).asJson(),
                                disabled = false,
                                origin = "auto-versioning",
                                contentTemplate = null,
                            ),
                            EventSubscription(
                                projectEntity = this,
                                name = "Auto versioning 2",
                                events = setOf(
                                    "auto-versioning-error",
                                    "auto-versioning-post-processing-error",
                                ),
                                keywords = "${source.name} ${project.name} $name",
                                channel = "mock",
                                channelConfig = mapOf(
                                    "target" to "$target-error",
                                ).asJson(),
                                disabled = false,
                                origin = "auto-versioning",
                                contentTemplate = null,
                            ),
                            EventSubscription(
                                projectEntity = this,
                                name = "Auto versioning 3",
                                events = setOf(
                                    "auto-versioning-pr-merge-timeout-error",
                                ),
                                keywords = "${source.name} ${project.name} $name",
                                channel = "mock",
                                channelConfig = mapOf(
                                    "target" to "$target-timeout",
                                ).asJson(),
                                disabled = false,
                                origin = "auto-versioning",
                                contentTemplate = null,
                            ),
                        ).toSet(),
                        newSubscriptions.toSet()
                    )
                }
            }
        }
    }

    @Test
    fun `Removing the notifications when AV config is deleted`() {
        asAdmin {
            val target = uid("t")
            val source = project {
                branch("main")
            }
            project {
                branch {
                    // Setting an AV configuration with notifications
                    val config = AutoVersioningConfig(
                        listOf(
                            AutoVersioningTestFixtures.sourceConfig(
                                sourceProject = source.name,
                                sourceBranch = "main",
                                notifications = listOf(
                                    AutoVersioningNotification(
                                        channel = "mock",
                                        config = mapOf(
                                            "target" to target
                                        ).asJson(),
                                        notificationTemplate = null,
                                    )
                                )
                            ),
                        )
                    )
                    autoVersioningConfigurationService.setupAutoVersioning(this, config)

                    // Checks that the subscriptions are saved
                    val subscriptions = eventSubscriptionService.filterSubscriptions(
                        EventSubscriptionFilter(
                            entity = toProjectEntityID(),
                            origin = "auto-versioning",
                        )
                    ).pageItems
                    assertEquals(
                        listOf(
                            EventSubscription(
                                projectEntity = this,
                                name = "Auto versioning 1",
                                events = setOf(
                                    "auto-versioning-error",
                                    "auto-versioning-success",
                                    "auto-versioning-post-processing-error",
                                    "auto-versioning-pr-merge-timeout-error",
                                    "auto-versioning-rejected",
                                ),
                                keywords = "${source.name} ${project.name} $name",
                                channel = "mock",
                                channelConfig = mapOf(
                                    "target" to target,
                                ).asJson(),
                                disabled = false,
                                origin = "auto-versioning",
                                contentTemplate = null,
                            ),
                        ).toSet(),
                        subscriptions.toSet()
                    )

                    // Unsetting the configuration
                    autoVersioningConfigurationService.setupAutoVersioning(this, null)

                    // Checks that the subscriptions are saved
                    val newSubscriptions = eventSubscriptionService.filterSubscriptions(
                        EventSubscriptionFilter(
                            entity = toProjectEntityID(),
                            origin = "auto-versioning",
                        )
                    ).pageItems
                    assertTrue(newSubscriptions.isEmpty(), "Subscriptions are gone")
                }
            }
        }

    }

    @Test
    fun `Registering a notification with a custom template`() {
        asAdmin {
            val target = uid("t")
            val source = project {
                branch("main")
            }
            project {
                branch {
                    // Setting an AV configuration with notifications and custom template
                    val config = AutoVersioningConfig(
                        listOf(
                            AutoVersioningTestFixtures.sourceConfig(
                                sourceProject = source.name,
                                sourceBranch = "main",
                                notifications = listOf(
                                    AutoVersioningNotification(
                                        channel = "mock",
                                        config = mapOf(
                                            "target" to target
                                        ).asJson(),
                                        scope = listOf(AutoVersioningNotificationScope.SUCCESS),
                                        notificationTemplate = """
                                            My custom template.
                                        """.trimIndent(),
                                    )
                                )
                            )
                        )
                    )
                    autoVersioningConfigurationService.setupAutoVersioning(this, config)

                    // Checks the subscriptions
                    val subscriptions = eventSubscriptionService.filterSubscriptions(
                        EventSubscriptionFilter(
                            entity = toProjectEntityID(),
                            channel = "mock",
                        )
                    ).pageItems
                    assertEquals(
                        listOf(
                            EventSubscription(
                                projectEntity = this,
                                name = "Auto versioning 1",
                                events = setOf(
                                    "auto-versioning-success",
                                ),
                                keywords = "${source.name} ${project.name} $name",
                                channel = "mock",
                                channelConfig = mapOf(
                                    "target" to target,
                                ).asJson(),
                                disabled = false,
                                origin = "auto-versioning",
                                contentTemplate = "My custom template.",
                            )
                        ),
                        subscriptions
                    )
                }
            }
        }
    }

    @Test
    fun `Registering notifications keeps non AV notifications`() {
        asAdmin {
            val target = uid("t")
            val source = project {
                branch("main")
            }
            project {
                branch {
                    // Setting non AV subscriptions
                    eventSubscriptionService.subscribe(
                        name = "non-av",
                        channel = mockNotificationChannel,
                        channelConfig = MockNotificationChannelConfig(target = target),
                        projectEntity = this,
                        keywords = "test",
                        origin = "test",
                        contentTemplate = null,
                        AutoVersioningEvents.AUTO_VERSIONING_ERROR,
                    )
                    // Setting an AV configuration with notifications
                    val config = AutoVersioningConfig(
                        listOf(
                            AutoVersioningTestFixtures.sourceConfig(
                                sourceProject = source.name,
                                sourceBranch = "main",
                                notifications = listOf(
                                    AutoVersioningNotification(
                                        channel = "mock",
                                        config = mapOf(
                                            "target" to target
                                        ).asJson(),
                                        notificationTemplate = null,
                                    )
                                )
                            )
                        )
                    )
                    autoVersioningConfigurationService.setupAutoVersioning(this, config)

                    // Checks that the subscriptions are saved
                    val subscriptions = eventSubscriptionService.filterSubscriptions(
                        EventSubscriptionFilter(
                            entity = toProjectEntityID(),
                            origin = "auto-versioning",
                        )
                    ).pageItems
                    assertEquals(
                        listOf(
                            EventSubscription(
                                projectEntity = this,
                                name = "Auto versioning 1",
                                events = setOf(
                                    "auto-versioning-error",
                                    "auto-versioning-success",
                                    "auto-versioning-post-processing-error",
                                    "auto-versioning-pr-merge-timeout-error",
                                    "auto-versioning-rejected",
                                ),
                                keywords = "${source.name} ${project.name} $name",
                                channel = "mock",
                                channelConfig = mapOf(
                                    "target" to target,
                                ).asJson(),
                                disabled = false,
                                origin = "auto-versioning",
                                contentTemplate = null,
                            )
                        ),
                        subscriptions
                    )

                    // Checks that the non AV subscriptions are saved
                    val otherSubscriptions = eventSubscriptionService.filterSubscriptions(
                        EventSubscriptionFilter(
                            entity = toProjectEntityID(),
                            origin = "test",
                        )
                    ).pageItems
                    assertEquals(
                        listOf(
                            EventSubscription(
                                projectEntity = this,
                                name = "non-av",
                                events = setOf(
                                    "auto-versioning-error",
                                ),
                                keywords = "test",
                                channel = "mock",
                                channelConfig = mapOf(
                                    "target" to target,
                                    "data" to null,
                                    "waitMs" to null,
                                    "rendererType" to null,
                                ).asJson(),
                                disabled = false,
                                origin = "test",
                                contentTemplate = null,
                            )
                        ),
                        otherSubscriptions
                    )
                }
            }
        }
    }

    @Test
    fun `Get AV config between two branches with parent not being configured`() {
        asAdmin {
            val parent = project<Branch> {
                branch()
            }
            val dependency = project<Branch> {
                branch()
            }
            assertNull(
                autoVersioningConfigurationService.getAutoVersioningBetween(parent, dependency),
                "No AV config between branches because parent not configured"
            )
        }
    }

    @Test
    fun `Get AV config between two branches with no matching project`() {
        asAdmin {
            val parent = project<Branch> {
                branch {
                    autoVersioningConfigurationService.setupAutoVersioning(
                        this,
                        AutoVersioningConfig(
                            configurations = listOf(
                                AutoVersioningTestFixtures.sourceConfig()
                            )
                        )
                    )
                }
            }
            val dependency = project<Branch> {
                branch()
            }
            assertNull(
                autoVersioningConfigurationService.getAutoVersioningBetween(parent, dependency),
                "No AV config between branches because no matching project"
            )
        }
    }

    @Test
    fun `Get AV config between two branches with not latest branch`() {
        asAdmin {
            val dependency = project<Branch> {
                branch("release/1.2") // The latest branch
                branch("release/1.1") // The branch we want to compare
            }
            val parent = project<Branch> {
                branch {
                    autoVersioningConfigurationService.setupAutoVersioning(
                        this,
                        AutoVersioningConfig(
                            configurations = listOf(
                                AutoVersioningTestFixtures.sourceConfig(
                                    sourceProject = dependency.project.name,
                                    sourceBranch = "release/.*"
                                )
                            )
                        )
                    )
                }
            }
            assertNull(
                autoVersioningConfigurationService.getAutoVersioningBetween(parent, dependency),
                "No AV config between branches because not latest branch"
            )
        }
    }

    @Test
    fun `Get AV config between two branches with matching branch`() {
        asAdmin {
            val dependency = project<Branch> {
                branch("release/1.1")
            }
            val parent = project<Branch> {
                branch {
                    autoVersioningConfigurationService.setupAutoVersioning(
                        this,
                        AutoVersioningConfig(
                            configurations = listOf(
                                AutoVersioningTestFixtures.sourceConfig(
                                    sourceProject = dependency.project.name,
                                    sourceBranch = "release/1.1"
                                )
                            )
                        )
                    )
                }
            }
            assertNotNull(
                autoVersioningConfigurationService.getAutoVersioningBetween(parent, dependency),
                "AV config on matching branch"
            ) {
                assertEquals(dependency.project.name, it.sourceProject)
                assertEquals("release/1.1", it.sourceBranch)
            }
        }
    }

    @Test
    fun `Get AV config between two branches with &same branch expression`() {
        asAdmin {
            val dependency = project<Branch> {
                branch("release/1.1")
            }
            val parent = project<Branch> {
                branch("release/1.1") {
                    autoVersioningConfigurationService.setupAutoVersioning(
                        this,
                        AutoVersioningConfig(
                            configurations = listOf(
                                AutoVersioningTestFixtures.sourceConfig(
                                    sourceProject = dependency.project.name,
                                    sourceBranch = "&same"
                                )
                            )
                        )
                    )
                }
            }
            assertNotNull(
                autoVersioningConfigurationService.getAutoVersioningBetween(parent, dependency),
                "AV config on matching branch"
            ) {
                assertEquals(dependency.project.name, it.sourceProject)
                assertEquals("&same", it.sourceBranch)
            }
        }
    }

    @Test
    fun `Get AV config between two branches with &same-release branch expression`() {
        asAdmin {
            val dependency = project<Branch> {
                branch("release/1.24.15")
            }
            val parent = project<Branch> {
                branch("release/1.24.6") {
                    autoVersioningConfigurationService.setupAutoVersioning(
                        this,
                        AutoVersioningConfig(
                            configurations = listOf(
                                AutoVersioningTestFixtures.sourceConfig(
                                    sourceProject = dependency.project.name,
                                    sourceBranch = "&same-release:2"
                                )
                            )
                        )
                    )
                }
            }
            assertNotNull(
                autoVersioningConfigurationService.getAutoVersioningBetween(parent, dependency),
                "AV config on matching branch"
            ) {
                assertEquals(dependency.project.name, it.sourceProject)
                assertEquals("&same-release:2", it.sourceBranch)
            }
        }
    }
}