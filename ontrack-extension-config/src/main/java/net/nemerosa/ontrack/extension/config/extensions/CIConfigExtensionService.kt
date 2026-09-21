package net.nemerosa.ontrack.extension.config.extensions

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.syncForward
import net.nemerosa.ontrack.extension.api.ExtensionManager
import net.nemerosa.ontrack.extension.config.model.ExtensionConfiguration
import net.nemerosa.ontrack.json.asJson
import org.springframework.stereotype.Component

@Component
class CIConfigExtensionService(
    private val extensionManager: ExtensionManager,
) {

    private val extensions: Map<String, CIConfigExtension<*>> by lazy {
        extensionManager.getExtensions(CIConfigExtension::class.java).associateBy { it.id }
    }

    fun merge(
        defaults: List<ExtensionConfiguration>,
        customs: List<ExtensionConfiguration>,
    ): List<ExtensionConfiguration> {
        val result = defaults.toMutableList()
        syncForward(
            from = customs,
            to = result
        ) {
            equality { a, b -> a.id == b.id }
            onCreation { e -> result += e }
            onModification { custom, existing ->
                result.removeIf { it.id == existing.id }
                result += existing.merge(custom)
            }
            onDeletion { e -> /* Not needed */ }
        }
        return result.toList()
    }

    private fun ExtensionConfiguration.merge(other: ExtensionConfiguration): ExtensionConfiguration {
        val extension = extensions[id] ?: throw CIConfigExtensionNotFoundException(id)
        return extension.merge(data, other.data)
    }

    private fun <T> CIConfigExtension<T>.merge(defaults: JsonNode, other: JsonNode): ExtensionConfiguration {
        val parsedDefaults = parseData(defaults)
        val data = mergeConfig(parsedDefaults, other)
        return ExtensionConfiguration(
            id = id,
            data = data.asJson(),
        )
    }

}