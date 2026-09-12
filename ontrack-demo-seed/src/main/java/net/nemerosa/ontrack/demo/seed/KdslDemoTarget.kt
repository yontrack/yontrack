package net.nemerosa.ontrack.demo.seed

import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.kdsl.spec.Branch
import net.nemerosa.ontrack.kdsl.spec.Build
import net.nemerosa.ontrack.kdsl.spec.Ontrack
import net.nemerosa.ontrack.kdsl.spec.Project
import net.nemerosa.ontrack.kdsl.spec.PromotionLevel
import net.nemerosa.ontrack.kdsl.spec.ValidationStamp
import net.nemerosa.ontrack.kdsl.spec.dashboards.DashboardWidget
import net.nemerosa.ontrack.kdsl.spec.dashboards.DashboardWidgetLayout
import net.nemerosa.ontrack.kdsl.spec.dashboards.dashboards
import net.nemerosa.ontrack.kdsl.spec.dashboards.deleteDashboard
import net.nemerosa.ontrack.kdsl.spec.dashboards.saveDashboard
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.DashboardContextUserScope
import net.nemerosa.ontrack.kdsl.spec.extension.environments.Environment
import net.nemerosa.ontrack.kdsl.spec.extension.environments.Slot
import net.nemerosa.ontrack.kdsl.spec.extension.environments.environments
import net.nemerosa.ontrack.kdsl.spec.extension.environments.workflows.addWorkflow
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.SlotPipelineStatus
import net.nemerosa.ontrack.kdsl.spec.extension.general.AutoPromotionProperty
import net.nemerosa.ontrack.kdsl.spec.extension.general.autoPromotion
import net.nemerosa.ontrack.kdsl.spec.extension.general.previousPromotionCondition
import net.nemerosa.ontrack.kdsl.spec.extension.general.promotionDependencies
import net.nemerosa.ontrack.kdsl.spec.extension.notifications.NotificationsMgt
import net.nemerosa.ontrack.kdsl.spec.extension.scm.MockScmRepositoryContext
import net.nemerosa.ontrack.kdsl.spec.extension.scm.mockScmBranchProperty
import net.nemerosa.ontrack.kdsl.spec.extension.scm.mockScmBuildCommitProperty
import net.nemerosa.ontrack.kdsl.spec.extension.scm.mockScmProjectProperty
import net.nemerosa.ontrack.kdsl.spec.setProperty
import net.nemerosa.ontrack.yaml.Yaml
import java.time.LocalDateTime

/**
 * [DemoTarget] against a real Yontrack instance, through the KDSL.
 *
 * Thin on purpose: every decision about what the demo contains lives in [DemoContent] and
 * [DemoSeed], so that both can be tested without a server.
 */
class KdslDemoTarget(private val ontrack: Ontrack) : DemoTarget {

    override fun projects(): List<DemoProject> = ontrack.projects().map { KdslDemoProject(ontrack, it) }

    override fun createProject(name: String, description: String): DemoProject =
        KdslDemoProject(ontrack, ontrack.createProject(name, description))

    override fun environments(): List<DemoEnvironment> =
        ontrack.environments.list().map(::KdslDemoEnvironment)

    override fun createEnvironment(
        name: String,
        order: Int,
        description: String,
        tags: List<String>,
    ): DemoEnvironment = KdslDemoEnvironment(
        ontrack.environments.createEnvironment(
            name = name,
            order = order,
            description = description,
            tags = tags,
        )
    )

    override fun dashboards(): List<DemoDashboardHandle> =
        ontrack.dashboards()
            // The built-in dashboard cannot be deleted, and there is nothing to reset
            // about it: it is the same on every instance.
            .filter { it.userScope != DashboardContextUserScope.BUILT_IN }
            .map { KdslDemoDashboardHandle(ontrack, it.uuid, it.name) }

    /**
     * Registers an issue in a throwaway repository and deletes it again — the real calls the
     * seed makes, so this covers the mutations existing at all (they do not, unless the mock
     * SCM is enabled) and the token being allowed to use them.
     *
     * What the server said is repeated verbatim rather than diagnosed: the first version of
     * this check asserted the property was missing, which sent the reader looking at
     * configuration when the actual answer was a 404 from an ingress that never routed the
     * REST endpoints to the backend.
     */
    override fun checkScmAvailable() {
        val probe = MockScmRepositoryContext(ontrack, PREFLIGHT_REPOSITORY)
        try {
            probe.repositoryIssue(key = "PREFLIGHT-1", message = "Checking the mock SCM is enabled")
            probe.deleteRepository()
        } catch (ex: Exception) {
            // Any failure at all: the mutations missing (the mock SCM is off), the token not
            // being an administrator's, the instance not answering. All of them mean the same
            // thing here — this instance will not take the dataset — and all of them are worth
            // knowing before the reset deletes anything.
            error(
                "The dataset needs the mock SCM for its change log, and this instance would not " +
                        "take it. Check that `ontrack.config.extension.scm.mock.enabled` " +
                        "(`ONTRACK_CONFIG_EXTENSION_SCM_MOCK_ENABLED`) is set and that the token is " +
                        "an administrator's. Nothing was deleted.\n\nThe server said:\n${ex.message}"
            )
        }
    }

    override fun saveDashboard(dashboard: DemoDashboard) {
        ontrack.saveDashboard(
            uuid = dashboard.uuid,
            name = dashboard.name,
            widgets = dashboard.widgets.map {
                DashboardWidget(
                    uuid = it.uuid,
                    key = it.key,
                    config = it.config,
                    layout = DashboardWidgetLayout(
                        x = it.layout.x,
                        y = it.layout.y,
                        w = it.layout.w,
                        h = it.layout.h,
                    ),
                )
            },
        )
    }

    companion object {
        /**
         * Repository the pre-flight check writes to and deletes again, named so that one
         * left behind by an interrupted run is recognisable.
         */
        const val PREFLIGHT_REPOSITORY = "demo-seed-preflight"

        /**
         * The release property carries the version a build shows as its display name.
         */
        const val RELEASE_PROPERTY = "net.nemerosa.ontrack.extension.general.ReleasePropertyType"

        /**
         * The event a promotion level's workflow subscription listens to, so that promoting a
         * build runs the workflow.
         */
        const val NEW_PROMOTION_RUN_EVENT = "new_promotion_run"
    }
}

private class KdslDemoDashboardHandle(
    private val ontrack: Ontrack,
    private val uuid: String,
    override val name: String,
) : DemoDashboardHandle {

    override fun delete() = ontrack.deleteDashboard(uuid)
}

private class KdslDemoProject(
    private val ontrack: Ontrack,
    val project: Project,
) : DemoProject {

    override val name: String get() = project.name

    /**
     * The mock SCM repository this project reads its change logs from, once
     * [configureScm] has pointed it at one.
     */
    var scm: MockScmRepositoryContext? = null
        private set

    override fun delete() = project.delete()

    override fun createBranch(name: String, description: String): DemoBranch =
        KdslDemoBranch(this, project.createBranch(name, description))

    /**
     * Empties the repository before declaring anything in it: the mock SCM holds its
     * repositories on the server, where they outlive the projects the reset deletes, so a
     * second run would otherwise register its commits on top of the first run's and give
     * every one of them a different id.
     */
    override fun configureScm(scm: ScmSpec) {
        val repository = MockScmRepositoryContext(ontrack, scm.repository)
        repository.deleteRepository()
        scm.issues.forEach { issue ->
            repository.repositoryIssue(key = issue.key, message = issue.summary, type = issue.type)
        }
        project.mockScmProjectProperty = scm.repository
        this.scm = repository
    }

    override fun markAsFavourite() = project.favourite()
}

private class KdslDemoBranch(
    private val project: KdslDemoProject,
    val branch: Branch,
) : DemoBranch {

    override val name: String get() = branch.name

    private var scmBranch: String? = null

    private val promotionLevels = mutableMapOf<String, PromotionLevel>()
    private val validationStamps = mutableMapOf<String, ValidationStamp>()

    override fun configureScmBranch(scmBranch: String) {
        branch.mockScmBranchProperty = scmBranch
        this.scmBranch = scmBranch
    }

    override fun markAsFavourite() = branch.favourite()

    override fun registerCommit(message: String): String {
        val repository = requireNotNull(project.scm) {
            "No SCM configured on ${project.name}"
        }
        val scmBranch = requireNotNull(scmBranch) {
            "No SCM branch configured on ${project.name}/$name"
        }
        return repository.repositoryCommit(message = message, branch = scmBranch)
    }

    override fun createPromotionLevel(name: String, description: String, workflow: WorkflowSpec?) {
        val promotionLevel = branch.createPromotionLevel(name, description)
        // Kept as they are created, because the auto promotion property is written with entity IDS
        // and there is no other way back from a name to one without a further query per lookup.
        promotionLevels[name] = promotionLevel
        workflow?.let {
            NotificationsMgt(promotionLevel.connector).subscribe(
                channel = "workflow",
                channelConfig = mapOf("workflow" to Yaml().read(it.yaml).first()),
                events = listOf(KdslDemoTarget.NEW_PROMOTION_RUN_EVENT),
                projectEntity = promotionLevel,
            )
        }
    }

    override fun createValidationStamp(name: String, description: String) {
        validationStamps[name] = branch.createValidationStamp(name, description)
    }

    override fun setAutoPromotion(promotionLevel: String, spec: AutoPromotionSpec) {
        // `validate` has already ruled out a name the branch does not declare, so a miss here is a
        // fault in the seed's own ordering rather than in the dataset - hence `getValue`.
        promotionLevels.getValue(promotionLevel).autoPromotion = AutoPromotionProperty(
            validationStamps = spec.validationStamps.map { validationStamps.getValue(it).id },
            promotionLevels = spec.promotionLevels.map { promotionLevels.getValue(it).id },
            include = spec.include,
            exclude = spec.exclude,
        )
    }

    override fun setPromotionDependencies(promotionLevel: String, dependencies: List<String>) {
        promotionLevels.getValue(promotionLevel).promotionDependencies = dependencies
    }

    override fun setPreviousPromotionCondition(promotionLevel: String, required: Boolean) {
        promotionLevels.getValue(promotionLevel).previousPromotionCondition = required
    }

    override fun createBuild(name: String, description: String, creation: LocalDateTime): DemoBuild {
        // Yontrack stamps a build with the time it is created, so the demo's history has
        // to be backdated in a second call.
        val build = branch.createBuild(name, description).updateCreationTime(creation)
        return KdslDemoBuild(build)
    }
}

private class KdslDemoBuild(val build: Build) : DemoBuild {

    override val name: String get() = build.name

    override fun setRelease(release: String) {
        build.setProperty(KdslDemoTarget.RELEASE_PROPERTY, mapOf("name" to release))
    }

    override fun promote(promotionLevel: String, description: String, at: LocalDateTime) {
        build.promote(promotionLevel, description, at)
    }

    override fun validate(validationStamp: String, status: ValidationStatus, description: String, at: LocalDateTime) {
        build.validate(validationStamp, status.name, description, at)
    }

    override fun linkTo(build: DemoBuild) {
        this.build.linkTo((build as KdslDemoBuild).build)
    }

    override fun setCommit(commitId: String) {
        build.mockScmBuildCommitProperty = commitId
    }
}

private class KdslDemoEnvironment(val environment: Environment) : DemoEnvironment {

    override val name: String get() = environment.name

    override fun delete() = environment.delete()

    override fun createSlot(project: DemoProject, description: String): DemoSlot =
        KdslDemoSlot(
            environment.createSlot(
                project = (project as KdslDemoProject).project,
                description = description,
            )
        )
}

private class KdslDemoSlot(val slot: Slot) : DemoSlot {

    /**
     * Runs the pipeline as far as [stopAt] says: all the way through, so the slot shows a
     * deployed build rather than one waiting for something to happen to it - or only up to
     * [DeploymentStop.RUNNING], which is the one state in which the deployment can still be
     * completed or cancelled by a person.
     */
    override fun deploy(build: DemoBuild, stopAt: DeploymentStop) {
        val pipeline = slot.createPipeline((build as KdslDemoBuild).build).startDeploying()
        if (stopAt == DeploymentStop.DONE) {
            pipeline.finishDeployment()
        }
    }

    override fun addAdmissionRule(spec: SlotAdmissionRuleSpec) {
        slot.addAdmissionRule(
            ruleId = spec.ruleId,
            ruleConfig = spec.config.asJson(),
            name = spec.name,
        )
    }

    override fun addWorkflow(spec: SlotWorkflowSpec) {
        slot.addWorkflow(
            trigger = SlotPipelineStatus.valueOf(spec.trigger),
            workflowYaml = spec.yaml,
        )
    }
}
