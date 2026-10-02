package net.nemerosa.ontrack.extension.audittrail.listener

import net.nemerosa.ontrack.extension.audittrail.license.AuditTrailLicense
import net.nemerosa.ontrack.extension.audittrail.listener.TrailPayloads.build
import net.nemerosa.ontrack.extension.audittrail.listener.TrailPayloads.payload
import net.nemerosa.ontrack.extension.audittrail.listener.TrailPayloads.promotionLevel
import net.nemerosa.ontrack.extension.audittrail.listener.TrailPayloads.validationRun
import net.nemerosa.ontrack.extension.audittrail.listener.TrailPayloads.validationStamp
import net.nemerosa.ontrack.extension.audittrail.model.TrailCascadeReasons
import net.nemerosa.ontrack.extension.audittrail.model.TrailEntryTypes
import net.nemerosa.ontrack.extension.audittrail.service.TrailService
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.security.Actor
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.CascadeDeletionListener
import net.nemerosa.ontrack.model.structure.CascadedBuildLink
import net.nemerosa.ontrack.model.structure.CascadedPromotionRun
import net.nemerosa.ontrack.model.structure.CascadedValidationRun
import net.nemerosa.ontrack.model.structure.PromotionLevel
import net.nemerosa.ontrack.model.structure.ValidationStamp
import org.springframework.stereotype.Component

/**
 * Writes, on every build a deletion reaches beyond the deleted entity, the entry of what it loses:
 * `validation.deleted`, `promotion.removed` or `link.removed`, as their own deletion would write
 * them, with a `reason` naming the cascade (see [TrailCascadeReasons]).
 *
 * Entries are appended in the transaction of the deletion, build after build by ID — the order in
 * which their trails are locked — and their actor is the one of the deletion. A deletion whose
 * entries cannot be written fails.
 *
 * Nothing is listed nor written while the licence is off.
 */
@Component
class AuditTrailCascadeDeletionListener(
    private val auditTrailLicense: AuditTrailLicense,
    private val trailService: TrailService,
    private val securityService: SecurityService,
) : CascadeDeletionListener {

    override val isListening: Boolean
        get() = auditTrailLicense.auditTrailEnabled

    override fun beforeValidationStampDeletion(validationStamp: ValidationStamp, runs: List<CascadedValidationRun>) =
        append(
            runs.map { run ->
                TrailEntryRequest(
                    build = run.build,
                    type = TrailEntryTypes.VALIDATION_DELETED,
                    payload = payload(
                        "validationStamp" to validationStamp(validationStamp),
                        "validationRun" to validationRun(run.id, run.runOrder),
                        "status" to run.status,
                        "reason" to TrailCascadeReasons.VALIDATION_STAMP_DELETED,
                    ),
                )
            }
        )

    override fun beforePromotionLevelDeletion(promotionLevel: PromotionLevel, runs: List<CascadedPromotionRun>) =
        append(
            runs.map { run ->
                TrailEntryRequest(
                    build = run.build,
                    type = TrailEntryTypes.PROMOTION_REMOVED,
                    payload = payload(
                        "promotionLevel" to promotionLevel(promotionLevel),
                        "promotionRun" to mapOf("id" to run.id),
                        "reason" to TrailCascadeReasons.PROMOTION_LEVEL_DELETED,
                    ),
                )
            }
        )

    override fun beforeBuildDeletion(build: Build, links: List<CascadedBuildLink>) =
        append(
            links.map { link ->
                TrailEntryRequest(
                    build = link.build,
                    type = TrailEntryTypes.LINK_REMOVED,
                    payload = payload(
                        "target" to build(build),
                        "qualifier" to link.qualifier,
                        "reason" to TrailCascadeReasons.TARGET_BUILD_DELETED,
                    ),
                )
            }
        )

    private fun append(entries: List<TrailEntryRequest>) {
        if (entries.isEmpty()) return
        val actor = (securityService.currentActor ?: Actor.system(reason = null)).asJson()
        entries.forEach { entry ->
            trailService.append(entry.build, entry.type, entry.payload, actor)
        }
    }
}
