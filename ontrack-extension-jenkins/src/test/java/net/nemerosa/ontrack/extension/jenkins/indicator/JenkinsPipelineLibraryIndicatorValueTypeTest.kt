package net.nemerosa.ontrack.extension.jenkins.indicator

import tools.jackson.databind.node.IntNode
import tools.jackson.databind.node.NullNode
import tools.jackson.databind.node.StringNode
import net.nemerosa.ontrack.extension.indicators.IndicatorsExtensionFeature
import net.nemerosa.ontrack.extension.jenkins.JenkinsExtensionFeature
import net.nemerosa.ontrack.extension.scm.SCMExtensionFeature
import net.nemerosa.ontrack.json.asJson
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JenkinsPipelineLibraryIndicatorValueTypeTest {

    private val type = JenkinsPipelineLibraryIndicatorValueType(
        JenkinsExtensionFeature(
            IndicatorsExtensionFeature(),
            SCMExtensionFeature()
        )
    )

    @Test
    fun `To client JSON`() {
        assertEquals(
            type.toClientJson(config(), version("1.0.0")),
            mapOf("version" to "1.0.0").asJson()
        )
    }

    @Test
    fun `From client JSON`() {
        assertNull(type.fromClientJson(config(), NullNode.instance))
        assertNull(type.fromClientJson(config(), StringNode("1.0.1")))
        assertNull(type.fromClientJson(config(), mapOf("test" to 42).asJson()))
        assertEquals(
            version("1.0.1"),
            type.fromClientJson(config(), mapOf("version" to "1.0.1").asJson())
        )
    }

    @Test
    fun `From stored JSON`() {
        assertNull(type.fromStoredJson(config(), NullNode.instance))
        assertEquals(version("42"), type.fromStoredJson(config(), IntNode(42)))
        assertNull(type.fromStoredJson(config(), mapOf("test" to 42).asJson()))
        assertNull(
            type.fromStoredJson(config(), mapOf("version" to "1.0.1").asJson())
        )
        assertEquals(
            version("1.0.1"),
            type.fromStoredJson(config(), "1.0.1".asJson())
        )
    }

    @Test
    fun `To stored JSON`() {
        assertTrue(type.toStoredJson(config(), null).isNull)
        assertEquals(
            "1.0.1".asJson(),
            type.toStoredJson(config(), version("1.0.1"))
        )
    }

    @Test
    fun `To config form`() {
        assertEquals(
            mapOf(
                "library" to "library",
                "required" to true,
                "lastSupported" to "1.1.0",
                "lastDeprecated" to null,
                "lastUnsupported" to null,
            ).asJson(),
            type.toConfigForm(config(true, "1.1.0"))
        )
    }

    @Test
    fun `From config form`() {
        assertEquals(
            config(true, "1.1.0"),
            type.fromConfigForm(
                mapOf(
                    "library" to "library",
                    "required" to true,
                    "lastSupported" to "1.1.0",
                    "lastDeprecated" to null,
                    "lastUnsupported" to null,
                ).asJson()
            )
        )
    }

    @Test
    fun `To config client JSON`() {
        assertEquals(
            mapOf(
                "settings" to mapOf(
                    "library" to "library",
                    "required" to true,
                    "lastSupported" to "1.1.0",
                    "lastDeprecated" to null,
                    "lastUnsupported" to null,
                )
            ).asJson(),
            type.toConfigClientJson(config(true, "1.1.0"))
        )
    }

    @Test
    fun `To config stored JSON`() {
        assertEquals(
            mapOf(
                "settings" to mapOf(
                    "library" to "library",
                    "required" to true,
                    "lastSupported" to "1.1.0",
                    "lastDeprecated" to null,
                    "lastUnsupported" to null,
                )
            ).asJson(),
            type.toConfigStoredJson(config(true, "1.1.0"))
        )
    }

    @Test
    fun `From config stored JSON`() {
        assertEquals(
            config(true, "1.1.0"),
            type.fromConfigStoredJson(
                mapOf(
                    "settings" to mapOf(
                        "library" to "library",
                        "required" to true,
                        "lastSupported" to "1.1.0",
                        "lastDeprecated" to null,
                        "lastUnsupported" to null,
                    )
                ).asJson(),
            )
        )
    }

    @Test
    fun `Backward compatible config stored JSON`() {
        assertEquals(
            config(true, "2.0.0", library = "n/a"),
            type.fromConfigStoredJson(
                mapOf(
                    "versionRequired" to true,
                    "versionMinimum" to "2.0.0",
                ).asJson(),
            )
        )
    }

    @Test
    fun `Backward compatible config stored JSON with null values`() {
        assertEquals(
            config(false, null, library = "n/a"),
            type.fromConfigStoredJson(
                mapOf(
                    "versionRequired" to null,
                    "versionMinimum" to null,
                ).asJson(),
            )
        )
    }

    private fun version(value: String) = JenkinsPipelineLibraryVersion(value)

    private fun config(
        versionRequired: Boolean = false,
        versionMinimum: String? = null,
        library: String = "library",
    ) =
        JenkinsPipelineLibraryIndicatorValueTypeConfig(
            JenkinsPipelineLibraryIndicatorLibrarySettings(
                library = library,
                required = versionRequired,
                lastSupported = versionMinimum,
            )
        )

}