package net.nemerosa.ontrack.extension.gitlab

import net.nemerosa.ontrack.extension.gitlab.model.GitLabConfiguration
import net.nemerosa.ontrack.test.TestUtils.uid
import net.nemerosa.ontrack.test.getOptionalEnv
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Properties of the real GitLab tests.
 *
 * Each one is read as a system property or, failing that, as the environment variable named by [envName],
 * which is also the name of the GitHub secret (or variable, for [TOKEN_EXPIRY]) behind it in CI.
 *
 * The test group is provisioned by `ontrack-extension-gitlab/scripts/gitlab-test-project.sh`, see the
 * module's README.
 */
object GitLabTestProperties {
    const val PREFIX = "ontrack.test.extension.gitlab"

    /** `true` to skip the real tests even when the credentials are set. */
    const val IGNORE = "$PREFIX.ignore"

    /** Path of the test group, like `yontrack-test`. */
    const val GROUP = "$PREFIX.group"

    /** Path of the fixture project **within** the group. */
    const val PROJECT = "$PREFIX.project"

    /** The bot's personal access token, scope `api`. */
    const val TOKEN = "$PREFIX.token"

    /** Expiry date of the token, `YYYY-MM-DD`. */
    const val TOKEN_EXPIRY = "$PREFIX.token.expiry"

    /** The credentials: when none of them is set, the real tests are skipped. */
    val CREDENTIALS = listOf(GROUP, PROJECT, TOKEN)

    val REQUIRED = CREDENTIALS + TOKEN_EXPIRY

    fun envName(property: String) = property.uppercase().replace('.', '_')
}

/**
 * The instance the real tests run against. There is no URL secret: the fixture is on gitlab.com.
 */
const val GITLAB_TEST_URL = "https://gitlab.com"

/**
 * The real GitLab test fixture.
 *
 * @property group Path of the test group
 * @property project Path of the fixture project within the group
 * @property token The bot's personal access token
 * @property tokenExpiry Expiry date of the token
 */
class GitLabTestEnv(
    val group: String,
    val project: String,
    val token: String,
    val tokenExpiry: LocalDate,
) {
    /**
     * Full path GitLab takes wherever an `:id` appears.
     */
    val projectPath: String get() = "$group/$project"

    fun daysBeforeTokenExpires(today: LocalDate = LocalDate.now()): Int =
        ChronoUnit.DAYS.between(today, tokenExpiry).toInt()
}

/**
 * Are the real tests to run?
 *
 * Skipped when ignored or when no credential is set. A partial set of credentials is a misconfiguration and
 * is reported as such rather than silently skipping.
 */
fun gitLabTestEnabled(lookup: (String) -> String? = ::getOptionalEnv): Boolean {
    if (lookup(GitLabTestProperties.IGNORE) == "true") return false
    val configured = GitLabTestProperties.CREDENTIALS.any { !lookup(it).isNullOrBlank() }
    if (!configured) return false
    val missing = GitLabTestProperties.REQUIRED.filter { lookup(it).isNullOrBlank() }
    if (missing.isNotEmpty()) {
        error(
            "The real GitLab tests are only partially configured. Missing: " +
                    missing.joinToString { "$it (${GitLabTestProperties.envName(it)})" } +
                    ". Set all of them, none of them, or ${GitLabTestProperties.IGNORE}=true."
        )
    }
    return true
}

fun readGitLabTestEnv(lookup: (String) -> String? = ::getOptionalEnv): GitLabTestEnv {
    fun required(property: String): String =
        lookup(property)?.takeIf { it.isNotBlank() }
            ?: error("Missing $property system property or ${GitLabTestProperties.envName(property)} environment variable.")
    return GitLabTestEnv(
        group = required(GitLabTestProperties.GROUP),
        project = required(GitLabTestProperties.PROJECT),
        token = required(GitLabTestProperties.TOKEN),
        tokenExpiry = LocalDate.parse(required(GitLabTestProperties.TOKEN_EXPIRY)),
    )
}

val gitLabTestEnv: GitLabTestEnv by lazy {
    readGitLabTestEnv()
}

/**
 * Prefix every branch a real test creates in the fixture project carries.
 *
 * It is what tells a leftover of the test suite apart from anything else in the project, and nothing outside
 * that prefix is ever deleted.
 */
const val GITLAB_TEST_BRANCH_PREFIX = "yontrack-it/"

/**
 * Name for a branch - and for the merge request opened from it - that a real test creates.
 *
 * It carries the creation time and, in CI, the run which created it, so that parallel shards, worktrees and
 * developers never collide and a leftover can be dated and traced back. A test deletes what it created in a
 * `finally`, so a leftover only ever comes from a killed run.
 */
fun gitLabTestBranch(purpose: String = "mr"): String {
    val run = getOptionalEnv("GITHUB_RUN_ID")?.takeIf { it.isNotBlank() } ?: "local"
    val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now())
    return "$GITLAB_TEST_BRANCH_PREFIX$purpose-$stamp-$run-${uid("")}"
}

/**
 * Creates a real configuration for GitLab, suitable for system tests.
 */
fun gitLabTestConfigReal(name: String = uid("C")) = GitLabConfiguration(
    name = name,
    url = GITLAB_TEST_URL,
    token = gitLabTestEnv.token,
)

/**
 * Creates a configuration suitable for mocked tests.
 */
fun gitLabTestConfigMock(name: String = uid("C")) = GitLabConfiguration(
    name = name,
    url = GITLAB_TEST_URL,
    token = "token",
)

/**
 * Annotation to use on tests relying on the real GitLab test group.
 *
 * They are skipped when no credential is set, which is the case of every ordinary CI run: the fixture is
 * provisioned by hand, and the tests are wired into integration shard 5 so that a checkout which does have
 * the secrets runs them.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Test
@EnabledIf("net.nemerosa.ontrack.extension.gitlab.TestOnGitLabCondition#isTestOnGitLabEnabled")
annotation class TestOnGitLab

/**
 * Testing if the environment is set for testing against GitLab.
 */
class TestOnGitLabCondition {

    companion object {
        @JvmStatic
        fun isTestOnGitLabEnabled(): Boolean = gitLabTestEnabled()
    }
}
