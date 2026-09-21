package net.nemerosa.ontrack.graphql

import tools.jackson.databind.node.StringNode
import net.nemerosa.ontrack.extension.general.ReleaseProperty
import net.nemerosa.ontrack.extension.general.ReleasePropertyType
import net.nemerosa.ontrack.extension.general.validation.TestSummaryValidationConfig
import net.nemerosa.ontrack.extension.general.validation.TestSummaryValidationDataType
import net.nemerosa.ontrack.extension.general.validation.TextValidationDataType
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.json.isNullOrNullNode
import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.NameDescription
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID
import net.nemerosa.ontrack.model.structure.config
import net.nemerosa.ontrack.model.structure.data
import net.nemerosa.ontrack.test.assertIs
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@AsAdminTest
class ValidationRunGraphQLIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var textValidationDataType: TextValidationDataType

    @Autowired
    private lateinit var testSummaryValidationDataType: TestSummaryValidationDataType

    @Test
    fun `Creating a validation run with missing status`() {
        project {
            branch {
                val vs = validationStamp()
                build {
                    val data = run(
                        """
                        mutation CreateValidationRun {
                            createValidationRun(input: {
                                project: "${project.name}",
                                branch: "${branch.name}",
                                build: "$name",
                                validationStamp: "${vs.name}"
                            }) {
                                validationRun {
                                    validationRunStatuses {
                                        statusID {
                                            id
                                        }
                                    }
                                }
                                errors {
                                    message
                                    exception
                                }
                            }
                        }
                    """
                    )
                    // Checks the errors
                    assertUserError(
                        data, "createValidationRun",
                        message = "Validation Run Status is required because no data is provided.",
                        exception = "net.nemerosa.ontrack.model.exceptions.ValidationRunDataStatusRequiredBecauseNoDataException"
                    )
                    assertTrue(
                        data["createValidationRun"]["validationRun"].isNullOrNullNode(),
                        "Validation run not returned"
                    )
                }
            }
        }
    }

    @Test
    fun `Creating a validation run at a given time`() {
        project {
            branch {
                val vs = validationStamp()
                build {
                    val time = LocalDateTime.of(2024, 3, 14, 9, 30)
                    val data = run(
                        """
                        mutation CreateValidationRun {
                            createValidationRunById(input: {
                                buildId: $id,
                                validationStamp: "${vs.name}",
                                validationRunStatus: "PASSED",
                                dateTime: "2024-03-14T09:30:00"
                            }) {
                                validationRun {
                                    id
                                }
                            }
                        }
                    """
                    )
                    val id = assertNoUserError(data, "createValidationRunById")
                        .path("validationRun").path("id").asInt()
                    // `ValidationRun.signature` reads through to the last status, so the status
                    // is the one thing to assert - the run has no time of its own to differ from it.
                    val run = structureService.getValidationRun(ID.of(id))
                    assertEquals(time, run.lastStatus.signature.time)
                }
            }
        }
    }

    @Test
    fun `Creating a validation run without a time stamps it with the current time`() {
        project {
            branch {
                val vs = validationStamp()
                build {
                    val before = Time.now()
                    val data = run(
                        """
                        mutation CreateValidationRun {
                            createValidationRunById(input: {
                                buildId: $id,
                                validationStamp: "${vs.name}",
                                validationRunStatus: "PASSED"
                            }) {
                                validationRun {
                                    id
                                }
                            }
                        }
                    """
                    )
                    val id = assertNoUserError(data, "createValidationRunById")
                        .path("validationRun").path("id").asInt()
                    val run = structureService.getValidationRun(ID.of(id))
                    assertTrue(
                        !run.signature.time.isBefore(before.minusMinutes(1)),
                        "Validation run is stamped with the current time",
                    )
                }
            }
        }
    }

    @Test
    fun `Creating a PASSED validation run`() {
        project {
            branch {
                val vs = validationStamp()
                build {
                    val data = run(
                        """
                        mutation CreateValidationRun {
                            createValidationRunById(input: {
                                buildId: $id,
                                validationStamp: "${vs.name}",
                                validationRunStatus: "PASSED"
                            }) {
                                validationRun {
                                    validationRunStatuses {
                                        statusID {
                                            id
                                        }
                                    }
                                }
                            }
                        }
                    """
                    )
                    assertNoUserError(data, "createValidationRunById").let { node ->
                        assertEquals(
                            "PASSED",
                            node.path("validationRun")
                                .path("validationRunStatuses").path(0)
                                .path("statusID").path("id").asText()
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `Creating a PASSED validation run using build names`() {
        project {
            branch {
                val vs = validationStamp()
                build {
                    val data = run(
                        """
                        mutation CreateValidationRun {
                            createValidationRun(input: {
                                project: "${project.name}",
                                branch: "${branch.name}",
                                build: "$name",
                                validationStamp: "${vs.name}",
                                validationRunStatus: "PASSED"
                            }) {
                                validationRun {
                                    validationRunStatuses {
                                        statusID {
                                            id
                                        }
                                    }
                                }
                                errors {
                                    message
                                    exception
                                }
                            }
                        }
                    """
                    )
                    val node = assertNoUserError(data, "createValidationRun")
                    assertEquals(
                        "PASSED",
                        node.path("validationRun")
                            .path("validationRunStatuses").path(0)
                            .path("statusID").path("id").asText()
                    )
                }
            }
        }
    }

    @Test
    fun `Creating a validation run with data`() {
        project {
            branch {
                val vs = validationStamp(
                    validationDataTypeConfig = testSummaryValidationDataType.config(
                        TestSummaryValidationConfig(warningIfSkipped = false)
                    )
                )
                build {
                    val data = run(
                        """
                        mutation CreateValidationRun {
                            createValidationRunById(input: {
                                buildId: $id,
                                validationStamp: "${vs.name}",
                                dataTypeId: "${TestSummaryValidationDataType::class.java.name}",
                                data: { passed: 13, skipped: 8, failed: 5 }
                            }) {
                                validationRun {
                                    data {
                                        descriptor {
                                            id
                                        }
                                        data
                                    }
                                    validationRunStatuses {
                                        statusID {
                                            id
                                        }
                                    }
                                }
                                errors {
                                    message
                                }
                            }
                        }
                    """
                    )
                    val node = assertNoUserError(data, "createValidationRunById")
                    assertEquals(0, node.path("errors").size())
                    assertEquals(
                        TestSummaryValidationDataType::class.java.name,
                        node.path("validationRun").path("data").path("descriptor").path("id").asText()
                    )
                    val runData = node.path("validationRun").path("data").path("data")
                    assertEquals(
                        13,
                        runData.path("passed").asInt()
                    )
                    assertEquals(
                        8,
                        runData.path("skipped").asInt()
                    )
                    assertEquals(
                        5,
                        runData.path("failed").asInt()
                    )
                    assertEquals(
                        "FAILED",
                        node.path("validationRun").path("validationRunStatuses").path(0).path("statusID").path("id")
                            .asText()
                    )
                }
            }
        }
    }

    @Test
    fun `Creating a validation run with data warning`() {
        project {
            branch {
                val vs = validationStamp(
                    validationDataTypeConfig = testSummaryValidationDataType.config(
                        TestSummaryValidationConfig(warningIfSkipped = true)
                    )
                )
                build {
                    val data = run(
                        """
                        mutation CreateValidationRun {
                            createValidationRunById(input: {
                                buildId: $id,
                                validationStamp: "${vs.name}",
                                dataTypeId: "${TestSummaryValidationDataType::class.java.name}",
                                data: { passed: 13, skipped: 8, failed: 0 }
                            }) {
                                validationRun {
                                    data {
                                        descriptor {
                                            id
                                        }
                                        data
                                    }
                                    validationRunStatuses {
                                        statusID {
                                            id
                                        }
                                    }
                                }
                                errors {
                                    message
                                }
                            }
                        }
                    """
                    )
                    val node = assertNoUserError(data, "createValidationRunById")
                    assertEquals(0, node.path("errors").size())
                    assertEquals(
                        TestSummaryValidationDataType::class.java.name,
                        node.path("validationRun").path("data").path("descriptor").path("id").asText()
                    )
                    val runData = node.path("validationRun").path("data").path("data")
                    assertEquals(
                        13,
                        runData.path("passed").asInt()
                    )
                    assertEquals(
                        8,
                        runData.path("skipped").asInt()
                    )
                    assertEquals(
                        0,
                        runData.path("failed").asInt()
                    )
                    assertEquals(
                        "WARNING",
                        node.path("validationRun").path("validationRunStatuses").path(0).path("statusID").path("id")
                            .asText()
                    )
                }
            }
        }
    }

    @Test
    fun `Validation run status with links in description`() {
        project {
            branch {
                val vs = validationStamp()
                build {
                    val run = validate(
                        validationStamp = vs,
                        validationRunStatusID = ValidationRunStatusID.STATUS_FAILED,
                        description = "Some text"
                    ).apply {
                        validationStatus(ValidationRunStatusID.STATUS_DEFECTIVE, "See https://issues/browser/ONT-1234")
                    }
                    val data = run(
                        """{
                        validationRuns(id: ${run.id}) {
                            validationRunStatuses {
                                annotatedDescription
                            }
                        }
                    }"""
                    )
                    val descriptions =
                        data["validationRuns"][0]["validationRunStatuses"].values().map { it["annotatedDescription"].asText() }
                    assertEquals(
                        listOf(
                            """See <a href="https://issues/browser/ONT-1234" target="_blank">https://issues/browser/ONT-1234</a>""",
                            "Some text"
                        ),
                        descriptions
                    )
                }
            }
        }
    }

    @Test
    fun `Validation run statuses for a validation run`() {
        project {
            branch {
                val vs = validationStamp("VS")
                build("1") {
                    val run = validate(
                        validationStamp = vs,
                        validationRunStatusID = ValidationRunStatusID.STATUS_FAILED,
                        description = "Validation failed"
                    ).apply {
                        validationStatus(ValidationRunStatusID.STATUS_INVESTIGATING, "Investigating")
                        validationStatus(ValidationRunStatusID.STATUS_EXPLAINED, "Explained")
                    }
                    val data = run(
                        """{
                        validationRuns(id: ${run.id}) {
                            validationRunStatuses {
                                statusID {
                                    id
                                }
                                description
                            }
                        }
                    }"""
                    )
                    val validationRunStatuses = data["validationRuns"][0]["validationRunStatuses"]
                    assertEquals(
                        listOf("EXPLAINED", "INVESTIGATING", "FAILED"),
                        validationRunStatuses.values().map { it["statusID"]["id"].asText() }
                    )
                    assertEquals(
                        listOf("Explained", "Investigating", "Validation failed"),
                        validationRunStatuses.values().map { it["description"].asText() }
                    )
                }
            }
        }
    }

    @Test
    fun `Validation run reference to build and validation stamp`() {
        project {
            branch {
                val vs = validationStamp("VS")
                build("1") {
                    val run = validate(vs, ValidationRunStatusID.STATUS_PASSED)
                    val data = run(
                        """{
                        validationRuns(id: ${run.id}) {
                            build {
                                id
                            }
                            validationStamp {
                                id
                                branch {
                                    id
                                    project {
                                        id
                                    }
                                }
                            }
                        }
                    }"""
                    )
                    val v = data["validationRuns"].first()
                    assertEquals(this.id(), v["build"]["id"].asInt())
                    assertEquals(vs.id(), v["validationStamp"]["id"].asInt())
                    assertEquals(this@branch.id(), v["validationStamp"]["branch"]["id"].asInt())
                    assertEquals(this@project.id(), v["validationStamp"]["branch"]["project"]["id"].asInt())
                }
            }
        }
    }

    @Test
    fun `Text validation data type`() {
        val vs = doCreateValidationStamp(textValidationDataType.config(null))
        val build = doCreateBuild(vs.branch, NameDescription.nd("1", ""))
        val run = doValidateBuild(
            build,
            vs,
            ValidationRunStatusID.STATUS_PASSED,
            textValidationDataType.data("Some text")
        )
        // Checks the data
        assertEquals("Some text", run.data?.data)

        // Performs a query
        val data = asUser().withView(vs).call {
            run(
                """
                {
                    validationRuns(id: ${run.id}) {
                        data {
                            descriptor {
                                id
                                feature {
                                    id
                                }
                            }
                            data
                        }
                    }
                }
            """.trimIndent()
            )
        }

        // Gets the data
        val runData = data["validationRuns"][0]["data"]["data"]
        assertIs<StringNode>(runData) {
            assertEquals("Some text", it.asText())
        }
    }

    @Test
    fun `Creating a validation run with some run info`() {
        project {
            branch {
                val vs = validationStamp()
                build {
                    val data = run(
                        """
                        mutation CreateValidationRun {
                            createValidationRunById(input: {
                                buildId: $id,
                                validationStamp: "${vs.name}",
                                validationRunStatus: "PASSED",
                                runInfo: {
                                    sourceType: "github",
                                    sourceUri: "url-to-github",
                                    triggerType: "push",
                                    runTime: 14
                                }
                            }) {
                                validationRun {
                                    runInfo {
                                        sourceType
                                        sourceUri
                                        triggerType
                                        runTime
                                    }
                                }
                                errors {
                                    message
                                }
                            }
                        }
                    """
                    )
                    val node = assertNoUserError(data, "createValidationRunById")
                    val runInfo = node.path("validationRun").path("runInfo")
                    assertEquals("github", runInfo.path("sourceType").asText())
                    assertEquals("url-to-github", runInfo.path("sourceUri").asText())
                    assertEquals("push", runInfo.path("triggerType").asText())
                    assertEquals(14, runInfo.path("runTime").asInt())
                }
            }
        }
    }

    @Test
    fun `Creating a validation run by name with some run info`() {
        project {
            branch {
                val vs = validationStamp()
                build {
                    val data = run(
                        """
                        mutation CreateValidationRun {
                            createValidationRun(input: {
                                project: "${project.name}",
                                branch: "${branch.name}",
                                build: "$name",
                                validationStamp: "${vs.name}",
                                validationRunStatus: "PASSED",
                                runInfo: {
                                    sourceType: "github",
                                    sourceUri: "url-to-github",
                                    triggerType: "push",
                                    runTime: 14
                                }
                            }) {
                                validationRun {
                                    runInfo {
                                        sourceType
                                        sourceUri
                                        triggerType
                                        runTime
                                    }
                                }
                                errors {
                                    message
                                }
                            }
                        }
                    """
                    )
                    val node = assertNoUserError(data, "createValidationRun")
                    val runInfo = node.path("validationRun").path("runInfo")
                    assertEquals("github", runInfo.path("sourceType").asText())
                    assertEquals("url-to-github", runInfo.path("sourceUri").asText())
                    assertEquals("push", runInfo.path("triggerType").asText())
                    assertEquals(14, runInfo.path("runTime").asInt())
                }
            }
        }
    }

    @Test
    fun `Creating a validation run using a release label`() {
        project {
            branch {
                val vs = validationStamp()
                build {
                    setProperty(this, ReleasePropertyType::class.java, ReleaseProperty("1.2"))
                    val data = run(
                        """
                        mutation CreateValidationRun {
                            createValidationRunByRelease(input: {
                                project: "${project.name}",
                                buildRelease: "1.2",
                                validationStamp: "${vs.name}",
                                validationRunStatus: "PASSED"
                            }) {
                                errors {
                                    message
                                }
                            }
                        }
                    """
                    )
                    assertNoUserError(data, "createValidationRunByRelease")
                    val runs = structureService.getValidationRunsForBuildAndValidationStamp(
                        buildId = id,
                        validationStampId = vs.id,
                        offset = 0,
                        count = 10
                    )
                    assertEquals(1, runs.size)
                    val run = runs.first()
                    assertEquals(
                        ValidationRunStatusID.PASSED,
                        run.lastStatus.statusID.id
                    )
                }
            }
        }
    }

    @Test
    fun `Creating a validation run using a release label when not found`() {
        project {
            branch {
                val vs = validationStamp()
                build {
                    // setProperty(this, ReleasePropertyType::class.java, ReleaseProperty("1.2"))
                    val data = run(
                        """
                        mutation CreateValidationRun {
                            createValidationRunByRelease(input: {
                                project: "${project.name}",
                                buildRelease: "1.2",
                                validationStamp: "${vs.name}",
                                validationRunStatus: "PASSED"
                            }) {
                                errors {
                                    message
                                    exception
                                }
                            }
                        }
                    """
                    )
                    assertUserError(
                        data, "createValidationRunByRelease",
                        message = """Could not find build with release "1.2" in ${project.name}.""",
                        exception = null
                    )
                }
            }
        }
    }

}