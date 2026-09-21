package net.nemerosa.ontrack.graphql

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.graphql.payloads.PayloadInterface
import net.nemerosa.ontrack.graphql.payloads.PayloadUserError
import net.nemerosa.ontrack.graphql.payloads.toPayloadErrors
import net.nemerosa.ontrack.json.parseInto
import net.nemerosa.ontrack.model.security.GlobalSettings
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.settings.SettingsManager
import net.nemerosa.ontrack.model.settings.SettingsManagerIdNotFoundException
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.stereotype.Controller

@Controller
class SettingsGraphQLController(
    private val securityService: SecurityService,
    private val settingsManagers: Collection<SettingsManager<*>>,
) {

    @MutationMapping
    fun saveSettings(@Argument input: SaveSettingsInput): SaveSettingsPayload {
        securityService.checkGlobalFunction(GlobalSettings::class.java)
        // Gets the settings manager by ID
        val settingsManager = settingsManagers.find { it.id == input.id }
            ?: throw SettingsManagerIdNotFoundException(input.id)
        // Saving
        return saveSettings(settingsManager, input.values)
    }

    private fun <T : Any> saveSettings(settingsManager: SettingsManager<T>, values: JsonNode): SaveSettingsPayload {
        // Parsing
        val typedValues: T = try {
            values.parseInto(settingsManager.settingsClass.kotlin)
        } catch (any: Exception) {
            return SaveSettingsPayload(any.toPayloadErrors())
        }
        // Saving
        settingsManager.saveSettings(typedValues)
        // OK
        return SaveSettingsPayload()
    }

}

data class SaveSettingsInput(
    val id: String,
    val values: JsonNode,
)

class SaveSettingsPayload(
    errors: List<PayloadUserError>? = null,
) : PayloadInterface(errors)

