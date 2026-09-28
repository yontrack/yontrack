package net.nemerosa.ontrack.extension.scorecard.casc

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.common.syncForward
import net.nemerosa.ontrack.extension.casc.context.AbstractCascContext
import net.nemerosa.ontrack.extension.casc.context.SubConfigContext
import net.nemerosa.ontrack.extension.scorecard.engine.MarkerKind
import net.nemerosa.ontrack.extension.scorecard.estates.*
import net.nemerosa.ontrack.extension.scorecard.license.ScorecardLicense
import net.nemerosa.ontrack.json.JsonParseException
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.json.schema.JsonArrayType
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.json.schema.toType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode

/**
 * CasC for the estates, under `ontrack.config.estates`: the list of the estates.
 *
 * The list is authoritative: an estate it names is created or updated, an estate it does not name
 * is deleted, with its snapshots.
 *
 * Without the licence of the delivery scorecard, the list is ignored with a warning — a lapsed
 * licence must not stop Yontrack from starting — and nothing is rendered.
 */
@Component
class EstatesCascContext(
    private val scorecardLicense: ScorecardLicense,
    private val estateService: EstateService,
) : AbstractCascContext(), SubConfigContext {

    private val logger: Logger = LoggerFactory.getLogger(EstatesCascContext::class.java)

    override val field: String = "estates"

    override fun jsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        JsonArrayType(
            description = "List of the estates of the delivery scorecard. An existing estate which is not listed is deleted.",
            items = jsonTypeBuilder.toType(EstateCasc::class),
        )

    override fun run(node: JsonNode, paths: List<String>) {
        val items = node.mapIndexed { index, child ->
            try {
                child.parse<EstateCasc>()
            } catch (ex: JsonParseException) {
                throw IllegalStateException(
                    "Cannot parse into ${EstateCasc::class.qualifiedName}: ${path(paths + index.toString())}",
                    ex
                )
            }
        }
        if (!scorecardLicense.estatesEnabled) {
            logger.warn("[casc] The licence does not allow the estates: ${path(paths)} is ignored.")
            return
        }
        syncForward(
            from = items,
            to = estateService.findAll(),
        ) {
            equality { item, estate -> item.name == estate.name }
            onCreation { item ->
                logger.info("[casc] Creating estate ${item.name}")
                estateService.create(item.toEstateInput())
            }
            onModification { item, estate ->
                logger.info("[casc] Updating estate ${item.name}")
                estateService.update(estate.id, item.toEstateInput())
            }
            onDeletion { estate ->
                logger.info("[casc] Deleting estate ${estate.name}")
                estateService.delete(estate.id)
            }
        }
    }

    override fun render(): JsonNode =
        if (scorecardLicense.estatesEnabled) {
            estateService.findAll().map { it.toCasc() }
        } else {
            emptyList()
        }.asJson()

    private fun Estate.toCasc() = EstateCasc(
        name = name,
        description = description,
        labels = labels.map { it.getDisplay() },
        marker = when (val marker = marker) {
            null -> null
            is EstatePromotionMarker -> EstateMarkerCasc(kind = MarkerKind.PROMOTION, levelName = marker.levelName)
            is EstateEnvironmentMarker -> EstateMarkerCasc(
                kind = MarkerKind.ENVIRONMENT,
                environment = marker.environment,
                qualifier = marker.qualifier,
            )
        },
        readings = readingConfigs.map {
            EstateReadingCasc(key = it.key, windowDays = it.windowDays, target = it.target)
        },
    )

    @APIDescription("Estate of the delivery scorecard")
    data class EstateCasc(
        @APIDescription("Unique name of the estate")
        val name: String,
        @APIDescription("Description of the estate")
        val description: String? = null,
        @APIDescription("Labels selecting the projects, as category:name or name. At least one; a project must carry all of them.")
        val labels: List<String>,
        @APIDescription("Marker the delivery readings are read up to. Omitted for the default marker.")
        val marker: EstateMarkerCasc? = null,
        @APIDescription("Window override and target per reading, at most one per reading")
        val readings: List<EstateReadingCasc> = emptyList(),
    ) {
        fun toEstateInput() = EstateInput(
            name = name,
            description = description,
            labels = labels,
            marker = marker?.let {
                when (it.kind) {
                    MarkerKind.PROMOTION -> EstatePromotionMarker(it.levelName ?: "")
                    MarkerKind.ENVIRONMENT -> EstateEnvironmentMarker(it.environment ?: "", it.qualifier ?: "")
                }
            },
            readingConfigs = readings.map {
                EstateReadingConfig(key = it.key, windowDays = it.windowDays, target = it.target)
            },
        )
    }

    @APIDescription("Marker of an estate")
    data class EstateMarkerCasc(
        @APIDescription("Kind of marker: PROMOTION or ENVIRONMENT")
        val kind: MarkerKind,
        @APIDescription("For a PROMOTION marker, name of the promotion level")
        val levelName: String? = null,
        @APIDescription("For an ENVIRONMENT marker, name of the environment")
        val environment: String? = null,
        @APIDescription("For an ENVIRONMENT marker, qualifier of the slots read, empty or omitted for the default one")
        val qualifier: String? = null,
    )

    @APIDescription("Window override and target of a reading in an estate")
    data class EstateReadingCasc(
        @APIDescription("Key of the reading, like delivery.leadTime")
        val key: String,
        @APIDescription("Number of days the reading is taken over, omitted for the window of the settings")
        val windowDays: Int? = null,
        @APIDescription("Threshold the reading is judged against, in the unit of the reading, omitted for none")
        val target: Double? = null,
    )
}
