package net.nemerosa.ontrack.boot.support

import net.nemerosa.ontrack.boot.Application
import net.nemerosa.ontrack.extension.general.BuildLinkDisplayProperty
import net.nemerosa.ontrack.extension.general.BuildLinkDisplayPropertyType
import net.nemerosa.ontrack.extension.general.MessageProperty
import net.nemerosa.ontrack.extension.general.MessagePropertyType
import net.nemerosa.ontrack.extension.general.MessageType
import net.nemerosa.ontrack.extension.general.ReleaseProperty
import net.nemerosa.ontrack.extension.general.ReleasePropertyType
import net.nemerosa.ontrack.it.QueryCounter
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.TokenOptions
import net.nemerosa.ontrack.model.structure.TokensConstants
import net.nemerosa.ontrack.model.structure.TokensService
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The properties read by a GraphQL request are loaded in a number of queries which does not grow
 * with the number of entities it lists (#2081): the properties of the entities of a list are
 * loaded together, in one query, into the read memo of the request.
 *
 * Before #2081, with a cold cache, each listed entity cost its own queries: 26 for a list of 5
 * builds and 251 for 50, 43 for the branch page with 5 builds and 168 with 30, 27 for a project
 * page with 2 branches and 36 with 5. After #2081, these requests run 2, 4 and 2 queries, whatever
 * the number of entities: the budgets below.
 */
@SpringBootTest(
    classes = [Application::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
class PropertiesPerRequestHttpIT : AbstractJwtHttpIT() {

    @Autowired
    private lateinit var queryCounter: QueryCounter

    @Autowired
    private lateinit var tokensService: TokensService

    private lateinit var token: String

    @BeforeEach
    fun token() {
        token = asAdmin().call {
            tokensService.generateNewToken(TokenOptions(name = uid("ci-"))).value
        }
    }

    private fun graphQL(query: String, variables: Map<String, Any?>) {
        val response = client.send(
            HttpRequest.newBuilder(URI("http://localhost:$port/graphql"))
                .header(TokensConstants.HTTP_ONTRACK_TOKEN, token)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        mapOf("query" to query, "variables" to variables).asJson().toString()
                    )
                )
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        assertEquals(200, response.statusCode(), response.body())
        val errors = response.body().parseAsJson().path("errors")
        assertTrue(errors.isMissingNode || errors.isEmpty, "No error: ${response.body()}")
    }

    /**
     * Number of queries on the properties run by the threads of the HTTP server during the [code]
     */
    private fun propertiesQueries(code: () -> Unit): Int {
        val queries = queryCounter.record(code)
            .on("PROPERTIES")
            .filter { it.thread.startsWith("http-") }
        return queries.size
    }

    /**
     * A project whose builds show a release, and a branch with [count] builds, each with a release,
     * a message, a promotion and a validation.
     */
    private fun branchWithBuilds(count: Int): Branch = asAdmin().call {
        project<Branch> {
            property(BuildLinkDisplayPropertyType::class, BuildLinkDisplayProperty(useLabel = true))
            branch {
                val pl = promotionLevel()
                val vs = validationStamp()
                repeat(count) { no ->
                    build {
                        property(ReleasePropertyType::class, ReleaseProperty("1.0.$no"))
                        property(MessagePropertyType::class, MessageProperty(MessageType.INFO, "Build $no"))
                        promote(pl)
                        validate(vs)
                    }
                }
                this
            }
        }
    }

    private fun projectWithBranches(count: Int): Project = asAdmin().call {
        project {
            property(MessagePropertyType::class, MessageProperty(MessageType.INFO, "Project"))
            repeat(count) { no ->
                branch {
                    property(MessagePropertyType::class, MessageProperty(MessageType.INFO, "Branch $no"))
                    build {
                        property(ReleasePropertyType::class, ReleaseProperty("1.0.$no"))
                    }
                }
            }
        }
    }

    private fun buildsPage(branch: Branch, size: Int) = propertiesQueries {
        graphQL(BUILDS, mapOf("branchId" to branch.id(), "offset" to 0, "size" to size))
    }

    private fun branchPage(branch: Branch, size: Int) = propertiesQueries {
        graphQL(BRANCH, mapOf("id" to branch.id()))
        graphQL(BUILDS, mapOf("branchId" to branch.id(), "offset" to 0, "size" to size))
    }

    private fun projectPage(project: Project) = propertiesQueries {
        graphQL(PROJECT, mapOf("id" to project.id()))
    }

    @Test
    fun `The properties of a list of builds are loaded in a number of queries which does not depend on the number of builds`() {
        val small = buildsPage(branchWithBuilds(5), size = 5)
        val large = buildsPage(branchWithBuilds(50), size = 50)
        assertEquals(small, large, "Same number of queries for 5 and 50 builds")
        assertTrue(large <= BUILDS_BUDGET, "No more than $BUILDS_BUDGET queries for 50 builds: $large")
    }

    @Test
    fun `The properties of the branch page are loaded in a number of queries which does not depend on the number of builds`() {
        val small = branchPage(branchWithBuilds(5), size = 5)
        val large = branchPage(branchWithBuilds(30), size = 30)
        assertEquals(small, large, "Same number of queries for 5 and 30 builds")
        assertTrue(large <= BRANCH_PAGE_BUDGET, "No more than $BRANCH_PAGE_BUDGET queries for the branch page: $large")
    }

    @Test
    fun `The properties of the branches of a project are loaded in a number of queries which does not depend on the number of branches`() {
        val small = projectPage(projectWithBranches(2))
        val large = projectPage(projectWithBranches(5))
        assertEquals(small, large, "Same number of queries for 2 and 5 branches")
        assertTrue(large <= PROJECT_PAGE_BUDGET, "No more than $PROJECT_PAGE_BUDGET queries for the project page: $large")
    }

    companion object {

        /**
         * The properties of the project, then those of all the builds
         */
        private const val BUILDS_BUDGET = 2

        /**
         * The project and the branch for the branch itself, then the project again and the
         * builds for the list of builds, a second request
         */
        private const val BRANCH_PAGE_BUDGET = 4

        /**
         * The properties of the project, then those of all the branches
         */
        private const val PROJECT_PAGE_BUDGET = 2

        /**
         * Branch header of the branch page, as `BranchView` asks for it
         */
        private const val BRANCH = """
            query GetBranch(${'$'}id: Int!) {
                branch(id: ${'$'}id) {
                    id
                    name
                    displayName
                    disabled
                    project { id name }
                    authorizations { name action authorized }
                    properties { type { typeName name } editable value error }
                    information { type title data }
                    userMenuActions { groupId extension id name local arguments }
                }
            }
        """

        /**
         * Builds of the branch page, as `branchQueries.js` asks for them
         */
        private const val BUILDS = """
            query LoadBuilds(${'$'}branchId: Int!, ${'$'}offset: Int!, ${'$'}size: Int!) {
                branches(id: ${'$'}branchId) {
                    buildsPaginated(offset: ${'$'}offset, size: ${'$'}size) {
                        pageInfo { totalSize }
                        pageItems {
                            id
                            name
                            displayName
                            creation { time actor { kind displayName } }
                            assistedChange { assisted basis }
                            decorations { decorationType error data feature { id } }
                            promotionRuns(lastPerLevel: true) {
                                id
                                creation { time user }
                                description
                                annotatedDescription
                                build { id name }
                                promotionLevel { id name description image }
                                authorizations { name action authorized }
                            }
                            validations {
                                validationStamp {
                                    id
                                    name
                                    image
                                    description
                                    annotatedDescription
                                    dataType { descriptor { id } }
                                }
                                validationRuns(count: 1) {
                                    id
                                    runInfo { runTime sourceUri }
                                    lastStatus {
                                        creation { time user }
                                        description
                                        annotatedDescription
                                        statusID { id name }
                                    }
                                }
                            }
                            authorizations { name action authorized }
                        }
                    }
                }
            }
        """

        /**
         * Project page, as `ProjectView` asks for it
         */
        private const val PROJECT = """
            query GetProject(${'$'}id: Int!) {
                project(id: ${'$'}id) {
                    id
                    name
                    properties { type { typeName name } editable value error }
                    information { type title data }
                    authorizations { name action authorized }
                    branches(order: true, count: 6) {
                        id
                        name
                        displayName
                        disabled
                        favourite
                        decorations { decorationType error data feature { id } }
                    }
                }
            }
        """
    }
}
