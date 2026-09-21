package net.nemerosa.ontrack.extension.jenkins

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.indicators.IndicatorsExtensionFeature
import net.nemerosa.ontrack.extension.jenkins.client.JenkinsClient
import net.nemerosa.ontrack.extension.jenkins.client.JenkinsClientFactory
import net.nemerosa.ontrack.extension.jenkins.client.JenkinsJob
import net.nemerosa.ontrack.extension.scm.SCMExtensionFeature
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.NameDescription.Companion.nd
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.Property
import net.nemerosa.ontrack.model.structure.PropertyService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class JenkinsJobDecorationExtensionTest {

    private lateinit var propertyService: PropertyService
    private lateinit var jenkinsClientFactory: JenkinsClientFactory
    private lateinit var extension: JenkinsJobDecorationExtension
    private lateinit var branch: Branch
    private lateinit var jenkinsJobProperty: Property<JenkinsJobProperty>

    @BeforeEach
    fun before() {
        propertyService = mockk()
        jenkinsClientFactory = mockk()
        extension = JenkinsJobDecorationExtension(
            JenkinsExtensionFeature(
                IndicatorsExtensionFeature(),
                SCMExtensionFeature()
            ),
            propertyService,
            jenkinsClientFactory
        )

        val jenkinsConfiguration = JenkinsConfiguration("Jenkins", "http://jenkins", "", "")

        val client: JenkinsClient = mockk()
        every { client.getJob("MyBuild") } returns JenkinsJob(
            "MyBuild",
            "http://jenkins/job/MyBuild"
        )
        every { jenkinsClientFactory.getClient(jenkinsConfiguration) } returns client

        branch = Branch.of(
            Project.of(nd("P", "")),
            nd("B", "")
        )


        jenkinsJobProperty = Property.of(
            JenkinsJobPropertyType(
                JenkinsExtensionFeature(
                    IndicatorsExtensionFeature(),
                    SCMExtensionFeature()
                ),
                mockk()
            ),
            JenkinsJobProperty(
                jenkinsConfiguration,
                "MyBuild"
            )
        )
    }

    @Test
    fun `Decoration for a branch`() {
        every {
            propertyService.getPropertyValue(
                branch,
                JenkinsJobPropertyType::class.java
            )
        } returns jenkinsJobProperty.value
        val decorations = extension.getDecorations(branch)
        assertNotNull(decorations) { list ->
            assertEquals(1, list.size)
            val decoration = list.first()
            assertEquals(
                "net.nemerosa.ontrack.extension.jenkins.JenkinsJobDecorationExtension",
                decoration.decorationType
            )
            assertEquals(
                JenkinsJob(
                    "MyBuild",
                    "http://jenkins/job/MyBuild"
                ),
                decoration.data
            )
        }
    }

}
