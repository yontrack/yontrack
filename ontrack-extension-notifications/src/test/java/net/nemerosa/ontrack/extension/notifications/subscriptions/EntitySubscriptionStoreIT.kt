package net.nemerosa.ontrack.extension.notifications.subscriptions

import tools.jackson.databind.node.ObjectNode
import net.nemerosa.ontrack.extension.notifications.mock.MockNotificationChannelConfig
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.repository.support.store.EntityDataStore
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class EntitySubscriptionStoreIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var entitySubscriptionStore: EntitySubscriptionStore

    @Autowired
    private lateinit var entityDataStore: EntityDataStore

    @Test
    fun `Migrating entity subscriptions`() {
        asAdmin {
            // Clearing the store for the test
            entitySubscriptionStore.clearAll()
            project {
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
                // Storing the old record for the project
                entityDataStore.replaceOrAddObject(
                    this,
                    EventSubscription::class.java.name,
                    "any key, will be replaced",
                    securityService.currentSignature,
                    null,
                    record,
                )
                // Running the migration
                entitySubscriptionStore.migrateSubscriptionNames()
                // Getting the subscriptions for the project
                val (total, subscriptions) = entitySubscriptionStore.findByFilter(
                    this,
                    0,
                    10,
                    EventSubscriptionFilter()
                )
                assertEquals(1, total)
                val subscription = subscriptions.first()
                val name = subscription.name
                val byName = entitySubscriptionStore.findByName(this, name)
                assertNotNull(byName) {
                    assertEquals(name, it.name)
                }
            }
        }
    }

}