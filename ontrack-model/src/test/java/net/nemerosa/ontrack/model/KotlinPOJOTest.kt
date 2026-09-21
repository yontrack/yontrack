package net.nemerosa.ontrack.model

import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.test.TestUtils.assertJsonRead
import net.nemerosa.ontrack.test.TestUtils.assertJsonWrite
import org.junit.jupiter.api.Test

class KotlinPOJOTest {

    @Test
    fun json_to_kotlin() {
        assertJsonRead<KotlinPOJO>(
            KotlinPOJO("Name", 10),
            mapOf(
                "name" to "Name",
                "value" to 10,
                "doubleValue" to 20,
            ).asJson(),
            KotlinPOJO::class.java
        )
    }

    @Test
    fun kotlin_to_json() {
        assertJsonWrite(
            mapOf(
                "name" to "Name",
                "value" to 10,
                "doubleValue" to 20,
            ).asJson(),
            KotlinPOJO("Name", 10)
        )
    }

}