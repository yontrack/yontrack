package net.nemerosa.ontrack.service.search

import net.nemerosa.ontrack.extension.support.CoreExtensionFeature
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.events.EventListener
import net.nemerosa.ontrack.model.security.GlobalSettings
import net.nemerosa.ontrack.model.security.ProjectEdit
import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.json.asJson
import org.springframework.stereotype.Component
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch

/**
 * Base for the indexers of the tests, whose documents are provided by the tests themselves.
 */
abstract class AbstractTestSearchDocumentIndexer(
    id: String,
    order: Int,
) : SearchDocumentIndexer {

    override val searchResultType = SearchResultType(
        feature = CoreExtensionFeature.INSTANCE.featureDescription,
        id = id,
        name = id,
        description = "Test type $id",
        order = order,
    )

    /**
     * Documents returned by [indexAll]
     */
    val source = CopyOnWriteArrayList<SearchDocument>()

    /**
     * When set, [indexAll] waits for it before returning its documents
     */
    @Volatile
    var blocker: CountDownLatch? = null

    override fun indexAll(processor: (SearchDocument) -> Unit) {
        blocker?.await()
        source.forEach(processor)
    }

    fun document(
        key: String,
        title: String,
        project: Project? = null,
        identifiers: List<String> = emptyList(),
        text: String? = null,
        updatedAt: java.time.LocalDateTime? = null,
    ) = SearchDocument(
        type = searchResultType.id,
        key = key,
        projectId = project?.id(),
        entity = project?.let { ProjectEntityID(it) },
        title = title,
        identifiers = identifiers,
        text = text,
        data = mapOf("key" to key).asJson(),
        updatedAt = updatedAt,
    )
}

@Component
class TestAlphaSearchDocumentIndexer : AbstractTestSearchDocumentIndexer(TYPE, 1000) {
    companion object {
        const val TYPE = "test-alpha"
    }
}

/**
 * Test type whose documents may belong to no project, visible then to the users granted
 * [GlobalSettings].
 */
@Component
class TestBetaSearchDocumentIndexer : AbstractTestSearchDocumentIndexer(TYPE, 1001) {
    companion object {
        const val TYPE = "test-beta"
    }

    override val globalFunction = GlobalSettings::class.java
}

/**
 * Test type whose documents are visible only in the projects where [ProjectEdit] is granted, on top
 * of the project view.
 */
@Component
class TestGammaSearchDocumentIndexer : AbstractTestSearchDocumentIndexer(TYPE, 1002) {
    companion object {
        const val TYPE = "test-gamma"
    }

    override val projectFunction = ProjectEdit::class.java
}

/**
 * Test type whose documents are not matched by similarity.
 */
@Component
class TestDeltaSearchDocumentIndexer : AbstractTestSearchDocumentIndexer(TYPE, 1003) {
    companion object {
        const val TYPE = "test-delta"
    }

    override val fuzzyMatching = false
}

/**
 * Writes a document which cannot be stored (NUL character in its title) when a project is created
 * with [DESCRIPTION] as its description, to check that a failed search write never fails the
 * creation of the entity.
 */
@Component
class FailingSearchDocumentListener(
    private val searchDocumentService: SearchDocumentService,
) : EventListener {

    companion object {
        const val DESCRIPTION = "search-document-failure"
    }

    override fun onEvent(event: Event) {
        if (event.eventType == EventFactory.NEW_PROJECT) {
            val project = event.getEntity<Project>(ProjectEntityType.PROJECT)
            if (project.description == DESCRIPTION) {
                searchDocumentService.index(
                    SearchDocument(
                        type = TestAlphaSearchDocumentIndexer.TYPE,
                        key = "failing-${project.id}",
                        projectId = project.id(),
                        entity = null,
                        title = "Not storable \u0000",
                        identifiers = emptyList(),
                        text = null,
                        data = mapOf("key" to "failing").asJson(),
                    )
                )
            }
        }
    }
}
