package net.nemerosa.ontrack.extension.scm.changelog.assistants

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.events.EventQueryService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The `build_assisted` event is signed by the one who finds the build assisted, at the moment they
 * do it - not by the creator of the build, at its creation (#2043).
 */
@AsAdminTest
class BuildAssistedEventSignatureIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var eventQueryService: EventQueryService

    @Test
    fun `The build_assisted event is signed by the caller, now`() {
        project {
            branch {
                val buildTime = Time.now.minusDays(10).withNano(0)
                val build = build().updateBuildSignature(user = BUILD_CREATOR, time = buildTime)
                val before = Time.now.withNano(0)

                setProperty(
                    build,
                    AssistedChangePropertyType::class.java,
                    AssistedChangeProperty(
                        assistants = listOf("Claude Code"),
                        assistedCommits = 1,
                        totalCommits = 2,
                    ),
                )

                val event = eventQueryService.getLastEvent(build, EventFactory.BUILD_ASSISTED)
                assertNotNull(event, "Event build_assisted posted")
                val signature = event.signature
                assertNotNull(signature, "Event build_assisted is signed")
                assertEquals(
                    securityService.currentSignature.user.name,
                    signature.user.name,
                    "Event build_assisted is signed by the caller, not by the creator of the build"
                )
                assertTrue(
                    !signature.time.isBefore(before),
                    "Event build_assisted is dated ${signature.time}, not before $before (the build was created at $buildTime)"
                )
            }
        }
    }

    companion object {
        private const val BUILD_CREATOR = "build-creator"
    }
}
