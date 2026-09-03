package net.nemerosa.ontrack.demo.seed

import com.fasterxml.jackson.databind.JsonNode
import java.time.LocalDateTime

/**
 * The Yontrack API, as far as seeding the demo needs it.
 *
 * The seed talks to Yontrack only through this interface, so that [DemoSeed] can be
 * exercised against an in-memory target and its output compared run to run — which is the
 * whole acceptance criterion of the demo seed. [KdslDemoTarget] is the one implementation
 * that talks to a real instance.
 *
 * The handles ([DemoProject], [DemoBranch], ...) mirror the KDSL objects rather than
 * re-addressing entities by name on every call: the seed creates the whole dataset in one
 * pass, so it always has the handle for whatever it is about to add to.
 */
interface DemoTarget {

    /**
     * Every project on the instance, whether the seed created it or not — the reset
     * deletes all of them.
     */
    fun projects(): List<DemoProject>

    fun createProject(name: String, description: String): DemoProject

    /**
     * Every environment on the instance. Environments are not projects and are not
     * covered by CasC, so the reset has to delete them explicitly.
     */
    fun environments(): List<DemoEnvironment>

    fun createEnvironment(
        name: String,
        order: Int,
        description: String,
        tags: List<String> = emptyList(),
    ): DemoEnvironment

    /**
     * Every dashboard the reset can reach and delete. Excludes the built-in dashboard,
     * which Yontrack does not allow deleting, and another account's private dashboards,
     * which it does not allow seeing.
     */
    fun dashboards(): List<DemoDashboardHandle>

    /**
     * Creates or replaces a dashboard. Yontrack rejects a second dashboard with the same
     * name unless the UUID matches, so the seed always names a fixed one.
     */
    fun saveDashboard(dashboard: DemoDashboard)
}

interface DemoDashboardHandle {
    val name: String
    fun delete()
}

interface DemoProject {
    val name: String
    fun delete()
    fun createBranch(name: String, description: String): DemoBranch
}

interface DemoBranch {
    val name: String
    fun createPromotionLevel(name: String, description: String, workflow: WorkflowSpec? = null)
    fun createValidationStamp(name: String, description: String)
    fun createBuild(name: String, description: String, creation: LocalDateTime): DemoBuild
}

interface DemoBuild {
    val name: String

    /**
     * Sets the release property, which is what a build shows as its display name.
     */
    fun setRelease(release: String)

    fun promote(promotionLevel: String, description: String, at: LocalDateTime)

    fun validate(validationStamp: String, status: ValidationStatus, description: String)

    /**
     * Records that this build uses [build].
     */
    fun linkTo(build: DemoBuild)
}

interface DemoEnvironment {
    val name: String
    fun delete()
    fun createSlot(project: DemoProject, description: String): DemoSlot
}

interface DemoSlot {
    /**
     * Runs a deployment of [build] on this slot all the way to done, so the environment
     * shows something.
     */
    fun deploy(build: DemoBuild)
}

/**
 * A dashboard to publish on the demo, shared with every user.
 *
 * @property uuid Fixed, so that re-seeding updates the dashboard instead of colliding with
 * the one the previous run left behind.
 */
data class DemoDashboard(
    val uuid: String,
    val name: String,
    val widgets: List<DemoWidget>,
)

/**
 * @property uuid Fixed, for the same reason as [DemoDashboard.uuid].
 */
data class DemoWidget(
    val uuid: String,
    val key: String,
    val config: JsonNode,
    val layout: DemoWidgetLayout,
)

data class DemoWidgetLayout(
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
)
