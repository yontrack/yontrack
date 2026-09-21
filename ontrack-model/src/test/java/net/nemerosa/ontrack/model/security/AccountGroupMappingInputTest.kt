package net.nemerosa.ontrack.model.security

import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.structure.ID
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AccountGroupMappingInputTest {

    @Test
    fun jsonRead() {
        val json = mapOf("name" to "M11242253", "group" to 9).asJson()
        val input = json.parse<AccountGroupMappingInput>()
        assertEquals("M11242253", input.name)
        assertEquals(ID.of(9), input.group)
    }

}
