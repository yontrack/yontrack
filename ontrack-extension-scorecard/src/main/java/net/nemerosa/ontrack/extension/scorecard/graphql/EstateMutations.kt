package net.nemerosa.ontrack.extension.scorecard.graphql

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.extension.findings.model.FindingKind
import net.nemerosa.ontrack.extension.scorecard.engine.MarkerKind
import net.nemerosa.ontrack.extension.scorecard.estates.*
import net.nemerosa.ontrack.graphql.schema.Mutation
import net.nemerosa.ontrack.graphql.support.ListRef
import net.nemerosa.ontrack.graphql.support.TypeRef
import net.nemerosa.ontrack.graphql.support.TypedMutationProvider
import org.springframework.stereotype.Component

/**
 * Management of the estates. Every mutation needs the licensed feature "Delivery scorecard" and the
 * `EstateManagement` global function.
 */
@Component
class EstateMutations(
    private val estateService: EstateService,
) : TypedMutationProvider() {

    override val mutations: List<Mutation> = listOf(
        simpleMutation(
            name = "createEstate",
            description = "Creates an estate. Its readings are computed by its daily job, or on recompute.",
            input = CreateEstateInput::class,
            outputName = "estate",
            outputDescription = "Created estate",
            outputType = Estate::class,
        ) { input ->
            estateService.create(input.toEstateInput())
        },
        simpleMutation(
            name = "updateEstate",
            description = "Replaces the definition of an estate. Its snapshots are kept.",
            input = UpdateEstateInput::class,
            outputName = "estate",
            outputDescription = "Updated estate",
            outputType = Estate::class,
        ) { input ->
            estateService.update(
                input.id,
                EstateInput(
                    name = input.name,
                    description = input.description,
                    labels = input.labels,
                    marker = input.marker?.toEstateMarker(),
                    readingConfigs = input.readings?.map { it.toEstateReadingConfig() } ?: emptyList(),
                    security = input.security?.toEstateSecurity() ?: EstateSecurity(),
                )
            )
        },
        unitMutation(
            name = "deleteEstate",
            description = "Deletes an estate, with its snapshots",
            input = DeleteEstateInput::class,
        ) { input ->
            estateService.delete(input.id)
        },
        unitMutation(
            name = "recomputeEstate",
            description = "Queues the recompute of the readings of every project of an estate, overwriting the snapshots of the day.",
            input = RecomputeEstateInput::class,
        ) { input ->
            estateService.recompute(estateService.getById(input.id))
        },
    )
}

@APIDescription("Creation of an estate")
data class CreateEstateInput(
    @APIDescription("Unique name of the estate")
    val name: String,
    @APIDescription("Description of the estate")
    val description: String?,
    @APIDescription("Labels selecting the projects, as category:name or name. At least one; a project must carry all of them.")
    @ListRef
    val labels: List<String>,
    @APIDescription("Marker the delivery readings are read up to. Null for the default marker.")
    @TypeRef(embedded = true)
    val marker: EstateMarkerInput?,
    @APIDescription("Window override and target per reading, at most one per reading")
    @ListRef(embedded = true)
    val readings: List<EstateReadingConfigInput>?,
    @APIDescription("What the estate expects of the security scans of its projects. Null for no expectation.")
    @TypeRef(embedded = true)
    val security: EstateSecurityInput?,
) {
    fun toEstateInput() = EstateInput(
        name = name,
        description = description,
        labels = labels,
        marker = marker?.toEstateMarker(),
        readingConfigs = readings?.map { it.toEstateReadingConfig() } ?: emptyList(),
        security = security?.toEstateSecurity() ?: EstateSecurity(),
    )
}

@APIDescription("Update of an estate: its whole definition is replaced")
data class UpdateEstateInput(
    @APIDescription("ID of the estate")
    val id: Int,
    @APIDescription("Unique name of the estate")
    val name: String,
    @APIDescription("Description of the estate")
    val description: String?,
    @APIDescription("Labels selecting the projects, as category:name or name. At least one; a project must carry all of them.")
    @ListRef
    val labels: List<String>,
    @APIDescription("Marker the delivery readings are read up to. Null for the default marker.")
    @TypeRef(embedded = true)
    val marker: EstateMarkerInput?,
    @APIDescription("Window override and target per reading, at most one per reading")
    @ListRef(embedded = true)
    val readings: List<EstateReadingConfigInput>?,
    @APIDescription("What the estate expects of the security scans of its projects. Null for no expectation.")
    @TypeRef(embedded = true)
    val security: EstateSecurityInput?,
)

@APIDescription("What an estate expects of the security scans of its projects")
data class EstateSecurityInput(
    @APIDescription("Kinds of scan every project must have run, each fresher than the freshness, to be covered. Null or empty: any fresh scan covers a project.")
    @ListRef
    val expectedKinds: List<FindingKind>?,
    @APIDescription("Number of days a scan stays fresh, null for the freshness of the settings")
    val freshnessDays: Int?,
    @APIDescription("Number of days a CRITICAL finding may stay open, null for no target")
    val criticalTargetDays: Int?,
    @APIDescription("Number of days a HIGH finding may stay open, null for no target")
    val highTargetDays: Int?,
) {
    fun toEstateSecurity() = EstateSecurity(
        expectedKinds = expectedKinds ?: emptyList(),
        freshnessDays = freshnessDays,
        criticalTargetDays = criticalTargetDays,
        highTargetDays = highTargetDays,
    )
}

@APIDescription("Marker of an estate")
data class EstateMarkerInput(
    @APIDescription("Kind of marker")
    val kind: MarkerKind,
    @APIDescription("For a PROMOTION marker, name of the promotion level")
    val levelName: String?,
    @APIDescription("For an ENVIRONMENT marker, name of the environment")
    val environment: String?,
    @APIDescription("For an ENVIRONMENT marker, qualifier of the slots read, empty or null for the default one")
    val qualifier: String?,
) {
    fun toEstateMarker(): EstateMarker = when (kind) {
        MarkerKind.PROMOTION -> EstatePromotionMarker(levelName ?: "")
        MarkerKind.ENVIRONMENT -> EstateEnvironmentMarker(environment ?: "", qualifier ?: "")
    }
}

@APIDescription("Window override and target of a reading in an estate")
data class EstateReadingConfigInput(
    @APIDescription("Key of the reading, like delivery.leadTime")
    val key: String,
    @APIDescription("Number of days the reading is taken over, null for the window of the settings")
    val windowDays: Int?,
    @APIDescription("Threshold the reading is judged against, in the unit of the reading, null for none")
    val target: Double?,
) {
    fun toEstateReadingConfig() = EstateReadingConfig(
        key = key,
        windowDays = windowDays,
        target = target,
    )
}

@APIDescription("Deletion of an estate")
data class DeleteEstateInput(
    @APIDescription("ID of the estate")
    val id: Int,
)

@APIDescription("Recompute of the readings of an estate")
data class RecomputeEstateInput(
    @APIDescription("ID of the estate")
    val id: Int,
)
