package net.nemerosa.ontrack.demo.seed

import tools.jackson.databind.JsonNode
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
     * Every label on the instance. Labels belong to the instance rather than to a project —
     * several projects carry the same one — so they outlive the projects the reset deletes
     * and the reset has to delete them explicitly, as it does environments and dashboards.
     */
    fun labels(): List<DemoLabel>

    fun createLabel(spec: LabelSpec): DemoLabel

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
     * Checks the instance takes security scans in the native formats - SARIF - and fails if it
     * does not.
     *
     * Called before the reset, for the same reason as [checkScmAvailable]: the native formats
     * need the licensed feature "Native scanner formats", which the development licence enables
     * and a production one may not, and finding that out on the first SARIF scan - after every
     * project has been deleted - leaves the demo blank.
     */
    fun checkNativeFindingsFormats()

    /**
     * Checks the instance runs the estates of the delivery scorecard, and the environments the
     * readings of one of them are read in, and fails if it does not.
     *
     * Called before the reset, for the same reason as [checkNativeFindingsFormats]: an estate needs
     * the licensed feature "Delivery scorecard", and finding that out on the first estate - after
     * every project has been deleted - leaves the demo without its scorecard.
     */
    fun checkScorecardLicensed()

    /**
     * Why the instance cannot offer [capability], or `null` when it can.
     *
     * Asked before the reset, and read-only: unlike [checkScmAvailable], a missing capability does
     * not stop the reset - the part of the dataset needing it is skipped, and the answer is what the
     * log says about it. It has to be the actual reason, worded for whoever reads the log of a
     * deployment: "the storage is NOT_CONFIGURED", not "unavailable".
     */
    fun unavailable(capability: DemoCapability): String?

    /**
     * Generates an API token named [name] for the account the seed runs as, revoking first any
     * token of that name - one an interrupted run left behind, since a name is unique per account.
     *
     * The token is an administrator's, like the account's: the seed revokes it once the dataset is
     * seeded.
     */
    fun openToken(name: String): DemoToken

    /**
     * Every registered agent the account the seed runs as can see - all of them, for an administrator.
     * An agent is an account and outlives the projects the reset deletes, so the reset deletes the ones
     * the dataset declares, and registers them again.
     */
    fun agents(): List<DemoAgent>

    /**
     * Registers an agent, owned by the account the seed runs as.
     */
    fun registerAgent(spec: AgentSpec): DemoAgent

    /**
     * Every estate of the delivery scorecard on the instance - none when the instance is not
     * licensed for them. An estate names labels, and the server refuses to delete a label an estate
     * selects its projects by, so the reset deletes the estates first.
     */
    fun estates(): List<DemoEstate>

    fun createEstate(spec: EstateSpec)

    /**
     * Creates or replaces a dashboard. Yontrack rejects a second dashboard with the same
     * name unless the UUID matches, so the seed always names a fixed one.
     */
    fun saveDashboard(dashboard: DemoDashboard)
}

/**
 * An API token the seed generated, through which builds are created - see [BuildSpec.token].
 */
interface DemoToken {
    val name: String

    /** Revokes the token: nothing created through it can be changed through it any more. */
    fun revoke()
}

/**
 * A registered agent of the instance.
 *
 * @property identifier `<slug>[agent]`, what the dataset recognises an agent by
 */
interface DemoAgent {
    val identifier: String

    /**
     * Deletes the agent and its tokens. What it did stays signed with its name.
     */
    fun delete()

    /**
     * Generates a token for the agent. It is not revoked at the end of the reset, unlike the tokens of
     * the account the seed runs as: an agent holds no right its owner does not, and fewer - see the
     * agent policy - and the token is what the agents page shows as recently used. Its value is never
     * written anywhere, and the next reset deletes it with its agent.
     */
    fun generateToken(name: String): DemoAgentToken
}

/**
 * A token of a registered agent.
 */
interface DemoAgentToken {

    /**
     * The token, sent with the session headers of [session]: whatever is done through it is signed
     * with the agent as its actor, and links to the session.
     *
     * [DemoToken.revoke] revokes the token of the agent.
     */
    fun inSession(session: AgentSessionSpec): DemoToken
}

interface DemoEstate {
    val name: String
    fun delete()
}

interface DemoDashboardHandle {
    val name: String
    fun delete()
}

/**
 * A label held by the instance, as [DemoDashboardHandle] is a dashboard held by it.
 *
 * @property display `category:name`, or `name` alone for a label with no category — what the
 * dataset names a label by.
 */
interface DemoLabel {
    val display: String
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

    /**
     * Puts [labels] on this project, replacing whatever it carried.
     *
     * Replace-all rather than add-one-at-a-time because that is what the server offers:
     * `setProjectLabels` removes from the project every label the call does not name.
     */
    fun setLabels(labels: List<DemoLabel>)

    /**
     * Recomputes the delivery scorecard of this project, in every set it is in, and waits for it.
     *
     * The readings are otherwise computed by a daily job, so a freshly seeded demo would show every
     * scorecard as not computed until the next night.
     */
    fun recomputeScorecard()
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
    /**
     * @param findings Thresholds of a `security-findings` stamp, `null` for an ordinary one
     * @param tests Whether the stamp is a `tests` one, of the test summary data type
     * @param chml CHML configuration of the stamp, or null for a stamp without any data type
     */
    fun createValidationStamp(
        name: String,
        description: String,
        findings: FindingsThresholdsSpec? = null,
        tests: Boolean = false,
        chml: CHMLSpec? = null,
    )

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

    /**
     * Marks [promotionLevel] as admitting agents - see [PromotionLevelSpec.agentsAdmitted].
     */
    fun admitAgents(promotionLevel: String)

    /**
     * Requires [validationStamps] of an assisted build before it is promoted to [promotionLevel] - see
     * [PromotionLevelSpec.assistedBuildsRequire]. Same ordering constraint as [setAutoPromotion]: the
     * stamps are those of the branch.
     */
    fun setAssistedBuildsRequire(promotionLevel: String, validationStamps: List<String>)

    /**
     * Restricts the runs of [validationStamp] to persons - see [ValidationStampSpec.nonAgentEvidence].
     */
    fun restrictEvidenceToNonAgents(validationStamp: String)

    /**
     * @param token Token the build is created through, and everything done to it through the
     * returned handle - its properties, validations, evidence, promotions and links. The account
     * the seed runs as otherwise.
     */
    fun createBuild(name: String, description: String, creation: LocalDateTime, token: DemoToken? = null): DemoBuild

    /**
     * Deletes a validation stamp of this branch, and its runs with it - see
     * [ValidationStampSpec.deleted].
     */
    fun deleteValidationStamp(name: String)
}

interface DemoBuild {
    val name: String

    /**
     * Sets the release property, which is what a build shows as its display name.
     */
    fun setRelease(release: String)

    /**
     * @param byPerson Whether the promotion is made by the account the seed runs as rather than by
     * whoever created the build - a person promoting what an agent built, to a level which does not
     * admit agents
     */
    fun promote(promotionLevel: String, description: String, at: LocalDateTime, byPerson: Boolean = false)

    /**
     * Records a run of [validationStamp] at [at].
     *
     * The time is passed in for the same reason [promote] takes one: a run stamped with the moment
     * of the reset reads as having happened seconds ago whatever the age of the build it names, and
     * on a delivery map that puts the stamp *after* the promotion it granted (#1718).
     *
     * @param byPerson Whether the run is recorded by the account the seed runs as rather than by
     * whoever created the build - a person, on a stamp taking evidence from non-agents only
     */
    fun validate(
        validationStamp: String,
        status: ValidationStatus,
        description: String,
        at: LocalDateTime,
        byPerson: Boolean = false,
    ): DemoValidationRun

    /**
     * Waits until the server has computed the assisted change of the build, once its commit is set.
     *
     * The server computes it in the background, and the *Assisted builds require* condition fails
     * closed: until the value is there, the build counts as assisted, and a promotion the dataset
     * declares on a gated level could be refused for no reason the dataset describes.
     */
    fun awaitAssistedChange()

    /**
     * Posts the report of a security scan on a `security-findings` stamp, dated at [at] for the
     * same reason as [validate]: the observations, the exposure and the resolution of the findings
     * all follow the time of the run, and a finding "first seen seconds ago" on a build of last
     * week is the wrong history.
     *
     * @param report The report of the scan, in [ScanSpec.format]
     */
    fun scan(scan: ScanSpec, report: JsonNode, at: LocalDateTime)

    /**
     * Records a run of a `tests` stamp with the counts of its tests, dated at [at] for the same
     * reason as [validate]: the test readings of the scorecard window the runs by the build, and a
     * history of runs all stamped at the reset reads as every test having run today.
     */
    fun validateWithTests(run: TestRunSpec, at: LocalDateTime)

    /**
     * Records that this build uses [build].
     */
    fun linkTo(build: DemoBuild)

    /**
     * Records the commit this build was built from, which is where a change log involving
     * it starts or stops.
     */
    fun setCommit(commitId: String)

    /**
     * Rewrites an entry of the trail of this build, through the demonstration tampering endpoint,
     * as the account the seed runs as.
     *
     * @throws IllegalStateException When the entry at [TamperingSpec.seq] is not of
     * [TamperingSpec.type]
     */
    fun tamper(spec: TamperingSpec)
}

/**
 * A validation run the seed has just created.
 */
interface DemoValidationRun {

    /**
     * Attaches a file to the run as its evidence, through the token the build was created with,
     * when it was.
     *
     * @param content Content of the file
     */
    fun attachEvidence(spec: EvidenceSpec, content: ByteArray): DemoEvidence

    /**
     * Gives the run a new status, as the account the seed runs as: a person looking at the run.
     */
    fun changeStatus(change: StatusChangeSpec)
}

/**
 * An evidence the seed has attached.
 */
interface DemoEvidence {

    /** Deletes the evidence, as the account the seed runs as. */
    fun delete()
}

interface DemoEnvironment {
    val name: String
    fun delete()
    fun createSlot(project: DemoProject, qualifier: String, description: String): DemoSlot
}

interface DemoSlot {
    /**
     * Runs a deployment of [build] on this slot as far as [stopAt] says - all the way to done
     * so the environment shows something, or only up to running so that there is a deployment
     * a person can still complete or cancel.
     *
     * @param times When each step happens, `null` for a deployment happening at the reset
     * @param message Why a failed deployment failed, or why a cancelled one was cancelled
     * @param overrides Admission rules overridden once the deployment is created, before it starts
     * @param token Token the deployment is run through - an agent's, in a session - rather than the
     * account the seed runs as
     */
    fun deploy(
        build: DemoBuild,
        stopAt: DeploymentStop,
        times: DeploymentTimes?,
        message: String?,
        overrides: List<RuleOverrideSpec> = emptyList(),
        token: DemoToken? = null,
    )

    /**
     * Lets agents start and finish deployments on this slot - see [SlotSpec.agentsAdmitted].
     */
    fun admitAgents()

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
 * When the steps of a backdated deployment happen: the pipeline is created at [start], starts
 * deploying at [running] and ends - done, failed or cancelled - at [end].
 */
data class DeploymentTimes(
    val start: LocalDateTime,
    val running: LocalDateTime,
    val end: LocalDateTime,
)

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
