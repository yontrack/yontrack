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
    /**
     * Labels, which are instance-level and not owned by any project: several projects carry
     * the same label, which is the whole point of one. They are declared here and named by
     * [ProjectSpec.labels], the way a slot names its project.
     */
    val labels: List<LabelSpec> = emptyList(),
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
/**
 * @property labels The labels this project carries, named by their [LabelSpec.display] the way
 * an auto promotion names a validation stamp. A label is instance-level, so it is declared once
 * in [DemoDataset.labels] and named here as many times as projects carry it.
 */
data class ProjectSpec(
    val name: String,
    val description: String,
    val branches: List<BranchSpec>,
    val scm: ScmSpec? = null,
    val favourite: Boolean = false,
    val labels: List<String> = emptyList(),
)

/**
 * A project label: a coloured tag put on any number of projects.
 *
 * @property category Category of the label, optional. Two categories read better than one on the
 * demo: a chip shows `category:name` and the reader can see what a category is for.
 * @property name Name of the label, unique within its category.
 * @property description Shown as the chip's tooltip and on the label page.
 * @property color Background colour of the chip, in the `#RRGGBB` format. Yontrack computes the
 * foreground colour from it.
 */
data class LabelSpec(
    val category: String?,
    val name: String,
    val description: String,
    val color: String,
) {
    /**
     * How Yontrack displays the label, and how the dataset names it: `category:name`, or just
     * `name` for a label with no category.
     */
    val display: String
        get() = category?.let { "$it:$name" } ?: name
}

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

/**
 * @property findings Thresholds of a `security-findings` stamp, whose runs are posted as the
 * reports of security scans - see [BuildSpec.scans] - rather than with a status: the server
 * computes the status from the findings. `null` for an ordinary stamp.
 */
data class ValidationStampSpec(
    val name: String,
    val description: String,
    val findings: FindingsThresholdsSpec? = null,
)

/**
 * Thresholds of a `security-findings` stamp, which are CHML's: a warning and a failure, each a
 * count of findings at a level or above. Accepted findings never count, and neither does
 * [FindingSeverity.UNKNOWN].
 */
data class FindingsThresholdsSpec(
    val warningLevel: FindingSeverity = FindingSeverity.HIGH,
    val warningValue: Int = 1,
    val failedLevel: FindingSeverity = FindingSeverity.CRITICAL,
    val failedValue: Int = 1,
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
 * @property scans Security scans of the build, each posted through `validateBuildWithFindings`
 * as the report of the scan, on a `security-findings` stamp. They take their rungs on the build's
 * ladder after its [validations], and before its promotions.
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
    val scans: List<ScanSpec> = emptyList(),
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
 * A security scan of a build, posted as a report through the API like any CI would post one.
 *
 * The dataset says what the scan found, not how a scanner would write it: [format] picks the
 * report [FindingsReports] renders from [findings], so the same findings can be sent in the
 * neutral format or in SARIF.
 *
 * @property validationStamp A `security-findings` stamp of the branch
 * @property format Format of the report
 * @property kind What was scanned
 * @property scanner Name of the scanner, as the findings are keyed by it
 * @property findings What the scan reported. A finding absent from the scan is one it no longer
 * reports - which is how a finding gets resolved.
 */
data class ScanSpec(
    val validationStamp: String,
    val format: ScanFormat,
    val kind: ScanKind,
    val scanner: String,
    val findings: List<FindingSpec>,
    val description: String = "",
)

/**
 * Formats of report the dataset renders.
 */
enum class ScanFormat {
    /** The neutral format of Yontrack, which needs no licence. */
    FINDINGS,

    /**
     * SARIF 2.1, a native format: the instance needs the licensed feature "Native scanner
     * formats". SARIF has no field for the expiry of an acceptance.
     */
    SARIF,
}

/**
 * Kinds of scan, `FindingKind` on the server side.
 */
enum class ScanKind {
    IMAGE,
    CODE,
    SECRETS,
    DAST,
    DEPENDENCIES,
    OTHER,
}

/**
 * Severities of a finding, `FindingSeverity` on the server side, the most severe first.
 */
enum class FindingSeverity {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW,
    UNKNOWN,
}

/**
 * One finding as a scan reports it.
 *
 * @property externalId Identifier given by the scanner: a CVE, a rule ID. What the search finds.
 * @property location Where the finding is: a purl for a dependency, a path for code. Never a
 * version - the one reported goes in [installedVersion].
 * @property acceptance Decision that the finding is tolerated, as the scanner-side file records it
 */
data class FindingSpec(
    val externalId: String,
    val location: String,
    val severity: FindingSeverity,
    val title: String,
    val url: String? = null,
    val installedVersion: String? = null,
    val fixedVersion: String? = null,
    val acceptance: AcceptanceSpec? = null,
)

/**
 * An acceptance of a finding, recorded outside Yontrack and read by it.
 *
 * @property source Where the decision is recorded
 * @property expiresInDays Days from the reset the acceptance holds for, or `null` for one without
 * expiry. Relative to the run, like a build's creation time, so that the demo never shows an
 * acceptance which lapsed only because the dataset got old - and an acceptance cannot be told to
 * have lapsed from the dataset alone.
 */
data class AcceptanceSpec(
    val statement: String,
    val source: String,
    val expiresInDays: Long? = null,
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
 * @property qualifier Which of a project's parallel deployments this slot is. The empty string
 * is the default one and is what a project with a single deployment per environment uses; a
 * named one - `canary` - is a second slot of the same project in the same environment, with a
 * graph, a history and a set of rules of its own. It is what makes the matrix nest rows.
 */
data class SlotSpec(
    val project: String,
    val description: String,
    val qualifier: String = "",
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
    val qualifier: String = "",
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
     * Created and left as a candidate: the deployment has not started, and the slot's admission
     * rules are what say why. This is the only stop under which the dataset may name a build the
     * rules refuse - that is the point of it (#1792), and both [validate] and the in-memory target
     * relax their checks accordingly.
     */
    CANDIDATE,

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
