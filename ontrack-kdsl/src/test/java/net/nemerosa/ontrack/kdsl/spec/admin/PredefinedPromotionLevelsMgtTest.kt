package net.nemerosa.ontrack.kdsl.spec.admin

import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.verify
import net.nemerosa.ontrack.kdsl.connector.Connector
import org.junit.jupiter.api.Test
import java.util.*

class PredefinedPromotionLevelsMgtTest {

    @Test
    fun `The image of a predefined promotion level is set with a PUT of its Base64 content`() {
        val connector = mockk<Connector>(relaxed = true)
        val png = byteArrayOf(1, 2, 3, 4)

        PredefinedPromotionLevelsMgt(connector).setPredefinedPromotionLevelImage(10, png)

        verify {
            connector.put(
                "/rest/admin/predefinedPromotionLevels/10/image",
                emptyMap(),
                Base64.getEncoder().encodeToString(png),
            )
        }
        confirmVerified(connector)
    }

}
