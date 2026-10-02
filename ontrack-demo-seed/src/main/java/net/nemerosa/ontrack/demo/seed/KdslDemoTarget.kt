package net.nemerosa.ontrack.demo.seed

import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.kdsl.spec.Branch
import net.nemerosa.ontrack.kdsl.spec.Build
import net.nemerosa.ontrack.kdsl.spec.Label
import net.nemerosa.ontrack.kdsl.spec.Ontrack
import net.nemerosa.ontrack.kdsl.spec.Project
import net.nemerosa.ontrack.kdsl.spec.PromotionLevel
import net.nemerosa.ontrack.kdsl.spec.ValidationStamp
import net.nemerosa.ontrack.kdsl.spec.createLabel
import net.nemerosa.ontrack.kdsl.spec.deleteLabel
import net.nemerosa.ontrack.kdsl.spec.labels
import net.nemerosa.ontrack.kdsl.spec.setLabels
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
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.FindingKind
import net.nemerosa.ontrack.kdsl.spec.extension.findings.FindingsReportFormat
import net.nemerosa.ontrack.kdsl.spec.extension.findings.createFindingsValidationStamp
import net.nemerosa.ontrack.kdsl.spec.extension.findings.validateWithFindings
import net.nemerosa.ontrack.kdsl.spec.extension.general.AutoPromotionProperty
import net.nemerosa.ontrack.kdsl.spec.extension.general.TestSummary
import net.nemerosa.ontrack.kdsl.spec.extension.general.createTestSummaryValidationStamp
import net.nemerosa.ontrack.kdsl.spec.extension.general.validateWithTestSummary
import net.nemerosa.ontrack.kdsl.spec.extension.general.autoPromotion
import net.nemerosa.ontrack.kdsl.spec.extension.general.previousPromotionCondition
import net.nemerosa.ontrack.kdsl.spec.extension.general.promotionDependencies
import net.nemerosa.ontrack.kdsl.spec.extension.license.isLicensedFeatureEnabled
import net.nemerosa.ontrack.kdsl.spec.extension.notifications.NotificationsMgt
import net.nemerosa.ontrack.kdsl.spec.extension.scm.MockScmRepositoryContext
import net.nemerosa.ontrack.kdsl.spec.extension.scm.mockScmBranchProperty
import net.nemerosa.ontrack.kdsl.spec.extension.scm.mockScmBuildCommitProperty
import net.nemerosa.ontrack.kdsl.spec.extension.scm.mockScmProjectProperty
import net.nemerosa.ontrack.kdsl.spec.extension.scorecard.Estate
import net.nemerosa.ontrack.kdsl.spec.extension.scorecard.EstateMarker
import net.nemerosa.ontrack.kdsl.spec.extension.scorecard.EstateReadingConfig
import net.nemerosa.ontrack.kdsl.spec.extension.scorecard.EstateSecurity
import net.nemerosa.ontrack.kdsl.spec.extension.scorecard.estates
import net.nemerosa.ontrack.kdsl.spec.extension.scorecard.recomputeScorecardAndWait
import net.nemerosa.ontrack.kdsl.spec.setProperty
import net.nemerosa.ontrack.yaml.Yaml
import tools.jackson.databind.JsonNode
import java.time.Duration
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

    override fun labels(): List<DemoLabel> =
        ontrack.labels().map { KdslDemoLabel(ontrack, it) }

    override fun createLabel(spec: LabelSpec): DemoLabel =
        KdslDemoLabel(
            ontrack,
            ontrack.createLabel(
                name = spec.name,
                category = spec.category,
                description = spec.description,
                color = spec.color,
            )
        )

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

    /**
     * Asks the licence rather than posting a probe: a SARIF scan needs a build to validate, and
     * the only builds on the instance at this point are the ones the reset is about to delete.
     */
    override fun checkNativeFindingsFormats() {
        val enabled = try {
            ontrack.isLicensedFeatureEnabled(FEATURE_NATIVE_FORMATS)
        } catch (ex: Exception) {
            error(
                "The dataset posts SARIF scans, and the licence of this instance could not be read " +
                        "to check it allows them. Nothing was deleted.\n\nThe server said:\n${ex.message}"
            )
        }
        check(enabled) {
            "The dataset posts SARIF scans, which need the licensed feature \"Native scanner formats\" " +
                    "($FEATURE_NATIVE_FORMATS), and the licence of this instance does not enable it. " +
                    "Nothing was deleted."
        }
    }

    /**
     * Asks the licence, as [checkNativeFindingsFormats] does: both features, because the estates
     * need the scorecard one and the readings of an estate read up to an environment need the
     * environments one - without it they all read "not licensed".
     */
    override fun checkScorecardLicensed() {
        listOf(
            FEATURE_SCORECARD to "Delivery scorecard",
            FEATURE_ENVIRONMENTS to "Environments",
        ).forEach { (feature, name) ->
            val enabled = try {
                ontrack.isLicensedFeatureEnabled(feature)
            } catch (ex: Exception) {
                error(
                    "The dataset creates estates of the delivery scorecard, and the licence of this " +
                            "instance could not be read to check it allows them. Nothing was deleted." +
                            "\n\nThe server said:\n${ex.message}"
                )
            }
            check(enabled) {
                "The dataset creates estates of the delivery scorecard, which need the licensed feature " +
                        "\"$name\" ($feature), and the licence of this instance does not enable it. " +
                        "Nothing was deleted."
            }
        }
    }

    /**
     * None on an instance whose licence does not allow the estates: the listing itself is refused
     * there, and the reset must still be able to run against such an instance with a dataset
     * which declares none.
     */
    override fun estates(): List<DemoEstate> =
        if (ontrack.isLicensedFeatureEnabled(FEATURE_SCORECARD)) {
            ontrack.estates.list().map(::KdslDemoEstate)
        } else {
            emptyList()
        }

    override fun createEstate(spec: EstateSpec) {
        ontrack.estates.create(
            name = spec.name,
            labels = spec.labels,
            description = spec.description,
            marker = when (val marker = spec.marker) {
                null -> null
                is EstateMarkerSpec.Promotion -> EstateMarker.Promotion(marker.level)
                is EstateMarkerSpec.Environment -> EstateMarker.Environment(marker.environment, marker.qualifier)
            },
            readings = spec.readings.map {
                EstateReadingConfig(key = it.key, windowDays = it.windowDays, target = it.target)
            },
            security = EstateSecurity(
                expectedKinds = spec.security.expectedKinds.map { FindingKind.safeValueOf(it.name) },
                freshnessDays = spec.security.freshnessDays,
                criticalTargetDays = spec.security.criticalTargetDays,
                highTargetDays = spec.security.highTargetDays,
            ),
        )
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
         * The CHML validation data type, by its FQCN - `createValidationStampById` does not read the
         * `chml` alias the CI configuration accepts.
         */
        const val CHML_DATA_TYPE = "net.nemerosa.ontrack.extension.general.validation.CHMLValidationDataType"

        /**
         * The event a promotion level's workflow subscription listens to, so that promoting a
         * build runs the workflow.
         */
        const val NEW_PROMOTION_RUN_EVENT = "new_promotion_run"

        /**
         * The licensed feature the native formats of the security scans - SARIF, Trivy JSON - need.
         */
        const val FEATURE_NATIVE_FORMATS = "extension.findings.native-formats"

        /**
         * The licensed feature the estates of the delivery scorecard need.
         */
        const val FEATURE_SCORECARD = "extension.scorecard"

        /**
         * The licensed feature the environments need - and the readings of an estate read up to one.
         */
        const val FEATURE_ENVIRONMENTS = "extension.environments"
    }
}

private class KdslDemoEstate(val estate: Estate) : DemoEstate {

    override val name: String get() = estate.name

    override fun delete() = estate.delete()
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

    override fun setLabels(labels: List<DemoLabel>) {
        project.setLabels(labels.map { (it as KdslDemoLabel).label.id })
    }

    override fun recomputeScorecard() {
        project.recomputeScorecardAndWait(timeout = Duration.ofMinutes(2))
    }
}

private class KdslDemoLabel(
    private val ontrack: Ontrack,
    val label: Label,
) : DemoLabel {

    override val display: String get() = label.display

    override fun delete() = ontrack.deleteLabel(label.id)
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
                name = "workflow",
                channel = "workflow",
                channelConfig = mapOf("workflow" to Yaml().read(it.yaml).first()),
                events = listOf(KdslDemoTarget.NEW_PROMOTION_RUN_EVENT),
                projectEntity = promotionLevel,
            )
        }
    }

    override fun createValidationStamp(
        name: String,
        description: String,
        findings: FindingsThresholdsSpec?,
        tests: Boolean,
        chml: CHMLSpec?,
    ) {
        validationStamps[name] = if (tests) {
            branch.createTestSummaryValidationStamp(name = name, description = description)
        } else if (findings != null) {
            branch.createFindingsValidationStamp(
                name = name,
                description = description,
                warningLevel = findings.warningLevel.name,
                warningValue = findings.warningValue,
                failedLevel = findings.failedLevel.name,
                failedValue = findings.failedValue,
            )
        } else {
            branch.createValidationStamp(
                name = name,
                description = description,
                dataType = chml?.let { KdslDemoTarget.CHML_DATA_TYPE },
                // The form shape, which is what `createValidationStampById` parses
                dataTypeConfig = chml?.let {
                    mapOf(
                        "failedLevel" to it.failedLevel.name,
                        "failedValue" to it.failedValue,
                        "warningLevel" to it.warningLevel.name,
                        "warningValue" to it.warningValue,
                        "warningPassesAutoPromotion" to it.warningPassesAutoPromotion,
                    )
                },
            )
        }
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

    override fun scan(scan: ScanSpec, report: JsonNode, at: LocalDateTime) {
        build.validateWithFindings(
            validation = scan.validationStamp,
            format = FindingsReportFormat.valueOf(scan.format.name),
            report = report,
            kind = FindingKind.safeValueOf(scan.kind.name),
            scanner = scan.scanner,
            description = scan.description.ifBlank { null },
            dateTime = at,
        )
    }

    override fun validateWithTests(run: TestRunSpec, at: LocalDateTime) {
        // Through the one mutation taking data AND a date: the typed `validateBuildByIdWithTests`
        // stamps the run with the moment of the call
        build.validateWithTestSummary(
            validation = run.validationStamp,
            description = run.description,
            testSummary = TestSummary(passed = run.passed, skipped = run.skipped, failed = run.failed),
            dateTime = at,
        )
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

    override fun createSlot(project: DemoProject, qualifier: String, description: String): DemoSlot =
        KdslDemoSlot(
            environment.createSlot(
                project = (project as KdslDemoProject).project,
                qualifier = qualifier,
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
    override fun deploy(build: DemoBuild, stopAt: DeploymentStop, times: DeploymentTimes?, message: String?) {
        // Backdated through the dates the pipeline mutations take, each step at its own time: the
        // scorecard's readings of an environment are the durations between them
        val pipeline = slot.createPipeline((build as KdslDemoBuild).build, dateTime = times?.start)
        when (stopAt) {
            // A candidate is left exactly where it was created: what the slot's rules refuse is the
            // whole content of that deployment, and starting it would either fail or hide it.
            DeploymentStop.CANDIDATE -> Unit
            // Cancelled before it started, as a person does with a candidate nobody wants any more
            DeploymentStop.CANCELLED -> pipeline.cancel(
                reason = message ?: "Cancelled.",
                dateTime = times?.end,
            )

            DeploymentStop.RUNNING -> pipeline.startDeploying(dateTime = times?.running)
            DeploymentStop.DONE -> pipeline.startDeploying(dateTime = times?.running)
                .finishDeployment(dateTime = times?.end)

            DeploymentStop.FAILED -> pipeline.startDeploying(dateTime = times?.running)
                .fail(message = message, dateTime = times?.end)
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
