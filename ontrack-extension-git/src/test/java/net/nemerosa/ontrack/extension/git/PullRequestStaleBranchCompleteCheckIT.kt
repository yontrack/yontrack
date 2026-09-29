package net.nemerosa.ontrack.extension.git

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.git.mocking.GitMockingConfigurator
import net.nemerosa.ontrack.extension.stale.StaleJobService
import net.nemerosa.ontrack.extension.stale.StaleProperty
import net.nemerosa.ontrack.extension.stale.StalePropertyType
import net.nemerosa.ontrack.job.JobRunListener
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.Project
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.jvm.optionals.getOrNull
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests between the PR check and the normal branch check.
 */
@WithGitPullRequestEnabled
class PullRequestStaleBranchCompleteCheckIT : AbstractGitTestSupport() {

    @Autowired
    private lateinit var gitMockingConfigurator: GitMockingConfigurator

    @Autowired
    private lateinit var staleJobService: StaleJobService

    @BeforeEach
    fun init() {
        gitMockingConfigurator.clearPullRequests()
    }

    @Test
    fun `All tests`() {
        notAPR() withProperty none() withBuild recent() returns kept()
        notAPR() withProperty none() withBuild medium() returns kept()
        notAPR() withProperty none() withBuild old() returns kept()
        //
        notAPR() withProperty propertyWithoutPromotion() withBuild recent() returns kept()
        notAPR() withProperty propertyWithoutPromotion() withBuild medium() returns disabled()
        notAPR() withProperty propertyWithoutPromotion() withBuild old() returns deleted()
        //
        notAPR() withProperty propertyWithPromotionAndNotPromoted() withBuild recent() returns kept()
        notAPR() withProperty propertyWithPromotionAndNotPromoted() withBuild medium() returns disabled()
        notAPR() withProperty propertyWithPromotionAndNotPromoted() withBuild old() returns deleted()
        //
        notAPR() withProperty propertyWithPromotionAndPromoted() withBuild recent() returns kept()
        notAPR() withProperty propertyWithPromotionAndPromoted() withBuild medium() returns kept()
        notAPR() withProperty propertyWithPromotionAndPromoted() withBuild old() returns kept()
        //
        notAPR() withProperty propertyWithMatchingIncludesAndNoExcludes() withBuild recent() returns kept()
        notAPR() withProperty propertyWithMatchingIncludesAndNoExcludes() withBuild medium() returns kept()
        notAPR() withProperty propertyWithMatchingIncludesAndNoExcludes() withBuild old() returns kept()
        //
        notAPR() withProperty propertyWithMatchingIncludesAndNotMatchingExcludes() withBuild recent() returns kept()
        notAPR() withProperty propertyWithMatchingIncludesAndNotMatchingExcludes() withBuild medium() returns kept()
        notAPR() withProperty propertyWithMatchingIncludesAndNotMatchingExcludes() withBuild old() returns kept()
        //
        notAPR() withProperty propertyWithMatchingIncludesAndMatchingExcludes() withBuild recent() returns kept()
        notAPR() withProperty propertyWithMatchingIncludesAndMatchingExcludes() withBuild medium() returns disabled()
        notAPR() withProperty propertyWithMatchingIncludesAndMatchingExcludes() withBuild old() returns deleted()
        //
        validPR() withProperty none() withBuild recent() returns kept()
        validPR() withProperty none() withBuild medium() returns kept()
        validPR() withProperty none() withBuild old() returns kept()
        //
        validPR() withProperty propertyWithoutPromotion() withBuild recent() returns kept()
        validPR() withProperty propertyWithoutPromotion() withBuild medium() returns disabled()
        validPR() withProperty propertyWithoutPromotion() withBuild old() returns deleted()
        //
        validPR() withProperty propertyWithPromotionAndNotPromoted() withBuild recent() returns kept()
        validPR() withProperty propertyWithPromotionAndNotPromoted() withBuild medium() returns disabled()
        validPR() withProperty propertyWithPromotionAndNotPromoted() withBuild old() returns deleted()
        //
        validPR() withProperty propertyWithPromotionAndPromoted() withBuild recent() returns kept()
        validPR() withProperty propertyWithPromotionAndPromoted() withBuild medium() returns kept()
        validPR() withProperty propertyWithPromotionAndPromoted() withBuild old() returns kept()
        //
        missingPR() withProperty none() withBuild recent() returns kept()
        missingPR() withProperty none() withBuild medium() returns disabled()
        missingPR() withProperty none() withBuild old() returns deleted()
        //
        missingPR() withProperty propertyWithoutPromotion() withBuild recent() returns kept()
        missingPR() withProperty propertyWithoutPromotion() withBuild medium() returns disabled()
        missingPR() withProperty propertyWithoutPromotion() withBuild old() returns deleted()
        //
        missingPR() withProperty propertyWithPromotionAndNotPromoted() withBuild recent() returns kept()
        missingPR() withProperty propertyWithPromotionAndNotPromoted() withBuild medium() returns disabled()
        missingPR() withProperty propertyWithPromotionAndNotPromoted() withBuild old() returns deleted()
        //
        missingPR() withProperty propertyWithPromotionAndPromoted() withBuild recent() returns kept()
        missingPR() withProperty propertyWithPromotionAndPromoted() withBuild medium() returns kept()
        missingPR() withProperty propertyWithPromotionAndPromoted() withBuild old() returns kept()
        //
        invalidPR() withProperty none() withBuild recent() returns kept()
        invalidPR() withProperty none() withBuild medium() returns disabled()
        invalidPR() withProperty none() withBuild old() returns deleted()
        //
        invalidPR() withProperty propertyWithoutPromotion() withBuild recent() returns kept()
        invalidPR() withProperty propertyWithoutPromotion() withBuild medium() returns disabled()
        invalidPR() withProperty propertyWithoutPromotion() withBuild old() returns deleted()
        //
        invalidPR() withProperty propertyWithPromotionAndNotPromoted() withBuild recent() returns kept()
        invalidPR() withProperty propertyWithPromotionAndNotPromoted() withBuild medium() returns disabled()
        invalidPR() withProperty propertyWithPromotionAndNotPromoted() withBuild old() returns deleted()
        //
        invalidPR() withProperty propertyWithPromotionAndPromoted() withBuild recent() returns kept()
        invalidPR() withProperty propertyWithPromotionAndPromoted() withBuild medium() returns kept()
        invalidPR() withProperty propertyWithPromotionAndPromoted() withBuild old() returns kept()
    }

    private inner class ExpectedResult(
            val description: String,
            val checkFn: (description: String, Branch?) -> Unit
    ) {

        fun check(description: String, branch: Branch?) {
            checkFn(description, branch)
        }
    }

    private fun kept() = ExpectedResult("kept") { description, branch ->
        assertNotNull(branch, "$description: Branch is kept") {
            assertFalse(it.isDisabled, "$description: Branch is not disabled")
        }
    }

    private fun disabled() = ExpectedResult("disabled") { description, branch ->
        assertNotNull(branch, "$description: Branch is kept") {
            assertTrue(it.isDisabled, "$description: Branch is disabled")
        }
    }

    private fun deleted() = ExpectedResult("deleted") { description, branch ->
        assertNull(branch, "$description: Branch is deleted")
    }

    private inner class BranchAndBuildConfiguration(
            private val branchConfiguration: BranchConfiguration,
            private val buildConfiguration: BuildConfiguration
    ) {
        infix fun returns(expectedResult: ExpectedResult) {
            val description = "${branchConfiguration.description} with ${buildConfiguration.description} expects to be ${expectedResult.description}"
            createRepo {
                commits(1)
            } and { repo, _ ->
                project {
                    prGitProject()
                    // Stale property for project
                    branchConfiguration.configureProject(this)
                    // Branch
                    branch {
                        // Configuration of the branch
                        branchConfiguration.configureBranch(this)
                        // Build
                        build {
                            // Configures the build
                            branchConfiguration.configureBuild(this)
                            // Additional configuration
                            buildConfiguration.configure(this)
                            // Launches the staleness computation
                            staleJobService.detectAndManageStaleBranches(JobRunListener.out(), project)
                            // Expectations
                            val actualBranch = structureService.findBranchByID(branch.id)
                            expectedResult.check(description, actualBranch)
                        }
                    }
                }
            }
        }
    }

    private inner class BuildConfiguration(
            private val age: Long
    ) {

        val description: String = "build $age days old"

        fun configure(build: Build) {
            if (age > 0) {
                build.updateBuildSignature(time = Time.now().minusDays(age))
            }
        }
    }

    private fun recent() = BuildConfiguration(age = 0)
    private fun medium() = BuildConfiguration(age = 6)
    private fun old() = BuildConfiguration(age = 15)

    private inner class BranchConfiguration(
            private val branchPRConfiguration: BranchPRConfiguration,
            private val propertyConfiguration: PropertyConfiguration
    ) {
        infix fun withBuild(buildConfiguration: BuildConfiguration) =
                BranchAndBuildConfiguration(this, buildConfiguration)

        fun configureBranch(branch: Branch) {
            branchPRConfiguration.configure(branch)
            propertyConfiguration.configureBranch(branch)
        }

        fun configureProject(project: Project) {
            propertyConfiguration.configureProject(project)
        }

        fun configureBuild(build: Build) {
            propertyConfiguration.configureBuild(build)
        }

        val description: String = "${branchPRConfiguration.description} with ${propertyConfiguration.description}"
    }

    private inner class PropertyConfiguration(
            val description: String,
            private val projectConfiguration: (Project) -> Unit,
            private val branchConfiguration: (Branch) -> Unit,
            private val buildConfiguration: (Build) -> Unit
    ) {

        fun configureProject(project: Project) {
            projectConfiguration(project)
        }

        fun configureBranch(branch: Branch) {
            branchConfiguration(branch)
        }

        fun configureBuild(build: Build) {
            buildConfiguration(build)
        }
    }

    private fun none() = PropertyConfiguration(
            description = "no stale property",
            projectConfiguration = {},
            branchConfiguration = {},
            buildConfiguration = {}
    )

    private fun propertyWithoutPromotion() = PropertyConfiguration(
            description = "stale property",
            projectConfiguration = {
                setProperty(it, StalePropertyType::class.java, StaleProperty(
                        disablingDuration = 5,
                        deletingDuration = 10,
                        promotionsToKeep = null,
                        includes = null,
                        excludes = null,
                ))
            },
            branchConfiguration = {},
            buildConfiguration = {}
    )

    private val promotionName = "PL"

    private fun propertyWithPromotionAndNotPromoted() = PropertyConfiguration(
            description = "stale property with promotion protection and no build being promoted",
            projectConfiguration = {
                setProperty(it, StalePropertyType::class.java, StaleProperty(
                        disablingDuration = 5,
                        deletingDuration = 10,
                        promotionsToKeep = listOf(promotionName),
                        includes = null,
                        excludes = null,
                ))
            },
            branchConfiguration = {
                it.promotionLevel(promotionName)
            },
            buildConfiguration = {}
    )

    private fun propertyWithPromotionAndPromoted() = PropertyConfiguration(
            description = "stale property with promotion protection and no build being promoted",
            projectConfiguration = {
                setProperty(it, StalePropertyType::class.java, StaleProperty(
                        disablingDuration = 5,
                        deletingDuration = 10,
                        promotionsToKeep = listOf(promotionName),
                        includes = null,
                        excludes = null,
                ))
            },
            branchConfiguration = {
                it.promotionLevel(promotionName)
            },
            buildConfiguration = {
                val promotion = structureService.findPromotionLevelByName(it.project.name, it.branch.name, promotionName).getOrNull()
                        ?: error("Cannot find promotion level")
                it.promote(promotion)
            }
    )

    private fun propertyWithMatchingIncludesAndNoExcludes() = PropertyConfiguration(
            description = "stale property with include protection matching",
            projectConfiguration = {
                setProperty(it, StalePropertyType::class.java, StaleProperty(
                        disablingDuration = 5,
                        deletingDuration = 10,
                        promotionsToKeep = listOf(promotionName),
                        includes = "release-.*",
                        excludes = null,
                ))
            },
            branchConfiguration = {
                structureService.saveBranch(
                    Branch(
                        id = it.id,
                        name = "release-2.0",
                        description = it.description,
                        isDisabled = it.isDisabled,
                        signature = it.signature,
                        project = it.project,
                    )
                )
                it.promotionLevel(promotionName)
            },
            buildConfiguration = {}
    )

    private fun propertyWithMatchingIncludesAndNotMatchingExcludes() = PropertyConfiguration(
            description = "stale property with include protection matching and no exclude protection matching",
            projectConfiguration = {
                setProperty(it, StalePropertyType::class.java, StaleProperty(
                        disablingDuration = 5,
                        deletingDuration = 10,
                        promotionsToKeep = listOf(promotionName),
                        includes = "release-.*",
                        excludes = "release-1.*",
                ))
            },
            branchConfiguration = {
                structureService.saveBranch(
                    Branch(
                        id = it.id,
                        name = "release-2.0",
                        description = it.description,
                        isDisabled = it.isDisabled,
                        signature = it.signature,
                        project = it.project,
                    )
                )
                it.promotionLevel(promotionName)
            },
            buildConfiguration = {}
    )

    private fun propertyWithMatchingIncludesAndMatchingExcludes() = PropertyConfiguration(
            description = "stale property with include protection matching and exclude protection matching",
            projectConfiguration = {
                setProperty(it, StalePropertyType::class.java, StaleProperty(
                        disablingDuration = 5,
                        deletingDuration = 10,
                        promotionsToKeep = listOf(promotionName),
                        includes = "release-.*",
                        excludes = "release-1.*",
                ))
            },
            branchConfiguration = {
                structureService.saveBranch(
                    Branch(
                        id = it.id,
                        name = "release-1.0",
                        description = it.description,
                        isDisabled = it.isDisabled,
                        signature = it.signature,
                        project = it.project,
                    )
                )
                it.promotionLevel(promotionName)
            },
            buildConfiguration = {}
    )

    private inner class BranchPRConfiguration(
            val description: String,
            private val configuration: (Branch) -> Unit
    ) {

        infix fun withProperty(propertyConfiguration: PropertyConfiguration) =
                BranchConfiguration(this, propertyConfiguration)

        fun configure(branch: Branch) {
            configuration(branch)
        }

    }

    private fun notAPR() = BranchPRConfiguration("Branch which is not a PR") {
        gitMockingConfigurator.clearPullRequests()
        it.gitBranch("main") {
            commitAsProperty()
        }
    }

    private fun validPR() = BranchPRConfiguration("Branch which is a valid PR") {
        gitMockingConfigurator.clearPullRequests()
        gitMockingConfigurator.registerPullRequest(1)
        it.gitBranch("PR-1") {
            commitAsProperty()
        }
    }

    private fun missingPR() = BranchPRConfiguration("Branch which is a missing PR") {
        gitMockingConfigurator.clearPullRequests()
        it.gitBranch("PR-1") {
            commitAsProperty()
        }
    }

    private fun invalidPR() = BranchPRConfiguration("Branch which is an invalid PR") {
        gitMockingConfigurator.clearPullRequests()
        gitMockingConfigurator.registerPullRequest(1, invalid = true)
        it.gitBranch("PR-1") {
            commitAsProperty()
        }
    }

}