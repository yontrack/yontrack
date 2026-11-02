package net.nemerosa.ontrack.boot.support

import net.nemerosa.ontrack.boot.Application
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AgentTestSupport
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.format
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.preferences.PreferencesService
import net.nemerosa.ontrack.model.security.Account
import net.nemerosa.ontrack.model.security.AgentService
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.model.structure.BranchFavouriteService
import net.nemerosa.ontrack.model.structure.ProjectFavouriteService
import net.nemerosa.ontrack.model.structure.TokenOptions
import net.nemerosa.ontrack.model.structure.TokensConstants
import net.nemerosa.ontrack.model.structure.TokensService
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What a read-only token is refused, and what it is not, as a client sees it over HTTP.
 *
 * Not transactional: the token the call authenticates with must be committed for the server, on
 * its own threads, to find it.
 */
@SpringBootTest(
    classes = [Application::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ReadOnlyTokenHttpIT : AbstractDSLTestSupport() {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var tokensService: TokensService

    @Autowired
    private lateinit var agentService: AgentService

    @Autowired
    private lateinit var agentTestSupport: AgentTestSupport

    @Autowired
    private lateinit var preferencesService: PreferencesService

    @Autowired
    private lateinit var projectFavouriteService: ProjectFavouriteService

    @Autowired
    private lateinit var branchFavouriteService: BranchFavouriteService

    private val client: HttpClient = HttpClient.newHttpClient()

    private fun account(role: String = Roles.GLOBAL_ADMINISTRATOR): Account =
        asAdmin { doCreateAccountWithGlobalRole(role) }

    private fun token(account: Account, readOnly: Boolean = true, name: String = "ro"): String = asAdmin {
        tokensService.generateToken(account.id(), TokenOptions(name = name, readOnly = readOnly)).value
    }

    private fun request(
        token: String,
        method: String,
        path: String,
        body: String? = null,
    ): HttpResponse<String> =
        client.send(
            HttpRequest.newBuilder(URI("http://localhost:$port$path"))
                .header(TokensConstants.HTTP_ONTRACK_TOKEN, token)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .method(
                    method,
                    body?.let { HttpRequest.BodyPublishers.ofString(it) } ?: HttpRequest.BodyPublishers.noBody()
                )
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    private fun graphql(token: String, query: String, operationName: String? = null): JsonNode {
        val response = request(
            token = token,
            method = "POST",
            path = "/graphql",
            body = mapOf("query" to query, "operationName" to operationName).asJson().format(),
        )
        assertEquals(200, response.statusCode(), response.body())
        return response.body().parseAsJson()
    }

    /**
     * The mutation is refused with a top-level error saying why, and none of its fields ran.
     */
    private fun assertMutationRefused(token: String, query: String, operationName: String? = null) {
        val json = graphql(token, query, operationName)
        assertEquals(
            "read-only token 'ro' may not run a mutation",
            json.path("errors").path(0).path("message").asString(),
            json.toString(),
        )
        assertTrue(json.path("errors").path(0).path("path").isMissingNode, "Top-level error: $json")
        assertTrue(json.path("data").isNull || json.path("data").isMissingNode, "Nothing ran: $json")
    }

    private fun assertRestRefused(token: String, method: String, path: String, body: String? = null) {
        val response = request(token, method, path, body)
        assertEquals(403, response.statusCode(), "$method $path: ${response.body()}")
        assertEquals(
            "read-only token 'ro' may not call $method $path",
            response.body().parseAsJson().path("message").asString(),
        )
    }

    @Test
    fun `A mutation from a MutationProvider is refused`() {
        val name = uid("p")
        assertMutationRefused(
            token(account()),
            """mutation { createProject(input: {name: "$name"}) { project { id } } }""",
        )
        assertTrue(asAdmin { structureService.findProjectByName(name).isEmpty }, "No project created")
    }

    @Test
    fun `A mutation from a MutationMapping is refused`() {
        assertMutationRefused(token(account()), "mutation { ping }")
    }

    @Test
    fun `A document naming a mutation among several operations is refused`() {
        val token = token(account())
        val document = "query Read { ping } mutation Write { ping }"
        assertMutationRefused(token, document, "Write")
        assertMutationRefused(token, document, "Read")
    }

    @Test
    fun `A full token runs mutations as before`() {
        val json = graphql(token(account(), readOnly = false), "mutation { ping }")
        assertEquals("pong", json.path("data").path("ping").asString(), json.toString())
    }

    @Test
    fun `A read-only token cannot generate a token, and nothing changes`() {
        val account = account()
        assertMutationRefused(
            token(account),
            """mutation { generateToken(input: {name: "full"}) { token { value } } }""",
        )
        assertEquals(listOf("ro"), asAdmin { tokensService.getTokens(account).map { it.name } })
    }

    @Test
    fun `A read-only token cannot revoke a token, not even its own, and nothing changes`() {
        val account = account()
        val token = token(account)
        token(account, readOnly = false, name = "full")
        listOf("ro", "full").forEach { name ->
            assertMutationRefused(token, """mutation { revokeToken(input: {name: "$name"}) { errors { message } } }""")
        }
        assertEquals(listOf("full", "ro"), asAdmin { tokensService.getTokens(account).map { it.name } })
    }

    @Test
    fun `A read-only token cannot register an agent, and nothing changes`() {
        val account = account()
        assertMutationRefused(
            token(account),
            """mutation { registerAgent(input: {slug: "${uid("a-").lowercase()}", displayName: "A", tool: "Other"}) { agent { id } } }""",
        )
        assertTrue(asAdmin { agentService.getAgentsOwnedBy(account) }.isEmpty())
    }

    @Test
    fun `A read-only token cannot generate a token for an agent, and nothing changes`() {
        val account = account()
        val agent = agentTestSupport.registerAgent(account)
        assertMutationRefused(
            token(account),
            """mutation { generateAgentToken(input: {id: ${agent.account.id()}, name: "full"}) { token { value } } }""",
        )
        assertEquals(listOf("ci"), asAdmin { tokensService.getTokens(agent.account).map { it.name } })
    }

    @Test
    fun `A read-only token cannot set the preferences, and nothing changes`() {
        val account = account()
        val before = asAdmin { preferencesService.getPreferences(account) }
        assertMutationRefused(
            token(account),
            """mutation { setPreferences(input: {dashboardUuid: "${uid("d-")}"}) { errors { message } } }""",
        )
        assertEquals(before, asAdmin { preferencesService.getPreferences(account) })
    }

    @Test
    fun `A read-only token cannot favourite a project or a branch, and nothing changes`() {
        val account = account()
        val token = token(account)
        val branch = asAdmin { project().branch() }
        assertMutationRefused(
            token,
            """mutation { favouriteProject(input: {id: ${branch.project.id()}}) { errors { message } } }""",
        )
        assertMutationRefused(
            token,
            """mutation { favouriteBranch(input: {id: ${branch.id()}}) { errors { message } } }""",
        )
        asFixedAccount(account) {
            assertFalse(projectFavouriteService.isProjectFavourite(branch.project))
            assertFalse(branchFavouriteService.isBranchFavourite(branch))
        }
    }

    @Test
    fun `POST, PUT, PATCH and DELETE on REST are refused, with the message`() {
        val account = account()
        val token = token(account)
        assertRestRefused(token, "POST", "/rest/tokens/create/full")
        assertRestRefused(token, "POST", "/rest/tokens/revokeAll")
        assertRestRefused(token, "PUT", "/rest/anything")
        assertRestRefused(token, "PATCH", "/rest/anything")
        assertRestRefused(token, "DELETE", "/rest/anything")
        assertEquals(listOf("ro"), asAdmin { tokensService.getTokens(account).map { it.name } })
    }

    @Test
    fun `The two POST which are reads work`() {
        val token = token(account())
        val graphql = graphql(token, "{ ping }")
        assertEquals("pong", graphql.path("data").path("ping").asString(), graphql.toString())
        val search = request(
            token = token,
            method = "POST",
            path = "/rest/search",
            body = mapOf("token" to "any", "type" to "project").asJson().format(),
        )
        assertEquals(200, search.statusCode(), search.body())
    }

    @Test
    fun `A path below the exempted ones is refused`() {
        val token = token(account())
        assertRestRefused(token, "POST", "/graphql/any")
        assertRestRefused(token, "POST", "/rest/search/index/reset", "{}")
        assertRestRefused(token, "POST", "/rest/search/")
        assertRestRefused(token, "PUT", "/graphql", "{}")
    }

    @Test
    fun `GET on REST works`() {
        val response = request(token(account()), "GET", "/rest/search/types")
        assertEquals(200, response.statusCode(), response.body())
    }

    @Test
    fun `A full token is not concerned by the REST check`() {
        val response = request(token(account(), readOnly = false), "PUT", "/rest/anything")
        assertFalse(response.body().contains("read-only token"), response.body())
    }

    @Test
    fun `A read on a function the policy denies says why`() {
        val json = graphql(token(account()), "{ accounts { id } }")
        assertTrue(
            json.path("errors").path(0).path("message").asString().startsWith("read-only token 'ro' may not use "),
            json.toString(),
        )
    }

    @Test
    fun `A read-only token lists its own tokens and those of its agents, not those of other accounts`() {
        val group = asAdmin { doCreateAccountGroupWithGlobalRole(Roles.GLOBAL_ADMINISTRATOR) }
        val account = asAdmin { doCreateAccount(group) }
        val other = asAdmin { doCreateAccount(group) }
        val otherTokenName = uid("other-")
        token(other, readOnly = false, name = otherTokenName)
        val agent = agentTestSupport.registerAgent(account)
        val token = token(account)

        val own = graphql(token, "{ user { account { tokens { name } } } }")
        assertEquals(
            listOf("ro"),
            own.path("data").path("user").path("account").path("tokens").toList().map { it.path("name").asString() },
        )

        val agents = graphql(token, "{ user { account { agents { email tokens { name } } } } }")
        assertEquals(
            listOf("ci"),
            agents.path("data").path("user").path("account").path("agents")
                .single { it.path("email").asString() == agent.account.email }
                .path("tokens").toList().map { it.path("name").asString() },
            agents.toString(),
        )

        val others = graphql(token, "{ user { assignedGroups { accounts { email tokens { name } } } } }")
        assertFalse(otherTokenName in others.toString(), others.toString())
        assertEquals(
            "read-only token 'ro' may not use AccountManagement",
            others.path("errors").path(0).path("message").asString(),
            others.toString(),
        )
    }

    @Test
    fun `A token reads whether it is read-only, and the attribute of the tokens`() {
        val account = account(Roles.GLOBAL_READ_ONLY)
        val readOnly = token(account)
        val full = token(account, readOnly = false, name = "full")
        val query = "{ user { tokenReadOnly account { tokens { name readOnly value } } } }"

        val user = graphql(readOnly, query).path("data").path("user")
        assertTrue(user.path("tokenReadOnly").asBoolean())
        assertFalse(graphql(full, query).path("data").path("user").path("tokenReadOnly").asBoolean())
        assertFalse(graphql(full, query).path("data").path("user").path("tokenReadOnly").isNull)

        val tokens = user.path("account").path("tokens").associateBy { it.path("name").asString() }
        assertTrue(tokens.getValue("ro").path("readOnly").asBoolean())
        assertFalse(tokens.getValue("full").path("readOnly").asBoolean())
        assertEquals("", tokens.getValue("ro").path("value").asString(), "The value is never read back")
    }

}
