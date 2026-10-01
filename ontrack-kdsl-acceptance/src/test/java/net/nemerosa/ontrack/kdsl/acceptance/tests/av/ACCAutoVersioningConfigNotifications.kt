package net.nemerosa.ontrack.kdsl.acceptance.tests.av

import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.uid
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.waitUntil
import net.nemerosa.ontrack.kdsl.spec.extension.av.AutoVersioningNotification
import net.nemerosa.ontrack.kdsl.spec.extension.av.AutoVersioningNotificationScope
import net.nemerosa.ontrack.kdsl.spec.extension.av.AutoVersioningSourceConfig
import net.nemerosa.ontrack.kdsl.spec.extension.av.setAutoVersioningConfig
import net.nemerosa.ontrack.kdsl.spec.extension.notifications.notifications
import net.nemerosa.ontrack.kdsl.spec.extension.scm.withMockScmRepository
import org.junit.jupiter.api.Test
import kotlin.math.exp

/**
 * Testing the notifications registered in the AV configuration.
 */
class ACCAutoVersioningConfigNotifications : AbstractACCAutoVersioningTestSupport() {

    @Test
    fun `Auto versioning notification for all scopes`() {
        val projectGroup = uid("tar-g-")
        withMockScmRepository(ontrack) {
            withAutoVersioning {
                repositoryFile("gradle.properties") {
                    "some-version = 1.0.0"
                }
                val dependency = branchWithPromotion(promotion = "IRON")

                project {

                    branch {
                        configuredForMockRepository()
                        setAutoVersioningConfig(
                            listOf(
                                AutoVersioningSourceConfig(
                                    sourceProject = dependency.project.name,
                                    sourceBranch = dependency.name,
                                    sourcePromotion = "IRON",
                                    targetPath = "gradle.properties",
                                    targetProperty = "another-version", // This will raise an error
                                    notifications = listOf(
                                        AutoVersioningNotification(
                                            channel = "in-memory",
                                            config = mapOf("group" to projectGroup).asJson(),
                                        )
                                    )
                                )
                            )
                        )

                        dependency.apply {
                            build(name = "2.0.0") {
                                promote("IRON")
                            }
                        }

                        waitForAutoVersioningCompletion()

                        // Check an error notification has been received at target branch level
                        waitUntil(
                            timeout = 10_000,
                            interval = 500L,
                            task = "Error notification at target level",
                            onTimeout = displayNotifications(projectGroup)
                        ) {
                            ontrack.notifications.inMemory.group(projectGroup).firstOrNull() ==
                                    """
                                        Auto versioning of ${project.name}/main for dependency ${dependency.project.name} version "2.0.0" has failed.
    
                                        Cannot find version in "gradle.properties".
    
                                        Error: Cannot find version in "gradle.properties".

                                        Auto-versioning audit
                                    """.trimIndent()
                        }

                    }
                }
            }
        }
    }

    @Test
    fun `Auto versioning notification for success using no custom template`() {
        val projectGroup = uid("tar-g-")
        withMockScmRepository(ontrack) {
            withAutoVersioning {
                repositoryFile("gradle.properties") {
                    "some-version = 1.0.0"
                }
                val dependency = branchWithPromotion(promotion = "IRON")

                project {

                    branch {
                        configuredForMockRepository()
                        setAutoVersioningConfig(
                            listOf(
                                AutoVersioningSourceConfig(
                                    sourceProject = dependency.project.name,
                                    sourceBranch = dependency.name,
                                    sourcePromotion = "IRON",
                                    targetPath = "gradle.properties",
                                    targetProperty = "some-version",
                                    notifications = listOf(
                                        AutoVersioningNotification(
                                            channel = "in-memory",
                                            config = mapOf("group" to projectGroup).asJson(),
                                            scope = listOf(
                                                AutoVersioningNotificationScope.SUCCESS
                                            )
                                        )
                                    )
                                )
                            )
                        )

                        dependency.apply {
                            build(name = "2.0.0") {
                                promote("IRON")
                            }
                        }

                        waitForAutoVersioningCompletion()

                        // Check a success notification has been received at target branch level
                        waitUntil(
                            timeout = 10_000,
                            interval = 500L,
                            task = "Success notification at target level",
                            onTimeout = displayNotifications(projectGroup)
                        ) {
                            ontrack.notifications.inMemory.group(projectGroup).firstOrNull() ==
                                    """
                                        Auto versioning of ${project.name}/main for dependency ${dependency.project.name} version "2.0.0" has been done.
                                        
                                        Auto versioning PR has been created, approved and merged.
                                        
                                        Pull request #1

                                        Auto-versioning audit
                                    """.trimIndent()
                        }

                    }
                }
            }
        }
    }

    @Test
    fun `Auto versioning notification for success with a custom template`() {
        val projectGroup = uid("tar-g-")
        withMockScmRepository(ontrack) {
            withAutoVersioning {
                repositoryFile("gradle.properties") {
                    "some-version = 1.0.0"
                }
                val dependency = branchWithPromotion(promotion = "IRON")

                project {

                    branch {
                        configuredForMockRepository()
                        setAutoVersioningConfig(
                            listOf(
                                AutoVersioningSourceConfig(
                                    sourceProject = dependency.project.name,
                                    sourceBranch = dependency.name,
                                    sourcePromotion = "IRON",
                                    targetPath = "gradle.properties",
                                    targetProperty = "some-version",
                                    notifications = listOf(
                                        AutoVersioningNotification(
                                            channel = "in-memory",
                                            config = mapOf("group" to projectGroup).asJson(),
                                            scope = listOf(
                                                AutoVersioningNotificationScope.SUCCESS
                                            ),
                                            notificationTemplate = """
                                                Auto versioning of ${'$'}{project}/${'$'}{branch} for dependency ${'$'}{xProject} version "${'$'}{VERSION}" has been done.
                                                
                                                The change log will be here soon, based on the ${'$'}{PROMOTION} promotion.
                                            """.trimIndent(),
                                        )
                                    )
                                )
                            )
                        )

                        dependency.apply {
                            build(name = "2.0.0") {
                                promote("IRON")
                            }
                        }

                        waitForAutoVersioningCompletion()

                        // Check a success notification has been received at target branch level
                        val expectedMessage = """
                            Auto versioning of ${project.name}/main for dependency ${dependency.project.name} version "2.0.0" has been done.
                            
                            The change log will be here soon, based on the IRON promotion.
                        """.trimIndent()
                        waitUntil(
                            timeout = 10_000,
                            interval = 500L,
                            task = "Success notification at target level",
                            onTimeout = displayNotifications(projectGroup, expectedMessage)
                        ) {
                            ontrack.notifications.inMemory.group(projectGroup).firstOrNull() ==
                                    expectedMessage
                        }

                    }
                }
            }
        }
    }

}