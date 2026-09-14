package net.nemerosa.ontrack.extension.bitbucket.cloud

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Checks the content the wizard uploads to the fixture repository.
 */
class BitbucketCloudTestFixtureTest {

    private fun resource(name: String): String =
        BitbucketCloudTestFixture::class.java.getResourceAsStream("${BitbucketCloudTestFixture.RESOURCE_DIR}/$name")
            ?.use { it.reader().readText() }
            ?: error("Missing fixture resource $name")

    private val pipelines: JsonNode by lazy {
        ObjectMapper(YAMLFactory()).readTree(resource(BitbucketCloudTestFixture.PIPELINES_FILE))
    }

    private fun declaredVariables(pipeline: String): Set<String> =
        pipelines.path("pipelines").path("custom").path(pipeline)
            .filter { it.has("variables") }
            .flatMap { it.path("variables") }
            .map { it.path("name").asText() }
            .toSet()

    @Test
    fun `Only custom pipelines, so that no push to the fixture repository consumes build minutes`() {
        assertEquals(listOf("custom"), pipelines.path("pipelines").fieldNames().asSequence().toList())
    }

    @Test
    fun `The pipelines the tests rely on are declared`() {
        val custom = pipelines.path("pipelines").path("custom").fieldNames().asSequence().toSet()
        assertEquals(
            setOf(
                BitbucketCloudTestFixture.PIPELINE_ECHO,
                BitbucketCloudTestFixture.PIPELINE_FAIL,
                BitbucketCloudTestFixture.PIPELINE_AUTO_VERSIONING,
            ),
            custom
        )
    }

    @Test
    fun `Every step is capped in time`() {
        assertTrue(pipelines.path("options").path("max-time").asInt() in 1..10)
    }

    @Test
    fun `The echo pipeline declares its message`() {
        assertEquals(setOf("MESSAGE"), declaredVariables(BitbucketCloudTestFixture.PIPELINE_ECHO))
    }

    @Test
    fun `The failing pipeline fails on demand`() {
        assertEquals(setOf("FAIL"), declaredVariables(BitbucketCloudTestFixture.PIPELINE_FAIL))
    }

    @Test
    fun `The auto-versioning pipeline declares the post-processing variables`() {
        assertEquals(
            BitbucketCloudTestFixture.AUTO_VERSIONING_VARIABLES.toSet(),
            declaredVariables(BitbucketCloudTestFixture.PIPELINE_AUTO_VERSIONING)
        )
    }

    @Test
    fun `The version file has a version`() {
        val properties = Properties()
        properties.load(resource(BitbucketCloudTestFixture.VERSION_FILE).reader())
        assertNotNull(properties.getProperty(BitbucketCloudTestFixture.VERSION_PROPERTY))
    }

}
