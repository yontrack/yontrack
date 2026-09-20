package net.nemerosa.ontrack.extension.github.model

import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.test.TestUtils
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitHubEngineConfigurationTest {

    @Test
    fun `Injection of password must be skipped when the token field is filled in`() {
        val old = GitHubEngineConfiguration(
            "test",
            null,
            user = "user",
            password = "xxxxx"
        )
        val new = GitHubEngineConfiguration(
            "test",
            null,
            oauth2Token = "xxxxx",
        )
        val injected = new.injectCredentials(old)
        injected.checkFields() // Must pass
        assertEquals(GitHubAuthenticationType.TOKEN, injected.authenticationType)
    }

    @Test
    fun `Injection of password must be skipped when the app fields are filled in`() {
        val old = GitHubEngineConfiguration(
            "test",
            null,
            user = "user",
            password = "xxxxx"
        )
        val new = GitHubEngineConfiguration(
            "test",
            null,
            appId = "xxxxx",
            appPrivateKey = TestUtils.resourceString("/test-app.pem")
        )
        val injected = new.injectCredentials(old)
        injected.checkFields() // Must pass
        assertEquals(GitHubAuthenticationType.APP, injected.authenticationType)
    }

    @Test
    fun `Injection of token must be skipped when the app fields are filled in`() {
        val old = GitHubEngineConfiguration(
            "test",
            null,
            oauth2Token = "xxxxx"
        )
        val new = GitHubEngineConfiguration(
            "test",
            null,
            appId = "xxxxx",
            appPrivateKey = TestUtils.resourceString("/test-app.pem")
        )
        val injected = new.injectCredentials(old)
        injected.checkFields() // Must pass
        assertEquals(GitHubAuthenticationType.APP, injected.authenticationType)
    }

    @Test
    fun `User is required when password is filled in`() {
        assertFailsWith<GitHubEngineConfigurationUserRequiredWithPasswordException> {
            GitHubEngineConfiguration(
                "test", null,
                user = "",
                password = "test",
            ).checkFields()
        }
    }

    @Test
    fun `Token must not be filled when password is filled in`() {
        assertFailsWith<GitHubEngineConfigurationTokenMustBeVoidWithPasswordException> {
            GitHubEngineConfiguration(
                "test", null,
                user = "test",
                password = "test",
                oauth2Token = "not welcomed"
            ).checkFields()
        }
    }

    @Test
    fun `App ID must not be filled when password is filled in`() {
        assertFailsWith<GitHubEngineConfigurationAppMustBeVoidWithPasswordException> {
            GitHubEngineConfiguration(
                "test", null,
                user = "test",
                password = "test",
                appId = "not welcomed"
            ).checkFields()
        }
    }

    @Test
    fun `App private key must not be filled when password is filled in`() {
        assertFailsWith<GitHubEngineConfigurationAppMustBeVoidWithPasswordException> {
            GitHubEngineConfiguration(
                "test", null,
                user = "test",
                password = "test",
                appPrivateKey = "not welcomed"
            ).checkFields()
        }
    }

    @Test
    fun `App installation account name must not be filled when password is filled in`() {
        assertFailsWith<GitHubEngineConfigurationAppMustBeVoidWithPasswordException> {
            GitHubEngineConfiguration(
                "test", null,
                user = "test",
                password = "test",
                appInstallationAccountName = "not welcomed"
            ).checkFields()
        }
    }

    @Test
    fun `App ID must not be filled when token is filled in`() {
        assertFailsWith<GitHubEngineConfigurationAppMustBeVoidWithTokenException> {
            GitHubEngineConfiguration(
                "test", null,
                oauth2Token = "xxxx",
                appId = "not welcomed",
            ).checkFields()
        }
    }

    @Test
    fun `App private key must not be filled when token is filled in`() {
        assertFailsWith<GitHubEngineConfigurationAppMustBeVoidWithTokenException> {
            GitHubEngineConfiguration(
                "test", null,
                oauth2Token = "xxxx",
                appPrivateKey = "not welcomed"
            ).checkFields()
        }
    }

    @Test
    fun `App installation account name must not be filled when token is filled in`() {
        assertFailsWith<GitHubEngineConfigurationAppMustBeVoidWithTokenException> {
            GitHubEngineConfiguration(
                "test", null,
                oauth2Token = "xxxx",
                appInstallationAccountName = "not welcomed"
            ).checkFields()
        }
    }

    @Test
    fun `App private key is required when app ID is filled in`() {
        assertFailsWith<GitHubEngineConfigurationAppPrivateKeyRequiredException> {
            GitHubEngineConfiguration(
                "test", null,
                appId = "123456",
                appPrivateKey = "",
            ).checkFields()
        }
    }

    @Test
    fun `App private key must be well formed`() {
        assertFailsWith<GitHubEngineConfigurationIncorrectAppPrivateKeyException> {
            GitHubEngineConfiguration(
                "test", null,
                appId = "123456",
                appPrivateKey = "xxxxx",
            ).checkFields()
        }
    }

    @Test
    fun `Backward compatibiity and check`() {
        assertEquals(
            GitHubEngineConfiguration(
                "test", "https://github.com",
                oauth2Token = "xxxx",
            ),
            mapOf(
                "name" to "test",
                "url" to "https://github.com",
                "oauth2Token" to "xxxx"
            ).asJson().parse<GitHubEngineConfiguration>().apply {
                checkFields()
            }
        )
    }

    @Test
    fun `Backward compatibility for auto merge token`() {
        assertEquals(
            GitHubEngineConfiguration(
                "test",
                "https://github.com",
                oauth2Token = "xxxx",
                autoMergeToken = null,
            ),
            mapOf(
                "name" to "test",
                "url" to "https://github.com",
                "oauth2Token" to "xxxx"
            ).asJson().parse<GitHubEngineConfiguration>().apply {
                checkFields()
            }
        )
    }

    @Test
    fun obfuscation_of_password() {
        val configuration = GitHubEngineConfiguration(
            "ontrack",
            GitHubEngineConfiguration.GITHUB_COM,
            "user",
            "secret",
            null
        )
        assertEquals(null, configuration.obfuscate().password)
    }

    @Test
    fun obfuscation_of_token() {
        val configuration = GitHubEngineConfiguration(
            "ontrack",
            GitHubEngineConfiguration.GITHUB_COM,
            null,
            null,
            "1234567890abcdef"
        )
        assertEquals(null, configuration.obfuscate().oauth2Token)
    }

    @Test
    fun `Obfuscation of app private key`() {
        val configuration = GitHubEngineConfiguration(
            "ontrack",
            GitHubEngineConfiguration.GITHUB_COM,
            appId = "123456",
            appPrivateKey = "xxxxxxx",
        )
        assertEquals(null, configuration.obfuscate().appPrivateKey)
    }

    @Test
    fun `Anonymous to json`() {
        assertEquals(
            mapOf(
                "name" to "ontrack",
                "url" to "https://github.com",
                "user" to null,
                "password" to null,
                "oauth2Token" to null,
                "appId" to null,
                "appPrivateKey" to null,
                "appInstallationAccountName" to null,
                "autoMergeToken" to null,
                "authenticationType" to "ANONYMOUS",
            ).asJson(),
            GitHubEngineConfiguration(
                "ontrack",
                GitHubEngineConfiguration.GITHUB_COM,
            ).asJson()
        )
    }

    @Test
    fun `Anonymous from JSON`() {
        assertEquals(
            GitHubEngineConfiguration(
                "ontrack",
                GitHubEngineConfiguration.GITHUB_COM,
            ),
            mapOf(
                "name" to "ontrack",
                "url" to "https://github.com",
                "user" to null,
                "password" to null,
                "oauth2Token" to null,
                "appId" to null,
                "appPrivateKey" to null,
                "appInstallationAccountName" to null,
            ).asJson().parse()
        )
    }

    @Test
    fun `Token to json`() {
        assertEquals(
            mapOf(
                "name" to "ontrack",
                "url" to "https://github.com",
                "user" to null,
                "password" to null,
                "oauth2Token" to "1234567890abcdef",
                "appId" to null,
                "appPrivateKey" to null,
                "appInstallationAccountName" to null,
                "autoMergeToken" to null,
                "authenticationType" to "TOKEN",
            ).asJson(),
            GitHubEngineConfiguration(
                "ontrack",
                GitHubEngineConfiguration.GITHUB_COM,
                null,
                null,
                "1234567890abcdef"
            ).asJson()
        )
    }

    @Test
    fun `Token from JSON`() {
        assertEquals(
            GitHubEngineConfiguration(
                "ontrack",
                GitHubEngineConfiguration.GITHUB_COM,
                null,
                null,
                "1234567890abcdef"
            ),
            mapOf(
                "name" to "ontrack",
                "url" to "https://github.com",
                "user" to null,
                "password" to null,
                "oauth2Token" to "1234567890abcdef",
                "appId" to null,
                "appPrivateKey" to null,
                "appInstallationAccountName" to null,
            ).asJson().parse()
        )
    }

    @Test
    fun `App to json`() {
        assertEquals(
            mapOf(
                "name" to "ontrack",
                "url" to "https://github.com",
                "user" to null,
                "password" to null,
                "oauth2Token" to null,
                "appId" to "123456",
                "appPrivateKey" to "xxxxxxx",
                "appInstallationAccountName" to null,
                "autoMergeToken" to null,
                "authenticationType" to "APP",
            ).asJson(),
            GitHubEngineConfiguration(
                "ontrack",
                GitHubEngineConfiguration.GITHUB_COM,
                appId = "123456",
                appPrivateKey = "xxxxxxx",
            ).asJson()
        )
    }

    @Test
    fun `App from JSON`() {
        assertEquals(
            GitHubEngineConfiguration(
                "ontrack",
                GitHubEngineConfiguration.GITHUB_COM,
                appId = "123456",
                appPrivateKey = "xxxxxxx",
            ),
            mapOf(
                "name" to "ontrack",
                "url" to "https://github.com",
                "user" to null,
                "password" to null,
                "oauth2Token" to null,
                "appId" to "123456",
                "appPrivateKey" to "xxxxxxx",
                "appInstallationAccountName" to null,
            ).asJson().parse()
        )
    }

    @Test
    fun `App from JSON with authentication type being saved`() {
        assertEquals(
            GitHubEngineConfiguration(
                "ontrack",
                GitHubEngineConfiguration.GITHUB_COM,
                appId = "123456",
                appPrivateKey = "xxxxxxx",
            ),
            mapOf(
                "name" to "ontrack",
                "url" to "https://github.com",
                "user" to null,
                "password" to null,
                "oauth2Token" to null,
                "appId" to "123456",
                "appPrivateKey" to "xxxxxxx",
                "appInstallationAccountName" to null,
                "authenticationType" to "APP",
            ).asJson().parse()
        )
    }

    @Test
    fun `App installation to json`() {
        assertEquals(
            mapOf(
                "name" to "ontrack",
                "url" to "https://github.com",
                "user" to null,
                "password" to null,
                "oauth2Token" to null,
                "appId" to "123456",
                "appPrivateKey" to "xxxxxxx",
                "appInstallationAccountName" to "test",
                "autoMergeToken" to null,
                "authenticationType" to "APP",
            ).asJson(),
            GitHubEngineConfiguration(
                "ontrack",
                GitHubEngineConfiguration.GITHUB_COM,
                appId = "123456",
                appPrivateKey = "xxxxxxx",
                appInstallationAccountName = "test",
            ).asJson()
        )
    }

    @Test
    fun `App installation from JSON`() {
        assertEquals(
            GitHubEngineConfiguration(
                "ontrack",
                GitHubEngineConfiguration.GITHUB_COM,
                appId = "123456",
                appPrivateKey = "xxxxxxx",
                appInstallationAccountName = "test",
            ),
            mapOf(
                "name" to "ontrack",
                "url" to "https://github.com",
                "user" to null,
                "password" to null,
                "oauth2Token" to null,
                "appId" to "123456",
                "appPrivateKey" to "xxxxxxx",
                "appInstallationAccountName" to "test",
            ).asJson().parse()
        )
    }

    @Test
    fun null_url_for_github_com() {
        val configuration = GitHubEngineConfiguration(
            "Test",
            null,
            "",
            "",
            ""
        )
        assertEquals("https://github.com", configuration.url)
    }

    @Test
    fun empty_url_for_github_com() {
        val configuration = GitHubEngineConfiguration(
            "Test",
            "",
            "",
            "",
            ""
        )
        assertEquals("https://github.com", configuration.url)
    }

    @Test
    fun `Anonymous mode`() {
        assertEquals(
            GitHubAuthenticationType.ANONYMOUS,
            GitHubEngineConfiguration("Test", null).authenticationType
        )
    }

    @Test
    fun `Password mode`() {
        assertEquals(
            GitHubAuthenticationType.PASSWORD,
            GitHubEngineConfiguration(
                "Test", null,
                user = "user",
                password = "xxx",
            ).authenticationType
        )
    }

    @Test
    fun `User token mode`() {
        assertEquals(
            GitHubAuthenticationType.USER_TOKEN,
            GitHubEngineConfiguration(
                "Test", null,
                user = "user",
                oauth2Token = "xxx",
            ).authenticationType
        )
    }

    @Test
    fun `Token mode`() {
        assertEquals(
            GitHubAuthenticationType.TOKEN,
            GitHubEngineConfiguration(
                "Test", null,
                oauth2Token = "xxx",
            ).authenticationType
        )
    }

    @Test
    fun `App mode`() {
        assertEquals(
            GitHubAuthenticationType.APP,
            GitHubEngineConfiguration(
                "Test", null,
                appId = "123456",
                appPrivateKey = "xxx",
            ).authenticationType
        )
    }

    @Test
    fun `App mode takes precedence`() {
        assertEquals(
            GitHubAuthenticationType.APP,
            GitHubEngineConfiguration(
                "Test", null,
                user = "user",
                password = "xxx",
                oauth2Token = "xxx",
                appId = "123456",
                appPrivateKey = "xxx",
            ).authenticationType
        )
    }

    @Test
    fun `SCM URL matching the config URL`() {
        val configUrl = "https://github.com"
        val config = GitHubEngineConfiguration(
            "Test",
            configUrl,
            oauth2Token = "xxx",
        )
        assertTrue(config.matchesUrl("https://github.com/nemerosa/yontrack.git"))
        assertTrue(config.matchesUrl("git@github.com:nemerosa/ontrack.git"))
        assertFalse(config.matchesUrl("https://github.dev.yontrack.com/nemerosa/yontrack.git"))
        assertFalse(config.matchesUrl("git@github.dev.yontrack.com:nemerosa/ontrack.git"))
    }

    @Test
    fun `SCM URL matching is an origin comparison, not a substring test`() {
        val config = GitHubEngineConfiguration(
            "Test",
            "https://github.com",
            oauth2Token = "xxx",
        )
        // The configured host as a suffix of the actual host
        assertFalse(config.matchesUrl("https://github.com.attacker.example/nemerosa/yontrack.git"))
        // The configured host in the path
        assertFalse(config.matchesUrl("https://attacker.example/github.com/nemerosa/yontrack.git"))
        // The configured host in the user information
        assertFalse(config.matchesUrl("https://github.com@attacker.example/nemerosa/yontrack.git"))
        // The configured host as the SSH user
        assertFalse(config.matchesUrl("git@github.com.attacker.example:nemerosa/yontrack.git"))
    }

    @Test
    fun `SCM URL matching ignores the scheme and the port`() {
        val config = GitHubEngineConfiguration(
            "Test",
            "https://github.dev.yontrack.com",
            oauth2Token = "xxx",
        )
        assertTrue(config.matchesUrl("ssh://git@github.dev.yontrack.com:7999/nemerosa/yontrack.git"))
        assertTrue(config.matchesUrl("http://github.dev.yontrack.com/nemerosa/yontrack.git"))
    }
}