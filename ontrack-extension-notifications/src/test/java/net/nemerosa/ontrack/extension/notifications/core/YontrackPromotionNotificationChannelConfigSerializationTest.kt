package net.nemerosa.ontrack.extension.notifications.core

import net.nemerosa.ontrack.json.asJson
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals

class YontrackPromotionNotificationChannelConfigSerializationTest {

    @Test
    fun `Serialization of the duration using shorthand`() {
        val config = YontrackPromotionNotificationChannelConfig(
            promotion = "GOLD",
            waitForPromotionTimeout = Duration.ofMinutes(30)
        )
        val json = config.asJson()
        assertEquals("30m", json.path("waitForPromotionTimeout").stringValue())
    }

    @Test
    fun `Serialization of the duration using seconds if not matching shorthand`() {
        // 30 minutes and 5 seconds doesn't match any shorthand in Duration.format()
        val config = YontrackPromotionNotificationChannelConfig(
            promotion = "GOLD",
            waitForPromotionTimeout = Duration.ofMinutes(30).plusSeconds(5)
        )
        val json = config.asJson()
        assertEquals("1805s", json.path("waitForPromotionTimeout").stringValue())
    }
}
