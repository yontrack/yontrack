package net.nemerosa.ontrack.extension.jenkins.indicator

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.casc.context.AbstractCascContext
import net.nemerosa.ontrack.extension.casc.context.settings.SubSettingsContext
import net.nemerosa.ontrack.json.JsonParseException
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.json.schema.JsonArrayType
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.json.schema.toType
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import net.nemerosa.ontrack.model.settings.SettingsManagerService
import org.springframework.stereotype.Component

@Component
class JenkinsPipelineLibraryIndicatorSettingsCasc(
    private val cachedSettingsService: CachedSettingsService,
    private val settingsManagerService: SettingsManagerService,
) : AbstractCascContext(), SubSettingsContext {

    override val field: String = "jenkins-pipeline-library-indicator"

    override fun jsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType {
        return JsonArrayType(
            description = "List of library versions requirements",
            items = jsonTypeBuilder.toType(JenkinsPipelineLibraryIndicatorLibrarySettings::class)
        )
    }

    override fun run(node: JsonNode, paths: List<String>) {
        val items = node.mapIndexed { index, child ->
            try {
                child.parse<JenkinsPipelineLibraryIndicatorLibrarySettings>()
            } catch (ex: JsonParseException) {
                throw IllegalStateException(
                    "Cannot parse into ${JenkinsPipelineLibraryIndicatorLibrarySettings::class.qualifiedName}: ${
                        path(
                            paths + index.toString()
                        )
                    }",
                    ex
                )
            }
        }
        settingsManagerService.saveSettings(
            JenkinsPipelineLibraryIndicatorSettings(
                libraryVersions = items
            )
        )
    }

    override fun render(): JsonNode {
        val settings = cachedSettingsService.getCachedSettings(JenkinsPipelineLibraryIndicatorSettings::class.java)
        return settings.libraryVersions.asJson()
    }
}