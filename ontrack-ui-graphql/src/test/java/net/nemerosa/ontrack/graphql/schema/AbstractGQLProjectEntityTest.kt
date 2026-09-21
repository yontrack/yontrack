package net.nemerosa.ontrack.graphql.schema

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.graphql.schema.GQLTypeCreation.Companion.getCreationFromSignature
import net.nemerosa.ontrack.model.structure.Signature.Companion.of
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AbstractGQLProjectEntityTest {

    @Test
    fun signature_to_map() {
        val now = Time.now()
        val (user, time) = getCreationFromSignature(of(now, "test"))
        assertEquals("test", user)
        assertEquals(Time.store(now), time)
    }

    @Test
    fun null_signature_to_map() {
        val (user, time) = getCreationFromSignature(null)
        assertNull(user)
        assertNull(time)
    }
}