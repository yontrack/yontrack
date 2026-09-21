package net.nemerosa.ontrack.extension.notifications.recording

import net.nemerosa.ontrack.extension.notifications.channels.NotificationResultType
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.asJsonString
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.json.parseAsJson
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals

/**
 * A notification record as 5.x stores it, read and written back through the JSON utilities. The
 * `timestamp` string is compared as text in SQL (`DefaultNotificationRecordingService`), so its
 * format must survive the Jackson 3 migration (#1843) unchanged. Written on Jackson 2, and meant to
 * pass unchanged on Jackson 3.
 */
class NotificationRecordCharacterizationTest {

    private val stored: String =
        NotificationRecordCharacterizationTest::class.java
            .getResource("/characterization/notification-record-5x.json")!!
            .readText()

    @Test
    fun `Reading a record stored by 5_x`() {
        val record: NotificationRecord = stored.parseAsJson().parse()
        assertEquals("0b6c3f1e-7a0e-4c1d-9a61-7f0c6d1e2b3a", record.id)
        assertEquals("entity-subscription", record.source?.id)
        assertEquals(12, record.source?.data?.path("entityId")?.asInt())
        assertEquals(LocalDateTime.of(2025, 11, 4, 9, 12, 34, 567_891_000), record.timestamp)
        assertEquals("slack", record.channel)
        assertEquals("#builds", record.channelConfig.path("channel").asText())
        assertEquals("new_promotion_run", record.event.path("eventType").asText())
        assertEquals(NotificationResultType.OK, record.result.type)
        assertEquals(null, record.result.message)
        assertEquals(true, record.result.output?.path("delivered")?.asBoolean())
    }

    @Test
    fun `Writing a record back gives the 5_x format`() {
        val record: NotificationRecord = stored.parseAsJson().parse()
        assertEquals(
            stored.parseAsJson().asJsonString(),
            record.asJson().asJsonString(),
        )
        assertEquals(
            """{"id":"0b6c3f1e-7a0e-4c1d-9a61-7f0c6d1e2b3a","source":{"id":"entity-subscription","data":{"entityType":"BRANCH","entityId":12,"subscription":"on-promotion"}},"timestamp":"2025-11-04T09:12:34.567891Z","channel":"slack","channelConfig":{"channel":"#builds","type":"SUCCESS"},"event":{"eventType":"new_promotion_run","signature":{"time":"2025-11-04T09:12:30Z","user":{"name":"admin"}},"entities":{"BUILD":42},"values":{}},"result":{"type":"OK","message":null,"output":{"ts":"1730711554.000100","count":1,"delivered":true}}}""",
            record.asJson().asJsonString(),
        )
    }

}
