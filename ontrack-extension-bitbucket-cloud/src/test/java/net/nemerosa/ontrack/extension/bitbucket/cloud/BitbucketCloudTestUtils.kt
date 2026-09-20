package net.nemerosa.ontrack.extension.bitbucket.cloud

import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudAuthType
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfiguration
import net.nemerosa.ontrack.test.TestUtils.uid
import net.nemerosa.ontrack.test.getOptionalEnv
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Properties of the real Bitbucket Cloud tests.
 *
 * Each one is read as a system property or, failing that, as the environment variable named by [envName],
 * which is also the name of the GitHub secret (or variable, for [TOKENS_EXPIRY]) behind it in CI.
 *
 * The test workspace is provisioned by `ontrack-extension-bitbucket-cloud/scripts/bitbucket-cloud-test-workspace.sh`,
 * see the module's README.
 */
object BitbucketCloudTestProperties {
    const val PREFIX = "ontrack.test.extension.bitbucket.cloud"

    /** `true` to skip the real tests even when the credentials are set. */
    const val IGNORE = "$PREFIX.ignore"

    /**
     * `true` to run the real **pipeline** tests, which consume build minutes. Only
     * `.github/workflows/bitbucket-real.yml` sets this - see the module's README (#1761).
     */
    const val PIPELINES = "$PREFIX.pipelines"

    const val WORKSPACE = "$PREFIX.workspace"
    const val PROJECT = "$PREFIX.project"
    const val REPOSITORY = "$PREFIX.repository"
    const val BOT_EMAIL = "$PREFIX.bot.email"
    const val BOT_TOKEN = "$PREFIX.bot.token"
    const val APPROVER_EMAIL = "$PREFIX.approver.email"
    const val APPROVER_TOKEN = "$PREFIX.approver.token"
    const val ACCESS_TOKEN = "$PREFIX.access.token"
    const val TOKENS_EXPIRY = "$PREFIX.tokens.expiry"

    /** The credentials: when none of them is set, the real tests are skipped. */
    val CREDENTIALS = listOf(
        WORKSPACE,
        PROJECT,
        REPOSITORY,
        BOT_EMAIL,
        BOT_TOKEN,
        APPROVER_EMAIL,
        APPROVER_TOKEN,
        ACCESS_TOKEN,
    )

    val REQUIRED = CREDENTIALS + TOKENS_EXPIRY

    fun envName(property: String) = property.uppercase().replace('.', '_')
}

/**
 * An Atlassian account and its API token, used with HTTP basic authentication.
 */
class BitbucketCloudTestIdentity(
    val email: String,
    val token: String,
) {
    override fun toString(): String = email
}

/**
 * The real Bitbucket Cloud test workspace.
 *
 * @property workspace Workspace slug
 * @property project Key of the project holding the fixture repository
 * @property repository Slug of the fixture repository
 * @property bot Identity doing everything
 * @property approver Identity approving the bot's pull requests
 * @property accessToken Repository access token on the fixture repository, used as a Bearer token
 * @property tokensExpiry Earliest expiry date of the three tokens
 */
class BitbucketCloudTestEnv(
    val workspace: String,
    val project: String,
    val repository: String,
    val bot: BitbucketCloudTestIdentity,
    val approver: BitbucketCloudTestIdentity,
    val accessToken: String,
    val tokensExpiry: LocalDate,
) {
    fun daysBeforeTokensExpire(today: LocalDate = LocalDate.now()): Int =
        ChronoUnit.DAYS.between(today, tokensExpiry).toInt()
}

/**
 * Are the real tests to run?
 *
 * Skipped when ignored or when no credential is set. A partial set of credentials is a misconfiguration and
 * is reported as such rather than silently skipping.
 */
fun bitbucketCloudTestEnabled(lookup: (String) -> String? = ::getOptionalEnv): Boolean {
    if (lookup(BitbucketCloudTestProperties.IGNORE) == "true") return false
    val configured = BitbucketCloudTestProperties.CREDENTIALS.any { !lookup(it).isNullOrBlank() }
    if (!configured) return false
    val missing = BitbucketCloudTestProperties.REQUIRED.filter { lookup(it).isNullOrBlank() }
    if (missing.isNotEmpty()) {
        error(
            "The real Bitbucket Cloud tests are only partially configured. Missing: " +
                    missing.joinToString { "$it (${BitbucketCloudTestProperties.envName(it)})" } +
                    ". Set all of them, none of them, or ${BitbucketCloudTestProperties.IGNORE}=true."
        )
    }
    return true
}

/**
 * Are the real pipeline tests to run? They need the credentials and their own switch.
 */
fun bitbucketCloudPipelinesTestEnabled(lookup: (String) -> String? = ::getOptionalEnv): Boolean =
    lookup(BitbucketCloudTestProperties.PIPELINES) == "true" && bitbucketCloudTestEnabled(lookup)

fun readBitbucketCloudTestEnv(lookup: (String) -> String? = ::getOptionalEnv): BitbucketCloudTestEnv {
    fun required(property: String): String =
        lookup(property)?.takeIf { it.isNotBlank() }
            ?: error("Missing $property system property or ${BitbucketCloudTestProperties.envName(property)} environment variable.")
    return BitbucketCloudTestEnv(
        workspace = required(BitbucketCloudTestProperties.WORKSPACE),
        project = required(BitbucketCloudTestProperties.PROJECT),
        repository = required(BitbucketCloudTestProperties.REPOSITORY),
        bot = BitbucketCloudTestIdentity(
            email = required(BitbucketCloudTestProperties.BOT_EMAIL),
            token = required(BitbucketCloudTestProperties.BOT_TOKEN),
        ),
        approver = BitbucketCloudTestIdentity(
            email = required(BitbucketCloudTestProperties.APPROVER_EMAIL),
            token = required(BitbucketCloudTestProperties.APPROVER_TOKEN),
        ),
        accessToken = required(BitbucketCloudTestProperties.ACCESS_TOKEN),
        tokensExpiry = LocalDate.parse(required(BitbucketCloudTestProperties.TOKENS_EXPIRY)),
    )
}

val bitbucketCloudTestEnv: BitbucketCloudTestEnv by lazy {
    readBitbucketCloudTestEnv()
}

/**
 * Creates a real configuration for Bitbucket Cloud, suitable for system tests, using the bot's API token.
 */
fun bitbucketCloudTestConfigReal(name: String = uid("C")) = bitbucketCloudTestEnv.run {
    BitbucketCloudConfiguration(
        name = name,
        authType = BitbucketCloudAuthType.API_TOKEN,
        email = bot.email,
        token = bot.token,
        autoMergeEmail = approver.email,
        autoMergeToken = approver.token,
    )
}

/**
 * Creates a real configuration for Bitbucket Cloud, suitable for system tests, using the repository access token.
 */
fun bitbucketCloudTestConfigRealAccessToken(name: String = uid("C")) = bitbucketCloudTestEnv.run {
    BitbucketCloudConfiguration(
        name = name,
        authType = BitbucketCloudAuthType.ACCESS_TOKEN,
        token = accessToken,
    )
}

fun bitbucketCloudTestConfigMock(
    name: String = uid("C"),
    authType: BitbucketCloudAuthType = BitbucketCloudAuthType.API_TOKEN,
) =
    BitbucketCloudConfiguration(
        name = name,
        authType = authType,
        email = if (authType == BitbucketCloudAuthType.API_TOKEN) "user@example.com" else null,
        token = "token",
    )

/**
 * Annotation to use on tests relying on the real Bitbucket Cloud test workspace.
 *
 * The first such test of a JVM removes the test branches and pull requests left over by earlier runs.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Test
@EnabledIf("net.nemerosa.ontrack.extension.bitbucket.cloud.TestOnBitbucketCloudCondition#isTestOnBitbucketCloudEnabled")
@ExtendWith(BitbucketCloudTestCleanupExtension::class)
annotation class TestOnBitbucketCloud

/**
 * Annotation to use on tests running real Bitbucket pipelines. They consume build minutes and are only
 * enabled by [BitbucketCloudTestProperties.PIPELINES].
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Test
@EnabledIf("net.nemerosa.ontrack.extension.bitbucket.cloud.TestOnBitbucketCloudCondition#isTestOnBitbucketCloudPipelinesEnabled")
@ExtendWith(BitbucketCloudTestCleanupExtension::class)
annotation class TestOnBitbucketCloudPipelines

/**
 * Testing if the environment is set for testing against Bitbucket Cloud
 */
class TestOnBitbucketCloudCondition {

    companion object {
        @JvmStatic
        fun isTestOnBitbucketCloudEnabled(): Boolean = bitbucketCloudTestEnabled()

        @JvmStatic
        fun isTestOnBitbucketCloudPipelinesEnabled(): Boolean = bitbucketCloudPipelinesTestEnabled()
    }
}
