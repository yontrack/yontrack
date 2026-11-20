package net.nemerosa.ontrack.extension.av.dispatcher

import net.nemerosa.ontrack.extension.av.tracking.AutoVersioningTracking
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.PromotionRun

interface AutoVersioningDispatcher {

    fun dispatch(
        promotionRun: PromotionRun,
        tracking: AutoVersioningTracking,
    )

    /**
     * Reschedule an entry, without any schedule
     *
     * Needs `ProjectView`, then `AutoVersioningReschedule`, on the project of the [branch], the
     * target of the order.
     *
     * @param branch Target branch of the order
     * @param uuid UUID of the order to reschedule
     * @return New order
     */
    fun reschedule(branch: Branch, uuid: String): AutoVersioningOrder

}