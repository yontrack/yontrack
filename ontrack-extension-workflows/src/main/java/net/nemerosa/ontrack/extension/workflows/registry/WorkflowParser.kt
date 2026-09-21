package net.nemerosa.ontrack.extension.workflows.registry

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.workflows.definition.Workflow
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.yaml.Yaml

object WorkflowParser {

    fun parseJsonWorkflow(json: JsonNode): Workflow =
        json.parse()

    fun parseYamlWorkflow(workflowYaml: String): Workflow =
        Yaml().read(workflowYaml)
            .firstOrNull()
            ?.let { parseJsonWorkflow(it) }
            ?: throw WorkflowParsingException()

}