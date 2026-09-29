package net.nemerosa.ontrack.extension.av.ci

import io.mockk.mockk
import io.mockk.verify
import net.nemerosa.ontrack.extension.av.AutoVersioningExtensionFeature
import net.nemerosa.ontrack.extension.av.config.AutoVersioningSourceConfig
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.deprecation.DeprecationService
import net.nemerosa.ontrack.model.deprecation.DeprecationSurface
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AutoVersioningBranchCIConfigExtensionTest {

    @Test
    fun `Legacy attribute names are reported once per name`() {
        val deprecationService = mockk<DeprecationService>(relaxed = true)
        val extension = AutoVersioningBranchCIConfigExtension(
            autoVersioningExtensionFeature = mockk<AutoVersioningExtensionFeature>(relaxed = true),
            autoVersioningConfigurationService = mockk(),
            branchDisplayNameService = mockk(),
            deprecationService = deprecationService,
        )
        val legacy = mapOf(
            "project" to "my-project",
            "sourceBranch" to "main",
            "sourcePromotion" to "GOLD",
            "path" to "versions.properties",
        )
        val config = extension.parseData(
            mapOf("configurations" to listOf(legacy, legacy)).asJson()
        )
        assertEquals("my-project", config.configurations.first().sourceProject)
        verify(exactly = 1) {
            deprecationService.deprecatedUsage(
                DeprecationSurface.CI_CONFIG,
                "autoVersioning.configurations.project",
                "Removed in V6. Use sourceProject instead. See #1926",
            )
        }
        verify(exactly = 1) {
            deprecationService.deprecatedUsage(
                DeprecationSurface.CI_CONFIG,
                "autoVersioning.configurations.path",
                "Removed in V6. Use targetPath instead. See #1926",
            )
        }
        verify(exactly = 2) { deprecationService.deprecatedUsage(any(), any(), any()) }
    }

    @Test
    fun `Legacy attribute names are reported in a custom configuration`() {
        val deprecationService = mockk<DeprecationService>(relaxed = true)
        val extension = AutoVersioningBranchCIConfigExtension(
            autoVersioningExtensionFeature = mockk<AutoVersioningExtensionFeature>(relaxed = true),
            autoVersioningConfigurationService = mockk(),
            branchDisplayNameService = mockk(),
            deprecationService = deprecationService,
        )
        extension.mergeConfig(
            defaults = AutoVersioningBranchCIConfig(configurations = emptyList()),
            custom = mapOf(
                "configurations" to listOf(
                    mapOf(
                        "sourceProject" to "my-project",
                        "sourceBranch" to "main",
                        "promotion" to "GOLD",
                        "targetPath" to "versions.properties",
                    )
                )
            ).asJson()
        )
        verify(exactly = 1) {
            deprecationService.deprecatedUsage(
                DeprecationSurface.CI_CONFIG,
                "autoVersioning.configurations.promotion",
                "Removed in V6. Use sourcePromotion instead. See #1926",
            )
        }
    }

    @Test
    fun `Merging partial configurations with no branch filter`() {
        val extension = AutoVersioningBranchCIConfigExtension(
            autoVersioningExtensionFeature = mockk<AutoVersioningExtensionFeature>(relaxed = true),
            autoVersioningConfigurationService = mockk(),
            branchDisplayNameService = mockk(),
            deprecationService = mockk(relaxed = true),
        )
        val config = extension.mergeConfig(
            defaults = AutoVersioningBranchCIConfig(
                configurations = listOf(
                    AutoVersioningSourceConfig(
                        sourceProject = "my-project",
                        sourceBranch = "main",
                        sourcePromotion = "GOLD",
                        targetPath = "versions.properties",
                        targetProperty = "yontrackVersion",
                        validationStamp = "my-chart-validator",
                        disabled = true
                    )
                )
            ),
            custom = mapOf(
                "configurations" to listOf(
                    mapOf(
                        "sourceProject" to "my-project",
                        "sourceBranch" to "main",
                        "sourcePromotion" to "GOLD",
                        "disabled" to false,
                    )
                )
            ).asJson()
        )
        assertEquals(false, config.configurations.single().disabled, "Disabled has been overridden")
    }

    @Test
    fun `Merging branch filters, branch filters are always the default`() {
        val extension = AutoVersioningBranchCIConfigExtension(
            autoVersioningExtensionFeature = mockk<AutoVersioningExtensionFeature>(relaxed = true),
            autoVersioningConfigurationService = mockk(),
            branchDisplayNameService = mockk(),
            deprecationService = mockk(relaxed = true),
        )
        val avConfig = AutoVersioningSourceConfig(
            sourceProject = "my-project",
            sourceBranch = "main",
            sourcePromotion = "GOLD",
            targetPath = "versions.properties",
            targetProperty = "yontrackVersion",
            validationStamp = "my-chart-validator",
            disabled = true
        )
        val config = extension.mergeConfig(
            defaults = AutoVersioningBranchCIConfig(
                branchFilter = AutoVersioningBranchCIConfigBranchFilter(
                    includes = listOf("main", "release\\/\\.*"),
                    excludes = listOf("release\\/1\\.*"),
                ),
                configurations = listOf(avConfig),
            ),
            custom = mapOf(
                "branchFilter" to mapOf(
                    "includes" to listOf("main", "release\\/\\.*"),
                    "excludes" to listOf("release\\/0\\.*"),
                ),
            ).asJson()
        )
        assertEquals(
            AutoVersioningBranchCIConfigBranchFilter(
                includes = listOf("main", "release\\/\\.*"),
                excludes = listOf("release\\/1\\.*"),
            ),
            config.branchFilter,
        )
    }

    @Test
    fun `Merging configurations and branch filters, branch filters are always the default`() {
        val extension = AutoVersioningBranchCIConfigExtension(
            autoVersioningExtensionFeature = mockk<AutoVersioningExtensionFeature>(relaxed = true),
            autoVersioningConfigurationService = mockk(),
            branchDisplayNameService = mockk(),
            deprecationService = mockk(relaxed = true),
        )
        val config = extension.mergeConfig(
            defaults = AutoVersioningBranchCIConfig(
                branchFilter = AutoVersioningBranchCIConfigBranchFilter(
                    includes = listOf("main", "release\\/\\.*"),
                    excludes = listOf("release\\/1\\.*"),
                ),
                configurations = listOf(
                    AutoVersioningSourceConfig(
                        sourceProject = "my-project",
                        sourceBranch = "main",
                        sourcePromotion = "GOLD",
                        targetPath = "versions.properties",
                        targetProperty = "yontrackVersion",
                        validationStamp = "my-chart-validator",
                        disabled = true
                    )
                )
            ),
            custom = mapOf(
                "branchFilter" to mapOf(
                    "includes" to listOf("main", "release\\/\\.*"),
                    "excludes" to listOf("release\\/0\\.*"),
                ),
                "configurations" to listOf(
                    mapOf(
                        "sourceProject" to "my-project",
                        "sourceBranch" to "main",
                        "sourcePromotion" to "GOLD",
                        "disabled" to false,
                    )
                )
            ).asJson()
        )
        assertEquals(false, config.configurations.single().disabled, "Disabled has been overridden")
        assertEquals(
            AutoVersioningBranchCIConfigBranchFilter(
                includes = listOf("main", "release\\/\\.*"),
                excludes = listOf("release\\/1\\.*"),
            ),
            config.branchFilter,
        )
    }

}