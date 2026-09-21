package net.nemerosa.ontrack.extension.notifications.subscriptions

import tools.jackson.databind.node.ObjectNode
import net.nemerosa.ontrack.extension.notifications.mock.MockNotificationChannelConfig
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.support.StorageService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class GlobalSubscriptionStoreIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var globalSubscriptionStore: GlobalSubscriptionStore

    @Autowired
    private lateinit var storageService: StorageService

    @Test
    fun `Migrating global subscriptions`() {
        asAdmin {
            // Clearing the store for the test
            globalSubscriptionStore.deleteAll()
            // Subscription record without a name
            val record = SubscriptionRecord(
                name = "will be removed for the test",
                channel = "mock",
                channelConfig = MockNotificationChannelConfig(
                    target = "#test",
                    data = "test"
                ).asJson(),
                events = setOf(EventFactory.NEW_PROMOTION_RUN.id),
                keywords = null,
                disabled = false,
                origin = "test",
                contentTemplate = "Some template"
            ).asJson().apply {
                (this as ObjectNode).remove("name")
            }
            // Checks that this "old" record has no name
            assertFalse(record.has("name"), "Old record has no name")
            // Storing the old record
            storageService.store(
                store = EventSubscription::class.java.name,
                key = "any-key",
                data = record,
            )
            // Running the migration
            globalSubscriptionStore.migrateSubscriptionNames()
            // Getting the old record by name
            val data = storageService.getData(EventSubscription::class.java.name)
            assertEquals(1, data.size, "One element")
            val name = data.keys.first()
            val newRecord = globalSubscriptionStore.find(name)
            assertNotNull(newRecord) {
                assertEquals(name, it.name)
            }
        }
    }

}