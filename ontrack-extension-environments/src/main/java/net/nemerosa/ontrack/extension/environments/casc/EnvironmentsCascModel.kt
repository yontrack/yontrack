package net.nemerosa.ontrack.extension.environments.casc

import com.fasterxml.jackson.annotation.JsonIgnore
import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.extension.environments.Slot
import net.nemerosa.ontrack.extension.environments.SlotPipelineStatus
import net.nemerosa.ontrack.extension.environments.schema.json.SlotAdmissionRuleDynamicJsonSchemaProvider
import net.nemerosa.ontrack.extension.workflows.definition.WorkflowNode
import net.nemerosa.ontrack.model.annotations.APIIgnore
import net.nemerosa.ontrack.model.json.schema.DynamicJsonSchema

@APIDescription("Definition of environments as code")
data class EnvironmentsCascModel(
    @APIDescription("If true (default), this list defines the exhaustive list of environments. Any existing environment which is not in this list will be deleted.")
    val keepEnvironments: Boolean = true,
    @APIDescription("List of environments")
    val environments: List<EnvironmentCasc>,
    @APIDescription("List of deployments slots (associated of a project and an environment)")
    val slots: List<SlotCasc> = emptyList(),
)

@APIDescription("Definition of an environment")
data class EnvironmentCasc(
    @APIDescription("Name of the environment. Must be unique.")
    val name: String,
    @APIDescription("Description of the environment")
    val description: String = "",
    @APIDescription("Numerical order for the environment. This allows environments to be sorted in the different views.")
    val order: Int,
    @APIDescription("List of tags for this environment")
    val tags: List<String> = emptyList(),
    @APIDescription("Image for this environment")
    val image: String? = null,
)

@APIDescription("Definition for a slot: the association of a project to several environments")
data class SlotCasc(
    @APIDescription("Name of the project")
    val project: String,
    @APIDescription("Optional qualifier for this slot")
    val qualifier: String = Slot.DEFAULT_QUALIFIER,
    @APIDescription("Prefix for the description for the slots (can be overridden by description at environment level)")
    val description: String = "",
    @APIDescription("Configuration of environments for this slot")
    val environments: List<SlotEnvironmentCasc>,
)

@APIDescription("Configuration of an environment for a slot")
data class SlotEnvironmentCasc(
    @APIDescription("Name of the environment. It must exist.")
    val name: String,
    @APIDescription("Description for the slot")
    val description: String = "",
    @APIDescription("List of admission rules for this slot")
    val admissionRules: List<SlotEnvironmentAdmissionRuleCasc> = emptyList(),
    @APIDescription("List of workflows for this slot")
    val workflows: List<SlotWorkflowCasc> = emptyList(),
)

@APIDescription("Definition of an admission rule for a slot")
@DynamicJsonSchema(
    discriminatorProperty = "ruleId",
    configurationProperty = "ruleConfig",
    provider = SlotAdmissionRuleDynamicJsonSchemaProvider::class,
)
data class SlotEnvironmentAdmissionRuleCasc(
    @APIDescription("Optional name for this rule")
    val name: String? = null,
    @APIDescription("Description for this rule")
    val description: String = "",
    @APIDescription("ID of the rule to use")
    val ruleId: String,
    @APIDescription("Configuration for the rule")
    val ruleConfig: JsonNode,
) {
    @get:JsonIgnore
    @APIIgnore
    val actualName: String = name ?: ruleId
}

@APIDescription("Definition of a workflow for a slot")
data class SlotWorkflowCasc(
    @APIDescription("Trigger used for this workflow")
    val trigger: SlotPipelineStatus,
    @APIDescription("Name of the workflow")
    val name: String,
    @APIDescription("List of workflow nodes")
    val nodes: List<WorkflowNode>,
)
