package net.nemerosa.ontrack.extension.sonarqube.measures

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.casc.CascExtensionFeature
import net.nemerosa.ontrack.extension.sonarqube.SonarQubeExtensionFeature
import net.nemerosa.ontrack.model.structure.BranchFixtures
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.BuildFixtures
import net.nemerosa.ontrack.test.assertIs
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SonarQubeMeasuresInformationExtensionTest {

    private lateinit var sonarQubeMeasuresCollectionService: SonarQubeMeasuresCollectionService
    private lateinit var informationExtension: SonarQubeMeasuresInformationExtension

    @BeforeEach
    fun setup() {
        sonarQubeMeasuresCollectionService = mockk()
        informationExtension = SonarQubeMeasuresInformationExtension(
            extensionFeature = SonarQubeExtensionFeature(
                CascExtensionFeature()
            ),
            sonarQubeMeasuresCollectionService = sonarQubeMeasuresCollectionService
        )
    }

    @Test
    fun `Getting measures for a build`() {
        val build: Build = BuildFixtures.testBuild()
        every {
            sonarQubeMeasuresCollectionService.getMeasures(build)
        } returns SonarQubeMeasures(
            mapOf(
                "measure-1" to 12.3,
                "measure-2" to 20.0
            )
        )
        val info = informationExtension.getInformation(build)
        assertNotNull(info) {
            assertIs<SonarQubeMeasures>(it.data) { q ->
                assertEquals(
                    mapOf(
                        "measure-1" to 12.3,
                        "measure-2" to 20.0
                    ),
                    q.measures
                )
            }
        }
    }

    @Test
    fun `Not getting any measure when no measure is attached to the build`() {
        val build: Build = BuildFixtures.testBuild()
        every {
            sonarQubeMeasuresCollectionService.getMeasures(build)
        } returns null
        val info = informationExtension.getInformation(build)
        assertNull(info)
    }

    @Test
    fun `Not getting any measure for something different than a build`() {
        val info = informationExtension.getInformation(BranchFixtures.testBranch())
        assertNull(info)
    }

}