package net.nemerosa.ontrack.extension.stash.config

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.stash.BitbucketServerFixtures
import net.nemerosa.ontrack.extension.stash.service.StashConfigurationService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BitbucketServerSCMEngineTest {

    private lateinit var stashConfigurationService: StashConfigurationService
    private lateinit var engine: BitbucketServerSCMEngine

    @BeforeEach
    fun before() {
        stashConfigurationService = mockk()
        engine = BitbucketServerSCMEngine(
            propertyService = mockk(),
            stashConfigurationService = stashConfigurationService,
            gitSCMEngineHelper = mockk(),
        )
    }

    @Test
    fun `Detection of Bitbucket Server SCM engine with wrong URL`() {
        bitbucketServerConfig()
        assertFalse(engine.matchesUrl("https://github.com/nemerosa/yontrack.git"))
    }

    @Test
    fun `Detection of Bitbucket Server SCM engine with no matching URL`() {
        bitbucketServerConfig()
        assertFalse(engine.matchesUrl("https://bitbucket.dev.nemerosa.com/scm/nemerosa/ontrack.git"))
    }

    @Test
    fun `Detection of Bitbucket Server SCM engine with matching HTTP URL`() {
        bitbucketServerConfig()
        assertTrue(engine.matchesUrl("https://bitbucket.dev.yontrack.com/scm/nemerosa/ontrack.git"))
    }

    @Test
    fun `Detection of Bitbucket Server SCM engine with matching SSH URL`() {
        bitbucketServerConfig()
        assertTrue(engine.matchesUrl("ssh://git@bitbucket.dev.yontrack.com:7999/nemerosa/ontrack.git"))
    }

    @Test
    fun `Detection of Bitbucket Server SCM engine rejects a host merely containing the configured one`() {
        bitbucketServerConfig()
        assertFalse(engine.matchesUrl("https://bitbucket.dev.yontrack.com.attacker.example/scm/nemerosa/ontrack.git"))
        assertFalse(engine.matchesUrl("https://attacker.example/bitbucket.dev.yontrack.com/nemerosa/ontrack.git"))
        assertFalse(engine.matchesUrl("https://bitbucket.dev.yontrack.com@attacker.example/nemerosa/ontrack.git"))
    }

    private fun bitbucketServerConfig(
        url: String = BitbucketServerFixtures.BITBUCKET_SERVER_URL,
    ) {
        every {
            stashConfigurationService.configurations
        } returns listOf(
            BitbucketServerFixtures.bitbucketServerConfig(url = url),
        )
    }

}