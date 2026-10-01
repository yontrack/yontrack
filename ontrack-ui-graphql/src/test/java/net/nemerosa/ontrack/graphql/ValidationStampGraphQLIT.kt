package net.nemerosa.ontrack.graphql

import com.fasterxml.jackson.databind.JsonNode
//import net.nemerosa.ontrack.extension.general.validation.*
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.structure.ValidationRun
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID
import net.nemerosa.ontrack.test.TestUtils.uid
import net.nemerosa.ontrack.test.assertNotPresent
import net.nemerosa.ontrack.test.assertPresent
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Integration tests around the `validationStamp` root query.
 */
@AsAdminTest
class ValidationStampGraphQLIT : AbstractQLKTITSupport() {

//    @Autowired
//    private lateinit var chmlValidationDataType: CHMLValidationDataType
//
//    @Autowired
//    private lateinit var testSummaryValidationDataType: TestSummaryValidationDataType

    @Test
    fun `Creation of a plain validation stamp`() {
        asAdmin {
            project {
                branch {
                    run(
                        """
                        mutation {
                            setupValidationStamp(input: {
                                project: "${project.name}",
                                branch: "$name",
                                validation: "test"
                            }) {
                                validationStamp {
                                    id
                                }
                                errors {
                                    message
                                }
                            }
                        }
                    """
                    ).let { data ->
                        val node = assertNoUserError(data, "setupValidationStamp")
                        assertTrue(node.path("validationStamp").path("id").asInt() != 0, "VS created")

                        assertPresent(structureService.findValidationStampByName(project.name, name, "test")) {
                            assertEquals("test", it.name)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Update of a plain validation stamp`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp()
                    run(
                        """
                        mutation {
                            setupValidationStamp(input: {
                                project: "${project.name}",
                                branch: "$name",
                                validation: "${vs.name}",
                                description: "New description"
                            }) {
                                validationStamp {
                                    id
                                }
                                errors {
                                    message
                                }
                            }
                        }
                    """
                    ).let { data ->
                        val node = assertNoUserError(data, "setupValidationStamp")
                        assertEquals(vs.id(), node.path("validationStamp").path("id").asInt(), "VS updated")

                        assertPresent(structureService.findValidationStampByName(project.name, name, vs.name)) {
                            assertEquals(vs.name, it.name)
                            assertEquals("New description", it.description)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Updating a validation stamp using the update mutation`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp()
                    run(
                        """
                        mutation {
                            updateValidationStampById(input: {
                                id: ${vs.id},
                                description: "New description"
                            }) {
                                validationStamp {
                                    id
                                }
                                errors {
                                    message
                                }
                            }
                        }
                    """
                    ).let { data ->
                        val node = assertNoUserError(data, "updateValidationStampById")
                        assertEquals(vs.id(), node.path("validationStamp").path("id").asInt(), "VS updated")

                        assertPresent(structureService.findValidationStampByName(project.name, name, vs.name)) {
                            assertEquals(vs.name, it.name)
                            assertEquals("New description", it.description)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Deleting a validation stamp`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp()
                    run(
                        """
                        mutation {
                            deleteValidationStampById(input: {
                                id: ${vs.id}
                            }) {
                                errors {
                                    message
                                }
                            }
                        }
                    """
                    ).let { data ->
                        assertNoUserError(data, "deleteValidationStampById")
                        assertNotPresent(structureService.findValidationStampByName(project.name, name, vs.name))
                    }
                }
            }
        }
    }

    @Test
    @Disabled("Missing General extension")
    fun `Bulk update of a validation stamp`() {
//        asAdmin {
//            project {
//                val vsName = uid("vs-")
//                val otherBranch = branch {
//                    validationStamp(name = vsName)
//                }
//                branch {
//                    val vs = validationStamp(
//                        name = vsName,
//                        description = "New version",
//                        validationDataTypeConfig = chmlValidationDataType.config(
//                            CHMLValidationDataTypeConfig(
//                                warningLevel = CHMLLevel(CHML.HIGH, 10),
//                                failedLevel = CHMLLevel(CHML.CRITICAL, 1)
//                            )
//                        ),
//                    )
//                    run(
//                        """
//                            mutation {
//                                bulkUpdateValidationStampById(input: {
//                                    id: ${vs.id}
//                                }) {
//                                    errors {
//                                        message
//                                    }
//                                }
//                            }
//                        """
//                    ).let { data ->
//                        assertNoUserError(data, "bulkUpdateValidationStampById")
//                        assertPresent(structureService.findValidationStampByName(project.name, otherBranch.name, vsName)) {
//                            assertEquals(vs.name, it.name)
//                            assertEquals(vs.description, it.description)
//                            assertEquals(
//                                "net.nemerosa.ontrack.extension.general.validation.CHMLValidationDataType",
//                                it.dataType?.descriptor?.id
//                            )
//                            assertEquals(
//                                CHMLValidationDataTypeConfig(
//                                    warningLevel = CHMLLevel(CHML.HIGH, 10),
//                                    failedLevel = CHMLLevel(CHML.CRITICAL, 1)
//                                ),
//                                it.dataType?.config
//                            )
//                        }
//                    }
//                }
//            }
//        }
    }

    @Test
    @Disabled("Missing General extension")
    fun `Update a validation stamp after it has been provisioned from a predefined stamp`() {
//        asAdmin {
//            val vsName = uid("vs_")
//            val description = "Description for $vsName"
//            project {
//                branch {
//                    // Predefined validation stamp of type "test summary"
//                    predefinedValidationStampService.newPredefinedValidationStamp(
//                        PredefinedValidationStamp.of(NameDescription.nd(vsName, description))
//                            .withDataType(
//                                testSummaryValidationDataType.config(
//                                    TestSummaryValidationConfig(warningIfSkipped = false)
//                                )
//                            )
//                    )
//                    // Using GraphQL to setup a validation stamp based on this
//                    // Doing that twice since we want an update to keep the predefined attributes
//                    repeat(2) {
//                        run(
//                            """
//                                mutation {
//                                    setupValidationStamp(input: {
//                                        project: "${project.name}",
//                                        branch: "$name",
//                                        validation: "$vsName",
//                                        description: ""
//                                    }) {
//                                        validationStamp {
//                                            id
//                                        }
//                                        errors {
//                                            message
//                                        }
//                                    }
//                                }
//                            """
//                        ) { data ->
//                            val node = assertNoUserError(data, "setupValidationStamp")
//                            // Checks the validation stamp has the predefined attributes
//                            val id = node.path("validationStamp").path("id").asInt()
//                            val vs = structureService.getValidationStamp(ID.of(id))
//                            assertEquals(vsName, vs.name)
//                            assertEquals(description, vs.description)
//                            assertNotNull(vs.dataType, "Data type is set") {
//                                assertNotNull("test-summary", it.descriptor.id)
//                                val config = assertIs<TestSummaryValidationConfig>(it.config)
//                                assertFalse(config.warningIfSkipped)
//                            }
//                        }
//                    }
//                }
//            }
//        }
    }

    @Test
    @Disabled("Missing General extension")
    fun `Creation of a CHML validation stamp`() {
//        asAdmin {
//            project {
//                branch {
//                    run(
//                        """
//                        mutation {
//                            setupValidationStamp(input: {
//                                project: "${project.name}",
//                                branch: "$name",
//                                validation: "test",
//                                dataType: "net.nemerosa.ontrack.extension.general.validation.CHMLValidationDataType",
//                                dataTypeConfig: {
//                                    warningLevel: "HIGH",
//                                    warningValue: 1,
//                                    failedLevel: "CRITICAL",
//                                    failedValue: 1
//                                }
//                            }) {
//                                validationStamp {
//                                    id
//                                }
//                                errors {
//                                    message
//                                }
//                            }
//                        }
//                    """
//                    ).let { data ->
//                        val node = assertNoUserError(data, "setupValidationStamp")
//                        assertTrue(node.path("validationStamp").path("id").asInt() != 0, "VS created")
//
//                        assertPresent(structureService.findValidationStampByName(project.name, name, "test")) {
//                            assertEquals("test", it.name)
//                            assertEquals(
//                                "net.nemerosa.ontrack.extension.general.validation.CHMLValidationDataType",
//                                it.dataType?.descriptor?.id
//                            )
//                            assertEquals(
//                                CHMLValidationDataTypeConfig(
//                                    warningLevel = CHMLLevel(CHML.HIGH, 1),
//                                    failedLevel = CHMLLevel(CHML.CRITICAL, 1)
//                                ),
//                                it.dataType?.config
//                            )
//                        }
//                    }
//                }
//            }
//        }
    }

    @Test
    @Disabled("Missing General extension")
    fun `Update of a CHML validation stamp`() {
//        asAdmin {
//            project {
//                branch {
//                    val vs = validationStamp(
//                        validationDataTypeConfig = chmlValidationDataType.config(
//                            CHMLValidationDataTypeConfig(
//                                warningLevel = CHMLLevel(CHML.CRITICAL, 1),
//                                failedLevel = CHMLLevel(CHML.CRITICAL, 10)
//                            )
//                        )
//                    )
//                    run(
//                        """
//                        mutation {
//                            setupValidationStamp(input: {
//                                project: "${project.name}",
//                                branch: "$name",
//                                validation: "${vs.name}",
//                                dataType: "net.nemerosa.ontrack.extension.general.validation.CHMLValidationDataType",
//                                dataTypeConfig: {
//                                    warningLevel: "HIGH",
//                                    warningValue: 1,
//                                    failedLevel: "CRITICAL",
//                                    failedValue: 1
//                                }
//                            }) {
//                                validationStamp {
//                                    id
//                                }
//                                errors {
//                                    message
//                                }
//                            }
//                        }
//                    """
//                    ).let { data ->
//                        val node = assertNoUserError(data, "setupValidationStamp")
//                        assertEquals(vs.id(), node.path("validationStamp").path("id").asInt(), "VS updated")
//
//                        assertPresent(structureService.findValidationStampByName(project.name, name, vs.name)) {
//                            assertEquals(vs.name, it.name)
//                            assertEquals(
//                                "net.nemerosa.ontrack.extension.general.validation.CHMLValidationDataType",
//                                it.dataType?.descriptor?.id
//                            )
//                            assertEquals(
//                                CHMLValidationDataTypeConfig(
//                                    warningLevel = CHMLLevel(CHML.HIGH, 1),
//                                    failedLevel = CHMLLevel(CHML.CRITICAL, 1)
//                                ),
//                                it.dataType?.config
//                            )
//                        }
//                    }
//                }
//            }
//        }
    }

    @Test
    fun `Creation of a validation stamp based on a plain predefined one`() {
        asAdmin {
            val vsName = uid("pvs")
            predefinedValidationStamp(vsName, "Predefined")
            project {
                branch {
                    run(
                        """
                        mutation {
                            setupValidationStamp(input: {
                                project: "${project.name}",
                                branch: "$name",
                                validation: "$vsName"
                            }) {
                                validationStamp {
                                    id
                                }
                                errors {
                                    message
                                }
                            }
                        }
                    """
                    ).let { data ->
                        val node = assertNoUserError(data, "setupValidationStamp")
                        assertTrue(node.path("validationStamp").path("id").asInt() != 0, "VS created")

                        assertPresent(structureService.findValidationStampByName(project.name, name, vsName)) {
                            assertEquals(vsName, it.name)
                            assertEquals("Predefined", it.description)
                        }
                    }
                }
            }
        }
    }

    @Test
    @Disabled("Missing General extension")
    fun `Creation of a validation stamp based on a typed predefined one`() {
//        asAdmin {
//            val vsName = uid("pvs")
//            predefinedValidationStamp(
//                vsName, "Predefined", dataType = chmlValidationDataType.config(
//                    CHMLValidationDataTypeConfig(
//                        warningLevel = CHMLLevel(CHML.CRITICAL, 1),
//                        failedLevel = CHMLLevel(CHML.CRITICAL, 10)
//                    )
//                )
//            )
//            project {
//                branch {
//                    run(
//                        """
//                        mutation {
//                            setupValidationStamp(input: {
//                                project: "${project.name}",
//                                branch: "$name",
//                                validation: "$vsName"
//                            }) {
//                                validationStamp {
//                                    id
//                                }
//                                errors {
//                                    message
//                                }
//                            }
//                        }
//                    """
//                    ).let { data ->
//                        val node = assertNoUserError(data, "setupValidationStamp")
//                        assertTrue(node.path("validationStamp").path("id").asInt() != 0, "VS created")
//
//                        assertPresent(structureService.findValidationStampByName(project.name, name, vsName)) {
//                            assertEquals(vsName, it.name)
//                            assertEquals("Predefined", it.description)
//                            assertEquals(
//                                "net.nemerosa.ontrack.extension.general.validation.CHMLValidationDataType",
//                                it.dataType?.descriptor?.id
//                            )
//                            assertEquals(
//                                CHMLValidationDataTypeConfig(
//                                    warningLevel = CHMLLevel(CHML.CRITICAL, 1),
//                                    failedLevel = CHMLLevel(CHML.CRITICAL, 10)
//                                ),
//                                it.dataType?.config
//                            )
//                        }
//                    }
//                }
//            }
//        }
    }

    @Test
    fun `No validation stamp`() {
        // Creates a VS and deletes it
        val vsId = project<Int> {
            branch<Int> {
                val vs = validationStamp()
                vs.delete()
                vs.id()
            }
        }
        val data = run(
            """{
            validationStamp(id: $vsId) {
                name
            }
        }"""
        )

        val vs = data["validationStamp"]
        assertTrue(vs.isNull, "No validation stamp")
    }


    @Test
    fun `Validation stamp by ID`() {
        val vs = doCreateValidationStamp()
        val data = run(
            """{
            validationStamp(id: ${vs.id}) {
                name
            }
        }"""
        )

        val name = data["validationStamp"]["name"].asText()
        assertEquals(vs.name, name)
    }

    /**
     * The `passed` filter, sent as a variable the way the UI sends it, keeps the runs whose
     * **current** status is passed or not, and the page info counts those runs only (#1712).
     *
     * ```
     * build 1   a1  FAILED -> PASSED     (passed now)
     * build 2   b2  PASSED -> DEFECTIVE  (not passed now)
     * build 3   c3  PASSED -> FIXED      (passed now)
     *           d3  PASSED               (passed now)
     *           e3  FAILED               (not passed now)
     * ```
     */
    @Test
    fun `Paginated list of validation runs filtered on their current status`() {
        project {
            branch {
                val vs = validationStamp()
                lateinit var a1: ValidationRun
                lateinit var b2: ValidationRun
                lateinit var c3: ValidationRun
                lateinit var d3: ValidationRun
                lateinit var e3: ValidationRun
                build("1") {
                    a1 = validate(vs, ValidationRunStatusID.STATUS_FAILED)
                        .forceStatusHistory(ValidationRunStatusID.STATUS_PASSED)
                }
                build("2") {
                    b2 = validate(vs, ValidationRunStatusID.STATUS_PASSED)
                        .forceStatusHistory(ValidationRunStatusID.STATUS_DEFECTIVE)
                }
                val build3 = build("3") {
                    c3 = validate(vs, ValidationRunStatusID.STATUS_PASSED)
                        .forceStatusHistory(ValidationRunStatusID.STATUS_FIXED)
                    d3 = validate(vs, ValidationRunStatusID.STATUS_PASSED)
                    e3 = validate(vs, ValidationRunStatusID.STATUS_FAILED)
                }

                val query = """
                    query PaginatedValidationRuns(
                        ${'$'}validationStampId: Int!,
                        ${'$'}buildId: Int,
                        ${'$'}passed: Boolean,
                        ${'$'}offset: Int = 0,
                        ${'$'}size: Int = 2) {
                        validationStamp(id: ${'$'}validationStampId) {
                            validationRunsPaginated(
                                buildId: ${'$'}buildId,
                                passed: ${'$'}passed,
                                offset: ${'$'}offset,
                                size: ${'$'}size
                            ) {
                                pageInfo {
                                    totalSize
                                    nextPage {
                                        offset
                                        size
                                    }
                                }
                                pageItems {
                                    id
                                }
                            }
                        }
                    }
                """

                fun page(passed: Boolean?, offset: Int = 0, buildId: Int? = null) = run(
                    query,
                    mapOf(
                        "validationStampId" to vs.id(),
                        "buildId" to buildId,
                        "passed" to passed,
                        "offset" to offset,
                        "size" to 2,
                    )
                ).path("validationStamp").path("validationRunsPaginated")

                fun JsonNode.ids() = path("pageItems").map { it.path("id").asInt() }
                fun JsonNode.totalSize() = path("pageInfo").path("totalSize").asInt()
                fun JsonNode.nextOffset(): Int? =
                    path("pageInfo").path("nextPage").takeIf { !it.isNull && !it.isMissingNode }?.path("offset")?.asInt()

                // Passed, first page
                page(passed = true).let {
                    assertEquals(listOf(d3.id(), c3.id()), it.ids())
                    assertEquals(3, it.totalSize())
                    assertEquals(2, it.nextOffset())
                }
                // Passed, last page
                page(passed = true, offset = 2).let {
                    assertEquals(listOf(a1.id()), it.ids())
                    assertEquals(3, it.totalSize())
                    assertEquals(null, it.nextOffset())
                }
                // Not passed, one page only
                page(passed = false).let {
                    assertEquals(listOf(e3.id(), b2.id()), it.ids())
                    assertEquals(2, it.totalSize())
                    assertEquals(null, it.nextOffset())
                }
                // No filter
                page(passed = null).let {
                    assertEquals(listOf(e3.id(), d3.id()), it.ids())
                    assertEquals(5, it.totalSize())
                    assertEquals(2, it.nextOffset())
                }
                // For a build, passed
                page(passed = true, buildId = build3.id()).let {
                    assertEquals(listOf(d3.id(), c3.id()), it.ids())
                    assertEquals(2, it.totalSize())
                    assertEquals(null, it.nextOffset())
                }
                // For a build, not passed
                page(passed = false, buildId = build3.id()).let {
                    assertEquals(listOf(e3.id()), it.ids())
                    assertEquals(1, it.totalSize())
                    assertEquals(null, it.nextOffset())
                }
            }
        }
    }

    @Test
    fun `Paginated list of validation runs`() {
        project {
            branch {
                // Creates a validation stamp
                val vs = validationStamp()
                // Creates 3 builds...
                repeat(3) { no ->
                    build("1.$no") {
                        // Validates N times the build
                        repeat(20) {
                            validate(vs)
                        }
                    }
                }
                // Checks the number of validation runs
                assertEquals(
                    60,
                    structureService.getValidationRunsCountForValidationStamp(vs.id),
                    "Checking the number of validation runs having been created"
                )
                // Paginated query with variables
                val query = """
                    query PaginatedValidationRuns(
                        ${'$'}validationStampId: Int!,
                        ${'$'}offset: Int = 0,
                        ${'$'}size: Int = 20) {
                        validationStamp(id: ${'$'}validationStampId) {
                            name
                            validationRunsPaginated(offset: ${'$'}offset, size: ${'$'}size) {
                                pageInfo {
                                    totalSize
                                    currentOffset
                                    currentSize
                                    previousPage {
                                        offset
                                        size
                                    }
                                    nextPage {
                                        offset
                                        size
                                    }
                                    pageIndex
                                    pageTotal
                                }
                                pageItems {
                                    id
                                    build {
                                        name
                                    }
                                }
                            }
                        }
                    }
                    """
                // Initial parameters
                val params = mutableMapOf(
                    "offset" to 0,
                    "size" to 20,
                    "validationStampId" to vs.id()
                )

                /**
                 * First page
                 */

                // Runs the query for the first page
                var data = run(query, params)
                // Checks the validation stamp info
                var vsNode = data["validationStamp"]
                assertEquals(vs.name, vsNode["name"].asText())
                // Gets the paginated list
                var paginated = vsNode["validationRunsPaginated"]
                var pageInfo = paginated["pageInfo"]
                var previousPage = pageInfo["previousPage"]
                var nextPage = pageInfo["nextPage"]
                assertEquals(60, pageInfo["totalSize"].asInt())
                assertEquals(0, pageInfo["currentOffset"].asInt())
                assertEquals(20, pageInfo["currentSize"].asInt())
                assertEquals(0, pageInfo["pageIndex"].asInt())
                assertEquals(3, pageInfo["pageTotal"].asInt())
                assertTrue(previousPage.isNull, "No previous page")
                assertFalse(nextPage.isNull, "There is a next page")
                assertEquals(20, nextPage["offset"].asInt())
                assertEquals(20, nextPage["size"].asInt())
                var pageItems = paginated["pageItems"]
                assertEquals(20, pageItems.size())
                repeat(20) {
                    val buildName = pageItems[it]["build"]["name"].asText()
                    assertEquals("1.2", buildName)
                }

                /**
                 * Middle page
                 */

                // Uses the next page as variables
                params["offset"] = nextPage["offset"].asInt()
                params["size"] = nextPage["size"].asInt()
                // Runs the query for the first page
                data = run(query, params)
                // Checks the validation stamp info
                vsNode = data["validationStamp"]
                assertEquals(vs.name, vsNode["name"].asText())
                // Gets the paginated list
                paginated = vsNode["validationRunsPaginated"]
                pageInfo = paginated["pageInfo"]
                previousPage = pageInfo["previousPage"]
                nextPage = pageInfo["nextPage"]
                assertEquals(60, pageInfo["totalSize"].asInt())
                assertEquals(20, pageInfo["currentOffset"].asInt())
                assertEquals(20, pageInfo["currentSize"].asInt())
                assertEquals(1, pageInfo["pageIndex"].asInt())
                assertEquals(3, pageInfo["pageTotal"].asInt())
                assertFalse(previousPage.isNull, "There is a previous page")
                assertEquals(0, previousPage["offset"].asInt())
                assertEquals(20, previousPage["size"].asInt())
                assertFalse(nextPage.isNull, "There is a next page")
                assertEquals(40, nextPage["offset"].asInt())
                assertEquals(20, nextPage["size"].asInt())
                pageItems = paginated["pageItems"]
                assertEquals(20, pageItems.size())
                repeat(20) {
                    val buildName = pageItems[it]["build"]["name"].asText()
                    assertEquals("1.1", buildName)
                }

                /**
                 * Last page
                 */

                // Uses the next page as variables
                params["offset"] = nextPage["offset"].asInt()
                params["size"] = nextPage["size"].asInt()
                // Runs the query for the first page
                data = run(query, params)
                // Checks the validation stamp info
                vsNode = data["validationStamp"]
                assertEquals(vs.name, vsNode["name"].asText())
                // Gets the paginated list
                paginated = vsNode["validationRunsPaginated"]
                pageInfo = paginated["pageInfo"]
                previousPage = pageInfo["previousPage"]
                nextPage = pageInfo["nextPage"]
                assertEquals(60, pageInfo["totalSize"].asInt())
                assertEquals(40, pageInfo["currentOffset"].asInt())
                assertEquals(20, pageInfo["currentSize"].asInt())
                assertEquals(2, pageInfo["pageIndex"].asInt())
                assertEquals(3, pageInfo["pageTotal"].asInt())
                assertFalse(previousPage.isNull, "There is a previous page")
                assertEquals(20, previousPage["offset"].asInt())
                assertEquals(20, previousPage["size"].asInt())
                assertTrue(nextPage.isNull, "There is no next page")
                pageItems = paginated["pageItems"]
                assertEquals(20, pageItems.size())
                repeat(20) {
                    val buildName = pageItems[it]["build"]["name"].asText()
                    assertEquals("1.0", buildName)
                }

                /**
                 * Navigates back to the middle page
                 */

                // Uses the previous page as variables
                params["offset"] = previousPage["offset"].asInt()
                params["size"] = previousPage["size"].asInt()
                // Runs the query for the first page
                data = run(query, params)
                // Checks the validation stamp info
                vsNode = data["validationStamp"]
                assertEquals(vs.name, vsNode["name"].asText())
                // Gets the paginated list
                paginated = vsNode["validationRunsPaginated"]
                pageInfo = paginated["pageInfo"]
                previousPage = pageInfo["previousPage"]
                nextPage = pageInfo["nextPage"]
                assertEquals(60, pageInfo["totalSize"].asInt())
                assertEquals(20, pageInfo["currentOffset"].asInt())
                assertEquals(20, pageInfo["currentSize"].asInt())
                assertEquals(1, pageInfo["pageIndex"].asInt())
                assertEquals(3, pageInfo["pageTotal"].asInt())
                assertFalse(previousPage.isNull, "There is a previous page")
                assertEquals(0, previousPage["offset"].asInt())
                assertEquals(20, previousPage["size"].asInt())
                assertFalse(nextPage.isNull, "There is a next page")
                assertEquals(40, nextPage["offset"].asInt())
                assertEquals(20, nextPage["size"].asInt())
                pageItems = paginated["pageItems"]
                assertEquals(20, pageItems.size())
                repeat(20) {
                    val buildName = pageItems[it]["build"]["name"].asText()
                    assertEquals("1.1", buildName)
                }

                /**
                 * And back to the first page
                 */

                // Uses the previous page as variables
                params["offset"] = previousPage["offset"].asInt()
                params["size"] = previousPage["size"].asInt()
                // Runs the query for the first page
                data = run(query, params)
                // Checks the validation stamp info
                vsNode = data["validationStamp"]
                assertEquals(vs.name, vsNode["name"].asText())
                // Gets the paginated list
                paginated = vsNode["validationRunsPaginated"]
                pageInfo = paginated["pageInfo"]
                previousPage = pageInfo["previousPage"]
                nextPage = pageInfo["nextPage"]
                assertEquals(60, pageInfo["totalSize"].asInt())
                assertEquals(0, pageInfo["currentOffset"].asInt())
                assertEquals(20, pageInfo["currentSize"].asInt())
                assertEquals(0, pageInfo["pageIndex"].asInt())
                assertEquals(3, pageInfo["pageTotal"].asInt())
                assertTrue(previousPage.isNull, "No previous page")
                assertFalse(nextPage.isNull, "There is a next page")
                assertEquals(20, nextPage["offset"].asInt())
                assertEquals(20, nextPage["size"].asInt())
                pageItems = paginated["pageItems"]
                assertEquals(20, pageItems.size())
                repeat(20) {
                    val buildName = pageItems[it]["build"]["name"].asText()
                    assertEquals("1.2", buildName)
                }
            }
        }
    }

}