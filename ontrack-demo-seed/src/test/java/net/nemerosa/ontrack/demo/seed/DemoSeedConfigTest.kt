package net.nemerosa.ontrack.demo.seed

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DemoSeedConfigTest {

    private fun env(vararg entries: Pair<String, String>) = mapOf(
        DemoSeedConfig.URL to "https://demo.dev.yontrack.com",
        DemoSeedConfig.TOKEN to "a-token",
        *entries,
    )

    @Test
    fun `reads the demo URL and the token`() {
        val config = DemoSeedConfig.from(env())

        assertEquals("https://demo.dev.yontrack.com", config.url)
        assertEquals("a-token", config.token)
        assertEquals(File("."), config.repository)
        assertEquals(GitChangelogSource.DEFAULT_MAX, config.changelogMax)
    }

    @Test
    fun `a trailing slash on the URL is dropped`() {
        val config = DemoSeedConfig.from(env(DemoSeedConfig.URL to "https://demo.dev.yontrack.com/"))

        assertEquals("https://demo.dev.yontrack.com", config.url)
    }

    @Test
    fun `a local instance is allowed`() {
        assertEquals(
            "http://localhost:8080",
            DemoSeedConfig.from(env(DemoSeedConfig.URL to "http://localhost:8080")).url,
        )
    }

    @Test
    fun `an instance that is not the demo is refused`() {
        val error = assertFailsWith<IllegalArgumentException> {
            DemoSeedConfig.from(env(DemoSeedConfig.URL to "https://yontrack.dev.yontrack.com"))
        }
        assertTrue("deletes every project" in error.message.orEmpty())
    }

    @Test
    fun `a host that merely starts like a local one is refused`() {
        val error = assertFailsWith<IllegalArgumentException> {
            DemoSeedConfig.from(env(DemoSeedConfig.URL to "https://localhost.example.com"))
        }
        assertTrue("deletes every project" in error.message.orEmpty())
    }

    @Test
    fun `a host that merely starts like the demo is refused`() {
        val error = assertFailsWith<IllegalArgumentException> {
            DemoSeedConfig.from(env(DemoSeedConfig.URL to "https://demonstration.example.com"))
        }
        assertTrue("deletes every project" in error.message.orEmpty())
    }

    @Test
    fun `an instance that is not the demo can be named on purpose`() {
        val config = DemoSeedConfig.from(
            env(
                DemoSeedConfig.URL to "https://sandbox.example.com",
                DemoSeedConfig.URL_PATTERN to "^https://sandbox\\.example\\.com",
            )
        )

        assertEquals("https://sandbox.example.com", config.url)
    }

    @Test
    fun `a missing URL is refused`() {
        val error = assertFailsWith<IllegalArgumentException> {
            DemoSeedConfig.from(mapOf(DemoSeedConfig.TOKEN to "a-token"))
        }
        assertTrue(DemoSeedConfig.URL in error.message.orEmpty())
    }

    @Test
    fun `a missing token is refused`() {
        val error = assertFailsWith<IllegalArgumentException> {
            DemoSeedConfig.from(mapOf(DemoSeedConfig.URL to "https://demo.dev.yontrack.com"))
        }
        assertTrue(DemoSeedConfig.TOKEN in error.message.orEmpty())
    }

    @Test
    fun `a changelog size that is not a number is refused`() {
        val error = assertFailsWith<IllegalArgumentException> {
            DemoSeedConfig.from(env(DemoSeedConfig.CHANGELOG_MAX to "plenty"))
        }
        assertTrue("plenty" in error.message.orEmpty())
    }

    @Test
    fun `the changelog size and the repository can be set`() {
        val config = DemoSeedConfig.from(
            env(
                DemoSeedConfig.CHANGELOG_MAX to "5",
                DemoSeedConfig.REPOSITORY to "/src/yontrack",
            )
        )

        assertEquals(5, config.changelogMax)
        assertEquals(File("/src/yontrack"), config.repository)
    }
}
