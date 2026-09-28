package net.nemerosa.ontrack.extension.sonarqube.property

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.casc.CascExtensionFeature
import net.nemerosa.ontrack.extension.sonarqube.SonarQubeExtensionFeature
import net.nemerosa.ontrack.extension.sonarqube.configuration.SonarQubeConfiguration
import net.nemerosa.ontrack.extension.sonarqube.configuration.SonarQubeConfigurationService
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.toJson
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SonarQubePropertyTypeTest {

    private val configurationService = mockk<SonarQubeConfigurationService>()
    private val configuration = SonarQubeConfiguration(
        "conf-name",
        "https://sonarqube.nemerosa.net",
        "xxxx"
    )

    private val type = SonarQubePropertyType(
        SonarQubeExtensionFeature(CascExtensionFeature()),
        configurationService
    )

    @BeforeEach
    fun setup() {
        every { configurationService.getConfiguration("conf-name") } returns configuration
    }

    @Test
    fun clientParsing() {
        val client = mapOf(
            "configuration" to "conf-name",
            "key" to "project:key",
            "validationStamp" to "sonarqube",
            "measures" to listOf("measure-1", "measure-2"),
            "override" to true,
            "branchModel" to true,
            "branchPattern" to "master|develop"
        ).asJson()
        val parsed = type.fromClient(client)
        assertEquals("conf-name", parsed.configuration.name)
        assertEquals("https://sonarqube.nemerosa.net", parsed.configuration.url)
        assertEquals("xxxx", parsed.configuration.password)
        assertEquals("project:key", parsed.key)
        assertEquals("sonarqube", parsed.validationStamp)
        assertEquals(listOf("measure-1", "measure-2"), parsed.measures)
        assertEquals(true, parsed.override)
        assertEquals(true, parsed.branchModel)
        assertEquals("master|develop", parsed.branchPattern)
    }

    @Test
    fun `Client parsing with default values`() {
        val client = mapOf(
            "configuration" to "conf-name",
            "key" to "project:key"
        ).asJson()
        val parsed = type.fromClient(client)
        assertEquals("conf-name", parsed.configuration.name)
        assertEquals("https://sonarqube.nemerosa.net", parsed.configuration.url)
        assertEquals("xxxx", parsed.configuration.password)
        assertEquals("project:key", parsed.key)
        assertEquals("sonarqube", parsed.validationStamp)
        assertEquals(listOf(), parsed.measures)
        assertEquals(false, parsed.override)
        assertEquals(false, parsed.branchModel)
        assertEquals(null, parsed.branchPattern)
    }

    @Test
    fun clientParsingWithNoMeasure() {
        val client = mapOf(
            "configuration" to "conf-name",
            "key" to "project:key",
            "validationStamp" to "sonarqube",
            "measures" to listOf<String>(),
            "override" to false
        ).toJson()!!
        val parsed = type.fromClient(client)
        assertEquals("conf-name", parsed.configuration.name)
        assertEquals("https://sonarqube.nemerosa.net", parsed.configuration.url)
        assertEquals("xxxx", parsed.configuration.password)
        assertEquals("project:key", parsed.key)
        assertEquals("sonarqube", parsed.validationStamp)
        assertEquals(emptyList(), parsed.measures)
        assertEquals(false, parsed.override)
    }

    @Test
    fun storage() {
        val item = SonarQubeProperty(
            configuration = configuration,
            key = "project:key",
            validationStamp = "sonarqube",
            measures = listOf("measure-1", "measure-2"),
            override = false,
            branchModel = false,
            branchPattern = null,
            validationMetrics = true,
        )
        // For storage
        val stored = type.forStorage(item)
        // From storage
        val retrieved = type.fromStorage(stored)
        // Checks
        assertEquals("conf-name", retrieved.configuration.name)
        assertEquals("https://sonarqube.nemerosa.net", retrieved.configuration.url)
        assertEquals("xxxx", retrieved.configuration.password)
        assertEquals("project:key", retrieved.key)
        assertEquals("sonarqube", retrieved.validationStamp)
        assertEquals(listOf("measure-1", "measure-2"), retrieved.measures)
        assertEquals(false, retrieved.override)
        assertEquals(false, retrieved.branchModel)
        assertEquals(null, retrieved.branchPattern)
    }

    @Test
    fun storageWithNoMeasure() {
        val item = SonarQubeProperty(
            configuration = configuration,
            key = "project:key",
            validationStamp = "sonarqube",
            measures = emptyList(),
            override = false,
            branchModel = true,
            branchPattern = "master|develop",
            validationMetrics = true,
        )
        // For storage
        val stored = type.forStorage(item)
        // From storage
        val retrieved = type.fromStorage(stored)
        // Checks
        assertEquals("conf-name", retrieved.configuration.name)
        assertEquals("https://sonarqube.nemerosa.net", retrieved.configuration.url)
        assertEquals("xxxx", retrieved.configuration.password)
        assertEquals("project:key", retrieved.key)
        assertEquals("sonarqube", retrieved.validationStamp)
        assertEquals(emptyList(), retrieved.measures)
        assertEquals(false, retrieved.override)
        assertEquals(true, retrieved.branchModel)
        assertEquals("master|develop", retrieved.branchPattern)
    }

}