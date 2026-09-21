package net.nemerosa.ontrack.extension.jenkins.model

import tools.jackson.databind.JsonNode
import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.indicators.IndicatorsExtensionFeature
import net.nemerosa.ontrack.extension.jenkins.*
import net.nemerosa.ontrack.extension.scm.SCMExtensionFeature
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.security.ProjectConfig
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.Branch.Companion.of
import net.nemerosa.ontrack.model.structure.ID.Companion.of
import net.nemerosa.ontrack.model.structure.NameDescription
import net.nemerosa.ontrack.model.structure.Project.Companion.of
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.test.TestUtils
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JenkinsJobPropertyTypeTest {

    private lateinit var type: JenkinsJobPropertyType
    private lateinit var securityService: SecurityService
    private lateinit var configurationService: JenkinsConfigurationService

    @BeforeEach
    fun before() {
        securityService = mockk<SecurityService>()
        configurationService = mockk<JenkinsConfigurationService>()
        type = JenkinsJobPropertyType(
            JenkinsExtensionFeature(
                IndicatorsExtensionFeature(),
                SCMExtensionFeature()
            ),
            configurationService
        )
    }

    @Test
    fun applies() {
        val types: Set<ProjectEntityType> = type.supportedEntityTypes
        assertTrue(types.contains(ProjectEntityType.PROJECT))
        assertTrue(types.contains(ProjectEntityType.BRANCH))
        assertTrue(types.contains(ProjectEntityType.PROMOTION_LEVEL))
        assertTrue(types.contains(ProjectEntityType.VALIDATION_STAMP))
        assertFalse(types.contains(ProjectEntityType.BUILD))
        assertFalse(types.contains(ProjectEntityType.PROMOTION_RUN))
        assertFalse(types.contains(ProjectEntityType.VALIDATION_RUN))
    }

    @Test
    fun canEdit() {
        val p1 = of(NameDescription("P1", "Project 1")).withId(of(1))
        val p2 = of(NameDescription("P2", "Project 2")).withId(of(2))
        val b1 = of(p1, NameDescription("B1", "Branch 1")).withId(of(10))
        val b2 = of(p2, NameDescription("B2", "Branch 2")).withId(of(20))

        every { securityService.isProjectFunctionGranted(1, ProjectConfig::class.java) } returns true
        every { securityService.isProjectFunctionGranted(2, ProjectConfig::class.java) } returns false

        assertTrue(type.canEdit(b1, securityService))
        assertFalse(type.canEdit(b2, securityService))
    }

    @Test
    fun canView() {
        val p1 = of(NameDescription("P1", "Project 1")).withId(of(1))
        val p2 = of(NameDescription("P2", "Project 2")).withId(of(2))
        val b1 = of(p1, NameDescription("B1", "Branch 1")).withId(of(10))
        val b2 = of(p2, NameDescription("B2", "Branch 2")).withId(of(20))
        assertTrue(type.canView(b1, securityService))
        assertTrue(type.canView(b2, securityService))
    }

    @Test
    fun forStorage() {
        val configuration = JenkinsConfiguration(
            "MyConfig",
            "http://jenkins",
            "user",
            "secret"
        )
        TestUtils.assertJsonEquals(
            mapOf(
                "configuration" to "MyConfig",
                "job" to "MyJob",
            ).asJson(),
            type.forStorage(
                JenkinsJobProperty(
                    configuration,
                    "MyJob"
                )
            )
        )
    }

    @Test
    fun fromStorage() {
        val configuration = JenkinsConfiguration(
            "MyConfig",
            "http://jenkins",
            "user",
            "secret"
        )
        every { configurationService.getConfiguration("MyConfig") } returns configuration
        // Stored JSON
        val node: JsonNode = mapOf(
            "configuration" to "MyConfig",
            "job" to "MyJob",
        ).asJson()
        // Reading
        val retrieved = type.fromStorage(node)
        assertEquals(configuration.url, retrieved.configuration.url)
        assertEquals("MyJob", retrieved.job)
    }
}
