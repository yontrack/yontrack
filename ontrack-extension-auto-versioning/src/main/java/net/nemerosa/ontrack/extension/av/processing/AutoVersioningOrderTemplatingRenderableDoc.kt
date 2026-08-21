package net.nemerosa.ontrack.extension.av.processing

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.extension.scm.changelog.ChangeLogTemplatingServiceConfig
import net.nemerosa.ontrack.extension.scm.changelog.SemanticChangeLogTemplatingServiceConfig
import net.nemerosa.ontrack.model.templating.TemplatingRenderableDoc
import net.nemerosa.ontrack.model.templating.TemplatingRenderableDocField
import org.springframework.stereotype.Component

/**
 * Documentation for AutoVersioningOrderTemplatingRenderable.
 */
@Component
@APIDescription("The `av` context can be used in templates in the PR title & body templates, in order to access information about the auto-versioning request.")
class AutoVersioningOrderTemplatingRenderableDoc : TemplatingRenderableDoc {

    override val id: String = "av"

    override val displayName: String = "Auto-versioning context"

    override val contextName: String = "Auto-versioning"

    override val fields: List<TemplatingRenderableDocField> = listOf(
        TemplatingRenderableDocField(
            name = "changelog",
            description = "Changelog for the project & version being updated",
            config = ChangeLogTemplatingServiceConfig::class,
        ),
        TemplatingRenderableDocField(
            name = "semanticChangelog",
            description = "Semantic changelog (grouped by conventional commit type) for the project & version being updated",
            config = SemanticChangeLogTemplatingServiceConfig::class,
        ),
    )
}