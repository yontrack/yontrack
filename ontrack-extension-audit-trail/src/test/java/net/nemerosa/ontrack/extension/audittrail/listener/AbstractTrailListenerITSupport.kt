package net.nemerosa.ontrack.extension.audittrail.listener

import net.nemerosa.ontrack.extension.audittrail.hash.TrailHashFormatV1
import net.nemerosa.ontrack.extension.audittrail.AbstractAuditTrailITSupport
import net.nemerosa.ontrack.extension.audittrail.model.TrailEntry
import net.nemerosa.ontrack.extension.audittrail.service.TrailService
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.security.Actor
import net.nemerosa.ontrack.model.security.ActorVia
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.model.structure.Build
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.JsonNode
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Support for the integration tests of the entries written from the events: a change is made
 * through the real mutation, by a CI actor, and the trail of the build is read back.
 */
abstract class AbstractTrailListenerITSupport : AbstractAuditTrailITSupport() {

    @Autowired
    protected lateinit var trailService: TrailService

    /**
     * Name of the API token of the CI actor.
     */
    protected val ciTokenName = "ci-demo"

    /**
     * Runs [code] as a CI pipeline, authenticated through the API token [ciTokenName] of an
     * administrator account.
     *
     * @return The result of the code and the actor, as an entry must carry it
     */
    protected fun <T> asCi(code: () -> T): Pair<T, JsonNode> {
        val account = asAdmin { doCreateAccountWithGlobalRole(Roles.GLOBAL_ADMINISTRATOR) }
        val actor = Actor(account = account.email, via = ActorVia.TOKEN, tokenName = ciTokenName)
        val result = asActor(account, actor) { code() }
        // Expected actor, written independently of its serialization
        return result to mapOf(
            "account" to account.email,
            "via" to "token",
            "tokenName" to ciTokenName,
        ).asJson()
    }

    /**
     * Entries of the trail of the build, checked to be well chained.
     */
    protected fun Build.trail(): List<TrailEntry> = asAdmin {
        trailService.getEntries(this).apply { assertChained(this@trail) }
    }

    /**
     * Entries of the trail of the build, after the [seq]-th one.
     */
    protected fun Build.trailAfter(seq: Int): List<TrailEntry> = trail().filter { it.seq > seq }

    /**
     * Seq of the last entry of the trail of the build, 0 if none.
     */
    protected fun Build.lastSeq(): Int = trail().lastOrNull()?.seq ?: 0

    /**
     * The build as entries name it.
     */
    protected fun Build.ref(): Map<String, Any> = mapOf(
        "id" to id(),
        "project" to project.name,
        "branch" to branch.name,
        "name" to name,
    )

    /**
     * Claimed time and user, as an entry writes them — worked independently of the hash format:
     * UTC, three digits of milliseconds.
     */
    protected fun claimed(time: LocalDateTime, user: String): Map<String, Any> = mapOf(
        "time" to String.format(
            "%04d-%02d-%02dT%02d:%02d:%02d.%03dZ",
            time.year, time.monthValue, time.dayOfMonth, time.hour, time.minute, time.second, time.nano / 1_000_000
        ),
        "user" to user,
    )

    /**
     * Checks the single entry written by a change.
     */
    protected fun List<TrailEntry>.assertSingleEntry(type: String, payload: Map<String, Any?>, actor: JsonNode) {
        assertEquals(listOf(type), map { it.type }, "One $type entry written")
        first().assertEntry(type, payload, actor)
    }

    protected fun TrailEntry.assertEntry(type: String, payload: Map<String, Any?>, actor: JsonNode) {
        assertEquals(type, this.type)
        assertEquals(payload.asJson(), this.payload, "Payload of $type")
        assertEquals(actor, this.actor, "Actor of $type")
    }

    private fun List<TrailEntry>.assertChained(build: Build) {
        forEachIndexed { index, entry ->
            assertEquals(build.id(), entry.buildId)
            assertEquals(index + 1, entry.seq, "Seq without gap")
            if (index == 0) {
                assertNull(entry.prevHash, "No previous hash for seq 1")
            } else {
                assertEquals(this[index - 1].hash, entry.prevHash, "Entry ${entry.seq} chained to the one before")
            }
            assertEquals(entry.hash, TrailHashFormatV1.hash(entry.envelope), "Hash of entry ${entry.seq}")
            assertTrue(
                index == 0 || !entry.time.isBefore(this[index - 1].time),
                "Server time of the entries never goes back"
            )
        }
    }
}
