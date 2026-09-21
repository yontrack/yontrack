package net.nemerosa.ontrack.extension.github.ingestion.processing.events

import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PingPayloadTest {

    @Test
    fun `Parsing of a ping event`() {
        assertEquals(
            PingPayload("Anything added dilutes everything else."),
            mapOf(
                "zen" to "Anything added dilutes everything else.",
                "hook_id" to 336699000,
            ).asJson().parse()
        )
    }

}