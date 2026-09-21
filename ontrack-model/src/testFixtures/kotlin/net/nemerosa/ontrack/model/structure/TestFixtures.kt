package net.nemerosa.ontrack.model.structure

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.structure.Signature.Companion.of
import java.time.LocalDateTime

object TestFixtures {

    @JvmField
    val SIGNATURE_OBJECT: JsonNode = mapOf(
        "time" to "2016-12-27T21:10:00Z",
        "user" to mapOf(
            "name" to "test"
        )
    ).asJson()

    @JvmField
    val SIGNATURE: Signature = of(
        LocalDateTime.of(2016, 12, 27, 21, 10),
        "test"
    )
}
