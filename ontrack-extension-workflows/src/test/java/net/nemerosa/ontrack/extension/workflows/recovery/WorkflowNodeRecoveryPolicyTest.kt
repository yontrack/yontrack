package net.nemerosa.ontrack.extension.workflows.recovery

import net.nemerosa.ontrack.extension.workflows.WorkflowConfigurationProperties
import net.nemerosa.ontrack.extension.workflows.engine.WorkflowInstanceLauncher
import net.nemerosa.ontrack.extension.workflows.engine.WorkflowInstanceNodeStatus
import net.nemerosa.ontrack.model.security.Actor
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDateTime
import kotlin.test.assertEquals

class WorkflowNodeRecoveryPolicyTest {

    private val now = LocalDateTime.of(2026, 10, 11, 12, 0)

    private val config = WorkflowConfigurationProperties.RecoveryConfigProperties().apply {
        staleAfter = Duration.ofMinutes(3)
        createdStaleAfter = Duration.ofMinutes(10)
        maxAge = Duration.ofDays(1)
    }

    private val launcher = WorkflowInstanceLauncher(account = "user@test.com", actor = null)

    private fun candidate(
        status: WorkflowInstanceNodeStatus,
        owner: String? = "dead",
        dispatched: LocalDateTime? = now.minusMinutes(30),
        instanceTimestamp: LocalDateTime = now.minusMinutes(30),
        launcher: WorkflowInstanceLauncher? = this.launcher,
    ) = WorkflowNodeRecoveryCandidate(
        instanceId = "instance",
        nodeId = "node",
        status = status,
        owner = owner,
        dispatched = dispatched,
        instanceTimestamp = instanceTimestamp,
        launcher = launcher,
    )

    private fun decide(
        candidate: WorkflowNodeRecoveryCandidate,
        processStarted: LocalDateTime = now.minusHours(1),
    ) = WorkflowNodeRecoveryPolicy.decide(
        candidate = candidate,
        heartbeats = mapOf(
            "live" to now.minusMinutes(1),
            "dead" to now.minusMinutes(4),
        ),
        now = now,
        processStarted = processStarted,
        config = config,
    )

    @Test
    fun `A waiting node of a live process is left alone`() {
        assertEquals(WorkflowNodeRecoveryAction.None, decide(candidate(WorkflowInstanceNodeStatus.WAITING, owner = "live")))
    }

    @Test
    fun `A started node of a live process is left alone`() {
        assertEquals(WorkflowNodeRecoveryAction.None, decide(candidate(WorkflowInstanceNodeStatus.STARTED, owner = "live")))
    }

    @Test
    fun `A waiting node of a process without heartbeat is resumed`() {
        assertEquals(WorkflowNodeRecoveryAction.Resume, decide(candidate(WorkflowInstanceNodeStatus.WAITING, owner = "dead")))
    }

    @Test
    fun `A waiting node of an unknown process is resumed`() {
        assertEquals(WorkflowNodeRecoveryAction.Resume, decide(candidate(WorkflowInstanceNodeStatus.WAITING, owner = "gone")))
    }

    @Test
    fun `A started node of a process without heartbeat is interrupted`() {
        assertEquals(
            WorkflowNodeRecoveryAction.Fail(WorkflowNodeRecoveryPolicy.INTERRUPTED),
            decide(candidate(WorkflowInstanceNodeStatus.STARTED, owner = "dead"))
        )
    }

    @Test
    fun `A waiting node without owner is left alone while the recovering process is young`() {
        assertEquals(
            WorkflowNodeRecoveryAction.None,
            decide(candidate(WorkflowInstanceNodeStatus.WAITING, owner = null), processStarted = now.minusMinutes(1))
        )
    }

    @Test
    fun `A waiting node without owner is resumed once the recovering process is old enough`() {
        assertEquals(
            WorkflowNodeRecoveryAction.Resume,
            decide(candidate(WorkflowInstanceNodeStatus.WAITING, owner = null), processStarted = now.minusMinutes(4))
        )
    }

    @Test
    fun `A started node without owner is interrupted once the recovering process is old enough`() {
        assertEquals(
            WorkflowNodeRecoveryAction.Fail(WorkflowNodeRecoveryPolicy.INTERRUPTED),
            decide(candidate(WorkflowInstanceNodeStatus.STARTED, owner = null), processStarted = now.minusMinutes(4))
        )
    }

    @Test
    fun `A created node recently dispatched is left alone`() {
        assertEquals(
            WorkflowNodeRecoveryAction.None,
            decide(candidate(WorkflowInstanceNodeStatus.CREATED, owner = null, dispatched = now.minusMinutes(5)))
        )
    }

    @Test
    fun `A created node dispatched long ago is dispatched again`() {
        assertEquals(
            WorkflowNodeRecoveryAction.Resume,
            decide(candidate(WorkflowInstanceNodeStatus.CREATED, owner = null, dispatched = now.minusMinutes(11)))
        )
    }

    @Test
    fun `A created node never recorded as dispatched is dated by its instance`() {
        assertEquals(
            WorkflowNodeRecoveryAction.None,
            decide(
                candidate(
                    WorkflowInstanceNodeStatus.CREATED,
                    owner = null,
                    dispatched = null,
                    instanceTimestamp = now.minusMinutes(5)
                )
            )
        )
        assertEquals(
            WorkflowNodeRecoveryAction.Resume,
            decide(
                candidate(
                    WorkflowInstanceNodeStatus.CREATED,
                    owner = null,
                    dispatched = null,
                    instanceTimestamp = now.minusMinutes(11)
                )
            )
        )
    }

    @Test
    fun `An abandoned node of an old instance is not resumed`() {
        assertEquals(
            WorkflowNodeRecoveryAction.Fail(WorkflowNodeRecoveryPolicy.TOO_OLD),
            decide(candidate(WorkflowInstanceNodeStatus.WAITING, instanceTimestamp = now.minusDays(2)))
        )
        assertEquals(
            WorkflowNodeRecoveryAction.Fail(WorkflowNodeRecoveryPolicy.TOO_OLD),
            decide(
                candidate(
                    WorkflowInstanceNodeStatus.CREATED,
                    owner = null,
                    dispatched = null,
                    instanceTimestamp = now.minusDays(2)
                )
            )
        )
    }

    @Test
    fun `An abandoned node whose launcher is unknown is not resumed`() {
        assertEquals(
            WorkflowNodeRecoveryAction.Fail(WorkflowNodeRecoveryPolicy.UNKNOWN_LAUNCHER),
            decide(candidate(WorkflowInstanceNodeStatus.WAITING, launcher = null))
        )
    }

    @Test
    fun `An abandoned node launched by the system is resumed`() {
        assertEquals(
            WorkflowNodeRecoveryAction.Resume,
            decide(
                candidate(
                    WorkflowInstanceNodeStatus.WAITING,
                    launcher = WorkflowInstanceLauncher(account = null, actor = Actor.system(reason = null)),
                )
            )
        )
    }

    @Test
    fun `An interrupted node is in error whatever its age and launcher`() {
        assertEquals(
            WorkflowNodeRecoveryAction.Fail(WorkflowNodeRecoveryPolicy.INTERRUPTED),
            decide(
                candidate(
                    WorkflowInstanceNodeStatus.STARTED,
                    instanceTimestamp = now.minusDays(2),
                    launcher = null,
                )
            )
        )
    }

    @Test
    fun `Finished nodes are left alone`() {
        listOf(
            WorkflowInstanceNodeStatus.SUCCESS,
            WorkflowInstanceNodeStatus.ERROR,
            WorkflowInstanceNodeStatus.CANCELLED,
            WorkflowInstanceNodeStatus.TIMEOUT,
        ).forEach { status ->
            assertEquals(WorkflowNodeRecoveryAction.None, decide(candidate(status)), "$status")
        }
    }
}
