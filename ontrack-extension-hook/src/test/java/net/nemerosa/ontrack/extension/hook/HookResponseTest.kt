package net.nemerosa.ontrack.extension.hook

import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.json.parseAsJson
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class HookResponseTest {

    @Test
    fun `A hook response is written without the removed info field`() {
        val json = HookResponse(type = HookResponseType.PROCESSED, infoLink = null).asJson()
        assertFalse(json.has("info"))
    }

    @Test
    fun `A hook response recorded before V6 with an info field is still read`() {
        val json = """
            {
                "type": "IGNORED",
                "info": "Hook `test` is disabled.",
                "infoLink": null
            }
        """.parseAsJson()
        val response = json.parse<HookResponse>()
        assertEquals(HookResponse(type = HookResponseType.IGNORED, infoLink = null), response)
        assertNull(response.infoLink)
    }

}
