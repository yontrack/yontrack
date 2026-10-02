package net.nemerosa.ontrack.extension.audittrail.listener

import net.nemerosa.ontrack.extension.audittrail.listener.TrailPayloads.claimed
import net.nemerosa.ontrack.extension.audittrail.listener.TrailPayloads.payload
import net.nemerosa.ontrack.extension.audittrail.model.TrailEntryTypes
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.PromotionLevel
import net.nemerosa.ontrack.model.structure.PromotionRun
import org.springframework.stereotype.Component

/**
 * Entries of the promotions of a build — the revocation of an auto-promotion included, which
 * deletes the promotion run.
 */
@Component
class PromotionTrailEventMapper : TrailEventMapper {

    override val eventTypes: Set<String> = setOf(
        EventFactory.NEW_PROMOTION_RUN.id,
        EventFactory.DELETE_PROMOTION_RUN.id,
    )

    override fun map(event: Event): List<TrailEntryRequest> =
        when (event.eventType.id) {
            EventFactory.NEW_PROMOTION_RUN.id -> {
                val run: PromotionRun = event.getEntity(ProjectEntityType.PROMOTION_RUN)
                listOf(
                    TrailEntryRequest(
                        build = run.build,
                        type = TrailEntryTypes.PROMOTION_ADDED,
                        payload = payload(
                            "promotionLevel" to promotionLevel(run.promotionLevel),
                            "promotionRun" to mapOf("id" to run.id()),
                            "description" to run.description,
                            "claimed" to claimed(run.signature),
                        ),
                    )
                )
            }

            EventFactory.DELETE_PROMOTION_RUN.id -> {
                // The event does not carry the run, only its ID
                val build: Build = event.getEntity(ProjectEntityType.BUILD)
                val promotionLevel: PromotionLevel = event.getEntity(ProjectEntityType.PROMOTION_LEVEL)
                listOf(
                    TrailEntryRequest(
                        build = build,
                        type = TrailEntryTypes.PROMOTION_REMOVED,
                        payload = payload(
                            "promotionLevel" to promotionLevel(promotionLevel),
                            "promotionRun" to mapOf("id" to event.getIntValue(PROMOTION_RUN_ID)),
                        ),
                    )
                )
            }

            else -> emptyList()
        }

    private fun promotionLevel(promotionLevel: PromotionLevel): Map<String, Any> = mapOf(
        "id" to promotionLevel.id(),
        "name" to promotionLevel.name,
    )

    companion object {
        /**
         * Name of the value of the deletion event holding the ID of the deleted promotion run.
         */
        private const val PROMOTION_RUN_ID = "PROMOTION_RUN_ID"
    }
}
