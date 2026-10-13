package net.nemerosa.ontrack.extension.queue.search

import net.nemerosa.ontrack.extension.queue.QueueAckMode
import net.nemerosa.ontrack.extension.queue.QueueMetadata
import net.nemerosa.ontrack.extension.queue.QueueProcessor
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.SearchDocumentRenameReindexation
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

/**
 * Re-indexes the search documents of a renamed project or branch, on any replica (#2083).
 *
 * The search is part of the core, which has no queue: this extension, which brings the queues, runs
 * its re-indexations.
 *
 * Acked at the end of the processing, so that a message in hand when its replica stops is
 * redelivered to another one: the re-indexation computes from the current state, and running it
 * again is harmless. The re-indexations of an entity run one at a time, under a lock of the
 * [reindexation][SearchDocumentRenameReindexation.reindexRenamed].
 */
@Component
class SearchRenameQueueProcessor(
    private val searchDocumentRenameReindexation: SearchDocumentRenameReindexation,
) : QueueProcessor<SearchRenameQueuePayload> {

    override val id: String = "search-rename"

    override val payloadType: KClass<SearchRenameQueuePayload> = SearchRenameQueuePayload::class

    override val ackMode: QueueAckMode = QueueAckMode.END

    override fun isCancelled(payload: SearchRenameQueuePayload): String? = null

    override fun getRoutingIdentifier(payload: SearchRenameQueuePayload): String =
        "${payload.type.name}#${payload.id}"

    override fun process(payload: SearchRenameQueuePayload, queueMetadata: QueueMetadata?) {
        searchDocumentRenameReindexation.reindexRenamed(payload.type, ID.of(payload.id))
    }
}
