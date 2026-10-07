package net.nemerosa.ontrack.service

import net.nemerosa.ontrack.extension.api.ExtensionManager
import net.nemerosa.ontrack.extension.api.PromotionRunCheckExtension
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.PromotionLevel
import net.nemerosa.ontrack.model.structure.PromotionRun
import net.nemerosa.ontrack.model.structure.PromotionRunCheckReason
import net.nemerosa.ontrack.model.structure.PromotionRunCheckService
import net.nemerosa.ontrack.model.support.UserTransaction
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@UserTransaction
class PromotionRunCheckServiceImpl(
        private val extensionManager: ExtensionManager
) : PromotionRunCheckService {

    val sortedChecks: List<PromotionRunCheckExtension> by lazy {
        extensionManager.getExtensions(PromotionRunCheckExtension::class.java)
                .toList()
                .sortedBy { it.order }
    }

    /**
     * Checks all check components in turn.
     */
    override fun checkPromotionRunCreation(promotionRun: PromotionRun) {
        sortedChecks.forEach { it.checkPromotionRunCreation(promotionRun) }
    }

    /**
     * Asks all check components in turn, none of them stopping the others.
     */
    override fun explainPromotionRunCreation(
        build: Build,
        promotionLevel: PromotionLevel
    ): List<PromotionRunCheckReason> =
        sortedChecks.flatMap { check ->
            check.explainPromotionRunCreation(build, promotionLevel).map { reason ->
                PromotionRunCheckReason(check = check.checkName, reason = reason)
            }
        }
}
