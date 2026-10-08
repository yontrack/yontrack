package net.nemerosa.ontrack.extension.general

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.events.EventQueryService
import net.nemerosa.ontrack.model.events.EventType
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.NameDescription
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.Signature
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * An event on a build is signed by the one who acts on it, at the moment they do it - not by the
 * creator of the build, at its creation. Only `new_build` keeps the signature of the build (#2043).
 */
@AsAdminTest
class BuildEventSignatureIT : AbstractPropertyTypeTestSupport() {

    @Autowired
    private lateinit var eventQueryService: EventQueryService

    /**
     * Creation time of the build, ten days ago, at a round second so that storage keeps it as is.
     */
    private val buildTime: LocalDateTime = Time.now.minusDays(10).withNano(0)

    /**
     * A build created at [buildTime] by somebody else than the one acting on it.
     */
    private fun withBackdatedBuild(code: (build: Build) -> Unit) {
        project {
            branch {
                val build = structureService.newBuild(
                    Build.of(this, NameDescription.nd(uid("b-"), ""), Signature.of(buildTime, BUILD_CREATOR))
                )
                code(build)
            }
        }
    }

    @Test
    fun `The creation of a build is signed by the build`() {
        withBackdatedBuild { build ->
            val signature = lastSignature(build, EventFactory.NEW_BUILD)
            assertEquals(BUILD_CREATOR, signature.user.name)
            assertEquals(buildTime, signature.time)
        }
    }

    @Test
    fun `Setting the display name of a build is signed by the caller, now`() {
        withBackdatedBuild { build ->
            val before = Time.now.withNano(0)
            build.release("1.0.0")
            assertSignedNow(build, EventFactory.UPDATE_BUILD_DISPLAY_NAME, before)
        }
    }

    @Test
    fun `Removing the display name of a build is signed by the caller, now`() {
        withBackdatedBuild { build ->
            build.release("1.0.0")
            val before = Time.now.withNano(0)
            deleteProperty(build, ReleasePropertyType::class.java)
            assertEquals(
                2,
                eventQueryService.getEvents(
                    ProjectEntityType.BUILD, build.id, EventFactory.UPDATE_BUILD_DISPLAY_NAME, 0, 10
                ).size,
                "The setting and the removal are posted"
            )
            assertSignedNow(build, EventFactory.UPDATE_BUILD_DISPLAY_NAME, before)
        }
    }

    private fun lastSignature(build: Build, eventType: EventType): Signature {
        val event = eventQueryService.getLastEvent(build, eventType)
        assertNotNull(event, "Event ${eventType.id} posted")
        val signature = event.signature
        assertNotNull(signature, "Event ${eventType.id} is signed")
        return signature
    }

    private fun assertSignedNow(build: Build, eventType: EventType, before: LocalDateTime) {
        val signature = lastSignature(build, eventType)
        assertEquals(
            securityService.currentSignature.user.name,
            signature.user.name,
            "Event ${eventType.id} is signed by the caller, not by the creator of the build"
        )
        assertTrue(
            !signature.time.isBefore(before),
            "Event ${eventType.id} is dated ${signature.time}, not before $before (the build was created at $buildTime)"
        )
    }

    companion object {
        private const val BUILD_CREATOR = "build-creator"
    }
}
