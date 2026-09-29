package net.nemerosa.ontrack.extension.av.ci

import io.mockk.mockk
import net.nemerosa.ontrack.extension.av.AutoVersioningExtensionFeature
import net.nemerosa.ontrack.extension.av.config.AutoVersioningSourceConfig
import net.nemerosa.ontrack.json.JsonParseException
import net.nemerosa.ontrack.json.asJson
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AutoVersioningBranchCIConfigExtensionTest {

    private fun extension() = AutoVersioningBranchCIConfigExtension(
        autoVersioningExtensionFeature = mockk<AutoVersioningExtensionFeature>(relaxed = true),
        autoVersioningConfigurationService = mockk(),
        branchDisplayNameService = mockk(),
    )

    @Test
    fun `Legacy attribute names are rejected`() {
        val legacy = mapOf(
            "project" to "my-project",
            "sourceBranch" to "main",
            "sourcePromotion" to "GOLD",
            "targetPath" to "versions.properties",
            "targetProperty" to "version",
        )
        val ex = assertFailsWith<JsonParseException> {
            extension().parseData(
                mapOf("configurations" to listOf(legacy)).asJson()
            )
        }
        assertContains(ex.message ?: "", "sourceProject")
    }

    @Test
    fun `Legacy attribute names are rejected in a custom configuration`() {
        assertFailsWith<JsonParseException> {
            extension().mergeConfig(
                defaults = AutoVersioningBranchCIConfig(configurations = emptyList()),
                custom = mapOf(
                    "configurations" to listOf(
                        mapOf(
                            "sourceProject" to "my-project",
                            "sourceBranch" to "main",
                            "promotion" to "GOLD",
                            "targetPath" to "versions.properties",
                            "targetProperty" to "version",
                        )
                    )
                ).asJson()
            )
        }
    }

    @Test
    fun `Merging partial configurations with no branch filter`() {
        val extension = extension()
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
        val extension = extension()
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
        val extension = extension()
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