package net.nemerosa.ontrack.service

import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.*

@AsAdminTest
class PromotionRunServiceIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var promotionRunService: PromotionRunService

    @Test
    fun `Getting last promotion run for a promotion in a project`() {
        val plName = uid("pl_")
        project {
            branch {
                val branch1 = this
                val pl1 = promotionLevel(name = plName)
                build {
                    promote(pl1)
                }
                branch {
                    val pl2 = promotionLevel(name = plName)
                    build {
                        promote(pl2)
                    }
                    // Promotes a new build in (1)
                    branch1.apply {
                        val build1 = build {
                            promote(pl1)
                        }
                        // Looking for the last promotion run
                        assertNotNull(promotionRunService.getLastPromotionRunForProject(project, plName)) { run ->
                            assertEquals(pl1, run.promotionLevel, "Promotion on branch 1")
                            assertEquals(build1, run.build, "Build on branch 1")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Not getting any last promotion run for a promotion in a project`() {
        val plName = uid("pl_")
        project {
            branch {
                val branch1 = this
                val pl1 = promotionLevel(name = plName)
                build {
                    promote(pl1)
                }
                branch {
                    val pl2 = promotionLevel(name = plName)
                    build {
                        promote(pl2)
                    }
                    // Promotes a new build in (1)
                    branch1.apply {
                        build {
                            promote(pl1)
                        }
                        // Looking for the last promotion run
                        // using another name altogether
                        assertNull(
                            promotionRunService.getLastPromotionRunForProject(project, uid("pl_")),
                            "Not find any last run"
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `Getting last promotion run for a promotion in a branch`() {
        project {
            branch {
                val pl = promotionLevel()
                build()
                val build = build {
                    promote(pl)
                }
                build()
                assertNotNull(promotionRunService.getLastPromotionRunForBranch(this, pl.name)) { run ->
                    assertEquals(pl, run.promotionLevel)
                    assertEquals(build, run.build)
                }
            }
        }
    }

    @Test
    fun `Not getting any last promotion run for a promotion in a branch`() {
        project {
            branch {
                val pl = promotionLevel()
                val plx = promotionLevel()
                build()
                val build = build {
                    promote(pl)
                }
                build()
                assertNull(promotionRunService.getLastPromotionRunForBranch(this, plx.name))
            }
        }
    }

    @Test
    fun `Checking if a build is promoted`() {
        project {
            branch {
                val pl = promotionLevel()
                val build1 = build()
                val build2 = build {
                    promote(pl)
                }

                assertFalse(promotionRunService.isBuildPromoted(build1, pl), "Build is not promoted")
                assertTrue(promotionRunService.isBuildPromoted(build2, pl), "Build is promoted")
            }
        }
    }

    @Test
    fun `Field values are loaded when fetching promotion runs for a build`() {
        project {
            branch {
                val pl = promotionLevel()
                structureService.setPromotionLevelFields(
                    pl.id,
                    listOf(
                        PromotionLevelField(0, "ticket", "Ticket", null, PromotionLevelFieldType.TEXT, required = false, emptyList(), 0),
                    )
                )
                build {
                    structureService.newPromotionRun(
                        PromotionRun.of(
                            build = this,
                            promotionLevel = structureService.getPromotionLevel(pl.id),
                            signature = Signature.anonymous(),
                            description = null,
                        ).withFieldValues(listOf(PromotionRunFieldValue("ticket", "PROJ-42".asJson())))
                    )

                    // getPromotionRunsForBuild
                    val runs = structureService.getPromotionRunsForBuild(id)
                    val fv = runs.firstOrNull()?.fieldValues?.firstOrNull { it.name == "ticket" }
                    assertNotNull(fv, "ticket field value must be present in getPromotionRunsForBuild")
                    assertEquals("PROJ-42", fv.value?.asText())

                    // getLastPromotionRunsForBuild
                    val promotionLevels = structureService.getPromotionLevelListForBranch(branch.id)
                    val lastRuns = structureService.getLastPromotionRunsForBuild(this, promotionLevels)
                    val lastFv = lastRuns.firstOrNull()?.fieldValues?.firstOrNull { it.name == "ticket" }
                    assertNotNull(lastFv, "ticket field value must be present in getLastPromotionRunsForBuild")
                    assertEquals("PROJ-42", lastFv.value?.asText())

                    // getPromotionRunsForBuildAndPromotionLevel
                    val plRuns = structureService.getPromotionRunsForBuildAndPromotionLevel(this, structureService.getPromotionLevel(pl.id))
                    val plFv = plRuns.firstOrNull()?.fieldValues?.firstOrNull { it.name == "ticket" }
                    assertNotNull(plFv, "ticket field value must be present in getPromotionRunsForBuildAndPromotionLevel")
                    assertEquals("PROJ-42", plFv.value?.asText())
                }
            }
        }
    }

}