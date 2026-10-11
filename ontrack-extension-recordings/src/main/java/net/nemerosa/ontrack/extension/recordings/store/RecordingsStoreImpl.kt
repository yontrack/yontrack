package net.nemerosa.ontrack.extension.recordings.store

import net.nemerosa.ontrack.json.parseInto
import net.nemerosa.ontrack.model.pagination.PaginatedList
import net.nemerosa.ontrack.model.support.StorageService
import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class RecordingsStoreImpl(
        private val storageService: StorageService,
) : RecordingsStore {

    private fun store(store: String) =
            "${StoredRecording::class.java.name}.$store"

    override fun save(store: String, recording: StoredRecording) {
        storageService.store(store(store), recording.id, recording)
    }

    override fun findById(store: String, id: String): StoredRecording? =
            storageService.find(store(store), id, StoredRecording::class)

    override fun findByIdForUpdate(store: String, id: String): StoredRecording? =
            storageService.findJsonForUpdate(store(store), id)?.parseInto(StoredRecording::class)

    override fun findByFilter(
            store: String,
            queries: List<String>,
            queryVariables: Map<String, Any?>,
            offset: Int,
            size: Int,
    ): PaginatedList<StoredRecording> {

        val query = queries.joinToString(" AND ") { "( $it )" }

        return storageService.paginatedFilter(
                store = store(store),
                type = StoredRecording::class,
                offset = offset,
                size = size,
                query = query,
                queryVariables = queryVariables,
                orderQuery = "ORDER BY data::jsonb->>'startTime' DESC",
        )
    }

    override fun countByFilter(store: String, queries: List<String>, queryVariables: Map<String, Any?>): Int {
        val query = queries.joinToString(" AND ") { "( $it )" }
        return storageService.count(
                store = store(store),
                query = query,
                queryVariables = queryVariables,
        )
    }

    override fun removeAllBefore(store: String, retentionDate: LocalDateTime, nonRunningOnly: Boolean) {
        if (nonRunningOnly) {
            storageService.deleteWithFilter(
                    store = store(store),
                    query = "data::jsonb->>'endTime' IS NOT NULL AND data::jsonb->>'startTime' <= :beforeTime",
                    queryVariables = mapOf(
                            "beforeTime" to AbstractJdbcRepository.dateTimeForDB(retentionDate)
                    )
            )
        } else {
            storageService.deleteWithFilter(
                    store = store(store),
                    query = "data::jsonb->>'startTime' <= :beforeTime",
                    queryVariables = mapOf(
                            "beforeTime" to AbstractJdbcRepository.dateTimeForDB(retentionDate)
                    )
            )
        }
    }

    override fun removeAll(store: String) {
        storageService.deleteWithFilter(store(store))
    }
}