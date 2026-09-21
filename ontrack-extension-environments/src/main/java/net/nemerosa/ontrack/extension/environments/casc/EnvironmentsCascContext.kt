package net.nemerosa.ontrack.extension.environments.casc

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.casc.context.AbstractCascContext
import net.nemerosa.ontrack.extension.casc.context.SubConfigContext
import net.nemerosa.ontrack.extension.environments.service.EnvironmentService
import net.nemerosa.ontrack.extension.environments.service.SlotService
import net.nemerosa.ontrack.extension.environments.workflows.SlotWorkflowService
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.json.schema.toType
import org.springframework.stereotype.Component
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@Component
class EnvironmentsCascContext(
    private val environmentsInjection: EnvironmentsInjection,
    private val environmentService: EnvironmentService,
    private val slotService: SlotService,
    private val slotWorkflowService: SlotWorkflowService,
) : AbstractCascContext(), SubConfigContext {

    override val field: String = "environments"

    override fun jsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        jsonTypeBuilder.toType(EnvironmentsCascModel::class)

    override fun run(node: JsonNode, paths: List<String>) {
        val model: EnvironmentsCascModel = node.parse()
        run(model)
    }

    private fun run(model: EnvironmentsCascModel) {
        environmentsInjection.defineEnvironments(
            environments = model.environments,
            keepEnvironments = model.keepEnvironments,
        )
        environmentsInjection.defineSlots(slots = model.slots)
    }

    @OptIn(ExperimentalEncodingApi::class)
    override fun render(): JsonNode {
        val environments = environmentService.findAll()
        return EnvironmentsCascModel(
            keepEnvironments = true, // Always true when rendering
            environments = environments.map {
                EnvironmentCasc(
                    name = it.name,
                    description = it.description ?: "",
                    order = it.order,
                    tags = it.tags,
                    image = environmentService.getEnvironmentImage(it.id)
                        .takeIf { doc -> !doc.isEmpty }
                        ?.content
                        ?.let { bytes -> Base64.encode(bytes) }
                        ?.let { base64 -> "base64:$base64" },
                )
            },
            slots = environments.flatMap { environment ->
                slotService.findSlotsByEnvironment(environment)
            }.groupBy { it.project.name to it.qualifier }.map { (slotKey, slots) ->
                val (projectName, qualifier) = slotKey
                SlotCasc(
                    project = projectName,
                    qualifier = qualifier,
                    description = slots.first().description ?: "",
                    environments = slots.map { slot ->
                        SlotEnvironmentCasc(
                            name = slot.environment.name,
                            admissionRules = slotService.getAdmissionRuleConfigs(slot).map { rule ->
                                SlotEnvironmentAdmissionRuleCasc(
                                    name = rule.name,
                                    description = rule.description ?: "",
                                    ruleId = rule.ruleId,
                                    ruleConfig = rule.ruleConfig,
                                )
                            },
                            workflows = slotWorkflowService.getSlotWorkflowsBySlot(slot).map { sw ->
                                SlotWorkflowCasc(
                                    trigger = sw.trigger,
                                    name = sw.workflow.name,
                                    nodes = sw.workflow.nodes,
                                )
                            }
                        )
                    }
                )
            },
        ).asJson()
    }

}