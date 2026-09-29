package net.nemerosa.ontrack.kdsl.acceptance.tests.notifications

import net.nemerosa.ontrack.kdsl.acceptance.tests.AbstractACCDSLTestSupport
import net.nemerosa.ontrack.kdsl.spec.ProjectEntity
import net.nemerosa.ontrack.kdsl.spec.extension.notifications.Subscription
import net.nemerosa.ontrack.kdsl.spec.extension.notifications.notifications

abstract class AbstractACCDSLNotificationsTestSupport : AbstractACCDSLTestSupport() {

    /**
     * Subscription for a project entity.
     */
    protected fun ProjectEntity.subscribe(
        name: String,
        channel: String,
        channelConfig: Any,
        keywords: String?,
        events: List<String>,
        contentTemplate: String? = null,
    ) {
        ontrack.notifications.subscribe(
            name = name,
            channel = channel,
            channelConfig = channelConfig,
            keywords = keywords,
            events = events,
            projectEntity = this,
            contentTemplate = contentTemplate,
        )
    }

    /**
     * Gets the list of subscriptions for a project entity.
     */
    protected fun ProjectEntity.subscriptions(): List<Subscription> =
        ontrack.notifications.subscriptions(
            projectEntity = this,
        )

}