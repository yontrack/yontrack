package net.nemerosa.ontrack.extension.notifications.channels

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.notifications.subscriptions.EventSubscriptionConfigException
import net.nemerosa.ontrack.json.parseInto
import kotlin.reflect.KClass

abstract class AbstractNotificationChannel<C : Any, R>(
    private val configClass: KClass<C>,
) : NotificationChannel<C, R> {

    override fun validate(channelConfig: JsonNode): ValidatedNotificationChannelConfig<C> = try {
        val config = channelConfig.parseInto(configClass)
        validateParsedConfig(config)
        ValidatedNotificationChannelConfig.config(config)
    } catch (ex: EventSubscriptionConfigException) {
        ValidatedNotificationChannelConfig.error(ex.innerMessage)
    } catch (ex: Exception) {
        ValidatedNotificationChannelConfig.error(ex)
    }

    protected abstract fun validateParsedConfig(config: C)

}