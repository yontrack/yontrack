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
     * Checks the instance can take the mock SCM data a dataset declares, and fails if it
     * cannot.
     *
     * Called before the reset, for the same reason [DemoDataset.validate] is: the mock SCM
     * is off unless `ontrack.config.extension.scm.mock.enabled` is set, and finding that out
     * on the first commit — after every project has been deleted — leaves the demo blank.
     * Dataset validation cannot catch it, because it checks the dataset against Yontrack's
     * rules and not against the target's configuration.
     */
    fun checkScmAvailable()

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

    /**
     * Points the project at a mock SCM repository, emptying whatever that repository held —
     * the mock SCM keeps its repositories on the server, where they outlive the projects the
     * reset deletes — and declaring its issues.
     */
    fun configureScm(scm: ScmSpec)

    /**
     * Marks this project as a favourite of the account the seed runs as.
     *
     * The only per-user thing the seed writes. It is here because the mobile UI's home
     * screen is the current user's favourites and nothing else, so a demo with none opens
     * blank on a phone.
     */
    fun markAsFavourite()
}

interface DemoBranch {
    val name: String

    /**
     * Maps this branch onto a branch of the project's SCM repository. Called after
     * [DemoProject.configureScm].
     */
    fun configureScmBranch(scmBranch: String)

    /**
     * Marks this branch as a favourite of the account the seed runs as, as
     * [DemoProject.markAsFavourite] does for a project.
     */
    fun markAsFavourite()

    /**
     * Registers a commit on this branch's SCM branch.
     *
     * @return The id of the commit, which the mock SCM derives from the branch and the
     * position of the commit on it.
     */
    fun registerCommit(message: String): String

    fun createPromotionLevel(name: String, description: String, workflow: WorkflowSpec? = null)
    fun createValidationStamp(name: String, description: String)

    /**
     * Configures what grants [promotionLevel] by itself.
     *
     * Separate from [createPromotionLevel] because the property is written with entity *ids*: every
     * promotion level and every validation stamp of the branch has to exist before any of them can
     * be named here.
     */
    fun setAutoPromotion(promotionLevel: String, spec: AutoPromotionSpec)

    /**
     * Configures the promotion levels [promotionLevel] cannot be reached before. Same ordering
     * constraint as [setAutoPromotion], for the same reason.
     */
    fun setPromotionDependencies(promotionLevel: String, dependencies: List<String>)

    /**
     * Requires the promotion level immediately below [promotionLevel] in the branch's order before
     * [promotionLevel] can be granted.
     *
     * Named nothing, unlike [setPromotionDependencies]: the condition is a bare boolean and the
     * server resolves the predecessor from the branch's own promotion level order. It still comes
     * after [createPromotionLevel] for every level of the branch, because that order is what it
     * reads.
     */
    fun setPreviousPromotionCondition(promotionLevel: String, required: Boolean)
    fun createBuild(name: String, description: String, creation: LocalDateTime): DemoBuild
}

interface DemoBuild {
    val name: String

    /**
     * Sets the release property, which is what a build shows as its display name.
     */
    fun setRelease(release: String)

    fun promote(promotionLevel: String, description: String, at: LocalDateTime)

    /**
     * Records a run of [validationStamp] at [at].
     *
     * The time is passed in for the same reason [promote] takes one: a run stamped with the moment
     * of the reset reads as having happened seconds ago whatever the age of the build it names, and
     * on a delivery map that puts the stamp *after* the promotion it granted (#1718).
     */
    fun validate(validationStamp: String, status: ValidationStatus, description: String, at: LocalDateTime)

    /**
     * Records that this build uses [build].
     */
    fun linkTo(build: DemoBuild)

    /**
     * Records the commit this build was built from, which is where a change log involving
     * it starts or stops.
     */
    fun setCommit(commitId: String)
}

interface DemoEnvironment {
    val name: String
    fun delete()
    fun createSlot(project: DemoProject, description: String): DemoSlot
}

interface DemoSlot {
    /**
     * Runs a deployment of [build] on this slot as far as [stopAt] says - all the way to done
     * so the environment shows something, or only up to running so that there is a deployment
     * a person can still complete or cancel.
     */
    fun deploy(build: DemoBuild, stopAt: DeploymentStop)

    /**
     * Configures an admission rule on this slot.
     */
    fun addAdmissionRule(spec: SlotAdmissionRuleSpec)

    /**
     * Configures a workflow on this slot, for one of the three moments of a deployment.
     */
    fun addWorkflow(spec: SlotWorkflowSpec)
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
