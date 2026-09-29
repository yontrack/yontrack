package net.nemerosa.ontrack.extension.av.config

import com.fasterxml.jackson.databind.JsonNode

/**
 * Legacy attribute names of an [AutoVersioningSourceConfig], accepted as aliases since #1515
 * for the old Jenkins pipeline library, and removed in V6 (#1926).
 */
object AutoVersioningLegacyNames {

    /**
     * Legacy name → current name
     */
    private val names = linkedMapOf(
        "project" to AutoVersioningSourceConfig::sourceProject.name,
        "branch" to AutoVersioningSourceConfig::sourceBranch.name,
        "promotion" to AutoVersioningSourceConfig::sourcePromotion.name,
        "path" to AutoVersioningSourceConfig::targetPath.name,
        "regex" to AutoVersioningSourceConfig::targetRegex.name,
        "property" to AutoVersioningSourceConfig::targetProperty.name,
        "propertyRegex" to AutoVersioningSourceConfig::targetPropertyRegex.name,
        "propertyType" to AutoVersioningSourceConfig::targetPropertyType.name,
    )

    /**
     * Legacy names used by the JSON of a source configuration, each with its current name.
     */
    fun find(node: JsonNode): List<Pair<String, String>> =
        names.filterKeys { node.has(it) }.toList()

    fun message(current: String) = "Removed in V6. Use $current instead. See #1926"
}
