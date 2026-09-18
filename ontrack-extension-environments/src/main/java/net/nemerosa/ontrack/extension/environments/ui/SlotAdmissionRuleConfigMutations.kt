package net.nemerosa.ontrack.extension.environments.ui

import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.environments.SlotAdmissionRuleConfig
import net.nemerosa.ontrack.extension.environments.rules.SlotAdmissionRuleRegistry
import net.nemerosa.ontrack.extension.environments.service.SlotService
import net.nemerosa.ontrack.graphql.schema.Mutation
import net.nemerosa.ontrack.graphql.support.TypedMutationProvider
import org.springframework.stereotype.Component

@Component
class SlotAdmissionRuleConfigMutations(
    private val slotService: SlotService,
    private val slotAdmissionRuleRegistry: SlotAdmissionRuleRegistry,
) : TypedMutationProvider() {
    override val mutations: List<Mutation> = listOf(
        simpleMutation(
            name = "saveSlotAdmissionRuleConfig",
            description = "Saves or creates a configured admission rule for a slot",
            input = SaveSlotAdmissionRuleConfigInput::class,
            outputName = "admissionRuleConfig",
            outputDescription = "Saved admission rule config",
            outputType = SlotAdmissionRuleConfig::class,
        ) { input ->
            if (!input.id.isNullOrBlank()) {
                if (!input.slotId.isNullOrBlank()) {
                    throw SlotAdmissionRuleConfigInputException.SlotNotNeeded()
                } else {
                    /*
                     * Editing an existing rule (#1793). Until now a rule could only be deleted and
                     * added back, which changes its id - and the id is what a deployment's stored
                     * rule state is keyed on, so "delete and re-add" quietly detaches the answers
                     * already given to it.
                     *
                     * The slot is taken from the stored rule rather than from the input: a rule
                     * belongs to one slot for its whole life, and the input carries no slot here by
                     * the branch above.
                     */
                    val existing = slotService.findAdmissionRuleConfigById(input.id)
                        ?: throw SlotAdmissionRuleConfigInputException.NotFound(input.id)
                    // Getting the rule and checking the configuration, exactly as on creation
                    val rule = slotAdmissionRuleRegistry.getRule(input.ruleId)
                    rule.checkConfig(input.ruleConfig)
                    val config = SlotAdmissionRuleConfig(
                        id = existing.id,
                        slot = existing.slot,
                        name = input.name?.takeIf { it.isNotBlank() } ?: rule.id,
                        description = input.description,
                        ruleId = input.ruleId,
                        ruleConfig = input.ruleConfig,
                    )
                    slotService.saveAdmissionRuleConfig(config)
                    config
                }
            } else if (!input.slotId.isNullOrBlank()) {
                val slot = slotService.getSlotById(input.slotId)
                // Getting the rule
                val rule = slotAdmissionRuleRegistry.getRule(input.ruleId)
                // Checking the configuration
                rule.checkConfig(input.ruleConfig)
                // Creation of a new rule
                val config = SlotAdmissionRuleConfig(
                    slot = slot,
                    name = input.name?.takeIf { it.isNotBlank() } ?: rule.id,
                    description = input.description,
                    ruleId = input.ruleId,
                    ruleConfig = input.ruleConfig,
                )
                slotService.addAdmissionRuleConfig(
                    config = config
                )
                config
            } else {
                error("Either the ID or the slot ID must be provided.")
            }
        },
        unitMutation(
            name = "deleteSlotAdmissionRuleConfig",
            description = "Deletes an admission rule for a slot",
            input = DeleteSlotAdmissionRuleConfigInput::class,
        ) { input ->
            val config = slotService.findAdmissionRuleConfigById(input.id)
            if (config != null) {
                slotService.deleteAdmissionRuleConfig(config)
            }
        },
    )
}

data class SaveSlotAdmissionRuleConfigInput(
    val id: String?,
    val slotId: String?,
    val name: String?,
    val description: String,
    val ruleId: String,
    val ruleConfig: JsonNode,
)

data class DeleteSlotAdmissionRuleConfigInput(
    val id: String,
)