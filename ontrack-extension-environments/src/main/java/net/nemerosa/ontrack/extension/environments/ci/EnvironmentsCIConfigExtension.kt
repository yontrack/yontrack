package net.nemerosa.ontrack.extension.environments.ci

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.common.mergeList
import net.nemerosa.ontrack.extension.config.extensions.CIConfigExtension
import net.nemerosa.ontrack.extension.environments.EnvironmentsExtensionFeature
import net.nemerosa.ontrack.extension.environments.casc.EnvironmentCasc
import net.nemerosa.ontrack.extension.environments.casc.EnvironmentsInjection
import net.nemerosa.ontrack.extension.environments.casc.SlotCasc
import net.nemerosa.ontrack.extension.environments.casc.SlotEnvironmentCasc
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.json.schema.toType
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import org.springframework.stereotype.Component

@Component
class EnvironmentsCIConfigExtension(
    feature: EnvironmentsExtensionFeature,
    private val environmentsInjection: EnvironmentsInjection,
) : AbstractExtension(feature),
    CIConfigExtension<EnvironmentsCIConfigExtension.EnvironmentsCIConfigExtensionConfig> {

    override val id: String = "environments"

    override val projectEntityTypes: Set<ProjectEntityType> = setOf(ProjectEntityType.PROJECT)

    override fun parseData(data: JsonNode) = data.parse<EnvironmentsCIConfigExtensionConfig>()

    override fun mergeConfig(
        defaults: EnvironmentsCIConfigExtensionConfig,
        custom: JsonNode
    ): EnvironmentsCIConfigExtensionConfig {
        val parsedCustom = custom.parse<EnvironmentsCIConfigExtensionConfig>()
        return EnvironmentsCIConfigExtensionConfig(
            environments = mergeList(
                target = defaults.environments,
                changes = parsedCustom.environments,
                idFn = EnvironmentCasc::name,
            ) { e, existing ->
                existing.copy(
                    tags = mergeList(
                        target = existing.tags,
                        changes = e.tags,
                        idFn = { it }
                    ) { t, _ -> t },
                )
            },
            slots = mergeList(
                target = defaults.slots,
                changes = parsedCustom.slots,
                idFn = SlotCasc::qualifier,
            ) { slotChanges, slotExisting ->
                slotExisting.copy(
                    environments = mergeList(
                        target = slotExisting.environments,
                        changes = slotChanges.environments,
                        idFn = SlotEnvironmentCasc::name
                    ) { e, _ -> e }
                )
            }
        )
    }

    override fun createJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        jsonTypeBuilder.toType(EnvironmentsCIConfigExtensionConfig::class)

    override fun configure(
        entity: ProjectEntity,
        data: EnvironmentsCIConfigExtensionConfig
    ) {
        // Injection of environments
        environmentsInjection.defineEnvironments(
            environments = data.environments,
            keepEnvironments = true,
        )

        // Injection of slots for this project
        environmentsInjection.defineSlots(slots = data.slots)
    }

    data class EnvironmentsCIConfigExtensionConfig(
        @APIDescription("List of environments")
        val environments: List<EnvironmentCasc>,
        @APIDescription("List of deployments slots (associated of a project and an environment)")
        val slots: List<SlotCasc> = emptyList(),
    )

}