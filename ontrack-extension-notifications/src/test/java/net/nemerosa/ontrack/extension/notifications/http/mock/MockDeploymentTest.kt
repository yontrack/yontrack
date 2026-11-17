package net.nemerosa.ontrack.extension.notifications.http.mock

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MockDeploymentTest {

    private val start = Instant.parse("2026-11-17T10:00:00Z")

    @Test
    fun `The id carries the creation time, the duration and the outcome`() {
        val deployment = MockDeployment.create(
            now = start,
            durationSeconds = 7,
            outcome = MockDeploymentOutcome.FAILED,
        )
        val parsed = assertNotNull(MockDeployment.parse(deployment.id))
        assertEquals(start, parsed.startedAt)
        assertEquals(7, parsed.durationSeconds)
        assertEquals(MockDeploymentOutcome.FAILED, parsed.outcome)
        assertEquals(deployment, parsed)
    }

    @Test
    fun `The id is readable and made of URL-safe characters only`() {
        val deployment = MockDeployment.create(
            now = start,
            durationSeconds = 5,
            outcome = MockDeploymentOutcome.SUCCEEDED,
            nonce = "0a1b2c3d",
        )
        assertEquals("1794909600000-5-succeeded-0a1b2c3d", deployment.id)
    }

    @Test
    fun `Two deployments created at the same time have different ids`() {
        val a = MockDeployment.create(now = start, durationSeconds = 5, outcome = MockDeploymentOutcome.SUCCEEDED)
        val b = MockDeployment.create(now = start, durationSeconds = 5, outcome = MockDeploymentOutcome.SUCCEEDED)
        assertNotEquals(a.id, b.id)
    }

    @Test
    fun `Running until the duration has elapsed, then succeeded`() {
        val deployment = MockDeployment.create(
            now = start,
            durationSeconds = 5,
            outcome = MockDeploymentOutcome.SUCCEEDED,
        )
        assertEquals(MockDeploymentStatus.RUNNING, deployment.statusAt(start))
        assertEquals(MockDeploymentStatus.RUNNING, deployment.statusAt(start.plusMillis(4_999)))
        assertEquals(MockDeploymentStatus.SUCCEEDED, deployment.statusAt(start.plusSeconds(5)))
        assertEquals(MockDeploymentStatus.SUCCEEDED, deployment.statusAt(start.plusSeconds(3600)))
    }

    @Test
    fun `Running until the duration has elapsed, then failed`() {
        val deployment = MockDeployment.create(
            now = start,
            durationSeconds = 2,
            outcome = MockDeploymentOutcome.FAILED,
        )
        assertEquals(MockDeploymentStatus.RUNNING, deployment.statusAt(start.plusSeconds(1)))
        assertEquals(MockDeploymentStatus.FAILED, deployment.statusAt(start.plusSeconds(2)))
    }

    @Test
    fun `A deployment of no duration is over at once`() {
        val deployment = MockDeployment.create(
            now = start,
            durationSeconds = 0,
            outcome = MockDeploymentOutcome.SUCCEEDED,
        )
        assertEquals(MockDeploymentStatus.SUCCEEDED, deployment.statusAt(start))
    }

    @Test
    fun `A clock behind the creation time, on another replica, sees the deployment running`() {
        val deployment = MockDeployment.create(
            now = start,
            durationSeconds = 0,
            outcome = MockDeploymentOutcome.SUCCEEDED,
        )
        assertEquals(MockDeploymentStatus.RUNNING, deployment.statusAt(start.minusSeconds(1)))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "",
            "unknown",
            "1794909600000-5-succeeded",
            "1794909600000-5-running-0a1b2c3d",
            "1794909600000-5-SUCCEEDED-0a1b2c3d",
            "1794909600000--5-succeeded-0a1b2c3d",
            "1794909600000-x-succeeded-0a1b2c3d",
            "abc-5-succeeded-0a1b2c3d",
            "1794909600000-5-succeeded-0a1b2c3",
            "1794909600000-5-succeeded-0A1B2C3D",
            "1794909600000-5-succeeded-0a1b2c3d-extra",
            "1794909600000-3601-succeeded-0a1b2c3d",
            "99999999999999999999-5-succeeded-0a1b2c3d",
            "1794909600000-99999999999-succeeded-0a1b2c3d",
            "../1794909600000-5-succeeded-0a1b2c3d",
        ]
    )
    fun `An unknown or malformed id is not a deployment`(id: String) {
        assertNull(MockDeployment.parse(id))
    }

    @Test
    fun `The duration is bounded`() {
        assertNotNull(MockDeployment.parse("1794909600000-${MockDeployment.MAX_DURATION_SECONDS}-succeeded-0a1b2c3d"))
    }
}
