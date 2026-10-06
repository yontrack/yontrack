package net.nemerosa.ontrack.extension.github.ingestion.extensions.validation

import net.nemerosa.ontrack.extension.github.ingestion.extensions.support.AbstractIngestionBuildEventProcessor
import net.nemerosa.ontrack.extension.github.ingestion.processing.IngestionEventPreprocessingCheck
import net.nemerosa.ontrack.extension.github.ingestion.processing.IngestionEventProcessingResultDetails
import net.nemerosa.ontrack.extension.github.ingestion.support.IngestionModelAccessService
import net.nemerosa.ontrack.model.exceptions.ValidationRunDataTypeNotFoundException
import net.nemerosa.ontrack.model.structure.*
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

@Component
class IngestionValidateDateEventProcessor(
    private val ingestionModelAccessService: IngestionModelAccessService,
    private val structureService: StructureService,
    private val runInfoService: RunInfoService,
    private val validationRunStatusService: ValidationRunStatusService,
    private val validationRunService: ValidationRunService,
    private val validationDataTypeService: ValidationDataTypeService,
) : AbstractIngestionBuildEventProcessor<GitHubIngestionValidateDataPayload>(
    ingestionModelAccessService
) {

    override fun preProcessingCheck(payload: GitHubIngestionValidateDataPayload): IngestionEventPreprocessingCheck =
        IngestionEventPreprocessingCheck.TO_BE_PROCESSED

    override fun process(build: Build, input: GitHubIngestionValidateDataPayload): IngestionEventProcessingResultDetails {
        // Setting up the validation stamp
        val vs = ingestionModelAccessService.setupValidationStamp(
            branch = build.branch,
            vsName = input.validation,
            vsDescription = null,
        )
        // Parsing the data
        val validationDataType = validationDataTypeService.getValidationDataType<Any, Any>(input.validationData.type)
            ?: throw ValidationRunDataTypeNotFoundException(input.validationData.type)
        val parsedData = validationDataType.fromForm(input.validationData.data)
        // Validation status
        val validationRunStatusId = input.validationStatus?.run {
            validationRunStatusService.getValidationRunStatus(this)
        }
        // Gets any existing validation run
        val run = structureService.getValidationRunsForBuildAndValidationStamp(
            buildId = build.id,
            validationStampId = vs.id,
            offset = 0,
            count = 1,
        ).firstOrNull()
        // An existing run is never deleted, so that it keeps its evidence, properties and history
        if (run != null) {
            // Validating the data as the creation of a run does, before changing anything
            val validated = validationDataTypeService.validateData(
                parsedData?.let { validationDataType.data(it) },
                vs.dataType,
                validationRunStatusId,
            )
            // Same status as the one the run was created with: the data is updated in place, if it
            // changed. The statuses a user added since, like a triage, are kept.
            if (validated.runStatusID == run.validationRunStatuses.last().statusID) {
                if (!sameData(run.data, validated.runData)) {
                    validationRunService.updateValidationRunData(run, validated.runData)
                }
                return IngestionEventProcessingResultDetails.processed()
            }
        }
        // No run, or a different status: a new run, next to any existing one
        val validationRun = structureService.newValidationRun(
            build = build,
            validationRunRequest = ValidationRunRequest(
                validationStampName = input.validation,
                validationRunStatusId = validationRunStatusId,
                dataTypeId = input.validationData.type,
                data = parsedData,
            )
        )
        // Run info of the previous run
        val existingRunInfo = run?.let { runInfoService.getRunInfo(it) }
        if (existingRunInfo != null) {
            runInfoService.setRunInfo(
                validationRun,
                existingRunInfo.toRunInfoInput()
            )
        }
        // OK
        return IngestionEventProcessingResultDetails.processed()
    }

    /**
     * Whether the data of an existing run is the same as the new one, compared as JSON.
     */
    private fun sameData(existing: ValidationRunData<*>?, new: ValidationRunData<Any>?): Boolean {
        if (existing == null || new == null) {
            return existing == null && new == null
        }
        if (existing.descriptor.id != new.descriptor.id) {
            return false
        }
        val type = validationDataTypeService.getValidationDataType<Any, Any>(new.descriptor.id) ?: return false
        return existing.data?.let { type.toJson(it) } == type.toJson(new.data)
    }

    override val payloadType: KClass<GitHubIngestionValidateDataPayload> =
        GitHubIngestionValidateDataPayload::class

    override val event: String = EVENT

    companion object {
        const val EVENT = "x-ontrack-validate-date"
    }
}