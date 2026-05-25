package net.nemerosa.ontrack.extension.av.dispatcher

import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditEntryUUIDNotFoundException
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditService
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditStore
import net.nemerosa.ontrack.extension.av.config.AutoVersioningPushMode
import net.nemerosa.ontrack.extension.av.config.AutoVersioningSourceConfig
import net.nemerosa.ontrack.extension.av.scheduler.AutoVersioningScheduler
import net.nemerosa.ontrack.extension.av.scheduler.ScheduleService
import net.nemerosa.ontrack.extension.av.tracking.AutoVersioningBranchTrail
import net.nemerosa.ontrack.extension.av.tracking.AutoVersioningTracking
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.PromotionRun
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.*

@Component
class AutoVersioningDispatcherImpl(
    private val securityService: SecurityService,
    private val versionSourceFactory: VersionSourceFactory,
    private val autoVersioningAuditService: AutoVersioningAuditService,
    private val scheduleService: ScheduleService,
    private val autoVersioningScheduler: AutoVersioningScheduler,
    private val autoVersioningAuditStore: AutoVersioningAuditStore,
) : AutoVersioningDispatcher {

    private val logger: Logger = LoggerFactory.getLogger(AutoVersioningDispatcherImpl::class.java)

    override fun dispatch(
        promotionRun: PromotionRun,
        tracking: AutoVersioningTracking,
    ) {
        securityService.asAdmin {
            val trail = tracking.trail
            if (trail != null) {
                trail.branches.forEach { branchTrail ->
                    if (branchTrail.isEligible()) {
                        val order = createAutoVersioningOrder(
                            promotionRun = promotionRun,
                            branch = branchTrail.branch,
                            config = branchTrail.configuration,
                        )
                        // Posts the event on the queue
                        if (order != null) {
                            dispatchOrderWithTrail(tracking, branchTrail, order)
                        }
                    }
                }
            } else {
                // Dispatching not possible because no trail was created
            }
        }
    }

    private fun dispatchOrderWithTrail(
        tracking: AutoVersioningTracking,
        branchTrail: AutoVersioningBranchTrail,
        order: AutoVersioningOrder
    ) {
        tracking.withTrail {
            it.withOrder(branchTrail, order)
        }

        dispatchOrder(order)
    }

    private fun dispatchOrder(order: AutoVersioningOrder) {
        // Throttling
        autoVersioningAuditService.throttling(order)

        // Starting the audit
        val entry = autoVersioningAuditService.onCreated(order)

        // Triggering the scheduler immediately when no schedule is planned
        if (entry.order.schedule == null) {
            autoVersioningScheduler.scheduleEntry(entry)
        } else {
            autoVersioningAuditService.onPendingSchedule(entry.order)
        }
    }

    /**
     * Reschedule an entry, without any schedule
     */
    override fun reschedule(branch: Branch, uuid: String): AutoVersioningOrder {
        // Getting the entry to reschedule
        val entry = autoVersioningAuditStore.findByUUID(branch, uuid)
            ?: throw AutoVersioningAuditEntryUUIDNotFoundException(uuid)

        // Copying the order
        val order = entry.order.copy(
            uuid = UUID.randomUUID().toString(),
            schedule = null,
        )

        // Dispatching the order
        dispatchOrder(order)

        // OK
        return order
    }

    private fun createAutoVersioningOrder(
        promotionRun: PromotionRun,
        branch: Branch,
        config: AutoVersioningSourceConfig,
    ): AutoVersioningOrder? {
        try {
            val version = getBuildSourceVersion(promotionRun, config)
            return AutoVersioningOrder(
                uuid = UUID.randomUUID().toString(),
                sourceProject = promotionRun.build.project.name,
                sourceBuildId = promotionRun.build.id(),
                sourcePromotionRunId = promotionRun.id(),
                sourcePromotion = promotionRun.promotionLevel.name,
                sourceBackValidation = config.backValidation,
                qualifier = config.qualifier,
                branch = branch,
                targetPath = config.targetPath,
                targetRegex = config.targetRegex,
                targetProperty = config.targetProperty,
                targetPropertyRegex = config.targetPropertyRegex,
                targetPropertyType = config.targetPropertyType,
                targetVersion = version,
                autoApproval = config.autoApproval ?: true,
                upgradeBranchPattern = config.upgradeBranchPattern
                    ?: AutoVersioningSourceConfig.DEFAULT_UPGRADE_BRANCH_PATTERN,
                postProcessing = config.postProcessing,
                postProcessingConfig = config.postProcessingConfig,
                validationStamp = config.validationStamp,
                autoApprovalMode = config.autoApprovalMode ?: AutoVersioningSourceConfig.DEFAULT_AUTO_APPROVAL_MODE,
                reviewers = config.reviewers ?: emptyList(),
                prTitleTemplate = config.prTitleTemplate,
                prBodyTemplate = config.prBodyTemplate,
                prBodyTemplateFormat = config.prBodyTemplateFormat,
                additionalPaths = config.additionalPaths ?: emptyList(),
                schedule = computeSchedule(config.cronSchedule),
                pushMode = config.pushMode ?: AutoVersioningPushMode.PR,
            )
        } catch (ex: Exception) {
            // Logging the event
            logger.error(
                """
                    Build ${promotionRun.build.id} (${promotionRun.build.entityDisplayName}) was promoted to 
                    ${promotionRun.promotionLevel.name}, but is not eligible to auto-versioning because no 
                    version was returned or there was an error.
                """.trimIndent(),
                ex
            )
            // Not going further with the auto versioning request
            return null
        }
    }

    private fun computeSchedule(cronSchedule: String?): LocalDateTime? =
        if (cronSchedule.isNullOrBlank()) {
            null
        } else {
            scheduleService.nextExecutionTime(cronSchedule)
        }

    private fun getBuildSourceVersion(promotionRun: PromotionRun, config: AutoVersioningSourceConfig): String {
        return versionSourceFactory.getBuildVersion(promotionRun.build, config)
    }
}