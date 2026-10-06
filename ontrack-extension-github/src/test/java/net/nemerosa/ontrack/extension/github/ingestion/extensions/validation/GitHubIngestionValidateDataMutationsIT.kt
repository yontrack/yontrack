package net.nemerosa.ontrack.extension.github.ingestion.extensions.validation

import net.nemerosa.ontrack.extension.api.support.TestNumberValidationDataType
import net.nemerosa.ontrack.extension.api.support.TestValidationDataType
import net.nemerosa.ontrack.extension.audittrail.evidence.Evidence
import net.nemerosa.ontrack.extension.audittrail.evidence.EvidenceService
import net.nemerosa.ontrack.extension.audittrail.evidence.EvidenceUpload
import net.nemerosa.ontrack.extension.audittrail.model.TrailEntry
import net.nemerosa.ontrack.extension.audittrail.model.TrailEntryTypes
import net.nemerosa.ontrack.extension.audittrail.service.TrailService
import net.nemerosa.ontrack.extension.general.ReleaseProperty
import net.nemerosa.ontrack.extension.general.ReleasePropertyType
import net.nemerosa.ontrack.extension.github.ingestion.AbstractIngestionTestSupport
import net.nemerosa.ontrack.extension.github.ingestion.payload.IngestionHookPayload
import net.nemerosa.ontrack.extension.github.ingestion.payload.IngestionHookPayloadStatus
import net.nemerosa.ontrack.extension.github.workflow.BuildGitHubWorkflowRun
import net.nemerosa.ontrack.extension.github.workflow.BuildGitHubWorkflowRunProperty
import net.nemerosa.ontrack.extension.github.workflow.BuildGitHubWorkflowRunPropertyType
import net.nemerosa.ontrack.json.getRequiredTextField
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.RunInfoInput
import net.nemerosa.ontrack.model.structure.Signature
import net.nemerosa.ontrack.model.structure.ValidationRun
import net.nemerosa.ontrack.model.structure.ValidationRunStatus
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID
import net.nemerosa.ontrack.model.structure.ValidationStamp
import net.nemerosa.ontrack.model.structure.config
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.springframework.beans.factory.annotation.Autowired
import kotlin.jvm.optionals.getOrNull
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class GitHubIngestionValidateDataMutationsIT : AbstractIngestionTestSupport() {

    @Autowired
    private lateinit var testNumberValidationDataType: TestNumberValidationDataType

    @Autowired
    private lateinit var evidenceService: EvidenceService

    @Autowired
    private lateinit var trailService: TrailService

    @Test
    fun `Automation users can set the validation data`() {
        basicTest { code ->
            asAccountWithGlobalRole(Roles.GLOBAL_AUTOMATION) {
                code()
            }
        }
    }

    @Test
    fun `Build by ID, no prior validation run`() {
        basicTest { code ->
            asAdmin {
                code()
            }
        }
    }

    private fun basicTest(
        asAuth: (code: () -> Unit) -> Unit,
    ) {
        asAdmin {
            withGitHubIngestionSettings {
                project {
                    branch {
                        build {
                            setProperty(this, BuildGitHubWorkflowRunPropertyType::class.java,
                                BuildGitHubWorkflowRunProperty(
                                    workflows = listOf(
                                        BuildGitHubWorkflowRun(
                                            runId = 10,
                                            url = "",
                                            name = "some-workflow",
                                            runNumber = 1,
                                            running = true,
                                            event = "push",
                                        )
                                    )
                                )
                            )
                            asAuth {
                                run("""
                                    mutation {
                                        gitHubIngestionValidateDataByRunId(input: {
                                            owner: "nemerosa",
                                            repository: "${project.name}",
                                            validation: "test",
                                            validationData: {
                                                type: "${TestNumberValidationDataType::class.java.name}",
                                                data: {
                                                    value: 50
                                                }
                                            },
                                            validationStatus: "PASSED",
                                            runId: 10,
                                        }) {
                                            payload {
                                                uuid
                                            }
                                            errors {
                                                message
                                                exception
                                                location
                                            }
                                        }
                                    }
                                """) { data ->
                                    checkGraphQLUserErrors(data, "gitHubIngestionValidateDataByRunId") { node ->
                                        val uuid = node.path("payload").getRequiredTextField("uuid")
                                        assertTrue(uuid.isNotBlank(), "UUID has been returned")
                                    }
                                    asAdmin {
                                        // Checks the validation stamp has been created
                                        val vs = structureService.findValidationStampByName(
                                            project.name,
                                            branch.name,
                                            "test"
                                        ).getOrNull() ?: fail("Validation stamp not created")
                                        // Checks the build has been validated
                                        val run = structureService.getValidationRunsForBuildAndValidationStamp(
                                            buildId = id,
                                            validationStampId = vs.id,
                                            offset = 0,
                                            count = 1,
                                        ).firstOrNull()
                                        assertNotNull(run, "Validation run created") {
                                            assertEquals(
                                                ValidationRunStatusID.PASSED,
                                                it.lastStatusId
                                            )
                                            val runData = it.data?.data
                                            assertEquals(50, runData, "Validation run data has been set")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Build by ID not found`() {
        asAdmin {
            withGitHubIngestionSettings {
                project {
                    branch {
                        build {
                            setProperty(this, BuildGitHubWorkflowRunPropertyType::class.java,
                                BuildGitHubWorkflowRunProperty(
                                    workflows = listOf(
                                        BuildGitHubWorkflowRun(
                                            runId = 11, // Will not be found
                                            url = "",
                                            name = "some-workflow",
                                            runNumber = 1,
                                            running = true,
                                            event = "push",
                                        )
                                    )
                                )
                            )
                            run("""
                                mutation {
                                    gitHubIngestionValidateDataByRunId(input: {
                                        owner: "nemerosa",
                                        repository: "${project.name}",
                                        validation: "test",
                                        validationData: {
                                            type: "${TestNumberValidationDataType::class.java.name}",
                                            data: {
                                                value: 50
                                            }
                                        },
                                        validationStatus: "PASSED",
                                        runId: 10,
                                    }) {
                                        errors {
                                            message
                                            exception
                                            location
                                        }
                                    }
                                }
                            """) { data ->
                                checkGraphQLUserErrors(data, "gitHubIngestionValidateDataByRunId")
                                // Checks the validation stamp has not been created
                                assertNull(
                                    structureService.findValidationStampByName(
                                        project.name,
                                        branch.name,
                                        "test"
                                    ).getOrNull(),
                                    "Validation stamp has not been created"
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Build by name, no prior validation run`() {
        asAdmin {
            withGitHubIngestionSettings {
                project {
                    branch {
                        build {
                            setProperty(this, BuildGitHubWorkflowRunPropertyType::class.java,
                                BuildGitHubWorkflowRunProperty(
                                    workflows = listOf(
                                        BuildGitHubWorkflowRun(
                                            runId = 10,
                                            url = "",
                                            name = "some-workflow",
                                            runNumber = 1,
                                            running = true,
                                            event = "push",
                                        )
                                    )
                                )
                            )
                            run("""
                                mutation {
                                    gitHubIngestionValidateDataByBuildName(input: {
                                        owner: "nemerosa",
                                        repository: "${project.name}",
                                        validation: "test",
                                        validationData: {
                                            type: "${TestNumberValidationDataType::class.java.name}",
                                            data: {
                                                value: 50
                                            }
                                        },
                                        validationStatus: "PASSED",
                                        buildName: "$name"
                                    }) {
                                        errors {
                                            message
                                            exception
                                            location
                                        }
                                    }
                                }
                            """) { data ->
                                checkGraphQLUserErrors(data, "gitHubIngestionValidateDataByRunId")
                                // Checks the validation stamp has been created
                                val vs = structureService.findValidationStampByName(
                                    project.name,
                                    branch.name,
                                    "test"
                                ).getOrNull() ?: fail("Validation stamp not created")
                                // Checks the build has been validated
                                val run = structureService.getValidationRunsForBuildAndValidationStamp(
                                    buildId = id,
                                    validationStampId = vs.id,
                                    offset = 0,
                                    count = 1,
                                ).firstOrNull()
                                assertNotNull(run, "Validation run created") {
                                    assertEquals(
                                        ValidationRunStatusID.PASSED,
                                        it.lastStatusId
                                    )
                                    val runData = it.data?.data
                                    assertEquals(50, runData, "Validation run data has been set")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Build by label, no prior validation run`() {
        asAdmin {
            withGitHubIngestionSettings {
                project {
                    branch {
                        build {
                            setProperty(this, BuildGitHubWorkflowRunPropertyType::class.java,
                                BuildGitHubWorkflowRunProperty(
                                    workflows = listOf(
                                        BuildGitHubWorkflowRun(
                                            runId = 10,
                                            url = "",
                                            name = "some-workflow",
                                            runNumber = 1,
                                            running = true,
                                            event = "push",
                                        )
                                    )
                                )
                            )
                            setProperty(this, ReleasePropertyType::class.java,
                                ReleaseProperty("1.0.0")
                            )
                            run("""
                                mutation {
                                    gitHubIngestionValidateDataByBuildLabel(input: {
                                        owner: "nemerosa",
                                        repository: "${project.name}",
                                        validation: "test",
                                        validationData: {
                                            type: "${TestNumberValidationDataType::class.java.name}",
                                            data: {
                                                value: 50
                                            }
                                        },
                                        validationStatus: "PASSED",
                                        buildLabel: "1.0.0"
                                    }) {
                                        errors {
                                            message
                                            exception
                                            location
                                        }
                                    }
                                }
                            """) { data ->
                                checkGraphQLUserErrors(data, "gitHubIngestionValidateDataByRunId")
                                // Checks the validation stamp has been created
                                val vs = structureService.findValidationStampByName(
                                    project.name,
                                    branch.name,
                                    "test"
                                ).getOrNull() ?: fail("Validation stamp not created")
                                // Checks the build has been validated
                                val run = structureService.getValidationRunsForBuildAndValidationStamp(
                                    buildId = id,
                                    validationStampId = vs.id,
                                    offset = 0,
                                    count = 1,
                                ).firstOrNull()
                                assertNotNull(run, "Validation run created") {
                                    assertEquals(
                                        ValidationRunStatusID.PASSED,
                                        it.lastStatusId
                                    )
                                    val runData = it.data?.data
                                    assertEquals(50, runData, "Validation run data has been set")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Build by ID, no prior validation run, data validation`() {
        asAdmin {
            withGitHubIngestionSettings {
                project {
                    branch {
                        val vs = validationStamp(
                            name = "test",
                            validationDataTypeConfig = testNumberValidationDataType.config(100)
                        )
                        build {
                            setProperty(this, BuildGitHubWorkflowRunPropertyType::class.java,
                                BuildGitHubWorkflowRunProperty(
                                    workflows = listOf(
                                        BuildGitHubWorkflowRun(
                                            runId = 10,
                                            url = "",
                                            name = "some-workflow",
                                            runNumber = 1,
                                            running = true,
                                            event = "push",
                                        )
                                    )
                                ))
                            run("""
                                mutation {
                                    gitHubIngestionValidateDataByRunId(input: {
                                        owner: "nemerosa",
                                        repository: "${project.name}",
                                        validation: "test",
                                        validationData: {
                                            type: "${TestNumberValidationDataType::class.java.name}",
                                            data: {
                                                value: 50
                                            }
                                        },
                                        runId: 10,
                                    }) {
                                        errors {
                                            message
                                            exception
                                            location
                                        }
                                    }
                                }
                            """) { data ->
                                checkGraphQLUserErrors(data, "gitHubIngestionValidateDataByRunId")
                                // Checks the build has been validated
                                val run = structureService.getValidationRunsForBuildAndValidationStamp(
                                    buildId = id,
                                    validationStampId = vs.id,
                                    offset = 0,
                                    count = 1,
                                ).firstOrNull()
                                assertNotNull(run, "Validation run created") {
                                    assertEquals(
                                        ValidationRunStatusID.FAILED,
                                        it.lastStatusId
                                    )
                                    val runData = it.data?.data
                                    assertEquals(50, runData, "Validation run data has been set")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Build by ID, existing validation run`() {
        asAdmin {
            withGitHubIngestionSettings {
                project {
                    branch {
                        val vs = validationStamp("test")
                        build {
                            setProperty(this, BuildGitHubWorkflowRunPropertyType::class.java,
                                BuildGitHubWorkflowRunProperty(
                                    workflows = listOf(
                                        BuildGitHubWorkflowRun(
                                            runId = 10,
                                            url = "",
                                            name = "some-workflow",
                                            runNumber = 1,
                                            running = true,
                                            event = "push",
                                        )
                                    )
                                ))
                            // Existing validation (without any data)
                            val existing = validate(vs)
                            // Setting the data
                            run("""
                                mutation {
                                    gitHubIngestionValidateDataByRunId(input: {
                                        owner: "nemerosa",
                                        repository: "${project.name}",
                                        validation: "test",
                                        validationData: {
                                            type: "${TestNumberValidationDataType::class.java.name}",
                                            data: {
                                                value: 50
                                            }
                                        },
                                        validationStatus: "PASSED",
                                        runId: 10,
                                    }) {
                                        errors {
                                            message
                                            exception
                                            location
                                        }
                                    }
                                }
                            """) { data ->
                                checkGraphQLUserErrors(data, "gitHubIngestionValidateDataByRunId")
                                // Checks the build has been validated
                                val run = structureService.getValidationRunsForBuildAndValidationStamp(
                                    buildId = id,
                                    validationStampId = vs.id,
                                    offset = 0,
                                    count = 1,
                                ).firstOrNull()
                                assertNotNull(run, "Validation run created") {
                                    assertEquals(existing.id, it.id, "Existing validation run kept")
                                    assertEquals(
                                        ValidationRunStatusID.PASSED,
                                        it.lastStatusId
                                    )
                                    val runData = it.data?.data
                                    assertEquals(50, runData, "Validation run data has been set")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Existing validation run, same status, new data - the run and its evidence are kept`() {
        withValidatedBuild { vs ->
            val existing = lastRun(vs)
            val evidence = attachEvidence(existing)
            val seq = lastSeq()

            ingestValidationData(value = 200)

            assertEquals(1, runs(vs).size, "No new validation run")
            val run = lastRun(vs)
            assertEquals(existing.id, run.id, "Existing validation run kept")
            assertEquals(ValidationRunStatusID.PASSED, run.lastStatusId)
            assertEquals(200, run.data?.data, "Validation run data updated")
            assertEquals(
                listOf(evidence.id to null),
                asAdmin { evidenceService.getEvidences(run) }.map { it.id to it.deletedAt },
                "Evidence kept",
            )
            assertEquals(
                listOf(TrailEntryTypes.VALIDATION_DATA),
                trailAfter(seq).map { it.type },
            )
        }
    }

    @Test
    fun `Existing validation run, different status - a new run, the previous one keeps its evidence`() {
        withValidatedBuild { vs ->
            val existing = lastRun(vs)
            asAdmin {
                runInfoService.setRunInfo(
                    existing,
                    RunInfoInput(sourceType = "github", sourceUri = "https://github.com/nemerosa/run/10", runTime = 30)
                )
            }
            val evidence = attachEvidence(existing)
            val seq = lastSeq()

            ingestValidationData(value = 50)

            assertEquals(2, runs(vs).size, "New validation run")
            val previous = asAdmin { structureService.getValidationRun(existing.id) }
            assertEquals(ValidationRunStatusID.PASSED, previous.lastStatusId, "Previous run keeps its status")
            assertEquals(150, previous.data?.data, "Previous run keeps its data")
            assertEquals(
                listOf(evidence.id to null),
                asAdmin { evidenceService.getEvidences(previous) }.map { it.id to it.deletedAt },
                "Previous run keeps its evidence",
            )
            val run = lastRun(vs)
            assertTrue(run.id() > existing.id(), "New run is the latest")
            assertEquals(ValidationRunStatusID.FAILED, run.lastStatusId)
            assertEquals(50, run.data?.data)
            val runInfo = asAdmin { runInfoService.getRunInfo(run) }
            assertEquals("https://github.com/nemerosa/run/10", runInfo?.sourceUri, "Run info copied")
            assertEquals(30, runInfo?.runTime, "Run info copied")
            val types = trailAfter(seq).map { it.type }
            assertTrue(TrailEntryTypes.EVIDENCE_DELETED !in types, "No evidence deleted: $types")
            assertTrue(TrailEntryTypes.VALIDATION_DELETED !in types, "No validation deleted: $types")
        }
    }

    @Test
    fun `Existing validation run, identical data and status - nothing changes`() {
        withValidatedBuild { vs ->
            val existing = lastRun(vs)
            val seq = lastSeq()

            ingestValidationData(value = 150)

            assertEquals(listOf(existing.id), runs(vs).map { it.id }, "Same validation run")
            assertEquals(150, lastRun(vs).data?.data)
            assertEquals(emptyList(), trailAfter(seq).map { it.type }, "No trail entry")
        }
    }

    @Test
    fun `Existing validation run triaged by a user, identical data - the triage is kept`() {
        withValidatedBuild(value = 50) { vs ->
            val existing = lastRun(vs)
            assertEquals(ValidationRunStatusID.FAILED, existing.lastStatusId)
            asAdmin {
                structureService.newValidationRunStatus(
                    existing,
                    ValidationRunStatus(
                        ID.NONE,
                        Signature.of("reviewer"),
                        ValidationRunStatusID.STATUS_INVESTIGATING,
                        "Looking into it",
                    )
                )
            }
            val seq = lastSeq()

            ingestValidationData(value = 50)

            assertEquals(listOf(existing.id), runs(vs).map { it.id }, "Same validation run")
            assertEquals(ValidationRunStatusID.INVESTIGATING, lastRun(vs).lastStatusId, "Triage kept")
            assertEquals(emptyList(), trailAfter(seq).map { it.type }, "No trail entry")
        }
    }

    @Test
    fun `Existing validation run, data of another type - the run is unchanged`() {
        withValidatedBuild { vs ->
            val existing = lastRun(vs)
            val seq = lastSeq()

            val payload = ingestValidationData(
                type = TestValidationDataType::class.java.name,
                data = "{ critical: 0, high: 0, medium: 0 }",
            )

            assertEquals(IngestionHookPayloadStatus.ERRORED, payload.status, "Step failed")

            assertEquals(listOf(existing.id), runs(vs).map { it.id }, "Same validation run")
            val run = lastRun(vs)
            assertEquals(ValidationRunStatusID.PASSED, run.lastStatusId)
            assertEquals(150, run.data?.data, "Data unchanged")
            assertEquals(emptyList(), trailAfter(seq).map { it.type }, "No trail entry")
        }
    }

    /**
     * A build bound to the GitHub run 10, whose stamp, configured with a threshold of 100, has
     * already been validated through the ingestion with the [value] — 150 by default, a passed run.
     */
    private fun withValidatedBuild(value: Int = 150, code: Build.(vs: ValidationStamp) -> Unit) {
        asAdmin {
            withGitHubIngestionSettings {
                project {
                    branch {
                        val vs = validationStamp(
                            name = "test",
                            validationDataTypeConfig = testNumberValidationDataType.config(100)
                        )
                        build {
                            setProperty(
                                this, BuildGitHubWorkflowRunPropertyType::class.java,
                                BuildGitHubWorkflowRunProperty(
                                    workflows = listOf(
                                        BuildGitHubWorkflowRun(
                                            runId = 10,
                                            url = "",
                                            name = "some-workflow",
                                            runNumber = 1,
                                            running = true,
                                            event = "push",
                                        )
                                    )
                                )
                            )
                            ingestValidationData(value = value)
                            code(vs)
                        }
                    }
                }
            }
        }
    }

    private fun Build.ingestValidationData(
        value: Int = 0,
        type: String = TestNumberValidationDataType::class.java.name,
        data: String = "{ value: $value }",
    ): IngestionHookPayload {
        val result = run(
            """
                mutation {
                    gitHubIngestionValidateDataByRunId(input: {
                        owner: "nemerosa",
                        repository: "${project.name}",
                        validation: "test",
                        validationData: {
                            type: "$type",
                            data: $data
                        },
                        runId: 10,
                    }) {
                        payload {
                            uuid
                        }
                        errors {
                            message
                            exception
                            location
                        }
                    }
                }
            """
        )
        val uuid = checkGraphQLUserErrors(result, "gitHubIngestionValidateDataByRunId")
            .path("payload").path("uuid").asText()
        return assertNotNull(ingestionHookPayloadStorage.findByUUID(uuid), "Ingestion payload")
    }

    private fun Build.runs(vs: ValidationStamp): List<ValidationRun> = asAdmin {
        structureService.getValidationRunsForBuildAndValidationStamp(
            buildId = id,
            validationStampId = vs.id,
            offset = 0,
            count = 10,
        )
    }

    private fun Build.lastRun(vs: ValidationStamp): ValidationRun =
        assertNotNull(runs(vs).firstOrNull(), "Validation run")

    private fun attachEvidence(run: ValidationRun): Evidence = asAdmin {
        val content = "%PDF-1.7\n% ${uid("pdf-")}\n".toByteArray()
        evidenceService.attach(
            run,
            EvidenceUpload(
                fileName = "report.pdf",
                mediaType = "application/pdf",
                size = content.size.toLong(),
                content = { content.inputStream() },
            )
        )
    }

    private fun Build.trailAfter(seq: Int): List<TrailEntry> =
        asAdmin { trailService.getEntries(this) }.filter { it.seq > seq }

    private fun Build.lastSeq(): Int = asAdmin { trailService.getEntries(this) }.last().seq

}