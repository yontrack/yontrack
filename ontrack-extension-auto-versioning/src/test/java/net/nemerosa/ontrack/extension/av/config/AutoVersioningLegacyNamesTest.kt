package net.nemerosa.ontrack.extension.av.config

import net.nemerosa.ontrack.json.asJson
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AutoVersioningLegacyNamesTest {

    @Test
    fun `No legacy name in a configuration using the current names`() {
        val node = mapOf(
            "sourceProject" to "p",
            "sourceBranch" to "main",
            "sourcePromotion" to "GOLD",
            "targetPath" to "gradle.properties",
            "targetProperty" to "version",
        ).asJson()
        assertEquals(emptyList(), AutoVersioningLegacyNames.find(node))
    }

    @Test
    fun `Every legacy name is found with its replacement`() {
        val node = mapOf(
            "project" to "p",
            "branch" to "main",
            "promotion" to "GOLD",
            "path" to "gradle.properties",
            "regex" to "version=(.*)",
            "property" to "version",
            "propertyRegex" to "(.*)",
            "propertyType" to "properties",
            "postProcessing" to "github",
        ).asJson()
        assertEquals(
            listOf(
                "project" to "sourceProject",
                "branch" to "sourceBranch",
                "promotion" to "sourcePromotion",
                "path" to "targetPath",
                "regex" to "targetRegex",
                "property" to "targetProperty",
                "propertyRegex" to "targetPropertyRegex",
                "propertyType" to "targetPropertyType",
            ),
            AutoVersioningLegacyNames.find(node)
        )
    }
}
