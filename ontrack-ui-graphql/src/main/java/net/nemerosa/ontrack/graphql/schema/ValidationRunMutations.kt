package net.nemerosa.ontrack.graphql.schema

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.graphql.support.TypeRef
import net.nemerosa.ontrack.graphql.support.TypedMutationProvider
import net.nemerosa.ontrack.json.JsonParseException
import net.nemerosa.ontrack.model.exceptions.BuildNotFoundException
import net.nemerosa.ontrack.model.exceptions.ProjectNotFoundException
import net.nemerosa.ontrack.model.exceptions.ValidationRunDataJSONInputException
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.*
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import kotlin.jvm.optionals.getOrNull

@Component
class ValidationRunMutations(
    private val structureService: StructureService,
    private val securityService: SecurityService,
    private val validationRunStatusService: ValidationRunStatusService,
    private val validationDataTypeService: ValidationDataTypeService,
    private val runInfoService: RunInfoService,
    private val buildDisplayNameService: BuildDisplayNameService,
) : TypedMutationProvider() {

    override val mutations: List<Mutation> = listOf(
        simpleMutation(
            name = CREATE_VALIDATION_RUN_FOR_BUILD_BY_NAME,
            description = "Creating a validation run for a build identified by its name",
            input = CreateValidationRunInput::class,
            outputName = "validationRun",
            outputDescription = "Created validation run",
            outputType = ValidationRun::class
        ) { input ->
            val build = (structureService.findBuildByName(input.project, input.branch, input.build)
                .getOrNull()
                ?: throw BuildNotFoundException(input.project, input.branch, input.build))
            validate(build, input)
        },
        simpleMutation(
            name = CREATE_VALIDATION_RUN_FOR_BUILD_BY_ID,
            description = "Creating a validation run for a build identified by its ID",
            input = CreateValidationRunByIdInput::class,
            outputName = "validationRun",
            outputDescription = "Created validation run",
            outputType = ValidationRun::class
        ) { input ->
            val build = structureService.getBuild(ID.of(input.buildId))
            validate(build, input)
        },
        simpleMutation(
            name = "createValidationRunByRelease",
            description = "Creating a validation run for a build identified by its release label",
            input = CreateValidationRunByReleaseInput::class,
            outputName = "validationRun",
            outputDescription = "Created validation run",
            outputType = ValidationRun::class
        ) { input ->
            val project = structureService.findProjectByName(input.project)
                .getOrNull()
                ?: throw ProjectNotFoundException(input.project)
            val build = buildDisplayNameService.findBuildByDisplayName(
                project = project,
                name = input.buildRelease,
                onlyDisplayName = true
            ) ?: throw BuildReleaseNotFoundException(project, input.buildRelease)
            validate(build, input)
        },
        simpleMutation(
            name = "changeValidationRunStatus",
            description = "Change the status of a validation run",
            input = ChangeValidationRunStatusInput::class,
            outputName = "validationRun",
            outputDescription = "Updated validation run",
            outputType = ValidationRun::class
        ) { input ->
            val validationRun = structureService.getValidationRun(ID.of(input.validationRunId))
            val runStatus = ValidationRunStatus(
                ID.NONE,
                securityService.currentSignature,
                validationRunStatusService.getValidationRunStatus(input.validationRunStatusId),
                input.description
            )
            structureService.newValidationRunStatus(validationRun, runStatus)
        }
    )

    private fun validate(build: Build, input: ValidationRunInput): ValidationRun {
        val run = structureService.newValidationRun(
            build = build,
            validationRunRequest = ValidationRunRequest(
                validationStampName = input.validationStamp,
                validationRunStatusId = input.validationRunStatus?.let(validationRunStatusService::getValidationRunStatus),
                dataTypeId = input.dataTypeId,
                data = parseValidationRunData(build, input.validationStamp, input.dataTypeId, input.data),
                description = input.description,
                // Backdating a run is what lets a seeded or replayed history read as the history it
                // stands for; without it every run is stamped with the moment it was pushed.
                signature = input.dateTime?.let { securityService.currentSignature.withTime(it) },
            )
        )
        // Run info
        val runInfo = input.runInfo
        if (runInfo != null) {
            runInfoService.setRunInfo(
                entity = run,
                input = runInfo,
            )
        }
        // OK
        return run
    }

    fun parseValidationRunData(
        build: Build,
        validationStampName: String,
        dataTypeId: String?,
        data: JsonNode?,
    ): Any? = data?.run {
        // Gets the validation stamp
        val validationStamp: ValidationStamp = structureService.getOrCreateValidationStamp(
            build.branch,
            validationStampName
        )
        // Gets the data type ID if any
        // First, the data type in the request, and if not specified, the type of the validation stamp
        val typeId: String? = dataTypeId
            ?: validationStamp.dataType?.descriptor?.id
        // If no type, ignore the data
        return typeId
            ?.run {
                // Gets the actual type
                validationDataTypeService.getValidationDataType<Any, Any>(this)
            }?.run {
                // Parses data from the form
                try {
                    fromForm(data)
                } catch (ex: JsonParseException) {
                    throw ValidationRunDataJSONInputException(ex, data)
                }
            }
    }

    companion object {
        const val CREATE_VALIDATION_RUN_FOR_BUILD_BY_ID = "createValidationRunById"
        const val CREATE_VALIDATION_RUN_FOR_BUILD_BY_NAME = "createValidationRun"
    }
}

interface ValidationRunInput {
    val validationStamp: String
    val dateTime: LocalDateTime?
    val validationRunStatus: String?
    val description: String?
    val dataTypeId: String?
    val data: JsonNode?
    val runInfo: RunInfoInput?
}

class CreateValidationRunInput(
    @APIDescription("Project name")
    val project: String,
    @APIDescription("Branch name")
    val branch: String,
    @APIDescription("Build name")
    val build: String,
    @APIDescription("Validation stamp name")
    override val validationStamp: String,
    @APIDescription("Validation run status")
    override val validationRunStatus: String?,
    @APIDescription("Validation run date/time")
    override val dateTime: LocalDateTime?,
    @APIDescription("Validation description")
    override val description: String?,
    @APIDescription("Type of the data to associated with the validation")
    override val dataTypeId: String?,
    @APIDescription("Data to associated with the validation")
    override val data: JsonNode?,
    @APIDescription("Run info")
    @TypeRef
    override val runInfo: RunInfoInput?,
): ValidationRunInput

class CreateValidationRunByReleaseInput(
    @APIDescription("Project name")
    val project: String,
    @APIDescription("Build release")
    val buildRelease: String,
    @APIDescription("Validation stamp name")
    override val validationStamp: String,
    @APIDescription("Validation run status")
    override val validationRunStatus: String?,
    @APIDescription("Validation run date/time")
    override val dateTime: LocalDateTime?,
    @APIDescription("Validation description")
    override val description: String?,
    @APIDescription("Type of the data to associated with the validation")
    override val dataTypeId: String?,
    @APIDescription("Data to associated with the validation")
    override val data: JsonNode?,
    @APIDescription("Run info")
    @TypeRef
    override val runInfo: RunInfoInput?,
): ValidationRunInput

class CreateValidationRunByIdInput(
    @APIDescription("Build ID")
    val buildId: Int,
    @APIDescription("Validation stamp name")
    override val validationStamp: String,
    @APIDescription("Validation run status")
    override val validationRunStatus: String?,
    @APIDescription("Validation run date/time")
    override val dateTime: LocalDateTime?,
    @APIDescription("Validation description")
    override val description: String?,
    @APIDescription("Type of the data to associated with the validation")
    override val dataTypeId: String?,
    @APIDescription("Data to associated with the validation")
    override val data: JsonNode?,
    @APIDescription("Run info")
    @TypeRef
    override val runInfo: RunInfoInput?,
): ValidationRunInput

class ChangeValidationRunStatusInput(
    @APIDescription("Validation run ID")
    val validationRunId: Int,
    @APIDescription("Validation run status ID")
    val validationRunStatusId: String,
    @APIDescription("Optional validation run status description")
    val description: String? = null,
)
