package net.nemerosa.ontrack.kdsl.spec.extension.environments

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.kdsl.connector.Connector
import net.nemerosa.ontrack.kdsl.connector.graphql.convert
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.CreatePipelineMutation
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.SaveSlotAdmissionRuleConfigMutation
import net.nemerosa.ontrack.kdsl.connector.graphqlConnector
import net.nemerosa.ontrack.kdsl.spec.Build
import net.nemerosa.ontrack.kdsl.spec.Project
import net.nemerosa.ontrack.kdsl.spec.Resource

class Slot(
    connector: Connector,
    val id: String,
    val environment: Environment,
    val project: Project,
    val qualifier: String = "",
    val description: String = "",
) : Resource(connector) {

    fun createPipeline(build: Build): SlotPipeline {
        val pipeline = graphqlConnector.mutate(
            CreatePipelineMutation(
                id,
                build.id.toInt(),
            )
        ) { it?.startSlotPipeline?.payloadUserErrors?.convert() }
            ?.startSlotPipeline?.pipeline
            ?: error("Cannot get the create pipeline")
        return SlotPipeline(
            connector = connector,
            id = pipeline.id,
            number = pipeline.number ?: 1,
            slot = this,
            build = build,
            status = pipeline.status,
        )
    }

    /**
     * Configures an admission rule on this slot.
     *
     * @param ruleId ID of the rule, as its `SlotAdmissionRule` declares it - `promotion`,
     * `environment`, `branchPattern`...
     * @param ruleConfig Configuration of the rule, whose shape is the rule's own
     * @param name Name of the configured rule, unique within the slot. Letters, digits and dashes
     * only, starting with a letter.
     */
    fun addAdmissionRule(
        ruleId: String,
        ruleConfig: JsonNode,
        name: String = ruleId,
        description: String = "",
    ) {
        graphqlConnector.mutate(
            SaveSlotAdmissionRuleConfigMutation(
                id,
                name,
                description,
                ruleId,
                ruleConfig,
            )
        ) { it?.saveSlotAdmissionRuleConfig?.payloadUserErrors?.convert() }
    }

}
