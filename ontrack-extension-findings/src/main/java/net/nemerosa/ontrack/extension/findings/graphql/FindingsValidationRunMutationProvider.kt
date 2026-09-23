package net.nemerosa.ontrack.extension.findings.graphql

import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLInputObjectField
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLType
import graphql.schema.GraphQLTypeReference
import net.nemerosa.ontrack.extension.findings.ingestion.FindingsIngestionRequest
import net.nemerosa.ontrack.extension.findings.ingestion.FindingsIngestionService
import net.nemerosa.ontrack.extension.findings.model.FindingKind
import net.nemerosa.ontrack.graphql.schema.*
import net.nemerosa.ontrack.graphql.support.GQLScalarJSON
import net.nemerosa.ontrack.graphql.support.GQLScalarLocalDateTime
import net.nemerosa.ontrack.graphql.support.getMutationInputField
import net.nemerosa.ontrack.graphql.support.getRequiredMutationInputField
import net.nemerosa.ontrack.graphql.support.toTypeRef
import net.nemerosa.ontrack.model.exceptions.BuildNotFoundException
import net.nemerosa.ontrack.model.structure.RunInfoInput
import net.nemerosa.ontrack.model.structure.StructureService
import net.nemerosa.ontrack.model.structure.ValidationRun
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import java.time.LocalDateTime
import kotlin.jvm.optionals.getOrNull

/**
 * `validateBuildWithFindings`, the one door for the reports of security scans.
 *
 * In the manner of `validateBuildWithCHML`, but the data is a report: synchronous, the run, the
 * findings, their observations and their exposure are written in one transaction, and a report
 * which cannot be read fails the mutation, creating nothing.
 */
@Component
class FindingsValidationRunMutationProvider(
    private val structureService: StructureService,
    private val findingsIngestionService: FindingsIngestionService,
) : MutationProvider {

    override val mutations: List<Mutation> = listOf(
        object : Mutation {

            override val name: String = "validateBuildWithFindings"

            override val description: String =
                "Validates a build identified by name with the report of a security scan, " +
                        "creating a validation run with the counts of its findings."

            override fun inputFields(dictionary: MutableSet<GraphQLType>): List<GraphQLInputObjectField> = listOf(
                requiredStringInputField("project", "Name of the project"),
                requiredStringInputField("branch", "Name of the branch"),
                requiredStringInputField("build", "Name of the build"),
                requiredStringInputField("validation", "Name of the validation stamp"),
                optionalStringInputField("description", "Description of the validation run"),
                optionalRefInputField(
                    "runInfo",
                    "Optional run info to associated with this validation run",
                    RunInfoInput::class.toTypeRef()
                ),
                requiredStringInputField(
                    "format",
                    "Format of the report. `findings` is the neutral format of Yontrack. `sarif` (SARIF 2.1) and `trivy` (Trivy JSON, vulnerabilities only) are native scanner formats, which need the licensed feature \"Native scanner formats\"."
                ),
                optionalRefInputField(
                    "kind",
                    "Kind of scan. Takes precedence over the kind given by the report, if any.",
                    GraphQLTypeReference(FindingKind::class.java.simpleName),
                ),
                optionalStringInputField(
                    "scanner",
                    "Name of the scanner. Takes precedence over the scanner given by the report, if any."
                ),
                GraphQLInputObjectField.newInputObjectField()
                    .name("dateTime")
                    .description("Time of the scan, the moment of the call when not given. Dates the run, and with it the observations, the exposure and the resolution of the findings.")
                    .type(GQLScalarLocalDateTime.INSTANCE)
                    .build(),
                GraphQLInputObjectField.newInputObjectField()
                    .name("report")
                    .description("Report of the scan, as JSON")
                    .type(GraphQLNonNull(GQLScalarJSON.INSTANCE))
                    .build(),
            )

            override val outputFields: List<GraphQLFieldDefinition> = listOf(
                GraphQLFieldDefinition.newFieldDefinition()
                    .name("validationRun")
                    .description("Created validation run")
                    .type(ValidationRun::class.toTypeRef())
                    .build()
            )

            override fun fetch(env: DataFetchingEnvironment): Any {
                val project = getRequiredMutationInputField<String>(env, "project")
                val branch = getRequiredMutationInputField<String>(env, "branch")
                val buildName = getRequiredMutationInputField<String>(env, "build")
                val build = structureService.findBuildByName(project, branch, buildName).getOrNull()
                    ?: throw BuildNotFoundException(project, branch, buildName)
                val input = EnvMutationInput(env)
                val run = findingsIngestionService.ingest(
                    build = build,
                    request = FindingsIngestionRequest(
                        validation = getRequiredMutationInputField(env, "validation"),
                        description = getMutationInputField(env, "description"),
                        runInfo = input.getInputObject<RunInfoInput>("runInfo"),
                        format = getRequiredMutationInputField(env, "format"),
                        kind = getMutationInputField<Any>(env, "kind")?.let { FindingKind.valueOf(it.toString()) },
                        scanner = getMutationInputField(env, "scanner"),
                        report = getRequiredMutationInputField<JsonNode>(env, "report"),
                        dateTime = getMutationInputField<LocalDateTime>(env, "dateTime"),
                    )
                ).run
                return mapOf("validationRun" to run)
            }
        }
    )
}
