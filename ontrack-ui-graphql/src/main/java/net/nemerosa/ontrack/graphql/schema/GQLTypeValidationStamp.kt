package net.nemerosa.ontrack.graphql.schema

import graphql.Scalars.GraphQLBoolean
import graphql.Scalars.GraphQLInt
import graphql.schema.DataFetcher
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLObjectType.newObject
import graphql.schema.GraphQLTypeReference
import net.nemerosa.ontrack.extension.api.ExtensionManager
import net.nemerosa.ontrack.graphql.schema.authorizations.GQLInterfaceAuthorizableService
import net.nemerosa.ontrack.graphql.support.listType
import net.nemerosa.ontrack.graphql.support.pagination.GQLPaginatedListFactory
import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.model.support.FreeTextAnnotatorContributor
import org.springframework.stereotype.Component

@Component
class GQLTypeValidationStamp(
    private val gqlInterfaceAuthorizableService: GQLInterfaceAuthorizableService,
    private val structureService: StructureService,
    creation: GQLTypeCreation,
    private val projectEntityInterface: GQLProjectEntityInterface,
    private val paginatedListFactory: GQLPaginatedListFactory,
    private val validationRun: GQLTypeValidationRun,
    private val validationRunStatusService: ValidationRunStatusService,
    private val validationDataTypeConfig: GQLTypeValidationDataTypeConfig,
    projectEntityFieldContributors: List<GQLProjectEntityFieldContributor>,
    freeTextAnnotatorContributors: List<FreeTextAnnotatorContributor>,
    extensionManager: ExtensionManager,
) : AbstractGQLProjectEntity<ValidationStamp>(
        ValidationStamp::class.java,
        ProjectEntityType.VALIDATION_STAMP,
        projectEntityFieldContributors,
        creation,
        freeTextAnnotatorContributors,
        extensionManager,
) {

    override fun getTypeName() = VALIDATION_STAMP

    override fun createType(cache: GQLTypeCache): GraphQLObjectType {
        return newObject()
                .name(VALIDATION_STAMP)
                .withInterface(projectEntityInterface.typeRef)
                .fields(projectEntityInterfaceFields())
                // Authorizations
                .apply {
                    gqlInterfaceAuthorizableService.apply(this, PromotionLevel::class)
                }
                // Image flag
                .field { f ->
                    f.name("image")
                            .description("Flag to indicate if an image is associated")
                            .type(GraphQLBoolean)
                }
                // Data type
                .field {
                    it.name("dataType")
                            .description("Data definition associated with the validation stamp")
                            .type(validationDataTypeConfig.typeRef)
                }
                // Ref to branch
                .field(
                        newFieldDefinition()
                                .name("branch")
                                .description("Reference to branch")
                                .type(GraphQLTypeReference(GQLTypeBranch.BRANCH))
                                .build()
                )
                // Paginated list of validation runs
                .field(
                        paginatedListFactory.createPaginatedField<ValidationStamp, ValidationRun>(
                                cache = cache,
                                fieldName = "validationRunsPaginated",
                                fieldDescription = "Paginated list of validation runs",
                                itemType = validationRun.typeName,
                                itemListCounter = { environment, validationStamp ->
                                    val buildId: Int? = environment.getArgument<Int>("buildId")
                                    if (buildId != null) {
                                        structureService.getValidationRunsCountForBuildAndValidationStamp(
                                                ID.of(buildId),
                                                validationStamp.id
                                        )
                                    } else {
                                        structureService.getValidationRunsCountForValidationStamp(
                                                validationStamp.id
                                        )
                                    }
                                },
                                itemListProvider = { environment, validationStamp, offset, size ->
                                    val buildId: Int? = environment.getArgument<Int>("buildId")
                                    val passed: Boolean? = environment.getArgument("passed")
                                    val statuses = if (passed != null) {
                                        if (passed) {
                                            validationRunStatusService.validationRunStatusList.filter { it.isPassed }
                                        } else {
                                            validationRunStatusService.validationRunStatusList.filter { !it.isPassed }
                                        }
                                    } else {
                                        null
                                    }
                                    if (buildId != null) {
                                        if (statuses != null) {
                                            structureService.getValidationRunsForBuildAndValidationStampAndStatus(
                                                    ID.of(buildId),
                                                    validationStamp.id,
                                                    statuses,
                                                    offset,
                                                    size
                                            )
                                        } else {
                                            structureService.getValidationRunsForBuildAndValidationStamp(
                                                    ID.of(buildId),
                                                    validationStamp.id,
                                                    offset,
                                                    size
                                            )
                                        }
                                    } else if (statuses != null) {
                                        structureService.getValidationRunsForValidationStampAndStatus(
                                                validationStamp,
                                                statuses,
                                                offset,
                                                size
                                        )
                                    } else {
                                        structureService.getValidationRunsForValidationStamp(
                                                validationStamp,
                                                offset,
                                                size
                                        )
                                    }
                                },
                                arguments = listOf(
                                        GraphQLArgument.newArgument()
                                                .name("buildId")
                                                .description("Validation runs for this build only")
                                                .type(GraphQLInt)
                                                .build(),
                                        GraphQLArgument.newArgument()
                                                .name("passed")
                                                .description("Allows to filter on the last status of the run")
                                                .type(GraphQLBoolean)
                                                .build()
                                )
                        )
                )
                // Validation runs
                .field(
                        newFieldDefinition()
                                .name("validationRuns")
                                .description("List of runs for this validation stamp")
                                .type(listType(validationRun.typeRef))
                                .argument {
                                    it.name("count")
                                            .description("Maximum number of validation runs")
                                            .type(GraphQLInt)
                                            .defaultValue(50)
                                }
                                .dataFetcher(validationStampValidationRunsFetcher())
                                .build()
                )
                // OK
                .build()

    }

    private fun validationStampValidationRunsFetcher() =
            DataFetcher { environment ->
                val validationStamp: ValidationStamp = environment.getSource()!!
                // Gets all the validation runs
                return@DataFetcher structureService.getValidationRunsForValidationStamp(
                        validationStamp,
                        0,
                        environment.getArgument<Int>("count") ?: 50
                )
            }

    override fun getSignature(entity: ValidationStamp): Signature? {
        return entity.signature
    }

    companion object {

        const val VALIDATION_STAMP = "ValidationStamp"
    }

}
