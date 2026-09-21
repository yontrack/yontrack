package net.nemerosa.ontrack.repository

import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventType
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.ID.Companion.of
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.Signature
import net.nemerosa.ontrack.model.support.NameValue
import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.*
import javax.sql.DataSource

@Repository
class EventJdbcRepository(
    dataSource: DataSource,
) : AbstractJdbcRepository(dataSource), EventRepository {

    override fun post(event: Event): Event {
        val sql = StringBuilder("INSERT INTO EVENTS(EVENT_VALUES, EVENT_TIME, EVENT_USER, EVENT_TYPE, REF")
        val params = MapSqlParameterSource()
        params.addValue("eventValues", writeJson(event.values))
        params.addValue("eventTime", dateTimeForDB(event.signature!!.time))
        params.addValue("eventUser", event.signature!!.user.name)
        params.addValue("eventType", event.eventType.id)
        params.addValue("ref", if (event.ref != null) event.ref!!.name else null)
        for (type in event.entities.keys) {
            sql.append(", ").append(type.name)
        }
        for (type in event.extraEntities.keys) {
            sql.append(", X_").append(type.name)
        }
        sql.append(") VALUES (CAST(:eventValues as JSONB), :eventTime, :eventUser, :eventType, :ref")
        for ((type, entity) in event.entities) {
            val typeEntry = type.name.lowercase(Locale.getDefault())
            sql.append(", :").append(typeEntry)
            params.addValue(typeEntry, entity.id())
        }
        for ((type, entity) in event.extraEntities) {
            val typeEntry = "x_" + type.name.lowercase(Locale.getDefault())
            sql.append(", :").append(typeEntry)
            params.addValue(typeEntry, entity.id())
        }
        sql.append(")")
        val id = dbCreate(
            sql.toString(),
            params,
        )
        return event.withId(id)
    }

    override fun query(
        allowedProjects: List<Int>,
        offset: Int,
        count: Int,
        entityLoader: (type: ProjectEntityType, id: ID) -> ProjectEntity,
        eventTypeLoader: (type: String) -> EventType,
    ): List<Event> {
        return namedParameterJdbcTemplate!!.query(
            "SELECT * FROM EVENTS WHERE PROJECT IS NULL OR PROJECT IN (:projects)" +
                    " ORDER BY ID DESC" +
                    " LIMIT :count OFFSET :offset",
            params("projects", allowedProjects)
                .addValue("count", count)
                .addValue("offset", offset)
        ) { rs: ResultSet, _: Int -> toEvent(rs, entityLoader, eventTypeLoader) }
    }

    @Suppress("SqlResolve")
    override fun query(
        entityType: ProjectEntityType,
        entityId: ID,
        offset: Int,
        count: Int,
        entityLoader: (type: ProjectEntityType, id: ID) -> ProjectEntity,
        eventTypeLoader: (type: String) -> EventType,
    ): List<Event> {
        return namedParameterJdbcTemplate!!.query(
            """
                SELECT * 
                FROM EVENTS 
                WHERE ${entityType.name} = :entityId 
                OR X_${entityType.name} = :entityId 
                ORDER BY ID DESC 
                LIMIT :count OFFSET :offset
            """,
            params("entityId", entityId.get())
                .addValue("count", count)
                .addValue("offset", offset)
        ) { rs: ResultSet, _: Int -> toEvent(rs, entityLoader, eventTypeLoader) }
    }

    @Suppress("SqlResolve")
    override fun query(
        eventType: EventType,
        entityType: ProjectEntityType,
        entityId: ID,
        offset: Int,
        count: Int,
        entityLoader: (type: ProjectEntityType, id: ID) -> ProjectEntity,
        eventTypeLoader: (type: String) -> EventType,
    ): List<Event> {
        return namedParameterJdbcTemplate!!.query(
            """
                SELECT *
                FROM EVENTS 
                WHERE (${entityType.name} = :entityId OR X_${entityType.name} = :entityId)
                AND EVENT_TYPE = :eventType 
                ORDER BY ID DESC 
                LIMIT :count OFFSET :offset
            """,
            params("entityId", entityId.get())
                .addValue("eventType", eventType.id)
                .addValue("count", count)
                .addValue("offset", offset)
        ) { rs: ResultSet, _: Int -> toEvent(rs, entityLoader, eventTypeLoader) }
    }

    override fun getLastEventSignature(
        entityType: ProjectEntityType,
        entityId: ID,
        eventType: EventType,
    ): Signature? = getFirstItem(
        """
            SELECT * 
            FROM EVENTS 
            WHERE (${entityType.name} = :entityId OR X_${entityType.name} = :entityId)
            AND EVENT_TYPE = :eventType 
            ORDER BY ID DESC 
            LIMIT 1
        """,
        params("entityId", entityId.get()).addValue("eventType", eventType.id)
    ) { rs: ResultSet?, _: Int -> readSignature(rs, "event_time", "event_user") }

    override fun getLastEvent(
        entityType: ProjectEntityType,
        entityId: ID,
        eventType: EventType,
        entityLoader: (type: ProjectEntityType, id: ID) -> ProjectEntity,
        eventTypeLoader: (type: String) -> EventType,
    ): Event? = getFirstItem(
        """
            SELECT * 
            FROM EVENTS 
            WHERE (${entityType.name} = :entityId OR X_${entityType.name} = :entityId)
            AND EVENT_TYPE = :eventType 
            ORDER BY ID DESC 
            LIMIT 1
        """,
        params("entityId", entityId.get()).addValue("eventType", eventType.id)
    ) { rs: ResultSet, _: Int -> toEvent(rs, entityLoader, eventTypeLoader) }

    private fun toEvent(
        rs: ResultSet,
        entityLoader: (type: ProjectEntityType, id: ID) -> ProjectEntity,
        eventTypeLoader: (type: String) -> EventType,
    ): Event {
        // Event type name
        val eventTypeName = rs.getString("event_type")
        // Signature
        val signature = readSignature(rs, "event_time", "event_user")
        // Entities
        val entities: MutableMap<ProjectEntityType, ProjectEntity> = LinkedHashMap()
        for (type in ProjectEntityType.values()) {
            val entityId = rs.getInt(type.name)
            if (!rs.wasNull()) {
                val entity = entityLoader(type, of(entityId))
                entities[type] = entity
            }
        }
        // Extra entities
        val extraEntities: MutableMap<ProjectEntityType, ProjectEntity> = LinkedHashMap()
        for (type in ProjectEntityType.values()) {
            val entityId = rs.getInt("X_" + type.name)
            if (!rs.wasNull()) {
                val entity = entityLoader(type, of(entityId))
                extraEntities[type] = entity
            }
        }
        // Reference (if any)
        val refEntity = getEnum(
            ProjectEntityType::class.java, rs, "ref"
        )
        // Values
        val values = loadValues(rs)
        // OK
        return Event(
            id = rs.getInt("id"),
            eventType = eventTypeLoader(eventTypeName),
            signature = signature,
            entities = entities,
            extraEntities = extraEntities,
            ref = refEntity,
            values = values
        )
    }

    private fun loadValues(rs: ResultSet): Map<String, NameValue> {
        val map: MutableMap<String, NameValue> = LinkedHashMap()
        val node = readJson(rs, "event_values")
        val i = node.properties().iterator()
        while (i.hasNext()) {
            val (key, nameValue) = i.next()
            map[key] = NameValue(
                nameValue.path("name").asText(),
                nameValue.path("value").asText()
            )
        }
        return map
    }
}