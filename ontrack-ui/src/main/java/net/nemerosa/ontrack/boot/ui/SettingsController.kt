package net.nemerosa.ontrack.boot.ui

import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.json.ObjectMapperFactory
import net.nemerosa.ontrack.model.Ack
import net.nemerosa.ontrack.model.security.GlobalSettings
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.settings.SettingsManager
import net.nemerosa.ontrack.model.settings.SettingsManagerNotFoundException
import net.nemerosa.ontrack.model.settings.SettingsValidationException
import org.apache.commons.lang3.Strings
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

/**
 * Global settings management.
 */
@RestController
@RequestMapping("/rest/settings")
class SettingsController(
        private val securityService: SecurityService,
        private val settingsManagers: Collection<SettingsManager<*>>
) {

    private val objectMapper = ObjectMapperFactory.create()

    /**
     * Gets settings
     */
    @GetMapping("/{type:.*}")
    fun <T> getSettings(@PathVariable type: String): T {
        securityService.checkGlobalFunction(GlobalSettings::class.java)
        @Suppress("UNCHECKED_CAST") val settings: T? = settingsManagers
                .filter { candidate ->
                    Strings.CS.equals(
                            type,
                            getSettingsManagerName(candidate)
                    )
                }
                .map { it.settings }
                .firstOrNull() as T?
        return settings ?: throw SettingsManagerNotFoundException(type)
    }


    /**
     * Security
     */
    @PutMapping("/{type:.*}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun <T> updateSettings(@PathVariable type: String, @RequestBody settingsNode: JsonNode?): Ack {
        securityService.checkGlobalFunction(GlobalSettings::class.java)
        // Gets the settings manager by type
        @Suppress("UNCHECKED_CAST")
        val settingsManager: SettingsManager<T> = (settingsManagers
                .firstOrNull { candidate ->
                    Strings.CS.equals(
                            type,
                            getSettingsManagerName(candidate)
                    )
                }
                ?: throw  SettingsManagerNotFoundException(type))
                as SettingsManager<T>
        // Parsing
        val settings: T
        try {
            settings = objectMapper.treeToValue(settingsNode!!, settingsManager.settingsClass)
        } catch (e: JacksonException) {
            throw SettingsValidationException(e)
        }

        // Saves the settings
        settingsManager.saveSettings(settings)
        // OK
        return Ack.OK
    }

    private fun getSettingsManagerName(settingsManager: SettingsManager<*>): String {
        return settingsManager.id
    }

}
