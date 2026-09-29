package net.nemerosa.ontrack.service

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.security.PromotionLevelCreate
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.model.security.ValidationRunStatusChange
import net.nemerosa.ontrack.model.security.ValidationStampCreate
import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.security.access.AccessDeniedException
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * This class supersedes [StructureServiceIT], which cannot be replaced as yet.
 */
@AsAdminTest
class StructureServiceNewIT : AbstractDSLTestSupport() {

    @Test
    fun `Filtering on enabled branches only`() {
        project {
            val branches = (1..10).map {
                branch("1.$it") {
                    if (it % 2 == 0) {
                        structureService.disableBranch(this)
                    }
                }
            }
            val enabledBranches = structureService.filterBranchesForProject(this, BranchFilter(enabled = true))
            assertEquals(
                listOf(
                    "1.1", "1.3", "1.5", "1.7", "1.9"
                ).reversed(),
                enabledBranches.map { it.name }
            )
        }
    }

    /**
     * Making sure that branches without a build (yet) are still returned when getting
     * the list of branches for a project.
     */
    @Test
    fun `Getting branches for a project when there is no build`() {
        project {
            // Creating a branch without a build
            val branch = branch {}
            // Getting the list of branches (as asked by the project page)
            val branches = structureService.filterBranchesForProject(
                project, BranchFilter(
                    count = 20,
                    order = true,
                )
            )
            // Checks the branch is there
            assertEquals(
                listOf(branch),
                branches
            )
        }
    }

    /**
     * Branches can be sorted by most recent builds
     */
    @Test
    fun `Sorting branches for a project by most recent build`() {
        project {
            val ref = Time.now()
            // Creating three branches
            val (branch0, branch1, branch2) = (0..2).map {
                branch("branch-$it") {
                    updateBranchSignature(time = ref.minusHours(10))
                }
            }
            // Creating some builds at different times
            branch1.build {
                // Oldest
                updateBuildSignature(time = ref.minusHours(5))
            }
            branch0.build {
                // Middle
                updateBuildSignature(time = ref.minusHours(4))
            }
            branch2.build {
                // Most recent
                updateBuildSignature(time = ref.minusHours(3))
            }
            // Getting the list of branches (as asked by the project page)
            val list = structureService.filterBranchesForProject(
                project, BranchFilter(
                    count = 20,
                    order = true,
                )
            )
            // Checks the branch is there
            assertEquals(
                listOf(branch2, branch0, branch1).map { it.name },
                list.map { it.name }
            )
        }
    }

    @Test
    fun `Getting mix of branches for a project with builds and no build`() {
        project {
            val ref = Time.now()
            // Creating an older branch without a build
            branch("old-without-build") {
                updateBranchSignature(time = ref.minusHours(3))
            }
            // Creating a branch with a build
            branch("with-build") {
                build {
                    updateBuildSignature(time = ref.minusHours(2))
                }
            }
            // Creating a branch without a build (after the build)
            branch("recent-without-build") {
                updateBranchSignature(time = ref.minusHours(1))
            }
            // Getting the list of branches (as asked by the project page)
            val branches = structureService.filterBranchesForProject(
                project, BranchFilter(
                    count = 20,
                    order = true,
                )
            )
            // Checks the branches are both there, with the one without a build in front
            assertEquals(
                listOf("recent-without-build", "with-build", "old-without-build"),
                branches.map { it.name }
            )
        }
    }

    @Test
    fun `Creating a link twice must not fail`() {
        val target = project<Build> {
            branch<Build> {
                build()
            }
        }
        val source = project<Build> {
            branch<Build> {
                build()
            }
        }
        asAdmin {
            structureService.createBuildLink(source, target) // Once
            structureService.createBuildLink(source, target) // Twice
        }
    }

    @Test
    fun `Looking for projects using a pattern`() {
        val rootA = uid("P")
        val rootB = uid("P")
        repeat(5) {
            project(name = NameDescription.nd("X${rootA}$it", ""))
        }
        repeat(5) {
            project(name = NameDescription.nd("Y${rootB}$it", ""))
        }
        asAdmin {
            val projects = structureService.findProjectsByNamePattern(rootA)
            assertEquals(
                (0..4).map { "X$rootA$it" },
                projects.map { it.name }
            )
        }
    }

    @Test
    fun `Looking for projects using a pattern is restricted by authorizations`() {
        val rootA = uid("P")
        val rootB = uid("P")
        val projectsA = (0..4).map {
            project(name = NameDescription.nd("X${rootA}$it", ""))
        }
        repeat(5) {
            project(name = NameDescription.nd("Y${rootB}$it", ""))
        }
        withNoGrantViewToAll {
            asUserWithView(*projectsA.take(3).toTypedArray()) {
                val projects = structureService.findProjectsByNamePattern(rootA)
                assertEquals(
                    (0..2).map { "X$rootA$it" },
                    projects.map { it.name }
                )
            }
        }
    }

    @Test
    fun `An admin can change the signature of a branch`() {
        project {
            val branch = branch {
                updateBranchSignature(time = Time.now().minusDays(10))
            }
            // Loads the branch again
            asUserWithView {
                val reloaded = structureService.getBranch(branch.id)
                assertEquals(10, Duration.between(reloaded.signature.time, Time.now()).toDays())
            }
        }
    }

    @Test
    fun `Validation run status comment not editable by default`() {
        project {
            branch {
                val vs = validationStamp()
                build {
                    validate(vs, ValidationRunStatusID.STATUS_FAILED)
                    // Second validation with comment
                    val run = asUser().withProjectFunction(this, ValidationRunStatusChange::class.java).call {
                        validate(vs, ValidationRunStatusID.STATUS_INVESTIGATING, "First comment")
                    }
                    val statusId = run.lastStatus.id
                    // Not editable by anonymous user
                    asAnonymous().execute {
                        val editable = structureService.isValidationRunStatusCommentEditable(statusId)
                        assertFalse(editable, "Status comment by default not editable")
                    }
                }
            }
        }
    }

    @Test
    fun `New promotion level based on a predefined promotion level with description and image`() {
        // Unique promotion name
        val promotionName = uid("P")
        // Predefined promotion level
        predefinedPromotionLevel(promotionName, "My predefined description", image = true)
        // Creating a promotion level
        project {
            branch {
                asUser().withProjectFunction(this, PromotionLevelCreate::class.java).call {
                    val pl = structureService.newPromotionLevel(
                        PromotionLevel.of(
                            this,
                            NameDescription.nd(promotionName, "")
                        )
                    )
                    // Description must be aligned
                    assertEquals("My predefined description", pl.description)
                    // An image must have been set
                    assertTrue(pl.isImage, "An image must have been set")
                }
            }
        }
    }

    @Test
    fun `New validation stamp based on a predefined validation stamp with description and image`() {
        // Unique validation name
        val validationName = uid("V")
        // Predefined validation stamp
        predefinedValidationStamp(validationName, "My predefined description", image = true)
        // Creating a validation stamp
        project {
            branch {
                asUser().withProjectFunction(this, ValidationStampCreate::class.java).call {
                    val vs = structureService.newValidationStamp(
                        ValidationStamp.of(
                            this,
                            NameDescription.nd(validationName, "")
                        )
                    )
                    // Description must be aligned
                    assertEquals("My predefined description", vs.description)
                    // An image must have been set
                    assertTrue(vs.isImage, "An image must have been set")
                }
            }
        }
    }

    @Test
    fun `Validation runs for a validation stamp between two dates`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp()
                    // Creates several builds and validations with some date interval
                    val ref = Time.now().minusDays(100)
                    var index = 0L
                    repeat(5) { buildNo ->
                        build(name = "build-$buildNo") {
                            repeat(5) { runNo ->
                                validate(
                                    vs, description = "run-$runNo", signature = Signature.of(
                                        ref.plusDays(index++),
                                        "test"
                                    )
                                )
                            }
                        }
                    }
                    // Gets the validation runs between two dates
                    val runs = structureService.getValidationRunsForValidationStampBetweenDates(
                        vs.id,
                        ref.plusDays(12),
                        ref.plusDays(17),
                    )
                    // Extracts the build names & run descriptions
                    val names = runs.map {
                        it.build.name to it.lastStatus.description
                    }
                    // Checks the names
                    assertEquals(
                        listOf(
                            "build-3" to "run-2",
                            "build-3" to "run-1",
                            "build-3" to "run-0",
                            "build-2" to "run-4",
                            "build-2" to "run-3",
                            "build-2" to "run-2",
                        ),
                        names
                    )
                }
            }
        }
    }

    @Test
    fun `An admin can disable and enable a project`() {
        val project = project()
        asAdmin {
            structureService.disableProject(project)
            assertTrue(structureService.getProject(project.id).isDisabled, "Project is disabled")
            structureService.enableProject(project)
            assertFalse(structureService.getProject(project.id).isDisabled, "Project is enabled")
        }
    }

    @Test
    fun `Automation can disable and enable a project`() {
        val project = project()
        withGrantViewToAll {
            asGlobalRole(Roles.GLOBAL_AUTOMATION) {
                structureService.disableProject(project)
                assertTrue(structureService.getProject(project.id).isDisabled, "Project is disabled")
                structureService.enableProject(project)
                assertFalse(structureService.getProject(project.id).isDisabled, "Project is enabled")
            }
        }
    }

    @Test
    fun `Participants cannot disable and enable a project`() {
        val project = project()
        withGrantViewToAll {
            asGlobalRole(Roles.GLOBAL_PARTICIPANT) {
                assertFailsWith<AccessDeniedException> {
                    structureService.disableProject(project)
                }
                assertFalse(structureService.getProject(project.id).isDisabled, "Project is NOT disabled")
                assertFailsWith<AccessDeniedException> {
                    structureService.enableProject(project)
                }
                assertFalse(structureService.getProject(project.id).isDisabled, "Project is enabled")
            }
        }
    }

    @Test
    fun `An admin can disable and enable a branch`() {
        val branch = project<Branch> {
            branch()
        }
        asAdmin {
            structureService.disableBranch(branch)
            assertTrue(structureService.getBranch(branch.id).isDisabled, "Branch is disabled")
            structureService.enableBranch(branch)
            assertFalse(structureService.getBranch(branch.id).isDisabled, "Branch is enabled")
        }
    }

    @Test
    fun `Automation can disable and enable a branch`() {
        val branch = project<Branch> {
            branch()
        }
        withGrantViewToAll {
            asGlobalRole(Roles.GLOBAL_AUTOMATION) {
                structureService.disableBranch(branch)
                assertTrue(structureService.getBranch(branch.id).isDisabled, "Branch is disabled")
                structureService.enableBranch(branch)
                assertFalse(structureService.getBranch(branch.id).isDisabled, "Branch is enabled")
            }
        }
    }

    @Test
    fun `Participants cannot disable and enable a branch`() {
        val branch = project<Branch> {
            branch()
        }
        withGrantViewToAll {
            asGlobalRole(Roles.GLOBAL_PARTICIPANT) {
                assertFailsWith<AccessDeniedException> {
                    structureService.disableBranch(branch)
                }
                assertFalse(structureService.getBranch(branch.id).isDisabled, "Branch is NOT disabled")
                assertFailsWith<AccessDeniedException> {
                    structureService.enableBranch(branch)
                }
                assertFalse(structureService.getBranch(branch.id).isDisabled, "Branch is enabled")
            }
        }
    }

}