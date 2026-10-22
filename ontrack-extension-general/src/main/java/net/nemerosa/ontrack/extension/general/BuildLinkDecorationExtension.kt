package net.nemerosa.ontrack.extension.general

import net.nemerosa.ontrack.extension.api.DecorationExtension
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import org.springframework.stereotype.Component
import java.util.*

@Component
class BuildLinkDecorationExtension(
    extensionFeature: GeneralExtensionFeature,
    private val structureService: StructureService,
    private val ontrackConfigProperties: OntrackConfigProperties,
) : AbstractExtension(extensionFeature), DecorationExtension<BuildLinkDecorationList> {

    override fun getScope(): EnumSet<ProjectEntityType> = EnumSet.of(ProjectEntityType.BUILD)

    override fun getDecorations(entity: ProjectEntity): List<Decoration<BuildLinkDecorationList>> {
        val build = entity as Build
        val downstreamCount = structureService.getCountQualifiedBuildsUsedBy(build)
        val upstreamCount = structureService.getCountQualifiedBuildsUsing(build)
        // No link at all, nothing to show
        return if (downstreamCount + upstreamCount > 0) {
            listOf(
                Decoration.of(
                    this,
                    BuildLinkDecorationList(
                        buildId = build.id(),
                        linksCount = downstreamCount,
                        upstreamCount = upstreamCount,
                        maxLinks = ontrackConfigProperties.buildLinks.decorationMaxLinks,
                    )
                )
            )
        } else {
            emptyList()
        }
    }

}
