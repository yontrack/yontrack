package net.nemerosa.ontrack.extension.github.ingestion.extensions.links

import net.nemerosa.ontrack.extension.general.ReleasePropertyType
import net.nemerosa.ontrack.extension.github.ingestion.extensions.support.AbstractIngestionBuildEventProcessor
import net.nemerosa.ontrack.extension.github.ingestion.processing.IngestionEventPreprocessingCheck
import net.nemerosa.ontrack.extension.github.ingestion.processing.IngestionEventProcessingResultDetails
import net.nemerosa.ontrack.extension.github.ingestion.support.IngestionModelAccessService
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.BuildLink
import net.nemerosa.ontrack.model.structure.BuildSearchForm
import net.nemerosa.ontrack.model.structure.StructureService
import org.springframework.stereotype.Component
import kotlin.jvm.optionals.getOrNull
import kotlin.reflect.KClass

@Component
class IngestionLinksEventProcessor(
    ingestionModelAccessService: IngestionModelAccessService,
    private val structureService: StructureService,
) : AbstractIngestionBuildEventProcessor<GitHubIngestionLinksPayload>(
    ingestionModelAccessService
) {

    override fun preProcessingCheck(payload: GitHubIngestionLinksPayload): IngestionEventPreprocessingCheck =
        if (payload.buildLinks.isNotEmpty()) {
            IngestionEventPreprocessingCheck.TO_BE_PROCESSED
        } else {
            IngestionEventPreprocessingCheck.IGNORED
        }

    override fun process(build: Build, input: GitHubIngestionLinksPayload): IngestionEventProcessingResultDetails {
        val targets = input.buildLinks.mapNotNull { link ->
            getTargetBuild(link)?.run {
                BuildLink(this, link.qualifier ?: BuildLink.DEFAULT)
            }
        }
        // Gets the existing links
        val existingLinks =
            structureService.getBuildDependencyLinks(build, size = Int.MAX_VALUE).pageItems.toMutableList()
        // Added links
        val addedLinks = mutableSetOf<BuildLink>()
        // Adding all links of the input
        targets.forEach { link ->
            structureService.createBuildLink(build, link.build, link.qualifier)
            addedLinks += link
        }
        // Deletes all authorised links which were not added again
        var removedLinks = 0
        if (!input.addOnly) {
            // Other links, not authorised to view, were not subject to edition and are not visible
            existingLinks.removeAll(addedLinks)
            removedLinks = existingLinks.size
            existingLinks.forEach { link ->
                structureService.deleteBuildLink(
                    build,
                    link.build,
                    link.qualifier,
                )
            }
        }
        // OK
        val details = if (input.addOnly) {
            """Added ${addedLinks.size} links."""
        } else {
            """Added ${addedLinks.size} links, removed $removedLinks links."""
        }
        return IngestionEventProcessingResultDetails.processed(details)
    }

    private fun getTargetBuild(link: GitHubIngestionLink): Build? {
        // Gets the project if possible
        val project = structureService.findProjectByName(link.project).getOrNull() ?: return null
        // Gets the build by label
        return if (link.buildRef.startsWith("#")) {
            val label = link.buildRef.substringAfter("#")
            structureService.buildSearch(
                project.id, BuildSearchForm(
                    maximumCount = 1,
                    property = ReleasePropertyType::class.java.name,
                    propertyValue = label,
                )
            ).firstOrNull()
        }
        // Gets the build by name
        else {
            structureService.buildSearch(
                project.id, BuildSearchForm(
                    maximumCount = 1,
                    buildName = link.buildRef,
                    buildExactMatch = true,
                )
            ).firstOrNull()
        }
    }

    override val payloadType: KClass<GitHubIngestionLinksPayload> =
        GitHubIngestionLinksPayload::class

    override val event: String = EVENT

    companion object {
        const val EVENT = "x-ontrack-build-links"
    }
}