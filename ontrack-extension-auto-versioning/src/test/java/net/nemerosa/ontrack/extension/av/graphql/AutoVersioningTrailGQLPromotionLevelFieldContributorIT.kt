package net.nemerosa.ontrack.extension.av.graphql

import net.nemerosa.ontrack.extension.av.AbstractAutoVersioningTestSupport
import net.nemerosa.ontrack.extension.av.AutoVersioningTestFixtures
import net.nemerosa.ontrack.extension.av.config.AutoVersioningConfig
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.isNullOrNullNode
import net.nemerosa.ontrack.model.structure.PromotionLevel
import net.nemerosa.ontrack.test.TestUtils.uid
import net.nemerosa.ontrack.test.assertJsonNull
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AutoVersioningTrailGQLPromotionLevelFieldContributorIT : AbstractAutoVersioningTestSupport() {

    @Test
    fun `Getting the auto-versioning targets for a promotion level`() {
        withPromotionLevelTargets { pl, app1, app2 ->
            run(
                """
                    {
                        promotionLevel(id: ${pl.id}) {
                            autoVersioningTrailPaginated(filter: {onlyEligible: false}) {
                                pageItems {
                                    branch {
                                        id
                                    }
                                    configuration {
                                        targetPath
                                    }
                                    rejectionReason
                                }
                            }
                        }
                    }
                """
            ) { data ->
                assertEquals(
                    mapOf(
                        "promotionLevel" to mapOf(
                            "autoVersioningTrailPaginated" to mapOf(
                                "pageItems" to listOf(
                                    mapOf(
                                        "branch" to mapOf(
                                            "id" to app2.id().toString(),
                                        ),
                                        "configuration" to mapOf(
                                            "targetPath" to "app2.properties"
                                        ),
                                        "rejectionReason" to null,
                                    ),
                                    mapOf(
                                        "branch" to mapOf(
                                            "id" to app1.id().toString(),
                                        ),
                                        "configuration" to mapOf(
                                            "targetPath" to "app1.properties"
                                        ),
                                        "rejectionReason" to null,
                                    ),
                                )
                            )
                        )
                    ).asJson(),
                    data
                )
            }
        }
    }

    @Test
    fun `Getting the auto-versioning targets for a promotion level with a target being disabled`() {
        withPromotionLevelTargets { pl, app1, app2 ->
            structureService.disableBranch(app2)
            run(
                """
                        {
                            promotionLevel(id: ${pl.id}) {
                                autoVersioningTrailPaginated(filter: {onlyEligible: false}) {
                                    pageItems {
                                        branch {
                                            id
                                        }
                                        configuration {
                                            targetPath
                                        }
                                        rejectionReason
                                    }
                                }
                            }
                        }
                    """, mapOf(

                )
            ) { data ->
                assertEquals(
                    mapOf(
                        "promotionLevel" to mapOf(
                            "autoVersioningTrailPaginated" to mapOf(
                                "pageItems" to listOf(
                                    mapOf(
                                        "branch" to mapOf(
                                            "id" to app2.id().toString(),
                                        ),
                                        "configuration" to mapOf(
                                            "targetPath" to "app2.properties"
                                        ),
                                        "rejectionReason" to "Branch is disabled",
                                    ),
                                    mapOf(
                                        "branch" to mapOf(
                                            "id" to app1.id().toString(),
                                        ),
                                        "configuration" to mapOf(
                                            "targetPath" to "app1.properties"
                                        ),
                                        "rejectionReason" to null,
                                    ),
                                )
                            )
                        )
                    ).asJson(),
                    data
                )
            }
        }
    }

    @Test
    @AsAdminTest
    fun `Getting the paginated trail for a promotion level`() {
        testTrailPromotion { secondaryTargetProjectName, targetName ->
            mapOf(
                "$secondaryTargetProjectName/main" to null,
                "$targetName/v22" to null,
            )
        }
    }

    @Test
    @AsAdminTest
    fun `Getting the paginated trail for a promotion level with all branches`() {
        testTrailPromotion(size = 4, onlyEligible = false) { secondaryTargetProjectName, targetName ->
            mapOf(
                "$secondaryTargetProjectName/main" to null,
                "$targetName/v22" to null,
                "$targetName/v21" to "Branch is disabled",
                "$targetName/v20" to "Branch is disabled",
            )
        }
    }

    @Test
    @AsAdminTest
    fun `Getting the paginated trail for a promotion level with filter on project name`() {
        testTrailPromotion(size = 4, targetProjectName = true) { _, targetName ->
            mapOf(
                "$targetName/v22" to null,
            )
        }
    }

    private fun testTrailPromotion(
        size: Int = 2,
        onlyEligible: Boolean = true,
        targetProjectName: Boolean = false,
        expected: (
            secondaryTargetProjectName: String,
            targetName: String,
        ) -> Map<String, String?>,
    ) {
        withMultipleTrailPromotion { promotionLevel, secondaryTargetProjectName, targetName ->
            // Getting the trail using GraphQL
            run(
                $$"""
                query AVTrailQuery($promotionLevelId: Int!, $size: Int!, $onlyEligible: Boolean! $projectName: String = null){
                    promotionLevel(id: $promotionLevelId) {
                        autoVersioningTrailPaginated(
                            size: $size,
                            filter: {
                                onlyEligible: $onlyEligible,
                                projectName: $projectName,
                            }
                        ) {
                            pageInfo {
                                nextPage {
                                    offset
                                    size
                                }
                            }
                            pageItems {
                                branch {
                                    name
                                    project {
                                        name
                                    }
                                }
                                rejectionReason
                            }
                        }
                    }
                }
            """.trimIndent(),
                mapOf(
                    "promotionLevelId" to promotionLevel.id(),
                    "size" to size,
                    "onlyEligible" to onlyEligible,
                    "projectName" to targetProjectName.takeIf { it }?.let { targetName },
                )
            ) { data ->
                val page = data.path("promotionLevel").path("autoVersioningTrailPaginated")

                val pageInfo = page.path("pageInfo")
                assertJsonNull(pageInfo.path("nextPage"))

                val items = page.path("pageItems")
                val itemData = items.associate {
                    val branchNode = it.path("branch")
                    val branchName = branchNode.path("name").asText()
                    val projectName = branchNode.path("project").path("name").asText()
                    val rejectionReason =
                        it.path("rejectionReason")?.takeIf { reason -> !reason.isNullOrNullNode() }?.asText()
                    "$projectName/$branchName" to rejectionReason
                }

                val expectedData = expected(secondaryTargetProjectName, targetName)
                assertEquals(expectedData, itemData)
            }
        }
    }

    private fun withMultipleTrailPromotion(
        code: (
            promotionLevel: PromotionLevel,
            secondaryTargetProjectName: String,
            targetName: String,
        ) -> Unit,
    ) {
        val depName = uid("dep-")
        val depProject = project(depName)
        val depBranch = depProject.branch("main")
        val depPromotionLevel = depBranch.promotionLevel("GOLD")

        val targetName = uid("target-")
        mockSCMTester.withMockSCMRepository {

            val targetProject = project(targetName)
            targetProject.configureMockSCMProject()

            // Several target branches, one of them being disabled
            val versions = listOf("v20", "v21", "v22")
            for (version in versions) {
                val targetBranch = targetProject.branch(version)
                if (version != "v22") {
                    structureService.disableBranch(targetBranch)
                }
                // SCM setup of the target(s)
                targetBranch.configureMockSCMBranch(scmBranch = version)
                repositoryFile(
                    branch = version,
                    path = "versions.properties",
                    content = "version=1.0.0",
                )
                // Setting up the auto-versioning dep --> target
                autoVersioningConfigurationService.setupAutoVersioning(
                    targetBranch,
                    AutoVersioningConfig(
                        configurations = listOf(
                            AutoVersioningTestFixtures.sourceConfig(
                                sourceProject = depName,
                                sourceBranch = depBranch.name,
                                sourcePromotion = depPromotionLevel.name,
                                targetPath = "versions.properties",
                            )
                        )
                    )
                )
            }
        }

        // Secondary target, on a different project
        val secondaryTargetProjectName = uid("secondary-target-")
        val secondaryTargetProject = project(secondaryTargetProjectName)
        mockSCMTester.withMockSCMRepository {
            secondaryTargetProject.configureMockSCMProject()
            val secondaryTargetBranch = secondaryTargetProject.branch("main")
            // SCM setup of the target(s)
            secondaryTargetBranch.configureMockSCMBranch(scmBranch = "main")
            repositoryFile(
                path = "versions.properties",
                branch = "main",
                content = "version=1.0.0",
            )
            // Setting up the auto-versioning dep --> secondary target
            autoVersioningConfigurationService.setupAutoVersioning(
                secondaryTargetBranch,
                AutoVersioningConfig(
                    configurations = listOf(
                        AutoVersioningTestFixtures.sourceConfig(
                            sourceProject = depName,
                            sourceBranch = depBranch.name,
                            sourcePromotion = depPromotionLevel.name,
                            targetPath = "versions.properties",
                        )
                    )
                )
            )
        }

        // OK
        code(
            depPromotionLevel,
            secondaryTargetProjectName,
            targetName
        )
    }
}