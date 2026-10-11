package net.nemerosa.ontrack.extension.queue.actor

import net.nemerosa.ontrack.extension.queue.QueueConfigProperties
import net.nemerosa.ontrack.extension.queue.QueuePayload
import net.nemerosa.ontrack.extension.queue.dispatching.QueueDispatcher
import net.nemerosa.ontrack.extension.queue.record.QueueRecord
import net.nemerosa.ontrack.extension.queue.record.QueueRecordService
import net.nemerosa.ontrack.extension.queue.record.QueueRecordState
import net.nemerosa.ontrack.extension.queue.record.QueueRecordingsExtension
import net.nemerosa.ontrack.extension.recordings.RecordingsQueryService
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.waitUntil
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.format
import net.nemerosa.ontrack.model.security.Actor
import net.nemerosa.ontrack.model.security.ActorVia
import net.nemerosa.ontrack.model.security.AuthenticationStorageService
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

/**
 * The actor of a message is carried across the queue, to its processing.
 *
 * Not transactional: the messages are processed on the threads of the queue listeners.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class QueueActorIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var queueDispatcher: QueueDispatcher

    @Autowired
    private lateinit var actorQueueProcessor: ActorQueueProcessor

    @Autowired
    private lateinit var queueConfigProperties: QueueConfigProperties

    @Autowired
    private lateinit var queueRecordService: QueueRecordService

    @Autowired
    private lateinit var amqpTemplate: AmqpTemplate

    @Autowired
    private lateinit var recordingsQueryService: RecordingsQueryService

    @Autowired
    private lateinit var queueRecordingsExtension: QueueRecordingsExtension

    private fun record(recordId: String): QueueRecord = assertNotNull(
        asAdmin { recordingsQueryService.findById(queueRecordingsExtension, recordId) },
        "Record $recordId found"
    )

    @OptIn(ExperimentalTime::class)
    private fun processedActor(id: String): Actor {
        waitUntil("Message $id processed", interval = 500.milliseconds, timeout = 30.seconds) {
            actorQueueProcessor.actor(id) != null
        }
        return actorQueueProcessor.actor(id)!!
    }

    @Test
    fun `The actor of an account is carried across the queue`() {
        val account = asAdmin { doCreateAccountWithGlobalRole(Roles.GLOBAL_AUTOMATION) }
        val actor = Actor(account = account.email, via = ActorVia.TOKEN, tokenName = "pipeline")
        val id = uid("m-")
        asActor(account, actor) {
            queueDispatcher.dispatch(actorQueueProcessor, ActorQueuePayload(id), source = null)
        }
        assertEquals(actor, processedActor(id))
    }

    @Test
    fun `The actor of the system acting on behalf of an account is carried across the queue`() {
        val account = asAdmin { doCreateAccountWithGlobalRole(Roles.GLOBAL_AUTOMATION) }
        val actor = Actor(account = account.email, via = ActorVia.TOKEN, tokenName = "pipeline")
        val id = uid("m-")
        asActor(account, actor) {
            securityService.asAdmin("auto-versioning") {
                queueDispatcher.dispatch(actorQueueProcessor, ActorQueuePayload(id), source = null)
            }
        }
        assertEquals(Actor.system(reason = "auto-versioning", onBehalfOf = actor), processedActor(id))
    }

    /**
     * Publishes a message like the dispatcher of another Yontrack process would have.
     *
     * @return ID of the queue record
     */
    private fun publish(body: ActorQueuePayload, accountName: String, actor: Actor?): String {
        val queuePayload = QueuePayload.create(
            processor = actorQueueProcessor,
            accountName = accountName,
            body = body,
            actor = actor,
        )
        val routingKey = queueConfigProperties.getRoutingKey(actorQueueProcessor, body)
        asAdmin {
            queueRecordService.start(queuePayload, source = null)
            queueRecordService.setRouting(queuePayload, routingKey)
        }
        amqpTemplate.convertAndSend(
            "ontrack.queue.${actorQueueProcessor.id}",
            routingKey,
            queuePayload.asJson().format(),
        )
        return queuePayload.id
    }

    @OptIn(ExperimentalTime::class)
    private fun waitForRecordState(recordId: String, state: QueueRecordState) {
        waitUntil("Record $recordId in state $state", interval = 200.milliseconds, timeout = 30.seconds) {
            record(recordId).state == state
        }
    }

    /**
     * A message dispatched as the system by a Yontrack process before #2065 carries the random
     * run-as ID of that process: the previous version during a rolling upgrade, or a message still
     * queued at the upgrade.
     */
    @Test
    fun `A message dispatched as the system by another Yontrack process is processed as the system`() {
        val id = uid("m-")
        val actor = Actor.system(reason = "auto-versioning")
        val recordId = publish(ActorQueuePayload(id), accountName = "25555", actor = actor)
        assertEquals(actor, processedActor(id))
        waitForRecordState(recordId, QueueRecordState.COMPLETED)
    }

    @Test
    fun `A message dispatched as the run-as administrator is processed as the system`() {
        val id = uid("m-")
        val actor = Actor.system(reason = "auto-versioning")
        val recordId = publish(
            ActorQueuePayload(id),
            accountName = AuthenticationStorageService.RUN_AS_ADMINISTRATOR_ACCOUNT_ID,
            actor = actor,
        )
        assertEquals(actor, processedActor(id))
        waitForRecordState(recordId, QueueRecordState.COMPLETED)
    }

    @Test
    fun `A message whose account cannot be restored is recorded as errored`() {
        val id = uid("m-")
        val recordId = publish(ActorQueuePayload(id), accountName = "${uid("u-")}@yontrack.test", actor = null)
        waitForRecordState(recordId, QueueRecordState.ERRORED)
        assertEquals(null, actorQueueProcessor.actor(id), "Not processed")
    }
}
