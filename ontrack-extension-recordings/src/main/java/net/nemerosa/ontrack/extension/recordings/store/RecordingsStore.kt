package net.nemerosa.ontrack.extension.recordings.store

import net.nemerosa.ontrack.model.pagination.PaginatedList
import java.time.LocalDateTime

interface RecordingsStore {

    fun save(store: String, recording: StoredRecording)

    fun findById(store: String, id: String): StoredRecording?

    /**
     * Gets a recording and locks it until the end of the current transaction.
     */
    fun findByIdForUpdate(store: String, id: String): StoredRecording?

    fun removeAllBefore(store: String, retentionDate: LocalDateTime, nonRunningOnly: Boolean)

    fun findByFilter(
            store: String,
            queries: List<String>,
            queryVariables: Map<String, Any?>,
            offset: Int,
            size: Int,
    ): PaginatedList<StoredRecording>

    fun countByFilter(
            store: String,
            queries: List<String>,
            queryVariables: Map<String, Any?>,
    ): Int

    fun removeAll(store: String)

}