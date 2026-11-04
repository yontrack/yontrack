package net.nemerosa.ontrack.extension.scm.mock

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.extension.scm.changelog.SCMCommit
import net.nemerosa.ontrack.graphql.schema.Mutation
import net.nemerosa.ontrack.graphql.support.TypedMutationProvider
import net.nemerosa.ontrack.model.security.GlobalSettings
import net.nemerosa.ontrack.model.security.SecurityService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Registering mock SCM content over GraphQL.
 *
 * [MockSCMController] does the same over REST, and is what the acceptance tests use for
 * everything else. These three exist because they are the ones the **demo seed** needs, and
 * the demo is reached through an ingress that routes `/graphql` and `/hook` to the backend
 * and everything else to the Next UI: a `POST /extension/scm/mock/commit` there answers 404
 * from the UI, whatever this instance is configured with.
 *
 * Like the rest of the mock SCM, they exist only where
 * `ontrack.config.extension.scm.mock.enabled` is set — and, unlike the REST endpoints, they
 * are reachable from outside the cluster, so each one asks for a global function rather than
 * settling for an authenticated user.
 */
@Component
@ConditionalOnProperty(
    prefix = "ontrack.config.extension.scm.mock",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
class MockSCMMutations(
    private val mockSCMExtension: MockSCMExtension,
    private val securityService: SecurityService,
) : TypedMutationProvider() {

    override val mutations: List<Mutation> = listOf(

        simpleMutation(
            name = "mockScmRegisterCommit",
            description = "Registers a commit in a mock SCM repository, creating the repository " +
                    "and the branch if they are not there yet.",
            input = MockScmRegisterCommitInput::class,
            outputName = "commit",
            outputDescription = "The registered commit",
            // The type the change log already publishes, rather than one of this mutation's
            // own: `simpleMutation` resolves the output against the schema, so a type nothing
            // else declares has to be registered as a `GQLType` to exist at all.
            outputType = SCMCommit::class,
        ) { input ->
            securityService.checkGlobalFunction(GlobalSettings::class.java)
            val repository = mockSCMExtension.repositoryOrCreate(input.repository)
            val id = repository.registerCommit(input.scmBranch, input.message, input.author, input.authorEmail)
            repository.getCommit(id) ?: error("Commit $id was not registered")
        },

        unitMutation<MockScmRegisterIssueInput>(
            name = "mockScmRegisterIssue",
            description = "Registers an issue in a mock SCM repository's issue service. Issues " +
                    "must be registered before the commits mentioning them: the mock SCM links a " +
                    "commit to an issue as the commit comes in.",
        ) { input ->
            securityService.checkGlobalFunction(GlobalSettings::class.java)
            mockSCMExtension.repositoryOrCreate(input.repository).registerIssue(
                input.key,
                input.message,
                *listOfNotNull(input.type?.takeIf { it.isNotBlank() }).toTypedArray(),
            )
        },

        unitMutation<MockScmDeleteRepositoryInput>(
            name = "mockScmDeleteRepository",
            description = "Deletes a mock SCM repository and everything it holds. The mock SCM " +
                    "keeps its repositories on the bean, where they outlive the entities pointing " +
                    "at them, and derives a commit id from the position of the commit on its " +
                    "branch — so anything registering the same commits twice starts here.",
        ) { input ->
            securityService.checkGlobalFunction(GlobalSettings::class.java)
            mockSCMExtension.deleteRepository(input.repository)
        },

    )

}

@APIDescription("Input for the `mockScmRegisterCommit` mutation")
data class MockScmRegisterCommitInput(
    @APIDescription("Name of the mock SCM repository")
    val repository: String,
    @APIDescription("Branch of the repository the commit goes on")
    val scmBranch: String,
    @APIDescription("Commit message")
    val message: String,
    @APIDescription("Name of the author of the commit, `unknown` if not set")
    val author: String? = null,
    @APIDescription("Email of the author of the commit")
    val authorEmail: String? = null,
)

@APIDescription("Input for the `mockScmRegisterIssue` mutation")
data class MockScmRegisterIssueInput(
    @APIDescription("Name of the mock SCM repository")
    val repository: String,
    @APIDescription("Issue key, of the `ABC-123` shape the mock issue service reads out of a commit message")
    val key: String,
    @APIDescription("Issue summary")
    val message: String,
    @APIDescription("Issue type, which is what a change log groups its issues by")
    val type: String? = null,
)

@APIDescription("Input for the `mockScmDeleteRepository` mutation")
data class MockScmDeleteRepositoryInput(
    @APIDescription("Name of the mock SCM repository")
    val repository: String,
)
