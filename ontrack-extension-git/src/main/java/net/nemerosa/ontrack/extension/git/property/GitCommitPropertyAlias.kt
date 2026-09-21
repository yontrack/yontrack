package net.nemerosa.ontrack.extension.git.property

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.json.schema.JsonStringType
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.structure.PropertyAlias
import org.springframework.stereotype.Component

@Component
class GitCommitPropertyAlias : PropertyAlias {
    override val alias: String = "gitCommit"
    override val type: String = GitCommitPropertyType::class.java.name

    override fun parseConfig(data: JsonNode): JsonNode =
        GitCommitProperty(
            commit = data.asText()
        ).asJson()

    override fun createJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType = JsonStringType("Git commit SHA")
}