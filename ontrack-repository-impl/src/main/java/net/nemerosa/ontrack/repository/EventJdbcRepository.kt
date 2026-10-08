package net.nemerosa.ontrack.repository

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventFilter
import net.nemerosa.ontrack.model.events.EventType
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.ID.Companion.of
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.Signature
import net.nemerosa.ontrack.model.support.NameValue
import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository
import org.springframework.jdbc.core.RowCallbackHandler
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.LocalDateTime
import java.util.*
import javax.sql.DataSource

@Repository
class EventJdbcRepository(
    dataSource: DataSource,
) : AbstractJdbcRepository(dataSource), EventRepository {

    override fun post(event: Event): Event {
        val sql = StringBuilder("INSERT INTO EVENTS(EVENT_VALUES, EVENT_TIME, EVENT_USER, ACTOR, EVENT_TYPE, REF")
        val params = MapSqlParameterSource()
        params.addValue("eventValues", writeJson(event.values))
        params.addValue("eventTime", dateTimeForDB(event.signature!!.time))
        params.addValue("eventUser", event.signature!!.user.name)
        params.addValue("actor", writeSignatureActor(event.signature))
        params.addValue("eventType", event.eventType.id)
        params.addValue("ref", if (event.ref != null) event.ref!!.name else null)
        for (type in event.entities.keys) {
            sql.append(", ").append(type.name)
        }
        for (type in event.extraEntities.keys) {
            sql.append(", X_").append(type.name)
        }
        sql.append(") VALUES (CAST(:eventValues as JSONB), :eventTime, :eventUser, CAST(:actor AS JSONB), :eventType, :ref")
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

    @Suppress("SqlResolve")
    override fun findAgentEvents(
        entityType: ProjectEntityType,
        entityId: ID,
        offset: Int,
        size: Int,
        entityLoader: (type: ProjectEntityType, id: ID) -> ProjectEntity,
        eventTypeLoader: (type: String) -> EventType,
    ): List<Event> =
        namedParameterJdbcTemplate!!.query(
            """
                SELECT *
                FROM EVENTS
                WHERE (${entityType.name} = :entityId OR X_${entityType.name} = :entityId)
                AND ACTOR IS NOT NULL
                ORDER BY ID DESC
                LIMIT :size OFFSET :offset
            """,
            params("entityId", entityId.get())
                .addValue("size", size)
                .addValue("offset", offset)
        ) { rs: ResultSet, _: Int -> toEvent(rs, entityLoader, eventTypeLoader) }

    override fun findAgentEvents(
        filter: EventFilter,
        projects: Collection<Int>?,
        offset: Int,
        size: Int,
        entityLoader: (type: ProjectEntityType, id: ID) -> ProjectEntity,
        eventTypeLoader: (type: String) -> EventType,
    ): List<Event> {
        if (projects != null && projects.isEmpty()) {
            return emptyList()
        }
        val (where, params) = agentCriteria(filter, projects)
        return namedParameterJdbcTemplate!!.query(
            "SELECT * FROM EVENTS $where ORDER BY ID DESC LIMIT :size OFFSET :offset",
            params
                .addValue("size", size)
                .addValue("offset", offset)
        ) { rs: ResultSet, _: Int -> toEvent(rs, entityLoader, eventTypeLoader) }
    }

    override fun countAgentEventsByType(
        filter: EventFilter,
        projects: Collection<Int>,
        visibleProjects: Collection<Int>,
    ): Map<String, Int> {
        if (projects.isEmpty()) {
            return emptyMap()
        }
        val (where, params) = agentCriteria(filter, projects, visibleProjects)
        val counts = mutableMapOf<String, Int>()
        namedParameterJdbcTemplate!!.query(
            "SELECT EVENT_TYPE, COUNT(*) AS EVENT_COUNT FROM EVENTS $where GROUP BY EVENT_TYPE",
            params,
            RowCallbackHandler { rs ->
                counts[rs.getString("EVENT_TYPE")] = rs.getInt("EVENT_COUNT")
            },
        )
        return counts
    }

    /**
     * `WHERE` clause and parameters of a filter on the events of the agents, restricted to some
     * projects.
     *
     * @param projects IDs of the projects the events may concern as their project - `null` for no
     * restriction at all
     * @param visibleProjects IDs of the projects the events may concern as their extra project - `null`
     * for no restriction at all
     */
    private fun agentCriteria(
        filter: EventFilter,
        projects: Collection<Int>?,
        visibleProjects: Collection<Int>? = projects,
    ): Pair<String, MapSqlParameterSource> {
        val (where, params) = filterCriteria(filter)
        val criteria = mutableListOf<String>()
        if (where.isNotEmpty()) {
            criteria += where.removePrefix("WHERE ")
        }
        // Only the agents
        criteria += "ACTOR IS NOT NULL"
        // Only the given projects, on the project
        if (projects != null) {
            criteria += "PROJECT IN (:projects)"
            params.addValue("projects", projects)
        }
        // Only the visible projects, on the extra project
        if (visibleProjects != null) {
            criteria += "(X_PROJECT IS NULL OR X_PROJECT IN (:visibleProjects))"
            params.addValue("visibleProjects", visibleProjects)
        }
        return "WHERE ${criteria.joinToString(" AND ")}" to params
    }

    override fun findEvents(
        filter: EventFilter,
        offset: Int,
        size: Int,
        entityLoader: (type: ProjectEntityType, id: ID) -> ProjectEntity,
        eventTypeLoader: (type: String) -> EventType,
    ): List<Event> {
        val (where, params) = filterCriteria(filter)
        return namedParameterJdbcTemplate!!.query(
            "SELECT * FROM EVENTS $where ORDER BY ID DESC LIMIT :size OFFSET :offset",
            params
                .addValue("size", size)
                .addValue("offset", offset)
        ) { rs: ResultSet, _: Int -> toEvent(rs, entityLoader, eventTypeLoader) }
    }

    override fun findEventsBefore(
        filter: EventFilter,
        beforeId: Int,
        size: Int,
        entityLoader: (type: ProjectEntityType, id: ID) -> ProjectEntity,
        eventTypeLoader: (type: String) -> EventType,
    ): List<Event> {
        val (where, params) = filterCriteria(filter, beforeId)
        return namedParameterJdbcTemplate!!.query(
            "SELECT * FROM EVENTS $where ORDER BY ID DESC LIMIT :size",
            params.addValue("size", size)
        ) { rs: ResultSet, _: Int -> toEvent(rs, entityLoader, eventTypeLoader) }
    }

    override fun hasEventsBeyond(filter: EventFilter, beforeId: Int?, offset: Int): Boolean {
        val (where, params) = filterCriteria(filter, beforeId)
        return namedParameterJdbcTemplate!!.queryForList(
            "SELECT ID FROM EVENTS $where ORDER BY ID DESC LIMIT 1 OFFSET :offset",
            params.addValue("offset", offset),
            Int::class.java,
        ).isNotEmpty()
    }

    override fun deleteEventsBefore(time: LocalDateTime, size: Int): Int =
        namedParameterJdbcTemplate!!.update(
            "DELETE FROM EVENTS WHERE ID IN (SELECT ID FROM EVENTS WHERE EVENT_TIME < :time LIMIT :size)",
            params("time", Time.store(time)).addValue("size", size)
        )

    override fun getLastEventId(): Int? =
        jdbcTemplate.queryForObject("SELECT MAX(ID) FROM EVENTS", Int::class.java)

    /**
     * `WHERE` clause and parameters of a filter on the events.
     *
     * @param beforeId Only the events whose ID is lower than this one, if any
     */
    private fun filterCriteria(filter: EventFilter, beforeId: Int? = null): Pair<String, MapSqlParameterSource> {
        val criteria = mutableListOf<String>()
        val params = MapSqlParameterSource()
        beforeId?.let {
            criteria += "ID < :beforeId"
            params.addValue("beforeId", it)
        }
        filter.from?.let {
            criteria += "EVENT_TIME >= :from"
            params.addValue("from", Time.store(it))
        }
        filter.to?.let {
            criteria += "EVENT_TIME <= :to"
            params.addValue("to", Time.store(it))
        }
        filter.user?.takeIf { it.isNotEmpty() }?.let {
            criteria += """LOWER(EVENT_USER) LIKE (LOWER(:user) || '%') ESCAPE '\'"""
            params.addValue("user", escapeLike(it))
        }
        filter.eventTypes?.takeIf { it.isNotEmpty() }?.let {
            criteria += "EVENT_TYPE IN (:eventTypes)"
            params.addValue("eventTypes", it)
        }
        filter.project?.takeIf { it.isNotBlank() }?.let {
            criteria += "(PROJECT IN (SELECT ID FROM PROJECTS WHERE NAME = :project) OR X_PROJECT IN (SELECT ID FROM PROJECTS WHERE NAME = :project))"
            params.addValue("project", it)
        }
        when (val actor = filter.actorCriterion()) {
            null -> {}
            EventFilter.ACTOR_AGENT -> criteria += "ACTOR IS NOT NULL"
            EventFilter.ACTOR_HUMAN -> criteria += "ACTOR IS NULL"
            else -> {
                // Uses the EVENTS_IX_ACTOR_AGENT partial index
                criteria += "ACTOR IS NOT NULL AND ACTOR->>'agent' = :actorAgent"
                params.addValue("actorAgent", actor)
            }
        }
        val where = if (criteria.isEmpty()) "" else criteria.joinToString(" AND ", prefix = "WHERE ")
        return where to params
    }

    /**
     * Escapes the `LIKE` wildcards and the escape character itself.
     */
    private fun escapeLike(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")

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
    ) { rs: ResultSet?, _: Int -> readSignature(rs, "event_time", "event_user", "actor") }

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
        val signature = readSignature(rs, "event_time", "event_user", "actor")
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