package net.nemerosa.ontrack.graphql

import net.nemerosa.ontrack.extension.api.support.TestSimpleProperty
import net.nemerosa.ontrack.extension.api.support.TestSimplePropertyType
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.json.isNullOrNullNode
import net.nemerosa.ontrack.model.security.ProjectCreation
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.model.structure.BranchFavouriteService
import net.nemerosa.ontrack.model.structure.NameDescription
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID
import net.nemerosa.ontrack.test.TestUtils
import net.nemerosa.ontrack.test.TestUtils.uid
import net.nemerosa.ontrack.test.assertNotPresent
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.graphql.execution.ErrorType
import kotlin.jvm.optionals.getOrNull
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@AsAdminTest
class ProjectGraphQLIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var branchFavouriteService: BranchFavouriteService

    @Test
    fun `Project by ID`() {
        project {
            run("{projects(id: ${id}) { name }}") { data ->
                assertEquals(name, data.path("projects").first().path("name").asText())
            }
        }
    }

    @Test
    fun `Project by ID and name is not authorised`() {
        runWithError("""{projects(id: 1, name: "test") { name }}""", errorClassification = ErrorType.BAD_REQUEST)
    }

    @Test
    fun `Project by name`() {
        project {
            run("""{projects(name: "$name") { id }}""") { data ->
                assertEquals(
                    id(),
                    data.path("projects").first().path("id").asInt()
                )
            }
        }
    }

    @Test
    fun `Project branches`() {
        project {
            branch("B1")
            branch("B2")
            run("{projects(id: ${id}) { name branches { name } }}") { data ->
                assertEquals(
                    listOf("B2", "B1"),
                    data.path("projects").first()
                        .path("branches")
                        .values().map {
                            it.path("name").asText()
                        }
                )
            }
        }
    }

    @Test
    fun `Project branches not filtered by disabled status by default`() {
        project {
            branch("B1") {
                structureService.disableBranch(this)
            }
            branch("B2")
            run(
                """
                    {
                        project(id: ${id}) {
                            branches(enabled: null) {
                                name
                            }
                        }
                    }
                """
            ) { data ->
                assertEquals(
                    listOf("B2", "B1"),
                    data.path("project").path("branches").values().map { it.path("name").asText() }
                )
            }
        }
    }

    @Test
    fun `Filtering project branches being disabled`() {
        project {
            branch("B1") {
                structureService.disableBranch(this)
            }
            branch("B2")
            run(
                """
                    {
                        project(id: ${id}) {
                            branches(enabled: false) {
                                name
                            }
                        }
                    }
                """
            ) { data ->
                assertEquals(
                    listOf("B1"),
                    data.path("project").path("branches").values().map { it.path("name").asText() }
                )
            }
        }
    }

    @Test
    fun `Filtering project branches being enabled`() {
        project {
            branch("B1") {
                structureService.disableBranch(this)
            }
            branch("B2")
            run(
                """
                    {
                        project(id: ${id}) {
                            branches(enabled: true) {
                                name
                            }
                        }
                    }
                """
            ) { data ->
                assertEquals(
                    listOf("B2"),
                    data.path("project").path("branches").values().map { it.path("name").asText() }
                )
            }
        }
    }

    @Test
    fun `Branch by name`() {
        project {
            branch("B1")
            branch("B2")
            run("""{projects(id: ${id}) { name branches(name: "B2") { name } }}""") { data ->
                assertEquals(
                    listOf("B2"),
                    data.path("projects").first()
                        .path("branches")
                        .values().map {
                            it.path("name").asText()
                        }
                )
            }
        }
    }

    @Test
    fun `Branch by exact name`() {
        project {
            val b2 = branch("B2")
            branch("B2Same")
            run("""{projects(id: ${id}) { name branch(name: "B2") { id name } }}""") { data ->
                assertEquals(
                    b2.id(),
                    data.path("projects").first()
                        .path("branch")
                        .path("id")
                        .asInt()
                )
            }
        }
    }

    @Test
    fun `Branch by regular expression`() {
        project {
            branch("11.8.3")
            branch("11.9.0")
            run("""{projects(id: ${id}) { name branches(name: "11\\.9\\.*") { name } } }""") { data ->
                assertEquals(
                    listOf("11.9.0"),
                    data.path("projects").first()
                        .path("branches")
                        .values().map {
                            it.path("name").asText()
                        }
                )
            }
        }
    }

    @Test
    fun `Promotion levels for a branch`() {
        project {
            branch {
                (1..5).forEach { no ->
                    promotionLevel("PL$no", "Promotion level $no")
                }
                run(
                    """{
                    projects (id: ${project.id}) {
                        branches (name: "$name") {
                            promotionLevels {
                                name
                            }
                        }
                    }
                }"""
                ) { data ->
                    assertEquals(
                        (1..5).map { "PL$it" },
                        data.path("projects").first()
                            .path("branches").first()
                            .path("promotionLevels")
                            .values().map {
                                it.path("name").asText()
                            }
                    )
                }
            }

        }
    }

    @Test
    fun `Validation stamps for a branch`() {
        project {
            branch {
                (1..5).forEach { no ->
                    validationStamp("VS$no", description = "Validation stamp $no")
                }
                run(
                    """{
                    projects (id: ${project.id}) {
                        branches (name: "$name") {
                            validationStamps {
                                name
                            }
                        }
                    }
                }"""
                ) { data ->
                    assertEquals(
                        (1..5).map { "VS$it" },
                        data.path("projects").first()
                            .path("branches").first()
                            .path("validationStamps")
                            .values().map {
                                it.path("name").asText()
                            }
                    )
                }
            }

        }
    }

    @Test
    fun `Promotion runs for a promotion level`() {
        project {
            branch {
                val pl = promotionLevel()
                (1..5).forEach {
                    build("$it") {
                        if (it % 2 == 0) {
                            promote(pl)
                        }
                    }
                }
                run(
                    """{
                    projects (id: ${project.id}) {
                        branches (name: "$name") {
                            promotionLevels {
                                name
                                promotionRuns {
                                    build {
                                        name
                                    }
                                }
                            }
                        }
                    }
                }"""
                ) { data ->
                    assertEquals(
                        listOf("4", "2"),
                        data.path("projects").first()
                            .path("branches").first()
                            .path("promotionLevels").first()
                            .path("promotionRuns").values().map {
                                it.path("build").path("name").asText()
                            }
                    )
                }
            }
        }
    }

    @Test
    fun `Validation runs for a validation stamp`() {
        project {
            branch {
                val vs = validationStamp()
                (1..5).forEach {
                    build("$it") {
                        if (it % 2 == 0) {
                            validate(vs)
                        }
                    }
                }
                run(
                    """{
                    projects (id: ${project.id}) {
                        branches (name: "$name") {
                            validationStamps {
                                name
                                validationRuns {
                                    build {
                                        name
                                    }
                                }
                            }
                        }
                    }
                }"""
                ) { data ->
                    assertEquals(
                        listOf("4", "2"),
                        data.path("projects").first()
                            .path("branches").first()
                            .path("validationStamps").first()
                            .path("validationRuns").values().map {
                                it.path("build").path("name").asText()
                            }
                    )
                }
            }
        }
    }

    @Test
    fun `Promotion runs for a build`() {
        project {
            branch {
                val pl1 = promotionLevel("PL1")
                val pl2 = promotionLevel("PL2")
                build("1") {
                    promote(pl1)
                    promote(pl2)
                }
                run(
                    """{
                    projects (id: ${project.id}) {
                        branches (name: "$name") {
                            builds(count: 1) {
                                promotionRuns {
                                    promotionLevel {
                                        name
                                    }
                                }
                            }
                        }
                    }
                }"""
                ) { data ->
                    assertEquals(
                        listOf("PL2", "PL1"),
                        data.path("projects").first()
                            .path("branches").first()
                            .path("builds").first()
                            .path("promotionRuns").values().map {
                                it.path("promotionLevel").path("name").asText()
                            }
                    )
                }
            }
        }
    }

    @Test
    fun `Filtered promotion runs for a build`() {
        project {
            branch {
                val pl1 = promotionLevel("PL1")
                val pl2 = promotionLevel("PL2")
                build("1") {
                    promote(pl1)
                    promote(pl2)
                }
                run(
                    """{
                    projects (id: ${project.id}) {
                        branches (name: "$name") {
                            builds(count: 1) {
                                promotionRuns(promotion: "PL1") {
                                    promotionLevel {
                                        name
                                    }
                                }
                            }
                        }
                    }
                }"""
                ) { data ->
                    assertEquals(
                        listOf("PL1"),
                        data.path("projects").first()
                            .path("branches").first()
                            .path("builds").first()
                            .path("promotionRuns").values().map {
                                it.path("promotionLevel").path("name").asText()
                            }
                    )
                }
            }
        }
    }

    @Test
    fun `Filtered list of promotion runs for a promotion level`() {
        project {
            branch {
                val pl = promotionLevel()
                (1..20).forEach {
                    build("$it") {
                        if (it % 2 == 0) {
                            promote(pl)
                        }
                    }
                }
                run(
                    """{
                    projects (id: ${project.id}) {
                        branches (name: "$name") {
                            promotionLevels {
                                name
                                promotionRuns(first: 5) {
                                    build {
                                        name
                                    }
                                }
                            }
                        }
                    }
                }"""
                ) { data ->
                    assertEquals(
                        listOf("20", "18", "16", "14", "12"),
                        data.path("projects").first()
                            .path("branches").first()
                            .path("promotionLevels").first()
                            .path("promotionRuns").values().map {
                                it.path("build").path("name").asText()
                            }
                    )
                }
            }
        }
    }

    @Test
    fun `Last promotion runs for a promotion level`() {
        project {
            branch {
                val pl = promotionLevel()
                (1..20).forEach {
                    build("$it") {
                        if (it % 2 == 0) {
                            promote(pl)
                        }
                    }
                }
                run(
                    """{
                    projects (id: ${project.id}) {
                        branches (name: "$name") {
                            promotionLevels {
                                name
                                promotionRuns(last: 3) {
                                    build {
                                        name
                                    }
                                }
                            }
                        }
                    }
                }"""
                ) { data ->
                    assertEquals(
                        listOf("6", "4", "2"),
                        data.path("projects").first()
                            .path("branches").first()
                            .path("promotionLevels").first()
                            .path("promotionRuns").values().map {
                                it.path("build").path("name").asText()
                            }
                    )
                }
            }
        }
    }

    @Test
    fun `Projects filtered by property type`() {
        // Projects
        val p1 = project {
            setProperty(this, TestSimplePropertyType::class.java, TestSimpleProperty("P1"))
        }
        /*def p2 = */ project()
        val p3 = project {
            setProperty(this, TestSimplePropertyType::class.java, TestSimpleProperty("P3"))
        }
        val p4 = project {
            setProperty(this, TestSimplePropertyType::class.java, TestSimpleProperty("X1"))
        }
        // Looks for projects having this property
        run(
            """{
            projects(withProperty: {type: "${TestSimplePropertyType::class.java.name}"}) {
                name
            }
        }"""
        ) { data ->
            assertEquals(
                setOf(p1.name, p3.name, p4.name),
                data.path("projects").values().map { it.path("name").asText() }.toSet()
            )
        }
    }

    @Test
    fun `Projects filtered by property type and value pattern`() {
        // Projects
        val p1 = project {
            setProperty(this, TestSimplePropertyType::class.java, TestSimpleProperty("P1"))
        }
        /* val p2 = */ project()
        val p3 = project {
            setProperty(this, TestSimplePropertyType::class.java, TestSimpleProperty("P3"))
        }
        /* val p4 = */ project {
            setProperty(this, TestSimplePropertyType::class.java, TestSimpleProperty("X1"))
        }
        // Looks for projects having this property
        run(
            """{
            projects(withProperty: {type: "${TestSimplePropertyType::class.java.name}", value: "P"}) {
                name
            }
        }"""
        ) { data ->
            assertEquals(
                setOf(p1.name, p3.name),
                data.path("projects").values().map { it.path("name").asText() }.toSet()
            )
        }
    }

    @Test
    fun `Projects filtered by property type and value`() {
        // Projects
        val p1 = project {
            setProperty(this, TestSimplePropertyType::class.java, TestSimpleProperty("P1"))
        }
        /* val p2 = */ project()
        /* val p3 = */ project {
            setProperty(this, TestSimplePropertyType::class.java, TestSimpleProperty("P3"))
        }
        /* val p4 = */ project {
            setProperty(this, TestSimplePropertyType::class.java, TestSimpleProperty("X1"))
        }
        // Looks for projects having this property
        run(
            """{
            projects(withProperty: {type: "${TestSimplePropertyType::class.java.name}", value: "P1"}) {
                name
            }
        }"""
        ) { data ->
            assertEquals(
                setOf(p1.name),
                data.path("projects").values().map { it.path("name").asText() }.toSet()
            )
        }
    }

    @Test
    fun `Looking for projects using a pattern`() {
        val rootA = TestUtils.uid("P")
        val rootB = TestUtils.uid("P")
        repeat(5) {
            project(name = NameDescription.nd("X${rootA}$it", ""))
        }
        repeat(5) {
            project(name = NameDescription.nd("Y${rootB}$it", ""))
        }
        asAdmin {
            run(
                """{
                projects(pattern: "X$rootA") {
                    name
                }
            }"""
            ).let { data ->
                val names = data.path("projects").values().map { it.path("name").asText() }
                assertEquals(
                    (0..4).map { "X$rootA$it" },
                    names
                )
            }
        }
    }

    @Test
    fun `Looking for projects using a pattern is restricted by authorizations`() {
        val rootA = TestUtils.uid("P")
        val rootB = TestUtils.uid("P")
        val projectsA = (0..4).map {
            project(name = NameDescription.nd("X${rootA}$it", ""))
        }
        repeat(5) {
            project(name = NameDescription.nd("Y${rootB}$it", ""))
        }
        withNoGrantViewToAll {
            asUserWithView(*projectsA.take(3).toTypedArray()) {
                run(
                    """{
                    projects(pattern: "X$rootA") {
                        name
                    }
                }"""
                ).let { data ->
                    val names = data.path("projects").values().map { it.path("name").asText() }
                    assertEquals(
                        (0..2).map { "X$rootA$it" },
                        names
                    )
                }
            }
        }
    }

    @Test
    fun `Maximum number of branches`() {
        asAdmin {
            project {
                // Creates 20 branches
                repeat(20) {
                    branch(name = "1.$it")
                }
                // Query for the last 10
                run(
                    """{
                    projects(id: $id) {
                        branches(count: 10) {
                            name
                        }
                    }
                }"""
                ).let { data ->
                    val names = data.path("projects").path(0).path("branches").values().map {
                        it.path("name").asText()
                    }
                    assertEquals(
                        names,
                        (19 downTo 10).map {
                            "1.$it"
                        }
                    )
                }
            }
        }
    }


    @Test
    fun `Creating a project`() {
        asAdmin {
            val name = uid("P")
            val data = run(
                """
                mutation CreateProject(${'$'}name: String!) {
                    createProject(input: {name: ${'$'}name}) {
                        project {
                            id
                            name
                        }
                        errors {
                            message
                        }
                    }
                }
            """, mapOf("name" to name)
            )
            // Checks the project has been created
            assertNotNull(structureService.findProjectByName(name).getOrNull(), "Project has been created") {
                assertFalse(it.isDisabled, "Project is not disabled")
            }
            // Checks the data
            val project = data["createProject"]["project"]
            assertTrue(project["id"].asInt() > 0, "ID is set")
            assertEquals(name, project["name"].asText(), "Name is OK")
            assertTrue(data["createProject"]["errors"].isNullOrNullNode(), "No error")
        }
    }

    @Test
    fun `Creating a project but name already exist`() {
        asAdmin {
            project {
                val data = run(
                    """
                    mutation CreateProject(${'$'}name: String!) {
                        createProject(input: {name: ${'$'}name}) {
                            project {
                                id
                                name
                            }
                            errors {
                                message
                                exception
                            }
                        }
                    }
                """, mapOf("name" to name)
                )
                // Checks the errors
                val error = data["createProject"]["errors"][0]
                assertEquals("Project name already exists: $name", error["message"].asText())
                assertEquals(
                    "net.nemerosa.ontrack.model.exceptions.ProjectNameAlreadyDefinedException",
                    error["exception"].asText()
                )
                assertTrue(data["createProject"]["project"].isNullOrNullNode(), "Project not returned")
            }
        }
    }

    @Test
    fun `Creating a project with an invalid name`() {
        asAdmin {
            val data = run(
                """
                mutation CreateProject(${'$'}name: String!) {
                    createProject(input: {name: ${'$'}name}) {
                        project {
                            id
                            name
                        }
                        errors {
                            message
                            exception
                            location
                        }
                    }
                }
            """, mapOf("name" to "white space")
            )
            // Checks the errors
            val error = data["createProject"]["errors"][0]
            assertEquals(
                "The name can only have letters, digits, dots (.), dashes (-) or underscores (_).",
                error["message"].asText()
            )
            assertEquals(
                "net.nemerosa.ontrack.graphql.support.MutationInputValidationException",
                error["exception"].asText()
            )
            assertEquals("name", error["location"].asText())
            assertTrue(data["createProject"]["project"].isNullOrNullNode(), "Project not returned")
        }
    }

    @Test
    fun `Creating a project which already exist in get mode`() {
        asAdmin {
            val project = project()
            val data = run(
                """
                mutation CreateProject(${'$'}name: String!) {
                    createProjectOrGet(input: {name: ${'$'}name}) {
                        project {
                            id
                            name
                        }
                        errors {
                            message
                            exception
                            location
                        }
                    }
                }
            """, mapOf("name" to project.name)
            )
            // Checks the errors
            assertNoUserError(data, "createProjectOrGet")
            val node = data["createProjectOrGet"]["project"]
            assertEquals(project.id(), node["id"].asInt())
            assertEquals(project.name, node["name"].asText())
        }
    }

    @Test
    fun `Creating a project which already exist in get mode with specific project access rights`() {
        project {
            withNoGrantViewToAll {
                asUserWithView {
                    val data = run(
                        """
                                mutation CreateProject(${'$'}name: String!) {
                                    createProjectOrGet(input: {name: ${'$'}name}) {
                                        project {
                                            id
                                            name
                                        }
                                        errors {
                                            message
                                            exception
                                            location
                                        }
                                    }
                                }
                            """, mapOf("name" to name)
                    )
                    // Checks the errors
                    assertNoUserError(data, "createProjectOrGet")
                    val node = data["createProjectOrGet"]["project"]
                    assertEquals(id(), node["id"].asInt())
                    assertEquals(name, node["name"].asText())
                }
            }
        }
    }

    @Test
    fun `Creating a project which already exists for another user`() {
        val project = project()
        withNoGrantViewToAll {
            asUserWith<ProjectCreation> {
                val data = run(
                    """
                        mutation CreateProject(${'$'}name: String!) {
                            createProjectOrGet(input: {name: ${'$'}name}) {
                                project {
                                    id
                                    name
                                }
                                errors {
                                    message
                                    exception
                                    location
                                }
                            }
                        }
                    """, mapOf("name" to project.name)
                )
                // Checks the errors
                val error = data["createProjectOrGet"]["errors"][0]
                assertEquals("Project name already exists: ${project.name}", error["message"].asText())
                assertEquals(
                    "net.nemerosa.ontrack.model.exceptions.ProjectNameAlreadyDefinedException",
                    error["exception"].asText()
                )
                assertTrue(data["createProjectOrGet"]["project"].isNullOrNullNode(), "Project not returned")
            }
        }
    }

    @Test
    fun `Creating a project in get mode`() {
        asUserWith<ProjectCreation> {
            val name = uid("P")
            val data = run(
                """
                    mutation CreateProject(${'$'}name: String!) {
                        createProjectOrGet(input: {name: ${'$'}name}) {
                            project {
                                id
                                name
                            }
                            errors {
                                message
                                exception
                                location
                            }
                        }
                    }
                """, mapOf("name" to name)
            )
            // Checks the errors
            assertNoUserError(data, "createProjectOrGet")
            val node = data["createProjectOrGet"]["project"]
            assertEquals(name, node["name"].asText())
        }
    }

    @Test
    fun `Updating a project's name`() {
        asAdmin {
            project {
                val newName = uid("P")
                val data = run(
                    """
                    mutation UpdateProject(${'$'}name: String!) {
                        updateProject(input: {id: $id, name: ${'$'}name}) {
                            project {
                                id
                                name
                            }
                            errors {
                                message
                                exception
                            }
                        }
                    }
                """, mapOf("name" to newName)
                )
                // Checks for errors
                checkGraphQLUserErrors(data, "updateProject")
                // Checks the project has been created
                structureService.getProject(id).apply {
                    assertEquals(newName, name)
                }
                // Checks the data
                val project = data["updateProject"]["project"]
                assertTrue(project["id"].asInt() > 0, "ID is set")
                assertEquals(newName, project["name"].asText(), "Name is OK")
                assertTrue(data["updateProject"]["errors"].isNullOrNullNode(), "No error")
            }
        }
    }

    @Test
    fun `Updating a project's description`() {
        asAdmin {
            project {
                val newDescription = uid("P")
                val data = run(
                    """
                    mutation UpdateProject(${'$'}description: String!) {
                        updateProject(input: {id: $id, description: ${'$'}description}) {
                            project {
                                id
                                name
                                description
                            }
                            errors {
                                message
                                exception
                            }
                        }
                    }
                """, mapOf("description" to newDescription)
                )
                // Checks for errors
                checkGraphQLUserErrors(data, "updateProject")
                // Checks the project has been created
                structureService.getProject(id).apply {
                    assertEquals(newDescription, description)
                }
                // Checks the data
                val project = data["updateProject"]["project"]
                assertTrue(project["id"].asInt() > 0, "ID is set")
                assertEquals(newDescription, project["description"].asText(), "Description is OK")
                assertTrue(data["updateProject"]["errors"].isNullOrNullNode(), "No error")
            }
        }
    }

    @Test
    fun `Updating a project's state`() {
        asAdmin {
            project {
                val data = run(
                    """
                    mutation UpdateProject {
                        updateProject(input: {id: $id, disabled: true}) {
                            project {
                                id
                                name
                                disabled
                            }
                            errors {
                                message
                                exception
                            }
                        }
                    }
                """
                )
                // Checks for errors
                checkGraphQLUserErrors(data, "updateProject")
                // Checks the project has been created
                structureService.getProject(id).apply {
                    assertTrue(isDisabled)
                }
                // Checks the data
                val project = data["updateProject"]["project"]
                assertTrue(project["id"].asInt() > 0, "ID is set")
                assertTrue(project["disabled"].asBoolean(), "State is OK")
                assertTrue(data["updateProject"]["errors"].isNullOrNullNode(), "No error")
            }
        }
    }

    @Test
    fun `Updating a project fails before of duplicated name`() {
        asAdmin {
            val existingName = project().name
            project {
                val data = run(
                    """
                    mutation UpdateProject(${'$'}name: String!) {
                        updateProject(input: {id: $id, name: ${'$'}name}) {
                            project {
                                id
                                name
                            }
                            errors {
                                message
                                exception
                            }
                        }
                    }
                """, mapOf("name" to existingName)
                )
                // Checks the errors
                val error = data["updateProject"]["errors"][0]
                assertEquals("Project name already exists: $existingName", error["message"].asText())
                assertEquals(
                    "net.nemerosa.ontrack.model.exceptions.ProjectNameAlreadyDefinedException",
                    error["exception"].asText()
                )
                assertTrue(data["updateProject"]["project"].isNullOrNullNode(), "Project not returned")
            }
        }
    }

    @Test
    fun `Deleting a project`() {
        asAdmin {
            project {
                val data = run(
                    """
                    mutation DeleteProject {
                        deleteProject(input: {id: $id}) {
                            errors {
                                message
                                exception
                            }
                        }
                    }
                """
                )
                // Checks the project has been deleted
                assertNotPresent(structureService.findProjectByName(name))
                // Checks the data
                assertTrue(data["deleteProject"]["errors"].isNullOrNullNode(), "No error")
            }
        }
    }

    @Test
    fun `Disabling a project`() {
        asAdmin {
            project {
                val data = run(
                    """
                    mutation DisableProject {
                        disableProject(input: {id: $id}) {
                            project {
                                disabled
                            }
                        }
                    }
                """
                )
                // Checks the project has been disabled
                assertTrue(structureService.getProject(id).isDisabled)
                // Checks the data
                assertTrue(data["disableProject"]["project"]["disabled"].asBoolean())
            }
        }
    }

    @Test
    fun `Enabling a project`() {
        asAdmin {
            project {
                structureService.disableProject(this)
                val data = run(
                    """
                    mutation EnableProject {
                        enableProject(input: {id: $id}) {
                            project {
                                disabled
                            }
                        }
                    }
                """
                )
                // Checks the project has been enabled
                assertFalse(structureService.getProject(id).isDisabled)
                // Checks the data
                assertFalse(data["enableProject"]["project"]["disabled"].asBoolean())
            }
        }
    }

    @Test
    fun `Deleting a non existingproject`() {
        asAdmin {
            val project = project()
            structureService.deleteProject(project.id)
            val data = run(
                """
                    mutation DeleteProject {
                        deleteProject(input: {id: ${project.id}}) {
                            errors {
                                message
                                exception
                            }
                        }
                    }
                """
            )
            // Checks the errors
            val error = data["deleteProject"]["errors"][0]
            assertEquals("Project ID not found: ${project.id}", error["message"].asText())
            assertEquals("net.nemerosa.ontrack.model.exceptions.ProjectNotFoundException", error["exception"].asText())
        }
    }

    @Test
    fun `Favourite branches for project`() {
        val account = doCreateAccount()
        project {
            branch {}
            val fav = branch {
                asConfigurableAccount(account).withView(this).execute {
                    branchFavouriteService.setBranchFavourite(this, true)
                }
            }
            // Gets the favourite branches in project
            val data = asConfigurableAccount(account).withView(this).call {
                run(
                    """
                    {
                        projects(id: ${this.id}) {
                            branches(favourite: true) {
                                id
                            }
                        }
                    }
                """
                )
            }
            val branchIds: Set<Int> = data["projects"][0]["branches"].values().map { it["id"].asInt() }.toSet()
            assertEquals(
                setOf(fav.id()),
                branchIds
            )
        }
    }

    @Test
    fun `All projects`() {
        val p = doCreateProject()
        val data = run("{projects { id name }}")
        assertNotNull(data["projects"].find { it["name"].asText() == p.name }) {
            assertEquals(p.id(), it["id"].asInt())
        }
    }

    @Test
    fun `Project by name when not authorized must throw an authentication exception`() {
        // Creates a project
        val project = doCreateProject()
        // Looks for this project by name, with a not authorized user
        withNoGrantViewToAll {
            asUser().execute {
                runWithError(
                    """{ projects(name: "${project.name}") { id } }""",
                    errorClassification = ErrorType.FORBIDDEN
                )
            }
        }
    }

    @Test
    fun `Last promoted build`() {
        // Creating a promotion level
        val pl = doCreatePromotionLevel()
        // Creating a first promoted build
        val build1 = doCreateBuild(pl.branch, NameDescription.nd("1", ""))
        doPromote(build1, pl, "One")
        // Creating a second promoted build
        val build2 = doCreateBuild(pl.branch, NameDescription.nd("2", ""))
        doPromote(build2, pl, "Two")
        // Run a GraphQL query at project level and gets the last promotion run
        val data = run(
            """{
            |   projects(id: ${pl.project.id}) {
            |      branches {
            |          promotionLevels {
            |              name
            |              promotionRuns(first: 1) {
            |                build {
            |                  name
            |                }
            |              }
            |          }
            |      }
            |   }
            |}
        """.trimMargin()
        )
        // Checks that the build associated with the promotion is the last one
        val plNode = data["projects"][0]["branches"][0]["promotionLevels"][0]
        assertEquals(pl.name, plNode["name"].asText())
        val runNodes = plNode["promotionRuns"]
        assertEquals(1, runNodes.size())
        val build = runNodes[0]["build"]
        assertEquals(build2.name, build["name"].asText())
    }

    @Test
    fun `Validation run statuses for a run for a validation stamp`() {
        project {
            branch {
                val vs = validationStamp()
                build("1") {
                    validate(vs, ValidationRunStatusID.STATUS_FAILED, description = "Validation failed").apply {
                        validationStatus(ValidationRunStatusID.STATUS_INVESTIGATING, "Investigating")
                        validationStatus(ValidationRunStatusID.STATUS_EXPLAINED, "Explained")
                    }
                    val data = run(
                        """{
                        projects (id: ${project.id}) {
                            branches (name: "${branch.name}") {
                                validationStamps {
                                    name
                                    validationRuns {
                                        validationRunStatuses {
                                            statusID {
                                                id
                                            }
                                            description
                                        }
                                    }
                                }
                            }
                        }
                    }"""
                    )
                    val validationRunStatuses =
                        data["projects"][0]["branches"][0]["validationStamps"][0]["validationRuns"][0]["validationRunStatuses"]
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
    fun `Project creation time`() {
        project {
            run(
                """{
                projects(id: $id) {
                    name
                    creation {
                        time
                    }
                }
            }"""
            ) { data ->
                assertEquals(name, data.path("projects").first().path("name").asText())
                val time = data.path("projects").first().path("creation").path("time").asText()
                assertTrue(time.isNotBlank(), "Creation date has been set")
            }
        }
    }

    @Test
    fun `Branch creation time`() {
        project {
            branch {
                run(
                    """{
                    projects(id: ${project.id}) {
                        name
                        branches {
                            name
                            creation {
                                time
                            }
                        }
                    }
                }"""
                ) { data ->
                    val branch = data.path("projects").first()
                        .path("branches").first()
                    val time = branch.path("creation").path("time").asText()
                    assertTrue(time.isNotBlank(), "Creation date has been set")
                }
            }
        }
    }

    @Test
    fun `Getting list of authorizations on a project for an admin`() {
        asAdmin {
            project {
                run(
                    """
                    {
                        project(id: $id) {
                            authorizations {
                                name
                                action
                                authorized
                            }
                        }
                    }
                """
                ) { data ->
                    val auths = data.path("project").path("authorizations")
                    // Configuration rights
                    assertEquals(
                        true,
                        auths
                            .find { it.path("name").asText() == "project" && it.path("action").asText() == "config" }
                            ?.path("authorized")?.asBoolean()
                    )
                    // Disabling rights
                    assertEquals(
                        true,
                        auths
                            .find { it.path("name").asText() == "project" && it.path("action").asText() == "disable" }
                            ?.path("authorized")?.asBoolean()
                    )
                }
            }
        }
    }

    @Test
    fun `Getting list of authorizations on a project for automation`() {
        asGlobalRole(Roles.GLOBAL_AUTOMATION) {
            project {
                run(
                    """
                    {
                        project(id: $id) {
                            authorizations {
                                name
                                action
                                authorized
                            }
                        }
                    }
                """
                ) { data ->
                    val auths = data.path("project").path("authorizations")
                    // Configuration rights
                    assertEquals(
                        true,
                        auths
                            .find { it.path("name").asText() == "project" && it.path("action").asText() == "config" }
                            ?.path("authorized")?.asBoolean()
                    )
                    // Disabling rights
                    assertEquals(
                        true,
                        auths
                            .find { it.path("name").asText() == "project" && it.path("action").asText() == "disable" }
                            ?.path("authorized")?.asBoolean()
                    )
                }
            }
        }
    }

}