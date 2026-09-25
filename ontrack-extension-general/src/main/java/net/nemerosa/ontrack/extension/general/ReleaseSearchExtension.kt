package net.nemerosa.ontrack.extension.general

import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.events.EventListener
import net.nemerosa.ontrack.model.structure.*
import org.springframework.stereotype.Component

/**
 * Search documents for the release property of the builds: one document per build having a
 * release, which is its title and its identifier. The time of the build is its recency.
 *
 * The documents are written by the hooks of the [ReleasePropertyType], in the transaction of the
 * change of property, and rewritten when the build is updated, since they carry its name. The
 * documents of a deleted build, or of the builds of a deleted branch, are deleted by the search
 * service.
 */
@Component
class ReleaseSearchExtension(
    extensionFeature: GeneralExtensionFeature,
    private val propertyService: PropertyService,
    private val structureService: StructureService,
    private val searchDocumentService: SearchDocumentService,
) : AbstractExtension(extensionFeature), SearchDocumentIndexer, EventListener {

    override val searchResultType = SearchResultType(
        feature = extensionFeature.featureDescription,
        id = SEARCH_RESULT_TYPE,
        name = "Build with Release",
        description = "Release, label or version attached to a build",
        order = SearchResultType.ORDER_PROPERTIES + 10,
    )

    override val indexerName: String = "Release property"

    override fun indexAll(processor: (SearchDocument) -> Unit) {
        propertyService.forEachEntityWithProperty<ReleasePropertyType, ReleaseProperty> { entityId, property ->
            if (entityId.type == ProjectEntityType.BUILD) {
                structureService.findBuildByID(ID.of(entityId.id))?.let { build ->
                    processor(build.asSearchDocument(property))
                }
            }
        }
    }

    /**
     * The release of a build has been set or changed.
     */
    fun onReleaseChanged(build: Build, property: ReleaseProperty) {
        searchDocumentService.index(build.asSearchDocument(property))
    }

    /**
     * The release of a build has been removed.
     */
    fun onReleaseDeleted(build: Build) {
        searchDocumentService.delete(SEARCH_RESULT_TYPE, build.id.toString())
    }

    override fun onEvent(event: Event) {
        if (event.eventType == EventFactory.UPDATE_BUILD) {
            val build = event.getEntity<Build>(ProjectEntityType.BUILD)
            propertyService.getPropertyValue(build, ReleasePropertyType::class.java)?.let { property ->
                onReleaseChanged(build, property)
            }
        }
    }

    private fun Build.asSearchDocument(property: ReleaseProperty) = SearchDocument(
        type = SEARCH_RESULT_TYPE,
        key = id.toString(),
        projectId = project.id(),
        entity = ProjectEntityID(this),
        title = property.name,
        identifiers = listOf(property.name),
        text = null,
        data = mapOf(
            SearchResult.SEARCH_RESULT_BUILD to searchDocumentData(),
            SearchResult.SEARCH_RESULT_BUILD_RELEASE to property.name,
        ).asJson(),
        updatedAt = signature.time,
    )

    companion object {
        const val SEARCH_RESULT_TYPE = "build-release"
    }

}
