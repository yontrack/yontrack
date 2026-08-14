package net.nemerosa.ontrack.extension.notifications.schema

import net.nemerosa.ontrack.extension.notifications.channels.NotificationChannel
import net.nemerosa.ontrack.model.annotations.getAPITypeDescription
import net.nemerosa.ontrack.model.docs.Documentation
import net.nemerosa.ontrack.model.json.schema.DynamicJsonSchemaProvider
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import org.springframework.stereotype.Component
import org.springframework.util.ClassUtils
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.starProjectedType

@Component
class NotificationDynamicJsonSchemaProvider(
    private val channels: List<NotificationChannel<*, *>>,
) : DynamicJsonSchemaProvider {

    override val discriminatorValues: List<String>
        get() = channels.map { it.type }

    override fun getConfigurationTypes(builder: JsonTypeBuilder): Map<String, JsonType> =
        channels.associate { channel ->
            channel.type to getConfigurationType(channel, builder)
        }

    private fun getConfigurationType(
        channel: NotificationChannel<*, *>,
        builder: JsonTypeBuilder,
    ): JsonType {
        // A channel may be proxied by Spring (when it is transactional, for example) and a proxy
        // class carries none of the annotations of the channel it stands for.
        val channelClass = ClassUtils.getUserClass(channel).kotlin
        val docClass = channelClass.findAnnotation<Documentation>()
            ?: error("$channelClass does not have a Documentation annotation")
        return builder.toType(
            type = docClass.value.starProjectedType,
            description = getAPITypeDescription(channelClass)
        )
    }

    override fun toRef(id: String): String = "notification-config-$id"

}