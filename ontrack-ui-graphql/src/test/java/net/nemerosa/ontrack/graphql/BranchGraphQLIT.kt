package net.nemerosa.ontrack.graphql

import net.nemerosa.ontrack.extension.api.support.*
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.json.isNullOrNullNode
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.BranchFavouriteService
import net.nemerosa.ontrack.model.structure.NameDescription
import net.nemerosa.ontrack.test.TestUtils.uid
import net.nemerosa.ontrack.test.assertJsonNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.graphql.execution.ErrorType
import kotlin.jvm.optionals.getOrNull
import kotlin.test.*

@AsAdminTest
class BranchGraphQLIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var branchFavouriteService: BranchFavouriteService

    @Test
    fun `Branch by ID`() {
        project {
            branch {
                run("""{branches (id: ${id}) { name } }""") { data ->
                    assertEquals(
                            name,
                            data.path("branches").first()
                                    .path("name").asText()
                    )
                }
            }
        }
    }

    @Test
    fun `Branch by ID and project is not allowed`() {
        runWithError("""{branches (id: 1, project: "test") { name } }""", errorClassification = ErrorType.BAD_REQUEST)
    }

    @Test
    fun `Branch by project`() {
        project {
            branch("B1")
            branch("B2")
            run("""{branches (project: "${name}") { name } }""") { data ->
                assertEquals(
                        listOf("B2", "B1"),
                        data.path("branches").values().map { it.path("name").asText() }
                )
            }
        }
    }

    @Test
    fun `Branch by project and name`() {
        project {
            branch("B1")
            branch("B2")
            branch("C1")
            run("""{branches (project: "${project.name}", name: "C.*") { name } }""") { data ->
                assertEquals(
                        listOf("C1"),
                        data.path("branches").values().map { it.path("name").asText() }
                )
            }
        }
    }

    @Test
    fun `Branch signature`() {
        project {
            branch {
                run("""{branches (id: ${id}) { creation { user time } } }""") { data ->
                    val branch = data.path("branches").first()
                    val user = branch.path("creation").path("user").asText()
                    assertTrue(user.isNotBlank(), "User is defined")
                    val time = branch.path("creation").path("time").asText()
                    assertEquals('T', time[10])
                }
            }
        }
    }

    @Test
    fun `Branch without decorations`() {
        project {
            branch {
                run("""{
                    branches (id: ${id}) {
                        decorations {
                            decorationType
                            data
                            error
                        }
                    }   
                }""") { data ->
                    val decorations = data.path("branches").first()
                            .path("decorations")
                    assertJsonNotNull(decorations) {
                        assertTrue(isEmpty)
                    }
                }
            }
        }
    }

    @Test
    fun `Branch with decorations`() {
        project {
            branch {
                setProperty(this, TestDecoratorPropertyType::class.java, TestDecorationData("XXX", true))
                run("""{
                    branches (id: ${id}) {
                        decorations {
                            decorationType
                            data
                            error
                        }
                    }   
                }""") { data ->
                    val decoration = data.path("branches").first()
                            .path("decorations").first()
                    assertEquals(TestDecorator::class.java.name, decoration.path("decorationType").asText())
                    assertEquals("XXX", decoration.path("data").path("value").asText())
                }
            }
        }
    }

    @Test
    fun `Branch with filtered decorations and match`() {
        project {
            branch {
                setProperty(this, TestDecoratorPropertyType::class.java, TestDecorationData("XXX", true))
                run("""{
                    branches (id: ${id}) {
                        decorations(type: "${TestDecorator::class.java.name}") {
                            decorationType
                            data
                            error
                        }
                    }   
                }""") { data ->
                    val decoration = data.path("branches").first()
                            .path("decorations").first()
                    assertEquals(TestDecorator::class.java.name, decoration.path("decorationType").asText())
                    assertEquals("XXX", decoration.path("data").path("value").asText())
                }
            }
        }
    }

    @Test
    fun `Branch with filtered decorations and no match`() {
        project {
            branch {
                setProperty(this, TestDecoratorPropertyType::class.java, TestDecorationData("XXX", true))
                run("""{
                    branches (id: ${id}) {
                        decorations(type: "unknown.Decorator") {
                            decorationType
                            data
                            error
                        }
                    }   
                }""") { data ->
                    val decorations = data.path("branches").first()
                            .path("decorations")
                    assertTrue(decorations.isEmpty)
                }
            }
        }
    }

    @Test
    fun `Promotion level branch reference`() {
        project {
            branch {
                val pl = promotionLevel()
                run("""{
                    branches (id: ${pl.branch.id}) {
                        promotionLevels {
                            branch {
                                id
                                project {
                                    id
                                }
                            }
                        }
                    }
                }""") { data ->
                    val p = data.path("branches").first()
                            .path("promotionLevels").first()
                    assertEquals(pl.branch.id(), p.path("branch").path("id").asInt())
                    assertEquals(pl.project.id(), p.path("branch").path("project").path("id").asInt())
                }
            }
        }
    }

    @Test
    fun `Validation stamp branch reference`() {
        project {
            branch {
                val vs = validationStamp()
                run("""{
                    branches (id: ${vs.branch.id}) {
                        validationStamps {
                            branch {
                                id
                                project {
                                    id
                                }
                            }
                        }
                    }
                }""") { data ->
                    val v = data.path("branches").first()
                            .path("validationStamps").first()
                    assertEquals(vs.branch.id(), v.path("branch").path("id").asInt())
                    assertEquals(vs.project.id(), v.path("branch").path("project").path("id").asInt())
                }
            }
        }
    }

    @Test
    fun `Branches filtered by property type`() {
        // Branches
        val p1 = doCreateBranch()
        /*val p2 = */ doCreateBranch()
        val p3 = doCreateBranch()
        val p4 = doCreateBranch()
        // Properties
        setProperty(p1, TestSimplePropertyType::class.java, TestSimpleProperty("P1"))
        setProperty(p3, TestSimplePropertyType::class.java, TestSimpleProperty("P3"))
        setProperty(p4, TestSimplePropertyType::class.java, TestSimpleProperty("X1"))
        // Looks for projects having this property
        run("""{
            branches(withProperty: {type: "${TestSimplePropertyType::class.java.name}"}) {
                name
            }
        }""") { data ->
            assertEquals(
                    setOf(p1.name, p3.name, p4.name),
                    data.path("branches")
                            .values().map { it.path("name").asText() }
                            .toSet()
            )
        }
    }

    @Test
    fun `Branches filtered by property type and value pattern`() {
        // Branches
        val p1 = doCreateBranch()
        /*def p2 = */ doCreateBranch()
        val p3 = doCreateBranch()
        val p4 = doCreateBranch()
        // Properties
        setProperty(p1, TestSimplePropertyType::class.java, TestSimpleProperty("P1"))
        setProperty(p3, TestSimplePropertyType::class.java, TestSimpleProperty("P3"))
        setProperty(p4, TestSimplePropertyType::class.java, TestSimpleProperty("X1"))
        // Looks for projects having this property
        run("""{
            branches(withProperty: {type: "${TestSimplePropertyType::class.java.name}", value: "P"}) {
                name
            }
        }""") { data ->
            assertEquals(
                    setOf(p1.name, p3.name),
                    data.path("branches")
                            .values().map { it.path("name").asText() }
                            .toSet()
            )
        }
    }

    @Test
    fun `Branches filtered by property type and value`() {
        // Branches
        val p1 = doCreateBranch()
        /*def p2 = */ doCreateBranch()
        val p3 = doCreateBranch()
        val p4 = doCreateBranch()
        // Properties
        setProperty(p1, TestSimplePropertyType::class.java, TestSimpleProperty("P1"))
        setProperty(p3, TestSimplePropertyType::class.java, TestSimpleProperty("P3"))
        setProperty(p4, TestSimplePropertyType::class.java, TestSimpleProperty("X1"))
        // Looks for projects having this property
        run("""{
            branches(withProperty: {type: "${TestSimplePropertyType::class.java.name}", value: "P1"}) {
                name
            }
        }""") { data ->
            assertEquals(
                    setOf(p1.name),
                    data.path("branches")
                            .values().map { it.path("name").asText() }
                            .toSet()
            )
        }
    }

    @Test
    fun `Creating a branch for a project name`() {
        asAdmin {
            project {
                val data = run("""
                    mutation {
                        createBranch(input: {projectName: "$name", name: "main"}) {
                            branch {
                                id
                                name
                            }
                            errors {
                                message
                                exception
                            }
                        }
                    }
                """)
                // Checks the branch has been created
                assertNotNull(structureService.findBranchByName(name, "main").getOrNull(), "Branch has been created") {
                    assertFalse(it.isDisabled, "Branch is not disabled")
                }
                // Checks the data
                val branch = data["createBranch"]["branch"]
                assertTrue(branch["id"].asInt() > 0, "ID is set")
                assertEquals("main", branch["name"].asText(), "Name is OK")
                assertTrue(data["createBranch"]["errors"].isNullOrNullNode(), "No error")
            }
        }
    }

    @Test
    fun `Creating a branch for a project ID`() {
        asAdmin {
            project {
                val data = run("""
                    mutation {
                        createBranch(input: {projectId: ${id}, name: "main"}) {
                            branch {
                                id
                                name
                            }
                            errors {
                                message
                                exception
                            }
                        }
                    }
                """)
                // Checks the branch has been created
                assertNotNull(structureService.findBranchByName(name, "main").getOrNull(), "Branch has been created") {
                    assertFalse(it.isDisabled, "Branch is not disabled")
                }
                // Checks the data
                val branch = data["createBranch"]["branch"]
                assertTrue(branch["id"].asInt() > 0, "ID is set")
                assertEquals("main", branch["name"].asText(), "Name is OK")
                assertTrue(data["createBranch"]["errors"].isNullOrNullNode(), "No error")
            }
        }
    }

    @Test
    fun `Creating a branch with missing project ID and name`() {
        asAdmin {
            project {
                val data = run("""
                    mutation {
                        createBranch(input: {name: "main"}) {
                            branch {
                                id
                                name
                            }
                            errors {
                                message
                                exception
                            }
                        }
                    }
                """)
                // Checks the errors
                val error = data["createBranch"]["errors"][0]
                assertEquals("Project ID or name is required", error["message"].asText())
                assertEquals("net.nemerosa.ontrack.graphql.schema.ProjectIdOrNameMissingException", error["exception"].asText())
                assertTrue(data["createBranch"]["branch"].isNullOrNullNode(), "Branch not returned")
            }
        }
    }

    @Test
    fun `Creating a branch with both project ID and name`() {
        asAdmin {
            project {
                val data = run("""
                    mutation {
                        createBranch(input: {projectId: $id, projectName: "$name", name: "main"}) {
                            branch {
                                id
                                name
                            }
                            errors {
                                message
                                exception
                            }
                        }
                    }
                """)
                // Checks the errors
                val error = data["createBranch"]["errors"][0]
                assertEquals("Project ID or name is required, not both.", error["message"].asText())
                assertEquals("net.nemerosa.ontrack.graphql.schema.ProjectIdAndNameProvidedException", error["exception"].asText())
                assertTrue(data["createBranch"]["branch"].isNullOrNullNode(), "Branch not returned")
            }
        }
    }

    @Test
    fun `Creating a branch for a project ID but name already exists`() {
        asAdmin {
            project {
                branch(name = "main")
                val data = run("""
                    mutation {
                        createBranch(input: {projectId: ${id}, name: "main"}) {
                            branch {
                                id
                                name
                            }
                            errors {
                                message
                                exception
                            }
                        }
                    }
                """)
                // Checks the errors
                val error = data["createBranch"]["errors"][0]
                assertEquals("Branch name already exists: main", error["message"].asText())
                assertEquals("net.nemerosa.ontrack.model.exceptions.BranchNameAlreadyDefinedException", error["exception"].asText())
                assertTrue(data["createBranch"]["branch"].isNullOrNullNode(), "Branch not returned")
            }
        }
    }

    @Test
    fun `Creating a branch for a project ID but name is invalid`() {
        asAdmin {
            project {
                val data = run("""
                    mutation {
                        createBranch(input: {projectId: ${id}, name: "main with space"}) {
                            branch {
                                id
                                name
                            }
                            errors {
                                message
                                exception
                            }
                        }
                    }
                """)
                // Checks the errors
                val error = data["createBranch"]["errors"][0]
                assertEquals("The name can only have letters, digits, dots (.), dashes (-) or underscores (_).", error["message"].asText())
                assertEquals("net.nemerosa.ontrack.graphql.support.MutationInputValidationException", error["exception"].asText())
                assertTrue(data["createBranch"]["branch"].isNullOrNullNode(), "Branch not returned")
            }
        }
    }

    @Test
    fun `Creating a branch in get mode from a project ID`() {
        asAdmin {
            project {
                val data = run(
                        """
                        mutation  {
                            createBranchOrGet(input: {projectId: $id, name: "main"}) {
                                branch {
                                    id
                                    name
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
                assertNoUserError(data, "createBranchOrGet")
                val node = data["createBranchOrGet"]["branch"]
                assertEquals("main", node["name"].asText())
            }
        }
    }

    @Test
    fun `Creating a branch in get mode from a project name`() {
        asAdmin {
            project {
                val data = run(
                        """
                        mutation  {
                            createBranchOrGet(input: {projectName: "$name", name: "main"}) {
                                branch {
                                    id
                                    name
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
                assertNoUserError(data, "createBranchOrGet")
                val node = data["createBranchOrGet"]["branch"]
                assertEquals("main", node["name"].asText())
            }
        }
    }

    @Test
    fun `Creating a branch in get mode for an existing branch from a project ID`() {
        asAdmin {
            project {
                val branch = branch(name = "main")
                val data = run(
                        """
                        mutation  {
                            createBranchOrGet(input: {projectId: $id, name: "main"}) {
                                branch {
                                    id
                                    name
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
                assertNoUserError(data, "createBranchOrGet")
                val node = data["createBranchOrGet"]["branch"]
                assertEquals("main", node["name"].asText())
                assertEquals(branch.id(), node["id"].asInt())
            }
        }
    }

    @Test
    fun `Creating a branch in get mode for an existing branch from a project name`() {
        asAdmin {
            project {
                val branch = branch(name = "main")
                val data = run(
                        """
                        mutation  {
                            createBranchOrGet(input: {projectName: "$name", name: "main"}) {
                                branch {
                                    id
                                    name
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
                assertNoUserError(data, "createBranchOrGet")
                val node = data["createBranchOrGet"]["branch"]
                assertEquals("main", node["name"].asText())
                assertEquals(branch.id(), node["id"].asInt())
            }
        }
    }

    @Test
    fun `All favourite branches`() {
        val account = doCreateAccount()
        val branch1 = project<Branch> {
            branch {}
            branch {
                asConfigurableAccount(account).withView(this).execute {
                    branchFavouriteService.setBranchFavourite(this, true)
                }
            }
        }
        val branch2 = project<Branch> {
            branch {}
            branch {
                asConfigurableAccount(account).withView(this).execute {
                    branchFavouriteService.setBranchFavourite(this, true)
                }
            }
        }
        // Gets ALL the favourite branches
        val data = asConfigurableAccount(account).withView(branch1).withView(branch2).call {
            run("""
                    {
                        branches(favourite: true) {
                            id
                        }
                    }
                """)
        }
        val branchIds: Set<Int> = data["branches"].values().map { it["id"].asInt() }.toSet()
        assertEquals(
                setOf(branch1.id(), branch2.id()),
                branchIds
        )
    }

    @Test
    fun `Favourite branch on one project`() {
        val account = doCreateAccount()
        project {
            val fav = branch {
                asConfigurableAccount(account).withView(this).execute {
                    branchFavouriteService.setBranchFavourite(this, true)
                }
            }
            branch {}
            // Gets the favourite branches
            val data = asConfigurableAccount(account).withView(this).call {
                run("""
                    {
                        branches(project: "${this.name}", favourite: true) {
                            id
                        }
                    }
                """)
            }
            val branchIds: Set<Int> = data["branches"].values().map { it["id"].asInt() }.toSet()
            assertEquals(
                    setOf(fav.id()),
                    branchIds
            )
        }
    }

    @Test
    fun `Branch by name on two different projects`() {
        val name = uid("B")

        val p1 = doCreateProject()
        val b1 = doCreateBranch(p1, NameDescription.nd(name, ""))
        doCreateBranch(p1, NameDescription.nd("B2", ""))
        val p2 = doCreateProject()
        val b2 = doCreateBranch(p2, NameDescription.nd(name, ""))

        val data = run("""{branches (name: "$name") { id } }""")
        assertEquals(
                setOf(b1.id(), b2.id()),
                data["branches"].values().map { it["id"].asInt() }.toSet()
        )
    }

    @Test
    fun `Last promotion run only`() {
        // Creates a branch
        val branch = doCreateBranch()
        // ... a promotion level
        val pl = doCreatePromotionLevel(branch, NameDescription.nd("COPPER", ""))
        // ... one build
        val build = doCreateBuild(branch, NameDescription.nd("1", ""))
        // ... and promotes it twice
        doPromote(build, pl, "Once")
        doPromote(build, pl, "Twice")
        // Asks for the promotion runs of the build
        val data = run("""{
            |branches(id: ${branch.id}) {
            |   builds {
            |       promotionRuns(lastPerLevel: true) {
            |           promotionLevel {
            |               name
            |           }
            |           description
            |       }
            |   }
            |}
            |}""".trimMargin())
        // Gets the first build
        val buildNode = data.path("branches").get(0).path("builds").get(0)
        // Gets the promotion runs
        val promotionRuns = buildNode.path("promotionRuns")
        assertEquals(1, promotionRuns.size())
        val promotionRun = promotionRuns.get(0)
        assertEquals("Twice", promotionRun.path("description").asText())
        assertEquals("COPPER", promotionRun.path("promotionLevel").path("name").asText())
    }

    @Test
    fun `Getting list of authorizations on a branch for an admin`() {
        asAdmin {
            project {
                branch {
                    run(
                        """
                        {
                            branch(id: $id) {
                                authorizations {
                                    name
                                    action
                                    authorized
                                }
                            }
                        }
                    """
                    ) { data ->
                        val auths = data.path("branch").path("authorizations")
                        // Disabling rights
                        assertEquals(
                            true,
                            auths
                                .find {
                                    it.path("name").asText() == "branch" && it.path("action").asText() == "disable"
                                }
                                ?.path("authorized")?.asBoolean()
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `Getting list of authorizations on a project for automation`() {
        asGlobalRole(Roles.GLOBAL_AUTOMATION) {
            project {
                branch {
                    run(
                        """
                        {
                            branch(id: $id) {
                                authorizations {
                                    name
                                    action
                                    authorized
                                }
                            }
                        }
                    """
                    ) { data ->
                        val auths = data.path("branch").path("authorizations")
                        // Disabling rights
                        assertEquals(
                            true,
                            auths
                                .find {
                                    it.path("name").asText() == "branch" && it.path("action").asText() == "disable"
                                }
                                ?.path("authorized")?.asBoolean()
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `Deleting a branch by ID`() {
        project {
            branch {
                run(
                    """
                        mutation {
                            deleteBranchById(input: {id: $id}) {
                                errors {
                                    message
                                }
                            }
                        }
                    """.trimIndent()
                ) { data ->
                    checkGraphQLUserErrors(data, "deleteBranchById")
                    assertNull(
                        structureService.findBuildByID(id),
                        "Branch has been deleted"
                    )
                }
            }
        }
    }

}