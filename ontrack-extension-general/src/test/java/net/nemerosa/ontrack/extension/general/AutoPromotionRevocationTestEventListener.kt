package net.nemerosa.ontrack.extension.general

import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.events.EventListener
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.PromotionLevel
import org.springframework.stereotype.Component

/**
 * Test support for [AutoPromotionRevocationEventListener].
 *
 * Records the [EventFactory.AUTO_PROMOTION_REVOKED] events so a test can assert on them, and can be armed
 * to throw on every promotion run deletion - listeners are called synchronously and nothing catches their
 * exceptions, so this is how a revocation is made to fail for real.
 *
 * A singleton shared by every test of the context: call [reset] at the start of each test.
 */
@Component
class AutoPromotionRevocationTestEventListener : EventListener {

    val revocations = mutableListOf<Pair<Int, Int>>()

    var failOnPromotionRunDeletion: Boolean = false

    fun reset() {
        revocations.clear()
        failOnPromotionRunDeletion = false
    }

    fun revokedPromotionLevelIds(build: Build): List<Int> =
        revocations.filter { (buildId, _) -> buildId == build.id() }.map { (_, plId) -> plId }

    override fun onEvent(event: Event) {
        when {
            event.eventType === EventFactory.DELETE_PROMOTION_RUN ->
                if (failOnPromotionRunDeletion) {
                    error("Simulated failure while deleting a promotion run")
                }

            event.eventType === EventFactory.AUTO_PROMOTION_REVOKED ->
                revocations += event.getEntity<Build>(ProjectEntityType.BUILD).id() to
                        event.getEntity<PromotionLevel>(ProjectEntityType.PROMOTION_LEVEL).id()
        }
    }
}
