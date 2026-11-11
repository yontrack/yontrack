package net.nemerosa.ontrack.extension.av

import net.nemerosa.ontrack.extension.av.config.AutoVersioningConfig
import net.nemerosa.ontrack.extension.av.config.AutoVersioningConfigurationService
import net.nemerosa.ontrack.extension.scm.mock.MockSCMTester
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.PromotionLevel
import org.springframework.beans.factory.annotation.Autowired

@AsAdminTest
abstract class AbstractAutoVersioningTestSupport : AbstractQLKTITSupport() {

    @Autowired
    protected lateinit var autoVersioningConfigurationService: AutoVersioningConfigurationService

    @Autowired
    protected lateinit var mockSCMTester: MockSCMTester

    protected fun Branch.setAutoVersioning(
        init: AutoVersioningSetup.() -> Unit,
    ) {
        autoVersioningConfigurationService.setAutoVersioning(this, init)
    }

    protected fun withThreeDependencies(
        code: (
            target: Branch,
            dep1: Branch,
            dep2: Branch,
            dep3: Branch,
        ) -> Unit,
    ) {
        asAdmin {
            val dep1 = project<Branch> {
                branch {
                    promotionLevel("GOLD")
                }
            }
            val dep2 = project<Branch> {
                branch {
                    promotionLevel("GOLD")
                }
            }
            val dep3 = project<Branch> {
                branch {
                    promotionLevel("GOLD")
                }
            }
            mockSCMTester.withMockSCMRepository {
                project {
                    branch {
                        configureMockSCMBranch()
                        autoVersioningConfigurationService.setupAutoVersioning(
                            this,
                            AutoVersioningConfig(
                                configurations = listOf(
                                    AutoVersioningTestFixtures.sourceConfig(
                                        sourceProject = dep1.project.name,
                                        sourceBranch = dep1.name,
                                        sourcePromotion = "GOLD",
                                        targetPath = "dep1.properties",
                                    ),
                                    AutoVersioningTestFixtures.sourceConfig(
                                        sourceProject = dep2.project.name,
                                        sourceBranch = dep2.name,
                                        sourcePromotion = "GOLD",
                                        targetPath = "dep2.properties",
                                    ),
                                    AutoVersioningTestFixtures.sourceConfig(
                                        sourceProject = dep3.project.name,
                                        sourceBranch = dep3.name,
                                        sourcePromotion = "GOLD",
                                        targetPath = "dep3.properties",
                                    ),
                                )
                            )
                        )
                        code(this, dep1, dep2, dep3)
                    }
                }
            }
        }
    }

    protected fun withPromotionLevelTargets(
        disabled: Boolean? = null,
        code: (
            pl: PromotionLevel,
            app1: Branch,
            app2: Branch,
        ) -> Unit,
    ) {
        asAdmin {
            project {
                val sourceProject = this
                branch {
                    val sourceBranch = this
                    val pl = promotionLevel()

                    val app1 =
                        mockSCMTester.withMockSCMRepository {
                            project<Branch> {
                                branch {
                                    configureMockSCMBranch()
                                    autoVersioningConfigurationService.setupAutoVersioning(
                                        this,
                                        AutoVersioningConfig(
                                            configurations = listOf(
                                                AutoVersioningTestFixtures.sourceConfig(
                                                    sourceProject = sourceProject.name,
                                                    sourceBranch = sourceBranch.name,
                                                    sourcePromotion = pl.name,
                                                    targetPath = "app1.properties",
                                                    disabled = disabled,
                                                )
                                            )
                                        )
                                    )
                                }
                            }
                        }

                    val app2 =
                        mockSCMTester.withMockSCMRepository {
                            project<Branch> {
                                branch {
                                    configureMockSCMBranch()
                                    autoVersioningConfigurationService.setupAutoVersioning(
                                        this,
                                        AutoVersioningConfig(
                                            configurations = listOf(
                                                AutoVersioningTestFixtures.sourceConfig(
                                                    sourceProject = sourceProject.name,
                                                    sourceBranch = sourceBranch.name,
                                                    sourcePromotion = pl.name,
                                                    targetPath = "app2.properties",
                                                )
                                            )
                                        )
                                    )
                                }
                            }
                        }

                    code(pl, app1, app2)
                }
            }
        }
    }

    protected fun withSimpleSetup(
        cronSchedule: String? = null,
        prTitleTemplate: String? = null,
        code: (
            pl: PromotionLevel,
            target: Branch,
        ) -> Unit,
    ) {
        asAdmin {
            project {
                val sourceProject = this
                branch {
                    val sourceBranch = this
                    val pl = promotionLevel()

                    val target =
                        mockSCMTester.withMockSCMRepository {
                            project<Branch> {
                                branch {
                                    configureMockSCMBranch()

                                    repositoryFile(
                                        branch = "main",
                                        path = "target.properties",
                                        content = """
                                            version = 0.0.1
                                        """.trimIndent()
                                    )

                                    autoVersioningConfigurationService.setupAutoVersioning(
                                        this,
                                        AutoVersioningConfig(
                                            configurations = listOf(
                                                AutoVersioningTestFixtures.sourceConfig(
                                                    sourceProject = sourceProject.name,
                                                    sourceBranch = sourceBranch.name,
                                                    sourcePromotion = pl.name,
                                                    targetPath = "target.properties",
                                                    targetProperty = "version",
                                                    cronSchedule = cronSchedule,
                                                    prTitleTemplate = prTitleTemplate,
                                                )
                                            )
                                        )
                                    )
                                }
                            }
                        }

                    code(pl, target)
                }
            }
        }
    }

}