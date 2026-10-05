package net.nemerosa.ontrack.extension.github.ingestion.processing.events

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.general.AutoPromotionPropertyType
import net.nemerosa.ontrack.extension.github.client.UseGitHubClientMock
import net.nemerosa.ontrack.extension.github.ingestion.AbstractIngestionTestSupport
import net.nemerosa.ontrack.extension.github.ingestion.IngestionHookFixtures
import net.nemerosa.ontrack.extension.github.ingestion.config.model.IngestionConfig
import net.nemerosa.ontrack.extension.github.ingestion.config.model.IngestionConfigCascPromotion
import net.nemerosa.ontrack.extension.github.ingestion.config.model.IngestionConfigSetup
import net.nemerosa.ontrack.extension.github.ingestion.config.model.IngestionConfigSteps
import net.nemerosa.ontrack.extension.github.ingestion.config.model.support.FilterConfig
import net.nemerosa.ontrack.extension.github.ingestion.processing.config.ConfigLoaderService
import net.nemerosa.ontrack.extension.github.ingestion.processing.config.ConfigLoaderServiceITMockConfig
import net.nemerosa.ontrack.extension.github.ingestion.processing.config.ConfigService
import net.nemerosa.ontrack.extension.github.ingestion.processing.config.INGESTION_CONFIG_FILE_PATH
import net.nemerosa.ontrack.extension.github.ingestion.processing.model.WorkflowJobStepConclusion
import net.nemerosa.ontrack.extension.github.ingestion.processing.model.WorkflowJobStepStatus
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration
import kotlin.jvm.optionals.getOrNull
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Testing the auto promotion setup.
 */
@ContextConfiguration(classes = [ConfigLoaderServiceITMockConfig::class])
@UseGitHubClientMock
class AutoPromotionIT : AbstractIngestionTestSupport() {

    @Autowired
    private lateinit var configLoaderService: ConfigLoaderService

    @Autowired
    private lateinit var configService: ConfigService

    @Autowired
    private lateinit var workflowRunIngestionEventProcessor: WorkflowRunIngestionEventProcessor

    @Autowired
    private lateinit var workflowJobIngestionEventProcessor: WorkflowJobIngestionEventProcessor

    private val ref = Time.now()
    private val job = "job"

    @Test
    fun `Auto promotion`() {
        // Only one GitHub configuration
        onlyOneGitHubConfig()
        // Branch
        val branch = project<Branch> {
            branch()
        }
        // Auto promotion setup to be loaded for the repository
        ConfigLoaderServiceITMockConfig.customIngestionConfig(
            configLoaderService,
            IngestionConfig(
                steps = IngestionConfigSteps(
                    filter = FilterConfig.all // Steps are not included by default
                ),
                setup = IngestionConfigSetup(
                    promotions = listOf(
                        IngestionConfigCascPromotion(
                            name = "BRONZE",
                            validations = listOf("$job-build", "$job-unit-test"),
                        ),
                        IngestionConfigCascPromotion(
                            name = "SILVER",
                            validations = listOf("$job-integration-test"),
                            promotions = listOf("BRONZE"),
                        ),
                    )
                )
            )
        )
        asAdmin {
            // Starting the run
            startRun(branch)
            // Checks that validations & promotions are created, and that auto promotion is set up
            checkValidationStamps(branch)
            checkPromotionLevels(branch)
            // Job runs for the 'BRONZE' level
            runJobs(branch, "build", "unit-test")
            // Checks that 'BRONZE' has been granted
            checkValidations(branch, "$job-build", "$job-unit-test")
            checkPromotion(branch, "BRONZE")
            // Job runs for the 'SILVER' level
            runJobs(branch, "integration-test")
            // Checks that 'SILVER' has been granted
            checkValidations(branch, "$job-integration-test")
            checkPromotion(branch, "SILVER")
        }
    }

    private fun startRun(branch: Branch) {
        configService.loadAndSaveConfig(branch, INGESTION_CONFIG_FILE_PATH)
        workflowRunIngestionEventProcessor.process(
            IngestionHookFixtures.sampleWorkflowRunPayload(
                repoName = branch.project.name,
                headBranch = branch.name,
            ), null
        )
    }

    private fun checkValidationStamps(branch: Branch) {
        listOf("build", "unit-test", "integration-test").forEach { vsName ->
            val vs =
                structureService.findValidationStampByName(branch.project.name, branch.name, "$job-$vsName").getOrNull()
            assertNotNull(vs, "Validation stamp $job-$vsName has been created.")
        }
    }

    private fun checkPromotionLevels(branch: Branch) {
        assertNotNull(
            structureService.findPromotionLevelByName(branch.project.name, branch.name, "BRONZE")
                .getOrNull()
        ) { pl ->
            assertNotNull(getProperty(pl, AutoPromotionPropertyType::class.java)) { property ->
                assertEquals(
                    setOf("$job-build", "$job-unit-test"),
                    property.validationStamps.map { it.name }.toSet()
                )
                assertTrue(property.promotionLevels.isEmpty())
            }
        }
        assertNotNull(
            structureService.findPromotionLevelByName(branch.project.name, branch.name, "SILVER")
                .getOrNull()
        ) { pl ->
            assertNotNull(getProperty(pl, AutoPromotionPropertyType::class.java)) { property ->
                assertEquals(
                    setOf("$job-integration-test"),
                    property.validationStamps.map { it.name }.toSet()
                )
                assertEquals(
                    listOf("BRONZE"),
                    property.promotionLevels.map { it.name }
                )
            }
        }
    }

    private fun runJobs(branch: Branch, vararg validations: String) {
        workflowJobIngestionEventProcessor.process(
            WorkflowJobPayload(
                action = WorkflowJobAction.in_progress,
                workflowJob = WorkflowJob(
                    runId = 1,
                    runAttempt = 1,
                    status = WorkflowJobStepStatus.in_progress,
                    conclusion = null,
                    startedAtDate = ref,
                    completedAtDate = null,
                    name = job,
                    steps = validations.map { validation ->
                        WorkflowJobStep(
                            name = validation,
                            status = WorkflowJobStepStatus.completed,
                            conclusion = WorkflowJobStepConclusion.success,
                            number = 1,
                            startedAtDate = ref,
                            completedAtDate = ref.plusSeconds(10),
                        )
                    },
                    htmlUrl = "",
                ),
                repository = IngestionHookFixtures.sampleRepository(repoName = branch.project.name),
            ),
            null
        )
    }

    private fun checkValidations(branch: Branch, vararg validations: String) {
        val build = getBuild(branch)
        validations.forEach { validation ->
            val vs = structureService.findValidationStampByName(branch.project.name, branch.name, validation)
                .getOrNull()
                ?: fail("Cannot find validation $validation")
            val runs = structureService.getValidationRunsForBuildAndValidationStamp(
                build.id,
                vs.id,
                0,
                10
            )
            assertTrue(
                runs.any { it.lastStatus.statusID == ValidationRunStatusID.STATUS_PASSED },
                "Build has been validated for $validation"
            )
        }
    }

    private fun checkPromotion(branch: Branch, promotion: String) {
        val build = getBuild(branch)
        val pl = structureService.findPromotionLevelByName(build.project.name, build.branch.name, promotion)
            .getOrNull()
            ?: fail("Cannot find promotion $promotion")
        val runs = structureService.getPromotionRunsForBuildAndPromotionLevel(build, pl)
        assertTrue(runs.isNotEmpty(), "Build has been promoted to $promotion")
    }

    private fun getBuild(branch: Branch) =
        structureService.findBuildByName(
            branch.project.name,
            branch.name,
            buildName
        ).getOrNull() ?: fail("Cannot find the created build $buildName")

    private val buildName: String = "${IngestionHookFixtures.sampleRunName}-1".lowercase()

}