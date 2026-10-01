package net.nemerosa.ontrack.service.deliverymap

import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.deliverymap.*
import net.nemerosa.ontrack.model.structure.PromotionLevel
import net.nemerosa.ontrack.model.structure.StructureService
import net.nemerosa.ontrack.model.structure.ValidationStamp
import org.springframework.stereotype.Component

@Component
class DeliveryMapCheckpointFactoryImpl(
    private val structureService: StructureService,
) : DeliveryMapCheckpointFactory {

    override fun promotionLevel(promotionLevel: PromotionLevel): DeliveryMapCheckpoint {
        val run = structureService.getLastPromotionRunForPromotionLevel(promotionLevel)
        return DeliveryMapCheckpoint(
            id = DeliveryMapCheckpointTypes.promotionLevel(promotionLevel.id),
            type = DeliveryMapCheckpointTypes.PROMOTION_LEVEL,
            name = promotionLevel.name,
            description = promotionLevel.description,
            data = PromotionLevelCheckpointData(
                promotionLevelId = promotionLevel.id(),
                image = promotionLevel.isImage,
                // The run behind the arrival below, named so that the workflows drawn beside this
                // checkpoint (#1711) can be read as belonging to the promotion it names
                promotionRunId = run?.id(),
            ).asJson(),
            // No status: a promotion level is arrived at by being promoted, and arriving is the whole
            // outcome. There is no such thing as a build which is promoted and failed.
            arrival = run?.let {
                DeliveryMapArrival(
                    build = it.build,
                    time = it.signature.time,
                )
            },
        )
    }

    override fun validationStamp(validationStamp: ValidationStamp): DeliveryMapCheckpoint {
        // The latest build with a run of ANY status, and the status of that run: the checkpoint
        // shows where the stamp was last run and how it went, not where it last passed, so it is
        // deliberately not filtered on statuses.
        // The query orders by build id descending, so one row is all the checkpoint needs.
        val run = structureService.getValidationRunsForValidationStamp(validationStamp, 0, 1).firstOrNull()
        return DeliveryMapCheckpoint(
            id = DeliveryMapCheckpointTypes.validationStamp(validationStamp.id),
            type = DeliveryMapCheckpointTypes.VALIDATION_STAMP,
            name = validationStamp.name,
            description = validationStamp.description,
            data = ValidationStampCheckpointData(
                validationStampId = validationStamp.id(),
                image = validationStamp.isImage,
            ).asJson(),
            arrival = run?.let {
                DeliveryMapArrival(
                    build = it.build,
                    time = it.signature.time,
                    status = it.lastStatus.statusID,
                )
            },
        )
    }

}
