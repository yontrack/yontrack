package net.nemerosa.ontrack.extension.av.processing

import net.nemerosa.ontrack.extension.av.AbstractAutoVersioningTestSupport
import net.nemerosa.ontrack.extension.av.AutoVersioningTestFixtures.createOrder
import net.nemerosa.ontrack.extension.av.config.AutoVersioningPushMode
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.ValidationRun
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.jvm.optionals.getOrNull
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Testing that the back validation is recorded on the source build whatever the push mode
 * and whatever the outcome of the processing.
 */
class AutoVersioningProcessingServiceBackValidationIT : AbstractAutoVersioningTestSupport() {

    @Autowired
    private lateinit var autoVersioningProcessingService: AutoVersioningProcessingService

    @Test
    fun `Back validation is passed after a direct push`() {
        withBackValidation(
            currentVersion = "1.0.0",
            pushMode = AutoVersioningPushMode.PUSH,
        ) { outcome, runs ->
            assertEquals(AutoVersioningProcessingOutcome.CREATED, outcome)
            assertEquals(1, runs.size, "One back validation")
            assertEquals(ValidationRunStatusID.STATUS_PASSED, runs.first().lastStatus.statusID)
        }
    }

    @Test
    fun `Back validation is failed on a direct push when the target already has the version`() {
        withBackValidation(
            currentVersion = "2.0.0",
            pushMode = AutoVersioningPushMode.PUSH,
        ) { outcome, runs ->
            assertEquals(AutoVersioningProcessingOutcome.SAME_VERSION, outcome)
            assertEquals(1, runs.size, "One back validation")
            assertEquals(ValidationRunStatusID.STATUS_FAILED, runs.first().lastStatus.statusID)
        }
    }

    @Test
    fun `Back validation is failed on a direct push rejected by the version rule`() {
        withBackValidation(
            currentVersion = "3.0.0",
            pushMode = AutoVersioningPushMode.PUSH,
            versionRule = "semver",
        ) { outcome, runs ->
            assertEquals(AutoVersioningProcessingOutcome.REJECTED, outcome)
            assertEquals(1, runs.size, "One back validation")
            assertEquals(ValidationRunStatusID.STATUS_FAILED, runs.first().lastStatus.statusID)
        }
    }

    @Test
    fun `Back validation is failed when the target branch is not configured`() {
        withBackValidation(
            currentVersion = null,
            pushMode = AutoVersioningPushMode.PUSH,
        ) { outcome, runs ->
            assertEquals(AutoVersioningProcessingOutcome.NO_CONFIG, outcome)
            assertEquals(1, runs.size, "One back validation")
            assertEquals(ValidationRunStatusID.STATUS_FAILED, runs.first().lastStatus.statusID)
        }
    }

    @Test
    fun `Back validation is recorded only once for an auto approved PR`() {
        withBackValidation(
            currentVersion = "1.0.0",
            pushMode = AutoVersioningPushMode.PR,
        ) { outcome, runs ->
            assertEquals(AutoVersioningProcessingOutcome.CREATED, outcome)
            assertEquals(1, runs.size, "One back validation, not two")
            assertEquals(ValidationRunStatusID.STATUS_PASSED, runs.first().lastStatus.statusID)
        }
    }

    /**
     * @param currentVersion Version in the target file, `null` to leave the target branch without SCM configuration
     */
    private fun withBackValidation(
        currentVersion: String?,
        pushMode: AutoVersioningPushMode,
        versionRule: String? = null,
        code: (outcome: AutoVersioningProcessingOutcome, runs: List<ValidationRun>) -> Unit,
    ) {
        asAdmin {
            project {
                branch {
                    build {
                        val source = this
                        mockSCMTester.withMockSCMRepository {
                            project {
                                branch {
                                    if (currentVersion != null) {
                                        configureMockSCMBranch()
                                        repositoryFile(
                                            path = "gradle.properties",
                                            content = "version = $currentVersion",
                                        )
                                    }

                                    val order = createOrder(
                                        sourceProject = source.project.name,
                                        sourceBuildId = source.id(),
                                        sourceBackValidation = BACK_VALIDATION,
                                        targetVersion = "2.0.0",
                                        pushMode = pushMode,
                                        versionRule = versionRule,
                                    )

                                    val outcome = autoVersioningProcessingService.process(order)

                                    code(outcome, source.backValidationRuns())
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun Build.backValidationRuns(): List<ValidationRun> {
        val vs = structureService.findValidationStampByName(
            project = project.name,
            branch = branch.name,
            validationStamp = BACK_VALIDATION,
        ).getOrNull()
        assertNotNull(vs, "Back validation stamp has been created")
        return structureService.getValidationRunsForBuildAndValidationStamp(this, vs, 0, 10)
    }

    companion object {
        private const val BACK_VALIDATION = "back-validation"
    }

}
