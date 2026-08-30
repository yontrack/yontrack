package net.nemerosa.ontrack.extension.notifications.recording

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.json.format
import net.nemerosa.ontrack.model.pagination.PaginatedList
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.support.StorageService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration

@Service
@Transactional
class DefaultNotificationRecordingService(
    private val storageService: StorageService,
    private val securityService: SecurityService,
) : NotificationRecordingService {

    override fun clearAll() {
        securityService.checkGlobalFunction(NotificationRecordingAccess::class.java)
        storageService.deleteWithFilter(STORE)
    }

    override fun clear(retentionSeconds: Long) {
        securityService.checkGlobalFunction(NotificationRecordingAccess::class.java)
        val ref = Time.now() - Duration.ofSeconds(retentionSeconds)
        storageService.deleteWithFilter(
            store = STORE,
            query = "data::jsonb->>'timestamp' <= :timestamp",
            queryVariables = mapOf(
                "timestamp" to Time.store(ref)
            )
        )
    }

    override fun findRecordById(id: String): NotificationRecord? {
        securityService.checkGlobalFunction(NotificationRecordingAccess::class.java)

        return storageService.find(
            STORE,
            id,
            NotificationRecord::class
        )
    }

    override fun filter(filter: NotificationRecordFilter): PaginatedList<NotificationRecord> {
        securityService.checkGlobalFunction(NotificationRecordingAccess::class.java)

        val queries = mutableListOf<String>()
        val queryVariables = mutableMapOf<String, Any>()

        if (!filter.channel.isNullOrBlank()) {
            queries += "data::jsonb->>'channel' = :channel"
            queryVariables["channel"] = filter.channel
        }

        if (filter.resultType != null) {
            queries += "data::jsonb->'result'->>'type' = :resultType"
            queryVariables["resultType"] = filter.resultType.name
        }

        if (!filter.sourceId.isNullOrBlank()) {
            queries += "data::jsonb->'source'->>'id' = :sourceId"
            queryVariables["sourceId"] = filter.sourceId
            if (filter.sourceData != null) {
                queries += "data::jsonb->'source'->'data' @> CAST(:sourceData AS JSONB)"
                queryVariables["sourceData"] = filter.sourceData.format()
            }
        }

        if (filter.eventEntityId != null) {
            // This predicate is served by the partial expression indexes of
            // V80__1653_notification_record_entity_index.sql. Rewording it silently makes those
            // indexes unusable and turns this lookup back into a full scan of STORAGE --
            // NotificationRecordEntityIndexIT guards the match.
            queries += "(data::jsonb->'event'->'entities'->'${filter.eventEntityId.type.name}'->>'id')::int = :eventEntityId"
            queryVariables["eventEntityId"] = filter.eventEntityId.id
        }

        val query = queries.joinToString(" AND ") { "( $it )" }

        val total = storageService.count(
            store = STORE,
            query = query,
            queryVariables = queryVariables,
        )

        val records = storageService.filter(
            store = STORE,
            type = NotificationRecord::class,
            query = query,
            queryVariables = queryVariables,
            offset = filter.offset,
            size = filter.size,
            orderQuery = "ORDER BY data::jsonb->>'timestamp' DESC"
        )

        return PaginatedList.create(records, filter.offset, filter.size, total)
    }

    override fun record(record: NotificationRecord): String {
        storageService.store(
            STORE,
            record.id,
            record,
        )
        return record.id
    }

    companion object {
        internal val STORE = NotificationRecord::class.java.name
    }

}