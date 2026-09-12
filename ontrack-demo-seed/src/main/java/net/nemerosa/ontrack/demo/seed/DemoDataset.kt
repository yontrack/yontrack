package net.nemerosa.ontrack.demo.seed

import java.time.LocalDateTime

/**
 * Everything the demo shows, as plain data.
 *
 * Keeping the dataset declarative means the seed program itself has no content in it, and
 * a feature that wants a demo adds entries here rather than steps to a procedure.
 */
data class DemoDataset(
    val projects: List<ProjectSpec>,
    val environments: List<EnvironmentSpec> = emptyList(),
    /**
     * Deployments, run in the order they are declared, after every slot exists.
     *
     * They sit here rather than on the slot because their order is a fact about the demo as
     * a whole and not about one slot: an `environment` admission rule asks what is deployed
     * in another slot *right now*, so "staging, then production, then staging again" is a
     * sequence no per-slot list can express.
     */
    val deployments: List<DeploymentSpec> = emptyList(),
    val dashboard: DemoDashboard? = null,
)

/**
 * @property scm The SCM the project's change logs are read from. Only the mock SCM, whose
 * commits the seed writes itself: a real repository would trade a self-contained reset for
 * one depending on credentials and network egress.
 */
/**
 * @property favourite Whether the seeding account marks this project as one of its
 * favourites. A favourite is per user rather than per project, so this says what the demo
 * user sees on the mobile home screen - which is their favourites and nothing else, and so
 * would be blank on a demo that curated none (#1720).
 */
data class ProjectSpec(
    val name: String,
    val description: String,
    val branches: List<BranchSpec>,
    val scm: ScmSpec? = null,
    val favourite: Boolean = false,
)

/**
 * A mock SCM repository behind a project.
 *
 * @property repository Name of the repository, unique on the instance. The seed empties it
 * before it registers anything: the mock SCM holds its repositories on the server, where
 * they outlive the projects the reset deletes.
 * @property issues Issues of the repository's issue service. Registered before any commit,
 * because the mock SCM links a commit to an issue as the commit comes in.
 */
data class ScmSpec(
    val repository: String,
    val issues: List<IssueSpec> = emptyList(),
)

/**
 * @property key Issue key, of the `ABC-123` shape the mock issue service recognises in a
 * commit message.
 * @property type What a change log groups its issues by.
 */
data class IssueSpec(
    val key: String,
    val summary: String,
    val type: String? = null,
)

/**
 * @property scmBranch The branch of the project's SCM repository this branch follows.
 * @property favourite Whether the seeding account marks this branch as one of its
 * favourites, as [ProjectSpec.favourite] does for a project.
 */
data class BranchSpec(
    val name: String,
    val description: String,
    val scmBranch: String? = null,
    val favourite: Boolean = false,
    val promotionLevels: List<PromotionLevelSpec> = emptyList(),
    val validationStamps: List<ValidationStampSpec> = emptyList(),
    val builds: List<BuildSpec> = emptyList(),
)

/**
 * @property autoPromotion What grants this promotion by itself, when anything does. It is what the
 * delivery map reads to draw its *unlocks* edges, and the only thing that puts a validation stamp on
 * the map at all.
 * @property dependsOn Promotion levels of the same branch this one cannot be reached before, named
 * as the `PromotionDependenciesPropertyType` property names them. It constrains; it does not act,
 * which is why it is a separate field from [autoPromotion] rather than a corner of it.
 * @property requiresPreviousPromotion Whether this promotion cannot be granted before the level
 * immediately below it in the branch's order. It names nothing, because the condition names nothing:
 * the `PreviousPromotionConditionPropertyType` property is a bare boolean and the server reads the
 * predecessor off the branch's own order. Set on the promotion level here rather than on the branch
 * or the project, which is where the demo would otherwise put a chain on every ladder it has.
 */
data class PromotionLevelSpec(
    val name: String,
    val description: String,
    val workflow: WorkflowSpec? = null,
    val autoPromotion: AutoPromotionSpec? = null,
    val dependsOn: List<String> = emptyList(),
    val requiresPreviousPromotion: Boolean = false,
)

/**
 * Auto promotion of one promotion level: the build reaching everything named here is promoted, with
 * nobody having to do it.
 *
 * Everything is named rather than referenced, as the dataset names everything else. The property
 * itself is written with entity *ids*, and resolving the names is `KdslDemoTarget`'s job.
 *
 * @property validationStamps Stamps named explicitly, each drawn as its own checkpoint on the
 * delivery map.
 * @property promotionLevels Promotion levels which grant this one.
 * @property include Regular expression selecting stamps by name, whole-string as the server matches
 * it. Stamps selected this way collapse into one *aggregate* checkpoint labelled with the pattern.
 * @property exclude Regular expression removing stamps from what [include] selected. No dataset uses
 * it yet, and it is here rather than left out because `autoPromotionSelectsStamp` has to repeat the
 * server's selection rule in full: a reading which ignored `exclude` would answer wrongly the first
 * time anything set it, and would do so silently.
 */
data class AutoPromotionSpec(
    val validationStamps: List<String> = emptyList(),
    val promotionLevels: List<String> = emptyList(),
    val include: String = "",
    val exclude: String = "",
)

/**
 * A workflow run on every promotion run created on the promotion level it is attached to.
 *
 * @property yaml The workflow definition, in the format `ontrack.workflows.saveYamlWorkflow` accepts.
 */
data class WorkflowSpec(val yaml: String)

data class ValidationStampSpec(
    val name: String,
    val description: String,
)

/**
 * @property name Build name — the opaque run identity, as in a real pipeline.
 * @property release Version carried by the build, set as its release property, which is
 * what Yontrack shows as the build display name.
 * @property links Builds this build uses, resolved after every project exists.
 * @property commits Commit messages, oldest first, registered on the branch's SCM branch
 * when the build is created. The last one is the commit the build was built from; the ones
 * before it are the work that went into it, and are what the change log with the previous
 * build shows.
 */
data class BuildSpec(
    val name: String,
    val description: String,
    val creation: BuildCreation,
    val release: String? = null,
    val promotionLevels: List<String> = emptyList(),
    val validations: List<ValidationSpec> = emptyList(),
    val links: List<BuildRef> = emptyList(),
    val commits: List<String> = emptyList(),
)

/**
 * When a build was created.
 */
sealed interface BuildCreation {

    fun resolve(now: LocalDateTime): LocalDateTime

    /**
     * Relative to the run, so that the curated dataset reads as recent work however long
     * ago it was written.
     *
     * Never in the future, which `DaysAgo(0)` otherwise is for every reset before its [hour]: the
     * hour is a time of day rather than an offset, and a demo built at 09:00 by a reset which ran
     * at 08:30 reads as a defect in Yontrack rather than in the dataset.
     */
    data class DaysAgo(val days: Long, val hour: Int = 9, val minute: Int = 0) : BuildCreation {
        override fun resolve(now: LocalDateTime): LocalDateTime =
            minOf(
                now.minusDays(days).withHour(hour).withMinute(minute).withSecond(0).withNano(0),
                now,
            )
    }

    /**
     * Relative to the run in HOURS, for the newest build of a branch.
     *
     * [DaysAgo] pins a time of day, which is a time of day in the zone the reset runs in and not in
     * the reader's - so `DaysAgo(0)` is an hour or two into the future for half the world, and the
     * promotion rungs stacked on top of it more so. An offset in hours means the same thing to
     * everyone, and leaves the newest build room for its own promotions.
     */
    data class HoursAgo(val hours: Long) : BuildCreation {
        override fun resolve(now: LocalDateTime): LocalDateTime = now.minusHours(hours)
    }

    /**
     * An absolute instant, for a build mirroring something that really happened at a time
     * of its own — a commit.
     */
    data class At(val time: LocalDateTime) : BuildCreation {
        override fun resolve(now: LocalDateTime): LocalDateTime = time
    }
}

data class ValidationSpec(
    val validationStamp: String,
    val status: ValidationStatus,
    val description: String = "",
)

/**
 * Points at a build of another project, by name.
 */
data class BuildRef(
    val project: String,
    val branch: String,
    val build: String,
)

data class EnvironmentSpec(
    val name: String,
    val order: Int,
    val description: String,
    val tags: List<String> = emptyList(),
    val slots: List<SlotSpec> = emptyList(),
)

/**
 * @property admissionRules What has to be true of a build before it can be deployed here.
 * They are also what the delivery map reads to join a slot to the rest of the map: without
 * one, a slot is drawn unconnected.
 */
data class SlotSpec(
    val project: String,
    val description: String,
    val admissionRules: List<SlotAdmissionRuleSpec> = emptyList(),
    val workflows: List<SlotWorkflowSpec> = emptyList(),
)

/**
 * A workflow run at one of the three moments of a deployment on a slot.
 *
 * The trigger is what the delivery map reads to decide which way the slot's line runs:
 * `CANDIDATE` and `RUNNING` are hard gates and are drawn as *requires* into the slot, while
 * `DONE` runs once the deployment is over and is drawn as *emits* out of it.
 *
 * @property trigger `CANDIDATE`, `RUNNING` or `DONE`
 * @property yaml The workflow definition, in the format `ontrack.workflows.saveYamlWorkflow` accepts.
 */
data class SlotWorkflowSpec(
    val trigger: String,
    val yaml: String,
)

/**
 * One deployment of a build on a slot, run as far as [stopAt] says.
 *
 * The slot is named by its environment and by the project of the build, which is enough
 * while the demo gives a project at most one slot per environment.
 *
 * @property stopAt How far the deployment is taken. [DeploymentStop.DONE] by default, which
 * is what makes a slot show a deployed build rather than one waiting for something to happen
 * to it.
 */
data class DeploymentSpec(
    val environment: String,
    val build: BuildRef,
    val stopAt: DeploymentStop = DeploymentStop.DONE,
)

/**
 * How far [DemoSlot.deploy] takes a deployment.
 *
 * The demo needs both: a slot is only *holding* a build once its deployment is [DONE], and a
 * deployment is only completable or cancellable while it is [RUNNING]. A dataset with nothing
 * but finished deployments cannot demonstrate the mobile UI's Complete and Cancel at all,
 * because there is no deployment in a state where either is possible (#1736).
 */
enum class DeploymentStop {
    /**
     * Started and left running: the deployment is on its way, and completing or cancelling it
     * is something a person can still do.
     */
    RUNNING,

    /** Run all the way through, so the slot holds the build. */
    DONE,
}

/**
 * One configured admission rule of a slot.
 *
 * @property name Unique within the slot; letters, digits and dashes only, starting with a
 * letter.
 * @property ruleId ID of the rule as the backend declares it - `promotion`, `environment`,
 * `branchPattern`.
 * @property config Configuration of the rule, whose shape is that rule's own.
 */
data class SlotAdmissionRuleSpec(
    val name: String,
    val ruleId: String,
    val config: Map<String, Any>,
)

/**
 * Validation statuses the dataset uses. Yontrack knows more — `DEFECTIVE`, `INTERRUPTED`
 * and the rest; adding one here is how the dataset gets to use it.
 */
enum class ValidationStatus {
    PASSED,
    FAILED,
    WARNING,
}
