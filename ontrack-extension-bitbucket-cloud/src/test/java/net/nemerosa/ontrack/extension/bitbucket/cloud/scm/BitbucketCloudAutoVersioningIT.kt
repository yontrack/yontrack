package net.nemerosa.ontrack.extension.bitbucket.cloud.scm

import tools.jackson.databind.node.NullNode
import io.mockk.every
import net.nemerosa.ontrack.extension.av.config.AutoApprovalMode
import net.nemerosa.ontrack.extension.av.config.AutoVersioningPushMode
import net.nemerosa.ontrack.extension.av.dispatcher.AutoVersioningOrder
import net.nemerosa.ontrack.extension.av.processing.AutoVersioningProcessingOutcome
import net.nemerosa.ontrack.extension.av.processing.AutoVersioningProcessingService
import net.nemerosa.ontrack.extension.bitbucket.cloud.AbstractBitbucketCloudTestSupport
import net.nemerosa.ontrack.extension.bitbucket.cloud.client.BitbucketCloudClientFactory
import net.nemerosa.ontrack.extension.bitbucket.cloud.client.DefaultBitbucketCloudClient
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudAuthType
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfiguration
import net.nemerosa.ontrack.extension.git.property.GitBranchConfigurationProperty
import net.nemerosa.ontrack.extension.git.property.GitBranchConfigurationPropertyType
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.RequestMatcher
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import java.util.*
import kotlin.test.assertEquals

/**
 * Auto-versioning order on a Bitbucket Cloud project, down to the REST calls, which are mocked.
 */
@ContextConfiguration(classes = [BitbucketCloudClientMockConfig::class])
@AsAdminTest
class BitbucketCloudAutoVersioningIT : AbstractBitbucketCloudTestSupport() {

    @Autowired
    private lateinit var clientFactory: BitbucketCloudClientFactory

    @Autowired
    private lateinit var autoVersioningProcessingService: AutoVersioningProcessingService

    private val repo = "https://api.bitbucket.org/2.0/repositories/ws/repo"

    private fun request(method: HttpMethod, uriRegex: String) = RequestMatcher { request ->
        val uri = request.uri.toString()
        if (request.method != method || !Regex(uriRegex).matches(uri)) {
            throw AssertionError("Expected $method $uriRegex but got ${request.method} $uri")
        }
    }

    @Test
    fun `Auto-versioning order ends with a merged pull request`() {
        val name = uid("C")
        val config = BitbucketCloudConfiguration(
            name = name,
            authType = BitbucketCloudAuthType.API_TOKEN,
            email = "bot@example.com",
            token = "bot-token",
            autoMergeEmail = "approver@example.com",
            autoMergeToken = "approver-token",
        )
        val approverConfig = config.copy(email = "approver@example.com", token = "approver-token")

        val botClient = DefaultBitbucketCloudClient(config)
        val approverClient = DefaultBitbucketCloudClient(approverConfig)
        every { clientFactory.getBitbucketCloudClient(config) } returns botClient
        every { clientFactory.getBitbucketCloudClient(approverConfig) } returns approverClient

        val bot = MockRestServiceServer.bindTo(botClient.template).ignoreExpectOrder(true).build()
        val approver = MockRestServiceServer.bindTo(approverClient.template).build()

        val commit = """{"hash": "abc", "date": "2026-09-01T10:20:30+00:00", "message": "Initial"}"""
        val pr = """
            {
                "id": 7,
                "title": "Upgrade",
                "state": "OPEN",
                "links": {"html": {"href": "https://bitbucket.org/ws/repo/pull-requests/7"}}
            }
        """.trimIndent()

        // Cleaning up any previous upgrade branch
        bot.expect(ExpectedCount.once(), request(HttpMethod.DELETE, "$repo/refs/branches/feature/version-2\\.0\\.0-.+"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))
        // Creating the upgrade branch from main
        bot.expect(ExpectedCount.once(), request(HttpMethod.GET, "$repo/refs/branches/main"))
            .andRespond(withSuccess("""{"name": "main", "target": $commit}""", MediaType.APPLICATION_JSON))
        bot.expect(ExpectedCount.once(), request(HttpMethod.POST, "$repo/refs/branches"))
            .andRespond(
                withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                    .body("""{"name": "feature/version-2.0.0", "target": $commit}""")
            )
        // Reading the version file
        bot.expect(ExpectedCount.manyTimes(), request(HttpMethod.GET, "$repo/src/main/gradle\\.properties"))
            .andRespond(withSuccess("version = 1.0.0\n", MediaType.TEXT_PLAIN))
        // Committing the new version
        bot.expect(ExpectedCount.once(), request(HttpMethod.POST, "$repo/src"))
            .andRespond(withStatus(HttpStatus.CREATED))
        // Pull request
        bot.expect(ExpectedCount.once(), request(HttpMethod.POST, "$repo/pullrequests"))
            .andExpect(jsonPath("$.destination.branch.name").value("main"))
            .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(pr))
        // Polling
        bot.expect(ExpectedCount.once(), request(HttpMethod.GET, "$repo/pullrequests/7"))
            .andRespond(withSuccess(pr, MediaType.APPLICATION_JSON))
        bot.expect(ExpectedCount.once(), request(HttpMethod.GET, "$repo/pullrequests/7/statuses\\?pagelen=100"))
            .andRespond(withSuccess("""{"values": [{"state": "SUCCESSFUL"}]}""", MediaType.APPLICATION_JSON))
        // Merge
        bot.expect(ExpectedCount.once(), request(HttpMethod.POST, "$repo/pullrequests/7/merge"))
            .andExpect(jsonPath("$.merge_strategy").value("squash"))
            .andExpect(jsonPath("$.close_source_branch").value(true))
            .andRespond(withSuccess(pr.replace("OPEN", "MERGED"), MediaType.APPLICATION_JSON))
        // Approval by the auto merge identity
        approver.expect(ExpectedCount.once(), request(HttpMethod.POST, "$repo/pullrequests/7/approve"))
            .andRespond(withSuccess("""{"approved": true}""", MediaType.APPLICATION_JSON))

        withDisabledConfigurationTest {
            bitbucketCloudConfigurationService.newConfiguration(config)
        }
        project {
            val source = this
            project {
                setBitbucketCloudProperty(config, repository = "repo", workspace = "ws")
                branch {
                    setProperty(
                        this,
                        GitBranchConfigurationPropertyType::class.java,
                        GitBranchConfigurationProperty("main", null, false, 0)
                    )
                    val order = AutoVersioningOrder(
                        uuid = UUID.randomUUID().toString(),
                        sourceProject = source.name,
                        sourceBuildId = null,
                        sourcePromotionRunId = null,
                        sourcePromotion = "GOLD",
                        sourceBackValidation = null,
                        branch = this,
                        targetPath = "gradle.properties",
                        targetRegex = null,
                        targetProperty = "version",
                        targetPropertyRegex = null,
                        targetPropertyType = null,
                        targetVersion = "2.0.0",
                        autoApproval = true,
                        upgradeBranchPattern = "feature/version-<version>",
                        postProcessing = null,
                        postProcessingConfig = NullNode.instance,
                        validationStamp = null,
                        autoApprovalMode = AutoApprovalMode.CLIENT,
                        reviewers = emptyList(),
                        prTitleTemplate = null,
                        prBodyTemplate = null,
                        prBodyTemplateFormat = null,
                        additionalPaths = emptyList(),
                        schedule = null,
                        pushMode = AutoVersioningPushMode.PR,
                    )

                    val outcome = autoVersioningProcessingService.process(order)
                    assertEquals(AutoVersioningProcessingOutcome.CREATED, outcome)

                    bot.verify()
                    approver.verify()
                }
            }
        }
    }

}
