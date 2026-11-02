package net.nemerosa.ontrack.service.links

import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.model.links.BranchLinksService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

class BranchLinksServiceIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var branchLinksService: BranchLinksService

    @Test
    fun `Dependency links with multiple source builds`() {
        asAdmin {
            val targetBranch = project().branch()
            val targetBuild1 = targetBranch.build("1")
            val targetBuild2 = targetBranch.build("2")

            project {
                branch {
                    val sourceBuild1 = build("10")
                    val sourceBuild2 = build("11")

                    // sourceBuild1 -> targetBuild1
                    sourceBuild1.linkTo(targetBuild1)
                    // sourceBuild2 -> targetBuild2
                    sourceBuild2.linkTo(targetBuild2)

                    // Get the dependency links of the source branch
                    // It should only return the link from the most recent build (sourceBuild2)
                    // to the target build it points to (targetBuild2)
                    val links = branchLinksService.getDependencyLinks(this, 10)
                    assertEquals(1, links.size)
                    assertEquals(targetBranch.id, links[0].branch.id)
                    assertEquals(sourceBuild2.id, links[0].sourceBuild.id)
                    assertEquals(targetBuild2.id, links[0].targetBuild.id)
                }
            }
        }
    }

    @Test
    fun `Dependency links with multiple target builds for the same source build`() {
        asAdmin {
            val targetBranch = project().branch()
            val targetBuild1 = targetBranch.build("1")
            val targetBuild2 = targetBranch.build("2")

            project {
                branch {
                    val sourceBuild = build("10")

                    // sourceBuild -> targetBuild1
                    // sourceBuild -> targetBuild2
                    sourceBuild.linkTo(targetBuild1)
                    sourceBuild.linkTo(targetBuild2)

                    // Get the dependency links of the source branch
                    // It should return the link to the most recent target build
                    val links = branchLinksService.getDependencyLinks(this, 10)
                    assertEquals(1, links.size)
                    assertEquals(targetBranch.id, links[0].branch.id)
                    assertEquals(sourceBuild.id, links[0].sourceBuild.id)
                    assertEquals(targetBuild2.id, links[0].targetBuild.id)
                }
            }
        }
    }

    @Test
    fun `Consumer links with multiple target builds`() {
        asAdmin {
            val sourceBranch = project().branch()
            val sourceBuild1 = sourceBranch.build("1")
            val sourceBuild2 = sourceBranch.build("2")

            project {
                branch {
                    val targetBuild1 = build("10")
                    val targetBuild2 = build("11")

                    // sourceBuild1 -> targetBuild1
                    sourceBuild1.linkTo(targetBuild1)
                    // sourceBuild2 -> targetBuild2
                    sourceBuild2.linkTo(targetBuild2)

                    // Get the consumer links of the target branch
                    // In this case sourceBranch uses targetBranch, so sourceBranch is a consumer of targetBranch.
                    val links = branchLinksService.getConsumerLinks(this, 10)
                    assertEquals(1, links.size)
                    assertEquals(sourceBranch.id, links[0].branch.id)
                    assertEquals(sourceBuild2.id, links[0].sourceBuild.id)
                    assertEquals(targetBuild2.id, links[0].targetBuild.id)
                }
            }
        }
    }

    @Test
    fun `Dependency links with qualifiers`() {
        asAdmin {
            val targetBranch = project().branch()
            val targetBuild1 = targetBranch.build("1")
            val targetBuild2 = targetBranch.build("2")

            project {
                branch {
                    val sourceBuild = build("10")

                    sourceBuild.linkTo(targetBuild1, "qualifier-1")
                    sourceBuild.linkTo(targetBuild2, "qualifier-2")

                    val links = branchLinksService.getDependencyLinks(this, 10)
                    assertEquals(2, links.size)
                    
                    val link1 = links.find { it.qualifier == "qualifier-1" }
                    val link2 = links.find { it.qualifier == "qualifier-2" }

                    assertEquals(targetBuild1.id, link1?.targetBuild?.id)
                    assertEquals(targetBuild2.id, link2?.targetBuild?.id)
                }
            }
        }
    }

    @Test
    fun `Verify fix for cross-project ID comparison - scenario where it could fail before`() {
        asAdmin {
            val targetBranch = project().branch()
            val targetBuild1 = targetBranch.build("1")
            val targetBuild2 = targetBranch.build("2")

            project {
                branch {
                    val sourceBuild1 = build("10")
                    val sourceBuild2 = build("11")

                    // sourceBuild1 -> targetBuild2
                    sourceBuild1.linkTo(targetBuild2)
                    
                    // sourceBuild2 -> targetBuild1
                    sourceBuild2.linkTo(targetBuild1)

                    val links = branchLinksService.getDependencyLinks(this, 10)
                    assertEquals(1, links.size)
                    
                    // It should keep the link that points to the most recent SOURCE build
                    assertEquals(sourceBuild2.id, links[0].sourceBuild.id)
                    assertEquals(targetBuild1.id, links[0].targetBuild.id)
                }
            }
        }
    }

    @Test
    fun `Multiple source builds pointing to the same target build`() {
        asAdmin {
            val targetProject = project()
            val targetBranch = targetProject.branch("main")
            val targetBuild = targetBranch.build("1.70")

            project {
                branch {
                    val sourceBuild1 = build("1.40.7")
                    val sourceBuild2 = build("1.40.8")

                    // Both source builds point to the same target build
                    sourceBuild1.linkTo(targetBuild)
                    sourceBuild2.linkTo(targetBuild)

                    val links = branchLinksService.getDependencyLinks(this, 10)
                    assertEquals(1, links.size, "Only one link expected for the target branch")
                    assertEquals(targetBranch.id, links[0].branch.id)
                    assertEquals(sourceBuild2.id, links[0].sourceBuild.id, "Expected link from 1.40.8")
                    assertEquals(targetBuild.id, links[0].targetBuild.id)
                }
            }
        }
    }
    @Test
    fun `Multiple target builds pointing to the same source build (consumers)`() {
        asAdmin {
            val sourceProject = project()
            val sourceBranch = sourceProject.branch("main")
            val sourceBuild = sourceBranch.build("1.70")

            project {
                branch {
                    val targetBuild1 = build("1.40.7")
                    val targetBuild2 = build("1.40.8")

                    // Both target builds point to the same source build
                    targetBuild1.linkTo(sourceBuild)
                    targetBuild2.linkTo(sourceBuild)

                    val links = branchLinksService.getConsumerLinks(sourceBranch, 10)
                    assertEquals(1, links.size, "Only one link expected for the target branch")
                    assertEquals(this.id, links[0].branch.id)
                    assertEquals(targetBuild2.id, links[0].sourceBuild.id, "Expected link from 1.40.8")
                    assertEquals(sourceBuild.id, links[0].targetBuild.id)
                }
            }
        }
    }
}
