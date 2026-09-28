package net.nemerosa.ontrack.kdsl.spec.extension.environments

import com.apollographql.apollo.api.Optional
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.kdsl.connector.Connector
import net.nemerosa.ontrack.kdsl.connector.graphql.convert
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.CancelPipelineMutation
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
import java.time.LocalDateTime

class SlotPipeline(
    connector: Connector,
    val id: String,
    val number: Int,
    val slot: Slot,
    val build: Build,
    val status: SlotPipelineStatus,
    /**
     * Start of the pipeline
     */
    val start: LocalDateTime? = null,
    /**
     * End of the pipeline, when finished
     */
    val end: LocalDateTime? = null,
) : Resource(connector) {

    /**
     * Starts the deployment of this candidate pipeline.
     *
     * @param dateTime Time of the action, to backdate it. Defaults to now. Not in the future, not
     * before the creation of the build, not before the previous change of the pipeline.
     */
    fun startDeploying(dateTime: LocalDateTime? = null): SlotPipeline {
        val status = graphqlConnector.mutate(
            StartDeployingPipelineMutation(
                id,
                Optional.presentIfNotNull(dateTime),
            )
        ) { it?.startSlotPipelineDeployment?.payloadUserErrors?.convert() }
            ?.startSlotPipelineDeployment?.deploymentStatus?.ok
            ?: error("Cannot get the deployment status")
        if (!status) error("Deployment could not be started")
        return this
    }

    /**
     * Finishes the deployment of this running pipeline.
     *
     * @param dateTime Time of the action, to backdate it. Defaults to now. Not in the future, not
     * before the creation of the build, not before the previous change of the pipeline.
     */
    fun finishDeployment(dateTime: LocalDateTime? = null): SlotPipeline {
        graphqlConnector.mutate(
            FinishDeploymentPipelineMutation(
                id,
                Optional.presentIfNotNull(dateTime),
            )
        ) { it?.finishSlotPipelineDeployment?.payloadUserErrors?.convert() }
        return this
    }

    /**
     * Cancels this pipeline.
     *
     * @param reason Reason recorded in the pipeline's history
     * @param dateTime Time of the action, to backdate it. Defaults to now. Not in the future, not
     * before the creation of the build, not before the previous change of the pipeline.
     */
    fun cancel(reason: String, dateTime: LocalDateTime? = null): SlotPipeline {
        graphqlConnector.mutate(
            CancelPipelineMutation(
                id,
                reason,
                Optional.presentIfNotNull(dateTime),
            )
        ) { it?.cancelSlotPipeline?.payloadUserErrors?.convert() }
        return this
    }

    /**
     * Marks this running pipeline as failed.
     *
     * @param message Optional message recorded in the pipeline's history
     * @param dateTime Time of the action, to backdate it. Defaults to now. Not in the future, not
     * before the creation of the build, not before the previous change of the pipeline.
     * @throws IllegalStateException If the pipeline could not be marked as failed, typically because
     * it is not running
     */
    fun fail(message: String? = null, dateTime: LocalDateTime? = null): SlotPipeline {
        val status = graphqlConnector.mutate(
            FailPipelineMutation(
                id,
                Optional.presentIfNotNull(message),
                Optional.presentIfNotNull(dateTime),
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
