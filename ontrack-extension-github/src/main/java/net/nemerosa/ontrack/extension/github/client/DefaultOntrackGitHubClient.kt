package net.nemerosa.ontrack.extension.github.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import net.nemerosa.ontrack.common.BaseException
import net.nemerosa.ontrack.common.runIf
import net.nemerosa.ontrack.common.untilTimeout
import net.nemerosa.ontrack.extension.git.model.GitPullRequest
import net.nemerosa.ontrack.extension.github.app.GitHubAppRateLimitMetricsNames
import net.nemerosa.ontrack.extension.github.app.GitHubAppTokenService
import net.nemerosa.ontrack.extension.github.model.*
import net.nemerosa.ontrack.extension.github.support.parseLocalDateTime
import net.nemerosa.ontrack.git.support.GitConnectionConfig
import net.nemerosa.ontrack.git.support.GitConnectionRetry
import net.nemerosa.ontrack.json.*
import net.nemerosa.ontrack.model.metrics.increment
import org.apache.commons.codec.binary.Base64
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.getForObject
import java.net.URLEncoder
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

/**
 * GitHub client which uses the GitHub Rest API at https://docs.github.com/en/rest
 */
class DefaultOntrackGitHubClient(
    private val configuration: GitHubEngineConfiguration,
    private val gitHubAppTokenService: GitHubAppTokenService,
    private val timeout: Duration = Duration.ofSeconds(60),
    private val retries: UInt = 3u,
    private val interval: Duration = Duration.ofSeconds(30),
    private val gitConnectionConfig: GitConnectionConfig,
    private val notFoundRetries: UInt = 6u,
    private val notFoundInterval: Duration = Duration.ofSeconds(5),
    private val meterRegistry: MeterRegistry,
) : OntrackGitHubClient {

    private val logger: Logger = LoggerFactory.getLogger(OntrackGitHubClient::class.java)

    override fun getRateLimit(): GitHubRateLimit? =
        try {
            createGitHubRestTemplate()("Gets rate limit") {
                getForObject<JsonNode>("/rate_limit").getJsonField("resources")?.run {
                    parse()
                }
            }
        } catch (_: HttpClientErrorException.NotFound) {
            // Rate limit not supported
            null
        } catch (any: Exception) {
            logger.error(
                "Cannot get the rate limit for GitHub at ${configuration.url} (config = ${configuration.name})",
                any
            )
            // No rate limit
            null
        }

    override val organizations: List<GitHubUser>
        get() = if (configuration.authenticationType == GitHubAuthenticationType.APP) {
            listOfNotNull(
                gitHubAppTokenService.getAppInstallationAccount(configuration)?.run {
                    GitHubUser(
                        login = login,
                        url = url,
                    )
                }
            )
        } else {
            // Getting a client
            val client = createGitHubRestTemplate()
            // Gets the organization names
            client("Gets list of organizations") {
                getForObject<JsonNode>("/user/orgs?per_page=100").map { node ->
                    node.parse()
                }
            }
        }

    override fun findRepositoriesByOrganization(organization: String): List<GitHubRepository> =
        paginateGraphQL(
            message = "Getting repositories for organization $organization",
            query = """
                query OrgRepositories(${'$'}login: String!, ${'$'}after: String) {
                  organization(login: ${'$'}login) {
                    repositories(first: 100, after: ${'$'}after) {
                      pageInfo {
                        hasNextPage
                        endCursor
                      }
                      nodes {
                        name
                        description
                        updatedAt
                        createdAt
                      }
                    }
                  }
                }
            """,
            variables = mapOf("login" to organization),
            collectionAt = listOf("organization", "repositories"),
            nodes = true
        ) { node ->
            GitHubRepository(
                name = node.path("name").asText(),
                description = node.path("description").asText(),
                lastUpdate = node.path("updatedAt")?.asText()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { parseLocalDateTime(it) },
                createdAt = node.path("createdAt")?.asText()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { parseLocalDateTime(it) }
            )
        } ?: throw GitHubNoGraphQLResponseException("Getting repositories for organization $organization")

    private fun getRepositoryParts(repository: String): Pair<String, String> {
        val login = repository.substringBefore("/")
        val name = repository.substringAfter("/")
        return login to name
    }

    override fun getIssue(repository: String, id: Int): GitHubIssue? {
        // Logging
        logger.debug("[github] Getting issue {}/{}", repository, id)
        // Getting a client
        val client = createGitHubRestTemplate()
        // Gets the repository for this project
        val (owner, name) = getRepositoryParts(repository)
        // Gets the issue
        return try {
            client("Get issue $repository#$id") {
                getForObject<JsonNode>("/repos/$owner/$name/issues/$id").let {
                    GitHubIssue(
                        id = id,
                        url = it.getRequiredTextField("html_url"),
                        summary = it.getRequiredTextField("title"),
                        body = it.getTextField("body") ?: "",
                        bodyHtml = it.getTextField("body") ?: "",
                        assignee = it.getUserField("assignee"),
                        labels = it.getLabels(),
                        state = it.getState(),
                        milestone = it.getMilestone(),
                        createdAt = it.getCreatedAt(),
                        updateTime = it.getUpdatedAt(),
                        closedAt = it.getClosedAt(),
                    )
                }
            }
        } catch (ex: GitHubErrorsException) {
            if (ex.status == 404) {
                null
            } else {
                throw ex
            }
        }
    }

    override fun getOrganizationTeams(login: String): List<GitHubTeam>? =
        paginateGraphQL(
            message = "Getting teams for $login organization",
            query = """
               query OrgTeams(${'$'}login: String!, ${'$'}after: String) {
                  organization(login: ${'$'}login) {
                    teams(first: 100, after: ${'$'}after) {
                      pageInfo {
                        hasNextPage
                        endCursor
                      }
                      nodes {
                        slug
                        name
                        description
                        url
                      }
                    }
                  }
                } 
            """,
            variables = mapOf(
                "login" to login
            ),
            collectionAt = listOf("organization", "teams"),
            nodes = true
        ) { teamNode ->
            GitHubTeam(
                slug = teamNode.path("slug").asText(),
                name = teamNode.path("name").asText(),
                description = teamNode.path("description").asText(),
                html_url = teamNode.path("url").asText()
            )
        }

    override fun getTeamRepositories(login: String, teamSlug: String): List<GitHubTeamRepository>? =
        paginateGraphQL(
            message = "Getting repositories for team $teamSlug in organization $login",
            query = """
                query TeamRepositories(${'$'}login: String!, ${'$'}team: String!, ${'$'}after: String) {
                  organization(login: ${'$'}login) {
                    team(slug: ${'$'}team) {
                      repositories(first: 100, after: ${'$'}after) {
                        pageInfo {
                          hasNextPage
                          endCursor
                        }
                        edges {
                          permission
                          node {
                            name
                          }
                        }
                      }
                    }
                  }
                }
            """,
            variables = mapOf(
                "login" to login,
                "team" to teamSlug
            ),
            collectionAt = listOf("organization", "team", "repositories"),
            nodes = false
        ) { edge ->
            GitHubTeamRepository(
                repository = edge.path("node").path("name").asText(),
                permission = edge.path("permission").asText().let { GitHubRepositoryPermission.valueOf(it) }
            )
        }

    private fun <T> paginateGraphQL(
        message: String,
        query: String,
        variables: Map<String, *> = emptyMap<String, Any>(),
        collectionAt: List<String>,
        nodes: Boolean = false,
        code: (node: JsonNode) -> T,
    ): List<T>? {
        val results = mutableListOf<T>()
        var hasNext = true
        var after: String? = null
        while (hasNext) {
            val actualVariables = variables + ("after" to after)
            try {
                graphQL(message, query, actualVariables) { data ->
                    var page = data
                    collectionAt.forEach { childName ->
                        page = page.path(childName)
                    }
                    // List of results
                    val list = if (nodes) {
                        page.path("nodes")
                    } else {
                        page.path("edges")
                    }
                    // Conversion
                    list.forEach { item ->
                        results += code(item)
                    }
                    // Pagination
                    val pageInfo = page.path("pageInfo")
                    hasNext = pageInfo.path("hasNextPage").asBoolean()
                    if (hasNext) {
                        after = pageInfo.path("endCursor").asText()
                    }
                }
            } catch (_: GitHubNoGraphQLResponseException) {
                return null
            }
        }
        return results
    }

    override fun <T> graphQL(
        message: String,
        query: String,
        variables: Map<String, *>,
        token: String?,
        code: (data: JsonNode) -> T,
    ): T {
        // Getting a client
        val client = createGitHubTemplate(graphql = true, token = token)
        // GraphQL call
        return client(message) {
            val response = postForObject(
                "/graphql",
                mapOf(
                    "query" to query,
                    "variables" to variables
                ),
                JsonNode::class.java
            )
            if (response != null) {
                val errors = response.path("errors")
                if (errors != null && errors.isArray && errors.size() > 0) {
                    val messages: List<String> = errors.map { it.path("message").asText() }
                    throw GitHubNoGraphQLErrorsException(message, messages)
                } else {
                    val data = response.path("data")
                    code(data)
                }
            } else {
                throw GitHubNoGraphQLResponseException(message)
            }
        }
    }

    private operator fun <T> RestTemplate.invoke(
        message: String,
        code: RestTemplate.() -> T,
    ): T {
        logger.debug("[github] {}", message)
        return try {
            GitConnectionRetry.retry(
                message = message,
                defaultRetries = retries,
                defaultInterval = interval,
                config = gitConnectionConfig,
            ) {
                code()
            }
        } catch (ex: RestClientResponseException) {
            val error = ex.getJsonGitHubErrorMessage()
            if (error != null) {
                throw GitHubErrorsException(message, error, ex.statusCode.value())
            } else {
                throw ex
            }
        }
    }

    private fun RestClientResponseException.getJsonGitHubErrorMessage(): GitHubErrorMessage? {
        val contentType: Any? = this.responseHeaders?.contentType
        return if (contentType != null && contentType is MediaType && contentType.includes(MediaType.APPLICATION_JSON)) {
            val json = this.responseBodyAsString
            try {
                json.parseAsJson().parseOrNull<GitHubErrorMessage>()
            } catch (_: JsonParseException) {
                null
            }
        } else {
            null
        }
    }

    override fun createGitHubRestTemplate(token: String?): RestTemplate =
        createGitHubTemplate(graphql = false, token = token)

    private fun createGitHubTemplate(graphql: Boolean, token: String?): RestTemplate = RestTemplateBuilder()
        .rootUri(getApiRoot(configuration.url, graphql))
        .connectTimeout(timeout)
        .readTimeout(timeout)
        .run {
            if (token != null) {
                defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer $token")
            } else {
                when (configuration.authenticationType) {
                    GitHubAuthenticationType.ANONYMOUS -> this // Nothing to be done
                    GitHubAuthenticationType.PASSWORD -> {
                        basicAuthentication(configuration.user, configuration.password)
                    }

                    GitHubAuthenticationType.USER_TOKEN -> {
                        basicAuthentication(configuration.user, configuration.oauth2Token)
                    }

                    GitHubAuthenticationType.TOKEN -> {
                        defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer ${configuration.oauth2Token}")
                    }

                    GitHubAuthenticationType.APP -> {
                        defaultHeader(
                            HttpHeaders.AUTHORIZATION,
                            "Bearer ${gitHubAppTokenService.getAppInstallationToken(configuration)}"
                        )
                    }
                }
            }
        }
        .build()

    override fun getPR(
        repository: String,
        pr: Int
    ): GitHubPR? {
        // Getting a client
        val client = createGitHubRestTemplate()
        // Gets the repository for this project
        val (owner, name) = getRepositoryParts(repository)
        // Getting the PR
        return try {
            client("Get PR $repository#$pr") {
                getForObject<GitHubPR?>("/repos/$owner/$name/pulls/$pr")
            }
        } catch (ex: GitHubErrorsException) {
            if (ex.status == 404) {
                null
            } else {
                throw ex
            }
        }
    }

    @Deprecated("Will be removed in V6. Use getPR instead.")
    override fun getPullRequest(repository: String, id: Int, ignoreError: Boolean): GitPullRequest? {
        // Getting a client
        val client = createGitHubRestTemplate()
        // Gets the repository for this project
        val (owner, name) = getRepositoryParts(repository)
        // Getting the PR
        return try {
            client("Get PR $repository#$id") {
                getForObject<JsonNode?>("/repos/$owner/$name/pulls/$id")?.run {
                    GitPullRequest(
                        id = id,
                        key = "#$id",
                        source = path("head").path("ref").asText(),
                        target = path("base").path("ref").asText(),
                        title = getRequiredTextField("title"),
                        status = getRequiredTextField("state"),
                        url = getRequiredTextField("html_url"),
                    )
                }
            }
        } catch (ex: GitHubErrorsException) {
            if (ex.status == 404 || ignoreError) {
                null
            } else {
                throw ex
            }
        }
    }

    override fun getRepositorySettings(repository: String, askVisibility: Boolean): GitHubRepositorySettings {
        val (login, name) = getRepositoryParts(repository)
        return graphQL(
            message = "Get description for $repository",
            query = """
                query Description(${'$'}login: String!, ${'$'}name: String!) {
                  organization(login: ${'$'}login) {
                    repository(name: ${'$'}name) {
                        description
                        defaultBranchRef {
                            name
                        }
                        hasWikiEnabled
                        hasIssuesEnabled
                        hasProjectsEnabled
                    }
                  }
                }
            """,
            variables = mapOf("login" to login, "name" to name)
        ) { data ->
            val repo = data.path("organization").path("repository")
            val description = repo.path("description")
                .asText()
                ?.takeIf { it.isNotBlank() }
            val branch = repo.path("defaultBranchRef")
                .path("name")
                .asText()
                ?.takeIf { it.isNotBlank() }
            val hasWikiEnabled = repo.getBooleanField("hasWikiEnabled") ?: false
            val hasIssuesEnabled = repo.getBooleanField("hasIssuesEnabled") ?: false
            val hasProjectsEnabled = repo.getBooleanField("hasProjectsEnabled") ?: false
            // Visibility
            var visibility: GitHubRepositoryVisibility? = null
            if (askVisibility) {
                // Unfortunately, as of now, the visibility flag is not available through the GraphQL API
                // and a REST call is therefore needed to get this information
                val rest = createGitHubRestTemplate()
                visibility = rest.getForObject("/repos/${login}/${name}", GitHubRepositoryWithVisibility::class.java)
                    ?.visibility
                    ?.uppercase()
                    ?.let { GitHubRepositoryVisibility.valueOf(it) }
            }
            // OK
            GitHubRepositorySettings(
                description = description,
                defaultBranch = branch,
                hasWikiEnabled = hasWikiEnabled,
                hasIssuesEnabled = hasIssuesEnabled,
                hasProjectsEnabled = hasProjectsEnabled,
                visibility = visibility,
            )
        }
    }

    override fun getFileContent(
        repository: String,
        branch: String?,
        path: String,
        retryOnNotFound: Boolean,
    ): ByteArray? =
        getFile(repository, branch, path, retryOnNotFound)?.contentAsBinary()

    override fun getFile(repository: String, branch: String?, path: String, retryOnNotFound: Boolean): GitHubFile? {

        // Logging
        logger.debug("[github] Getting file {}/{}@{} with retryOnNotFound=$retryOnNotFound", repository, path, branch)
        // Getting a client
        val client = createGitHubRestTemplate()
        // Gets the repository for this project
        val (owner, name) = getRepositoryParts(repository)

        fun internalDownload(): GitHubFile? {
            return try {
                val restPath = "/repos/$owner/$name/contents/$path".runIf(branch != null) {
                    "$this?ref=$branch"
                }
                client("Get file content $repository/$path@$branch") {
                    getForObject<GitHubGetContentResponse>(restPath).let {
                        GitHubFile(
                            content = it.content,
                            sha = it.sha,
                        )
                    }
                }
            } catch (ex: GitHubErrorsException) {
                if (ex.status == 404) {
                    null
                } else {
                    throw ex
                }
            }
        }

        return if (retryOnNotFound) {
            var file: GitHubFile? = null
            var tries = 0u
            runBlocking {
                while (file == null && tries < notFoundRetries) {
                    tries++
                    logger.debug(
                        "[github] Getting file {}/{}@{} with tries $tries/$notFoundRetries",
                        repository,
                        path,
                        branch
                    )
                    file = internalDownload()
                    if (file == null) {
                        // Waiting before the next retry
                        delay(notFoundInterval.toMillis())
                    }
                }
            }
            file
        } else {
            internalDownload()
        }
    }

    private fun <T> retryOnNotFound(
        message: String,
        call: RestTemplate.() -> T?,
    ): T? {
        val client = createGitHubRestTemplate()

        fun internalCall(): T? {
            return try {
                client(message) {
                    call()
                }
            } catch (ex: GitHubErrorsException) {
                if (ex.status == 404) {
                    null
                } else {
                    throw ex
                }
            }
        }

        var result: T? = null
        var tries = 0u
        runBlocking {
            while (result == null && tries < retries) {
                tries++
                logger.debug("$message - tries $tries/$retries")
                result = internalCall()
                if (result == null) {
                    // Waiting before the next retry
                    delay(interval.toMillis())
                }
            }
        }

        return result
    }

    override fun getBranchLastCommit(repository: String, branch: String, retryOnNotFound: Boolean): String? {
        // Gets the repository for this project
        val (owner, name) = getRepositoryParts(repository)
        // Retries
        if (retryOnNotFound) {
            return retryOnNotFound("Get last commit for $branch") {
                getForObject(
                    "/repos/${owner}/${name}/git/ref/heads/${branch}",
                    GitHubGetRefResponse::class.java
                )?.`object`?.sha
            }
        } else {
            val client = createGitHubRestTemplate()
            return try {
                client.getForObject(
                    "/repos/${owner}/${name}/git/ref/heads/${branch}",
                    GitHubGetRefResponse::class.java
                )?.`object`?.sha
            } catch (_: HttpClientErrorException.NotFound) {
                null
            }
        }
    }

    override fun deleteBranch(repository: String, branch: String) {
        // Gets the repository for this project
        val (owner, name) = getRepositoryParts(repository)
        retryOnNotFound("Delete branch $branch") {
            delete(
                "/repos/${owner}/${name}/git/refs/heads/${branch}",
            )
        }
    }

    override fun createBranch(repository: String, source: String, destination: String): String? {
        // Gets the last commit of the source branch
        val sourceCommit = getBranchLastCommit(repository, source) ?: return null
        // Gets the repository for this project
        val (owner, name) = getRepositoryParts(repository)
        // Retries
        return retryOnNotFound("Create branch $destination from $source (source commit = $sourceCommit)") {
            postForObject(
                "/repos/${owner}/${name}/git/refs",
                mapOf(
                    "ref" to "refs/heads/$destination",
                    "sha" to sourceCommit
                ),
                GitHubGetRefResponse::class.java
            )
        }?.`object`?.sha
    }

    override fun setFileContent(
        repository: String,
        branch: String,
        sha: String,
        path: String,
        content: ByteArray,
        message: String
    ) {
        // Getting a client
        val client = createGitHubRestTemplate()
        // Gets the repository for this project
        val (owner, name) = getRepositoryParts(repository)
        // Call
        client("Setting file content at $path for branch $branch") {
            put(
                "/repos/$owner/$name/contents/$path",
                mapOf(
                    "message" to message,
                    "content" to Base64.encodeBase64String(content),
                    "sha" to sha,
                    "branch" to branch
                )
            )
        }
    }

    override fun createPR(
        repository: String,
        title: String,
        head: String,
        base: String,
        body: String,
        reviewers: List<String>,
    ): GitHubPR {
        // Gets the repository for this project
        val (owner, name) = getRepositoryParts(repository)
        // Retries
        return retryOnNotFound("Creating PR from $head to $base") {
            val pr = postForObject(
                "/repos/$owner/$name/pulls",
                mapOf(
                    "title" to title,
                    "head" to head,
                    "base" to base,
                    "body" to body
                ),
                GitHubPullRequestResponse::class.java
            )
            // Requesting reviewers
            if (pr != null && reviewers.isNotEmpty()) {
                postForObject(
                    "/repos/$owner/$name/pulls/${pr.number}/requested_reviewers",
                    mapOf(
                        "reviewers" to reviewers,
                    ),
                    JsonNode::class.java,
                )
            }
            // OK
            pr
        }?.run {
            // OK
            GitHubPR(
                number = number,
                mergeable = mergeable,
                mergeable_state = mergeable_state,
                html_url = html_url,
                state = state,
                merged = merged,
            )
        } ?: throw IllegalStateException("PR creation response did not return a PR.")
    }

    override fun approvePR(repository: String, pr: Int, body: String, token: String?) {
        // Getting a client
        val client = createGitHubRestTemplate(token)
        // Gets the repository for this project
        val (owner, name) = getRepositoryParts(repository)
        // Call
        client("Approving PR $pr") {
            postForObject(
                "/repos/$owner/$name/pulls/$pr/reviews",
                mapOf(
                    "body" to body,
                    "event" to "APPROVE"
                ),
                JsonNode::class.java
            )
        }
    }

    override fun enableAutoMerge(repository: String, pr: Int, message: String) {
        // Getting a client
        val client = createGitHubRestTemplate()
        // Gets the repository for this project
        val (owner, name) = getRepositoryParts(repository)
        // Gets the GraphQL node ID for this PR
        val nodeId = client("Getting PR node ID") {
            getForObject(
                "/repos/${owner}/${name}/pulls/${pr}",
                GitHubPullRequestResponse::class.java
            )
        }?.node_id ?: error("Cannot get PR node ID")
        // Only the GraphQL API is available
        graphQL(
            "Enabling auto merge on PR $pr",
            """
                 mutation EnableAutoMerge(${'$'}prNodeId: ID!, ${'$'}commitHeadline: String!) {
                    enablePullRequestAutoMerge(input: {
                        pullRequestId: ${'$'}prNodeId,
                        commitHeadline: ${'$'}commitHeadline
                    }) {
                        pullRequest {
                            number
                        }
                    }
                 }
            """,
            mapOf(
                "prNodeId" to nodeId,
                "commitHeadline" to message,
            )
        ) { data ->
            val prNumber = data.path("enablePullRequestAutoMerge").path("pullRequest").path("number")
            if (prNumber.isNullOrNullNode()) {
                throw GitHubAutoMergeNotEnabledException(repository)
            }
        }
    }

    override fun isPRMergeable(repository: String, pr: Int): Boolean {
        // Getting a client
        val client = createGitHubRestTemplate()
        // Gets the repository for this project
        val (owner, name) = getRepositoryParts(repository)
        // Call
        return client("Checking if PR $pr is mergeable") {
            val response = getForObject(
                "/repos/$owner/$name/pulls/$pr",
                GitHubPullRequestResponse::class.java
            )
            // We need both the mergeable flag and the mergeable state
            // Mergeable is not enough since it represents only the fact that the branch can be merged
            // from a Git-point of view and does not represent the checks
            (response?.mergeable ?: false) && (response.mergeable_state == "clean")
        }
    }

    override fun mergePR(repository: String, pr: Int, message: String) {
        // Getting a client
        val client = createGitHubRestTemplate()
        // Gets the repository for this project
        val (owner, name) = getRepositoryParts(repository)
        // Call
        client("Merging PR $pr") {
            put(
                "/repos/$owner/$name/pulls/$pr/merge",
                mapOf(
                    "commit_title" to message,
                )
            )
        }
    }

    override fun mergeBranch(repository: String, head: String, base: String): GitHubCommit? {
        val client = createGitHubRestTemplate()
        val (owner, name) = getRepositoryParts(repository)
        return client.postForObject(
            "/repos/$owner/$name/merges",
            mapOf(
                "base" to base,
                "head" to head,
            ),
            GitHubCommit::class.java,
        )
    }

    override fun compareCommits(repository: String, base: String, head: String): List<GitHubCommit> {
        // Getting a client
        val client = createGitHubRestTemplate()
        // Gets the repository for this project
        val (owner, name) = getRepositoryParts(repository)
        // Call
        return client<List<GitHubCommit>>("Comparing commits") {
            getForObject<JsonNode>(
                "/repos/$owner/$name/compare/$base...$head"
            ).path("commits").map { commitNode ->
                commitNode.parse()
            }
        }.reversed()
    }

    override fun getCommit(
        repository: String,
        commit: String
    ): GitHubCommit? {
        // Getting a client
        val client = createGitHubRestTemplate()
        // Gets the repository for this project
        val (owner, name) = getRepositoryParts(repository)
        // Call
        return try {
            client("Getting commit $commit") {
                getForObject<GitHubCommit?>(
                    "/repos/$owner/$name/commits/$commit"
                )
            }
        } catch (ex: GitHubErrorsException) {
            if (ex.status == 404) {
                null
            } else {
                throw ex
            }
        } catch (_: HttpClientErrorException.NotFound) {
            null
        }
    }

    override fun launchWorkflowRun(
        repository: String,
        workflow: String,
        branch: String,
        inputs: Map<String, String>,
        retries: Int,
        retriesDelaySeconds: Int,
    ): WorkflowRun {
        // Getting a client
        val client = createGitHubRestTemplate()
        // Generating a unique ID to find the launched workflow back
        val id = UUID.randomUUID().toString()
        client.postForLocation(
            "/repos/$repository/actions/workflows/$workflow/dispatches",
            mapOf(
                "ref" to branch,
                "inputs" to (inputs + mapOf("id" to id)),
            ),
        )
        // Looking for the launched workflow run
        return runBlocking {
            untilTimeout(
                name = "Getting workflow run for $repository/$workflow/$branch",
                retryCount = retries,
                retryDelay = Duration.ofSeconds(retriesDelaySeconds.toLong()),
            ) {
                // Gets the list of runs
                val runs = getWorkflowRuns(client, repository, branch)
                // Checks the artifacts for each run
                runs.find { run ->
                    hasWorkflowId(client, repository, run.id, id)
                }
            }
        }
    }

    override fun getWorkflowRun(repository: String, runId: Long): WorkflowRun {
        val client = createGitHubRestTemplate()
        return client.getForObject<WorkflowRun>("/repos/$repository/actions/runs/$runId")
    }

    override fun waitUntilWorkflowRun(repository: String, runId: Long, retries: Int, retriesDelaySeconds: Int) {
        val success = runBlocking {
            untilTimeout(
                name = "Waiting for workflow run $repository/$runId",
                retryCount = retries,
                retryDelay = Duration.ofSeconds(retriesDelaySeconds.toLong()),
            ) {
                val run = getWorkflowRun(repository, runId)
                run.success
            }
        }
        if (!success) {
            throw GitHubWorkflowRunFailedException(repository, runId)
        }
    }

    override fun getIssueLastCommit(repository: String, key: Int): String? {
        // Getting a client
        val client = createGitHubRestTemplate()
        // Gets the repository for this project
        val (owner, name) = getRepositoryParts(repository)
        // Search commits mentioning the issue, sorted by most recent
        return try {
            val url = "/search/commits?q={query}&sort={sort}&order={order}"
            val params = mapOf(
                "query" to "repo:$owner/$name #$key",
                "sort" to "committer-date",
                "order" to "desc"
            )
            val node = client.getForObject<JsonNode>(url, params)
            val items = node.path("items")
            if (items.isArray && items.size() > 0) {
                items[0].path("sha").asText(null)
            } else {
                null
            }
        } catch (_: HttpClientErrorException.NotFound) {
            null
        } catch (ex: HttpClientErrorException.Forbidden) {
            val error = ex.getJsonGitHubErrorMessage()
            if (error != null && "rate limit exceeded" in error.message) {
                // The search API has very restrictive API rate limits
                meterRegistry.increment(
                    GitHubAppRateLimitMetricsNames.Search.ontrack_extension_github_ratelimit_search_exceeded,
                    "repository" to repository
                )
                null
            } else {
                throw ex
            }
        }
    }

    override fun forAllCommits(repository: String, code: (commit: GitHubCommit) -> Unit) {
        val (owner, name) = getRepositoryParts(repository)
        paginateGraphQL(
            message = "Iterating over all commits for $repository",
            query = $$"""
                query AllCommits($owner: String!, $name: String!, $after: String) {
                    repository(owner: $owner, name: $name) {
                        defaultBranchRef {
                            target {
                                ... on Commit {
                                    history(first: 100, after: $after) {
                                        pageInfo {
                                            hasNextPage
                                            endCursor
                                        }
                                        edges {
                                            node {
                                                oid
                                                url
                                                message
                                                author {
                                                    name
                                                    email
                                                    date
                                                }
                                                committer {
                                                    name
                                                    email
                                                    date
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            """,
            variables = mapOf(
                "owner" to owner,
                "name" to name
            ),
            collectionAt = listOf("repository", "defaultBranchRef", "target", "history"),
            nodes = false,
        ) { n ->
            val node = n.path("node")
            val sha = node.path("oid").asText()
            val url = node.path("url").asText()
            val message = node.path("message").asText()
            val author = node.path("author").toGitHubAuthor()
            val committer = node.path("committer").toGitHubAuthor()
            code(
                GitHubCommit(
                    sha = sha,
                    url = url,
                    commit = GitHubCommitInfo(
                        author = author,
                        committer = committer,
                        message = message
                    ),
                    parents = null // Not needed for now
                )
            )
        }
    }

    private fun JsonNode.toGitHubAuthor() = GitHubAuthor(
        name = path("name").asText(),
        email = path("email").asText(),
        date = path("date").asText().let { parseLocalDateTime(it) }
    )

    private fun getWorkflowRuns(
        client: RestTemplate,
        repository: String,
        branch: String,
    ): List<WorkflowRun> {
        val encodedBranch = URLEncoder.encode(branch, Charsets.UTF_8)
        return client.getForObject<JsonNode>("/repos/$repository/actions/runs?event=workflow_dispatch&branch=$encodedBranch")
            .path("workflow_runs")
            .map {
                it.parse()
            }
    }

    private fun hasWorkflowId(
        client: RestTemplate,
        repository: String,
        runId: Long,
        id: String,
    ): Boolean {
        val expectedName = "inputs-$id.properties"
        val artifacts = client.getForObject<JsonNode>("/repos/$repository/actions/runs/$runId/artifacts")
            .path("artifacts")
            .map {
                it.parse<Artifact>()
            }
        return artifacts.any {
            it.name == expectedName
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class Artifact(
        val name: String,
    )

    private fun JsonNode.getUserField(field: String): GitHubUser? =
        getJsonField(field)?.run {
            GitHubUser(
                login = getRequiredTextField("login"),
                url = getTextField("html_url"),
            )
        }

    private fun JsonNode.getLabels(): List<GitHubLabel> {
        val field = "labels"
        return if (has(field)) {
            val list = get(field)
            list.map { node ->
                GitHubLabel(
                    name = node.getRequiredTextField("name"),
                    color = node.getRequiredTextField("color"),
                )
            }
        } else {
            emptyList()
        }
    }

    private fun JsonNode.getState(): GitHubState {
        val value = getRequiredTextField("state")
        return GitHubState.valueOf(value)
    }

    private fun JsonNode.getMilestone(): GitHubMilestone? = getJsonField("milestone")?.let { node ->
        GitHubMilestone(
            title = node.getRequiredTextField("title"),
            state = node.getState(),
            number = node.getRequiredIntField("number"),
            url = node.getRequiredTextField("html_url"),
        )
    }

    private fun JsonNode.getCreatedAt(): LocalDateTime =
        getRequiredDateTime("created_at")

    private fun JsonNode.getUpdatedAt(): LocalDateTime =
        getRequiredDateTime("updated_at")

    private fun JsonNode.getClosedAt(): LocalDateTime? =
        getDateTime("closed_at")

    private fun JsonNode.getRequiredDateTime(field: String): LocalDateTime =
        getDateTime(field) ?: throw JsonParseException("Missing field $field")

    private fun JsonNode.getDateTime(field: String): LocalDateTime? =
        getTextField(field)?.let {
            parseLocalDateTime(it)
        }

    companion object {
        /**
         * Cloud root API
         */
        private const val CLOUD_ROOT_API = "https://api.github.com"

        /**
         * Gets the API URL for the given server URL, for REST or for GraphQL.
         *
         * If [graphql] is true, the URL is returned _without_ the `/graphql` suffix.
         */
        fun getApiRoot(url: String, graphql: Boolean): String =
            // Cloud version
            if (url.trimEnd('/') == "https://github.com") {
                CLOUD_ROOT_API
            }
            // Enterprise version
            else {
                val rootUrl = url.trimEnd('/')
                if (graphql) {
                    "$rootUrl/api"
                } else {
                    "$rootUrl/api/v3"
                }
            }
    }

    private class GitHubNoGraphQLResponseException(message: String) : BaseException(
        """
            $message
            
            No GraphQL response was returned.
        """.trimIndent()
    )

    private class GitHubNoGraphQLErrorsException(message: String, messages: List<String>) : BaseException(
        format(message, messages)
    ) {
        companion object {
            fun format(message: String, messages: List<String>): String {
                val list = messages.joinToString("\n") { " - $it" }
                return "$message\n\n$list"
            }
        }
    }

    private class GitHubErrorsException(
        message: String,
        error: GitHubErrorMessage,
        val status: Int,
    ) : BaseException("HTTP $status: ${error.format(message)}")

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class GitHubErrorMessage(
        val message: String,
        val errors: List<GitHubError>?,
    ) {
        fun format(message: String): String = mapOf(
            "message" to message,
            "exception" to this
        ).toString()
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class GitHubError(
        val resource: String,
        val field: String,
        val code: String,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class GitHubGetRefResponse(
        val `object`: GitHubObject,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class GitHubObject(
        val sha: String,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class GitHubGetContentResponse(
        /**
         * Base64 encoded content
         */
        val content: String,
        /**
         * SHA of the file
         */
        val sha: String,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class GitHubPullRequestResponse(
        /**
         * Local ID of the PR
         */
        val number: Int,
        /**
         * Node ID (for use in GraphQL)
         */
        val node_id: String,
        /**
         * Is the PR mergeable?
         */
        val mergeable: Boolean?,
        /**
         * Mergeable status
         */
        val mergeable_state: String?,
        /**
         * Link to the PR
         */
        val html_url: String?,
        /**
         * Merged status
         */
        val merged: Boolean,
        /**
         * State
         */
        val state: String,
    )

}