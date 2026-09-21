package net.nemerosa.ontrack.extension.casc.context.settings

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.casc.context.AbstractCascContext
import net.nemerosa.ontrack.json.JsonParseException
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parseInto
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.json.schema.toType
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import net.nemerosa.ontrack.model.settings.SettingsManagerService
import kotlin.reflect.KClass

abstract class AbstractSubSettingsContext<T : Any>(
    override val field: String,
    private val settingsClass: KClass<T>,
    private val settingsManagerService: SettingsManagerService,
    private val cachedSettingsService: CachedSettingsService,
) : AbstractCascContext(), SubSettingsContext {

    override fun jsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        jsonTypeBuilder.toType(settingsClass)

    override fun run(node: JsonNode, paths: List<String>) {
        // Parsing to the settings node
        val settings = try {
            parseSettings(node)
        } catch (ex: JsonParseException) {
            throw IllegalStateException(
                "Cannot parse settings $settingsClass at ${path(paths + field)}",
                ex
            )
        }
        // Sets the settings
        settingsManagerService.saveSettings(settings)
    }

    private fun parseSettings(node: JsonNode): T = adjustNodeBeforeParsing(node).parseInto(settingsClass)

    protected open fun adjustNodeBeforeParsing(node: JsonNode): JsonNode = node

    override fun render(): JsonNode = obfuscate(cachedSettingsService.getCachedSettings(settingsClass.java)).asJson()

    open fun obfuscate(settings: T): T = settings
}