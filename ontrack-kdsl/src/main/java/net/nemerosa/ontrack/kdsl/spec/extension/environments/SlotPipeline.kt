package net.nemerosa.ontrack.kdsl.spec.extension.environments

import com.apollographql.apollo.api.Optional
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.kdsl.connector.Connector
import net.nemerosa.ontrack.kdsl.connector.graphql.convert
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.FailPipelineMutation
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.FinishDeploymentPipelineMutation
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.PipelineRequiredInputsQuery
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.StartDeployingPipelineMutation
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.UpdatePipelineDataMutation
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.SlotPipelineDataInputValue
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.SlotPipelineStatus
import net.nemerosa.ontrack.kdsl.connector.graphqlConnector
import net.nemerosa.ontrack.kdsl.spec.Build
import net.nemerosa.ontrack.kdsl.spec.Resource

class SlotPipeline(
    connector: Connector,
    val id: String,
    val number: Int,
    val slot: Slot,
    val build: Build,
    val status: SlotPipelineStatus,
) : Resource(connector) {

    fun startDeploying(): SlotPipeline {
        val status = graphqlConnector.mutate(
            StartDeployingPipelineMutation(
                id
            )
        ) { it?.startSlotPipelineDeployment?.payloadUserErrors?.convert() }
            ?.startSlotPipelineDeployment?.deploymentStatus?.ok
            ?: error("Cannot get the deployment status")
        if (!status) error("Deployment could not be started")
        return this
    }

    fun finishDeployment(): SlotPipeline {
        graphqlConnector.mutate(
            FinishDeploymentPipelineMutation(
                id
            )
        ) { it?.finishSlotPipelineDeployment?.payloadUserErrors?.convert() }
        return this
    }

    /**
     * Marks this running pipeline as failed.
     *
     * @param message Optional message recorded in the pipeline's history
     * @throws IllegalStateException If the pipeline could not be marked as failed, typically because
     * it is not running
     */
    fun fail(message: String? = null): SlotPipeline {
        val status = graphqlConnector.mutate(
            FailPipelineMutation(
                id,
                Optional.presentIfNotNull(message),
            )
        ) { it?.failSlotPipeline?.payloadUserErrors?.convert() }
            ?.failSlotPipeline?.failStatus
            ?: error("Cannot get the fail status")
        if (status.ok != true) error("Pipeline could not be marked as failed: ${status.message}")
        return this
    }

    fun manualApproval(message: String): SlotPipeline {
        // Getting the inputs of this pipeline
        val configId = graphqlConnector.query(
            PipelineRequiredInputsQuery(id)
        )?.slotPipelineById?.requiredInputs?.find {
            it.config.ruleId == "manual"
        }?.config?.id ?: error("Could not find any manual rule")
        // Sending the message
        graphqlConnector.mutate(
            UpdatePipelineDataMutation(
                id,
                listOf(
                    SlotPipelineDataInputValue(
                        configId = configId,
                        data = mapOf(
                            "approval" to true,
                            "message" to message
                        ).asJson()
                    )
                )
            )
        ) { it?.updatePipelineData?.payloadUserErrors?.convert() }
        // OK
        return this
    }

}
