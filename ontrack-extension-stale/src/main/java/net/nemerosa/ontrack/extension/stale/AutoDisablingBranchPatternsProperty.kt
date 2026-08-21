package net.nemerosa.ontrack.extension.stale

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.model.docs.DocumentationList

/**
 * Configuration of branch patterns to be automatically disabled.
 */
data class AutoDisablingBranchPatternsProperty(
    @APIDescription("List of patterns and their behaviour")
    @DocumentationList
    val items: List<AutoDisablingBranchPatternsPropertyItem>,
)
