package net.nemerosa.ontrack.extension.av.config

import net.nemerosa.ontrack.extension.av.AutoVersioningTestFixtures.sourceConfig
import net.nemerosa.ontrack.json.JsonParseException
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AutoVersioningSourceConfigTest {

    @Test
    fun `Target paths when only one path`() {
        assertEquals(
            listOf("Jenkinsfile"),
            sourceConfig(targetPath = "Jenkinsfile").defaultPath.paths
        )
    }

    @Test
    fun `Target paths when several paths`() {
        assertEquals(
            listOf(
                "Jenkinsfile",
                "Jenkinsfile.acceptance",
                "Jenkinsfile.nightly",
                ".jobs/pipelines/deployDGCDocker.groovy"
            ),
            sourceConfig(targetPath = "Jenkinsfile, Jenkinsfile.acceptance, Jenkinsfile.nightly, .jobs/pipelines/deployDGCDocker.groovy")
                .defaultPath.paths
        )
    }

    @Test
    fun `Missing both target regex and target property`() {
        assertFailsWith<MissingTargetRegexOrPropertyException> {
            sourceConfig(targetRegex = null, targetProperty = null).validate()
        }
        assertFailsWith<MissingTargetRegexOrPropertyException> {
            sourceConfig(targetRegex = "", targetProperty = null).validate()
        }
        assertFailsWith<MissingTargetRegexOrPropertyException> {
            sourceConfig(targetRegex = "", targetProperty = "").validate()
        }
        assertFailsWith<MissingTargetRegexOrPropertyException> {
            sourceConfig(targetRegex = null, targetProperty = "").validate()
        }
    }

    @Test
    fun `At least target regex or target property`() {
        sourceConfig(targetRegex = "my-version = (.*)", targetProperty = null).validate()
        sourceConfig(targetRegex = "my-version = (.*)", targetProperty = "my-version").validate()
        sourceConfig(targetRegex = null, targetProperty = "my-version").validate()
    }

    @Test
    fun `Null upgrade branch pattern is valid`() {
        val config = sourceConfig(upgradeBranchPattern = null)
        config.validate()
    }

    @Test
    fun `Upgrade branch pattern with version and project is valid`() {
        val config = sourceConfig(
            upgradeBranchPattern = "my-custom-branch-with-<project>-and-<version>"
        )
        config.validate()
    }

    @Test
    fun `Upgrade branch pattern with version and no project is valid`() {
        val config = sourceConfig(
            upgradeBranchPattern = "my-custom-branch-with-<version>"
        )
        config.validate()
    }

    @Test
    fun `Upgrade branch pattern with no version is not valid`() {
        val config = sourceConfig(
            upgradeBranchPattern = "my-custom-branch-with-<project>"
        )
        assertFailsWith<UpgradeBranchPrefixNoVersionException> {
            config.validate()
        }
    }

    @Test
    fun `Default upgrade branch with hash`() {
        assertEquals(
            "feature/auto-upgrade-PRJ-1.0.0-c55cefaf36fcdce3f97fdd15ba82e6b6",
            AutoVersioningSourceConfig.getUpgradeBranch(
                AutoVersioningSourceConfig.DEFAULT_UPGRADE_BRANCH_PATTERN,
                "PRJ",
                "1.0.0",
                "branch-name",
                "GOLD",
                listOf("gradle.properties"),
                true
            )
        )
    }

    @Test
    fun `Default upgrade branch hash is different for a different promotion`() {
        assertEquals(
            "feature/auto-upgrade-PRJ-1.0.0-b65e603d45e3d6a8d9caefc689a45b27",
            AutoVersioningSourceConfig.getUpgradeBranch(
                AutoVersioningSourceConfig.DEFAULT_UPGRADE_BRANCH_PATTERN,
                "PRJ",
                "1.0.0",
                "branch-name",
                "SILVER",
                listOf("gradle.properties"),
                true
            )
        )
    }

    @Test
    fun `Default upgrade branch hash is different for a different path`() {
        assertEquals(
            "feature/auto-upgrade-PRJ-1.0.0-db6bfe675fdca14ad9cac17e6b786a90",
            AutoVersioningSourceConfig.getUpgradeBranch(
                AutoVersioningSourceConfig.DEFAULT_UPGRADE_BRANCH_PATTERN,
                "PRJ",
                "1.0.0",
                "branch-name",
                "SILVER",
                listOf("pom.xml"),
                true
            )
        )
    }

    @Test
    fun `Default upgrade branch without hash`() {
        assertEquals(
            "feature/auto-upgrade-PRJ-1.0.0-branch-name",
            AutoVersioningSourceConfig.getUpgradeBranch(
                AutoVersioningSourceConfig.DEFAULT_UPGRADE_BRANCH_PATTERN,
                "PRJ",
                "1.0.0",
                "branch-name",
                "GOLD",
                listOf("gradle.properties"),
                false
            )
        )
    }

    @Test
    fun `Branch with project and version`() {
        assertEquals(
            "custom-with-PRJ-and-1.0.0-7b5b8ade11d6930b82a8e54e0333d547",
            AutoVersioningSourceConfig.getUpgradeBranch(
                "custom-with-<project>-and-<version>",
                "PRJ",
                "1.0.0",
                "any",
                "GOLD",
                listOf("gradle.properties"),
                true
            )
        )
    }

    @Test
    fun `Branch with version only`() {
        assertEquals(
            "custom-for-1.0.0-7b5b8ade11d6930b82a8e54e0333d547",
            AutoVersioningSourceConfig.getUpgradeBranch(
                "custom-for-<version>",
                "PRJ",
                "1.0.0",
                "any",
                "GOLD",
                listOf("gradle.properties"),
                true
            )
        )
    }

    @Test
    fun `Branch with version and branch`() {
        assertEquals(
            "custom-for-1.0.0-7b5b8ade11d6930b82a8e54e0333d547",
            AutoVersioningSourceConfig.getUpgradeBranch(
                "custom-for-<version>-<branch>",
                "PRJ",
                "1.0.0",
                "any",
                "GOLD",
                listOf("gradle.properties"),
                true
            )
        )
    }

    @Test
    fun `Null post processing config must be deserialized as null`() {
        val config = sourceConfig()
        val json = config.asJson()
        val parsed = json.parse<AutoVersioningSourceConfig>().postDeserialize()
        assertNull(
            parsed.postProcessingConfig,
            "null postProcessingConfig must be deserialized as null, not a NullNode."
        )
        assertEquals(config, parsed)
    }

    /**
     * The 8 legacy attribute names accepted by Yontrack 5 (#1515) are removed in V6 (#1926):
     * a configuration still using one of them is rejected.
     */
    @Test
    fun `Legacy attribute names are rejected`() {
        val current = mapOf(
            "sourceProject" to "p",
            "sourceBranch" to "main",
            "sourcePromotion" to "GOLD",
            "targetPath" to "gradle.properties",
            "targetProperty" to "version",
        )
        listOf(
            "project",
            "branch",
            "promotion",
            "path",
            "regex",
            "property",
            "propertyRegex",
            "propertyType",
        ).forEach { legacy ->
            val ex = assertFailsWith<JsonParseException>("Legacy name $legacy is rejected") {
                (current + (legacy to "any")).asJson().parse<AutoVersioningSourceConfig>()
            }
            assertContains(ex.message ?: "", "\"$legacy\"", message = "The error names $legacy")
        }
    }

    @Test
    fun `Configuration using only the legacy names is rejected`() {
        assertFailsWith<JsonParseException> {
            mapOf(
                "project" to "p",
                "branch" to "main",
                "promotion" to "GOLD",
                "path" to "gradle.properties",
                "property" to "version",
            ).asJson().parse<AutoVersioningSourceConfig>()
        }
    }

}
