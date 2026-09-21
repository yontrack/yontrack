package net.nemerosa.ontrack.kdsl.connector

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.json.parseAsJson
import java.nio.charset.Charset

interface ConnectorResponseBody {

    fun asText(charset: Charset = Charsets.UTF_8): String

    fun asTextOrNull(charset: Charset = Charsets.UTF_8): String?

    fun asJson(): JsonNode = asText().parseAsJson()

    fun asJsonOrNull(): JsonNode? = asTextOrNull()?.parseAsJson()

}