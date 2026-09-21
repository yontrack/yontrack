package net.nemerosa.ontrack.extension.indicators.ui

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.indicators.computing.ConfigurableIndicatorService
import net.nemerosa.ontrack.extension.indicators.computing.ConfigurableIndicatorState
import net.nemerosa.ontrack.json.getTextField
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

/**
 * Getting UI information about the configurable indicators
 */
@RestController
@RequestMapping("/extension/indicators/configurables")
class ConfigurableIndicatorController(
    private val configurableIndicatorService: ConfigurableIndicatorService,
) {

    /**
     * Edits a configurable indicator
     *
     * @param id ID of the configurable indicator
     */
    @PutMapping("{id}/edit")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun save(@PathVariable id: String, @RequestBody data: JsonNode) {
        // Gets the basic data
        val enabled = data.path(ConfigurableIndicatorState::enabled.name).asBoolean()
        val link = data.getTextField(ConfigurableIndicatorState::link.name)?.takeIf { it.isNotBlank() }
        // Extracts the attributes into a map
        val values = mutableMapOf<String, String?>()
        val configurableIndicatorType = configurableIndicatorService.getConfigurableIndicatorType(id)
        configurableIndicatorType.attributes.forEach { attribute ->
            val attributeValue = data.path(attribute.key).asText().takeIf { it.isNotBlank() }
            values[attribute.key] = attributeValue
        }
        // Saves the configurable indicator
        configurableIndicatorService.saveConfigurableIndicator(
            configurableIndicatorType,
            ConfigurableIndicatorState(
                enabled,
                link,
                ConfigurableIndicatorState.toAttributeList(configurableIndicatorType, values)
            )
        )
    }

}