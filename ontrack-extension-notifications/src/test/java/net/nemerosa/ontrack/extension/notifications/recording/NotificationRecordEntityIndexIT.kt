package net.nemerosa.ontrack.extension.notifications.recording

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.notifications.AbstractNotificationTestSupport
import net.nemerosa.ontrack.extension.notifications.channels.NotificationResult
import net.nemerosa.ontrack.extension.notifications.mock.MockNotificationChannelConfig
import net.nemerosa.ontrack.extension.notifications.mock.MockNotificationChannelOutput
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.*
import kotlin.test.assertTrue

/**
 * The `eventEntityId` filter of [NotificationRecordFilter] is on the hot path of the
 * `workflowInstances` field, and `STORAGE` has no index of its own beyond its primary key. The
 * partial expression indexes added by `V80__1653_notification_record_entity_index.sql` exist to
 * serve exactly that predicate.
 *
 * An index whose expression does not match the query text is dead weight that nothing would notice,
 * so this asserts the planner can actually use it: the queries below run with sequential scans
 * disabled, which makes the plan reveal whether the index is *applicable* without depending on how
 * much data happens to be in the table.
 */
internal class NotificationRecordEntityIndexIT : AbstractNotificationTestSupport() {

    @Autowired
    private lateinit var notificationRecordingService: NotificationRecordingService

    /**
     * The planner only prefers the intended index if there is something to index. On an empty store
     * the `STORE = ...` predicate estimates zero rows and `STORAGE_PK` is just as good, so the plan
     * would be ambiguous through no fault of the migration.
     */
    @BeforeEach
    fun records() {
        asAdmin {
            val project = project {}
            val event = eventFactory.newProject(project)
            repeat(50) {
                notificationRecordingService.record(
                    NotificationRecord(
                        id = UUID.randomUUID().toString(),
                        source = mockSource(),
                        timestamp = Time.now(),
                        channel = "workflow",
                        channelConfig = MockNotificationChannelConfig("#target").asJson(),
                        event = event.asJson(),
                        result = NotificationResult.ok(
                            output = MockNotificationChannelOutput(text = "text", data = null)
                        ).toNotificationRecordResult(),
                    )
                )
            }
            namedParameterJdbcTemplate.jdbcTemplate.execute("ANALYZE STORAGE")
        }
    }

    /**
     * Mirrors the predicate built by [DefaultNotificationRecordingService.filter] for
     * `eventEntityId`. If that predicate is ever reworded, this test fails and the migration must
     * follow.
     */
    private fun eventEntityPredicate(type: ProjectEntityType) =
        "(data::jsonb->'event'->'entities'->'${type.name}'->>'id')::int = :eventEntityId"

    private fun explain(type: ProjectEntityType): String {
        val sql = """
            EXPLAIN SELECT NAME, DATA FROM STORAGE
            WHERE STORE = :store
            AND ( data::jsonb->>'channel' = :channel )
            AND ( ${eventEntityPredicate(type)} )
            ORDER BY data::jsonb->>'timestamp' DESC
            OFFSET 0 LIMIT 100
        """.trimIndent()
        val jdbc = namedParameterJdbcTemplate
        jdbc.jdbcTemplate.execute("SET LOCAL enable_seqscan = off")
        return jdbc.query(
            sql,
            mapOf(
                "store" to DefaultNotificationRecordingService.STORE,
                "channel" to "workflow",
                "eventEntityId" to 1,
            )
        ) { rs, _ -> rs.getString(1) }.joinToString("\n")
    }

    @Test
    fun `The promotion run index serves the record lookup`() {
        val plan = explain(ProjectEntityType.PROMOTION_RUN)
        assertTrue(
            plan.contains("storage_notification_event_promotion_run", ignoreCase = true),
            "Expected the promotion run index to be usable, but the plan was:\n$plan",
        )
    }

    @Test
    fun `Every project entity type has a usable index`() {
        ProjectEntityType.entries.forEach { type ->
            val plan = explain(type)
            val expectedIndex = "storage_notification_event_${type.name.lowercase()}"
            assertTrue(
                plan.contains(expectedIndex, ignoreCase = true),
                "Expected index $expectedIndex to be usable for $type, but the plan was:\n$plan",
            )
        }
    }
}
