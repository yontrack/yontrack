package net.nemerosa.ontrack.demo.seed

import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime

/**
 * Resets the demo and recreates its dataset, through the Yontrack API.
 *
 * The demo's state is a function of the build, not an accumulation: the program deletes
 * every project and every environment, then recreates the dataset from scratch. Destructive
 * by design, and idempotent because of it — running it twice in a row leaves the same demo.
 *
 * Settings are covered by CasC and users live in Keycloak, so projects, environments, labels,
 * estates, the demo dashboard and the dataset's agents are the only things this has to reset.
 *
 * @param clock Read once per run, so every build creation time in one run shares a
 * reference. Injected so that a test can pin it and compare two runs.
 */
class DemoSeed(
    private val target: DemoTarget,
    private val clock: Clock = Clock.systemUTC(),
    private val log: (String) -> Unit = ::println,
) {

    fun run(fullDataset: DemoDataset) {
        // Before anything is deleted: a dataset the server would reject must not cost the
        // demo its current content.
        fullDataset.validate()
        // Read-only as well, and before the reset for the same reason: a part of the dataset needing
        // something the instance does not offer is left out, rather than failing half-way through
        val capabilities = Capabilities()
        val dataset = withoutUnavailableProjects(fullDataset, capabilities)
        val evidence = dataset.declaresEvidence() && capabilities.available(DemoCapability.EVIDENCE) { reason ->
            log("Leaving out the evidence: ${DemoCapability.EVIDENCE.display} is not available on this instance - $reason")
        }
        // Same reason, one step further: a dataset can be valid and still ask the instance
        // for something it does not run.
        if (dataset.projects.any { it.scm != null }) {
            target.checkScmAvailable()
        }
        // And for the same reason: a SARIF scan needs a licensed feature the instance may not have
        if (dataset.projects.any { project ->
                project.branches.any { branch ->
                    branch.builds.any { build -> build.scans.any { it.format == ScanFormat.SARIF } }
                }
            }
        ) {
            target.checkNativeFindingsFormats()
        }
        // And again: an estate needs a licensed feature the instance may not have
        if (dataset.estates.isNotEmpty()) {
            target.checkScorecardLicensed()
        }
        val now = LocalDateTime.now(clock)
        reset(dataset)
        create(dataset, now, evidence)
    }

    /**
     * What the instance offers, each asked once.
     */
    private inner class Capabilities {
        private val reasons = mutableMapOf<DemoCapability, String?>()

        fun reason(capability: DemoCapability): String? {
            if (capability !in reasons) {
                reasons[capability] = target.unavailable(capability)
            }
            return reasons[capability]
        }

        /**
         * Whether [capability] is available, calling [onUnavailable] with the reason when it is not.
         */
        fun available(capability: DemoCapability, onUnavailable: (String) -> Unit): Boolean {
            val reason = reason(capability)
            return if (reason == null) {
                true
            } else {
                onUnavailable(reason)
                false
            }
        }
    }

    /**
     * The dataset without the projects requiring what the instance does not offer, nor their slots
     * and deployments - `validate` has already ruled out a link from another project to one of them.
     */
    private fun withoutUnavailableProjects(dataset: DemoDataset, capabilities: Capabilities): DemoDataset {
        val left = dataset.projects.filter { project ->
            project.requires.all { capability ->
                capabilities.available(capability) { reason ->
                    log("Leaving out the project ${project.name}: ${capability.display} is not available on this instance - $reason")
                }
            }
        }.map { it.name }.toSet()
        if (left.size == dataset.projects.size) return dataset
        return dataset.copy(
            projects = dataset.projects.filter { it.name in left },
            environments = dataset.environments.map { environment ->
                environment.copy(slots = environment.slots.filter { it.project in left })
            },
            deployments = dataset.deployments.filter { it.build.project in left },
        )
    }

    private fun DemoDataset.declaresEvidence(): Boolean =
        projects.any { project ->
            project.branches.any { branch ->
                branch.builds.any { build -> build.validations.any { it.evidence.isNotEmpty() } }
            }
        }

    /**
     * Environments before projects: a slot belongs to both, and deleting the environment
     * takes its slots with it whatever the project deletion happens to cascade.
     *
     * Dashboards go too. They are not projects, but a dashboard a visitor saved — or one an
     * older seed left under a name this one no longer uses — would otherwise outlive every
     * reset, and the demo's state is meant to be a function of the build.
     */
    private fun reset(dataset: DemoDataset) {
        // First: an estate selects its projects by labels, and the server refuses to delete a label an
        // estate still names. Deleting an estate deletes its readings, and nothing else.
        target.estates().forEach { estate ->
            log("Deleting estate ${estate.name}")
            estate.delete()
        }
        target.environments().forEach { environment ->
            log("Deleting environment ${environment.name}")
            environment.delete()
        }
        target.projects().forEach { project ->
            log("Deleting project ${project.name}")
            project.delete()
        }
        target.dashboards().forEach { dashboard ->
            log("Deleting dashboard ${dashboard.name}")
            dashboard.delete()
        }
        // After the projects, and for the same reason as the dashboards: a label belongs to
        // the instance rather than to a project, so deleting every project leaves every label
        // behind. Deleting them here is also what makes a second run reproduce the first
        // rather than add to it - Yontrack refuses a second label of the same category and
        // name.
        target.labels().forEach { label ->
            log("Deleting label ${label.display}")
            label.delete()
        }
        // An agent is an account, and outlives the projects the reset deletes; the server refuses a
        // second one of the same slug. Only the ones the dataset declares: any other agent of the
        // instance was registered by somebody, and is theirs.
        val agents = dataset.agents.map { it.identifier }.toSet()
        target.agents().filter { it.identifier in agents }.forEach { agent ->
            log("Deleting agent ${agent.identifier}")
            agent.delete()
        }
    }

    /**
     * @param evidence Whether the instance takes evidence
     */
    private fun create(dataset: DemoDataset, now: LocalDateTime, evidence: Boolean) {
        // Before anything is created through them, and revoked whatever happens: they are the tokens
        // of the account the seed runs as, an administrator's
        val tokens = dataset.projects
            .flatMap { project -> project.branches.flatMap { branch -> branch.builds.mapNotNull { it.token } } }
            .distinct()
            .associateWith { name ->
                log("Generating the API token $name")
                target.openToken(name)
            }
        try {
            create(dataset, now, evidence, tokens + agentTokens(dataset))
        } finally {
            tokens.values.forEach { token ->
                log("Revoking the API token ${token.name}")
                token.revoke()
            }
        }
    }

    /**
     * Registers the agents of the dataset, and opens a token for each of its agent sessions, keyed by
     * [sessionKey]. The tokens of the agents are kept - see [DemoAgent.generateToken].
     */
    private fun agentTokens(dataset: DemoDataset): Map<String, DemoToken> {
        val agentTokens = dataset.agents.associate { spec ->
            log("Registering agent ${spec.identifier}")
            spec.slug to target.registerAgent(spec).generateToken(AGENT_TOKEN)
        }
        return dataset.agentSessions().associate { session ->
            sessionKey(session) to agentTokens.getValue(session.agent).inSession(session)
        }
    }

    private fun DemoDataset.agentSessions(): List<AgentSessionSpec> =
        (projects.flatMap { project -> project.branches.flatMap { branch -> branch.builds.mapNotNull { it.agent } } } +
                deployments.mapNotNull { it.agent }).distinct()

    private fun create(dataset: DemoDataset, now: LocalDateTime, evidence: Boolean, tokens: Map<String, DemoToken>) {
        val projects = mutableMapOf<String, DemoProject>()
        val branches = mutableMapOf<Pair<String, String>, DemoBranch>()
        val builds = mutableMapOf<BuildRef, DemoBuild>()

        // Before the projects: a project is given its labels as it is created, and the
        // assignment is written with label *ids*, so every label has to exist first.
        val labels = dataset.labels.associate { spec ->
            log("Creating label ${spec.display}")
            spec.display to target.createLabel(spec)
        }

        dataset.projects.forEach { spec ->
            log("Creating project ${spec.name}")
            val project = target.createProject(spec.name, spec.description)
            projects[spec.name] = project
            // Before the branches: a branch maps onto a branch of the repository the
            // project is pointed at here, and a commit is linked to its issues as it is
            // registered, so the issues have to be in place first.
            spec.scm?.let { project.configureScm(it) }
            if (spec.favourite) project.markAsFavourite()
            // One call, whatever the project carries: the server replaces the whole set.
            if (spec.labels.isNotEmpty()) {
                project.setLabels(spec.labels.map { labels.getValue(it) })
            }
            spec.branches.forEach { branchSpec ->
                branches[spec.name to branchSpec.name] =
                    createBranch(spec, branchSpec, project, now, builds, tokens, evidence)
            }
        }

        // Second pass: a build can use a build of a project created later on.
        dataset.projects.forEach { spec ->
            spec.branches.forEach { branchSpec ->
                branchSpec.builds.forEach { buildSpec ->
                    val build = builds.getValue(BuildRef(spec.name, branchSpec.name, buildSpec.name))
                    buildSpec.links.forEach { ref ->
                        build.linkTo(builds.resolve(ref))
                    }
                }
            }
        }

        // Keyed by environment, project *and* qualifier: a project can have several slots in the
        // same environment, and `canary` is a different slot from the default one.
        val slots = mutableMapOf<Triple<String, String, String>, DemoSlot>()
        dataset.environments.forEach { spec ->
            log("Creating environment ${spec.name}")
            val environment = target.createEnvironment(
                name = spec.name,
                order = spec.order,
                description = spec.description,
                tags = spec.tags,
            )
            spec.slots.forEach { slotSpec ->
                val slot = environment.createSlot(
                    projects.getValue(slotSpec.project),
                    slotSpec.qualifier,
                    slotSpec.description,
                )
                if (slotSpec.agentsAdmitted) {
                    log("Admitting agents on slot ${slotName(spec, slotSpec)}")
                    slot.admitAgents()
                }
                // Before any deployment: the rules are what a deployment is checked against, and
                // adding them afterwards would leave the slot holding a build it now refuses
                slotSpec.admissionRules.forEach { ruleSpec ->
                    log("Adding admission rule ${ruleSpec.name} to slot ${slotName(spec, slotSpec)}")
                    slot.addAdmissionRule(ruleSpec)
                }
                slots[Triple(spec.name, slotSpec.project, slotSpec.qualifier)] = slot
            }
        }

        // After every slot exists, and in declaration order: an `environment` admission rule asks
        // what is deployed in another slot at that moment
        dataset.deployments.forEach { spec ->
            val ref = spec.build
            log("Deploying ${ref.build} of ${ref.project}/${ref.branch} to ${spec.environment} (${spec.stopAt})")
            slots.getValue(Triple(spec.environment, ref.project, spec.qualifier))
                .deploy(
                    build = builds.resolve(ref),
                    stopAt = spec.stopAt,
                    times = spec.at?.let { deploymentTimes(it.resolve(now), now) },
                    message = spec.message,
                    overrides = spec.overrides,
                    token = spec.agent?.let { tokens.getValue(sessionKey(it)) },
                )
        }

        // AFTER the deployments, unlike the admission rules. A `CANDIDATE` or `RUNNING` workflow is
        // a hard gate: the deployment cannot start, or cannot finish, until it has passed, and a
        // workflow runs asynchronously. Configuring one first would leave every deployment below
        // racing a workflow, which is how a reset that has to be reliable becomes flaky.
        dataset.environments.forEach { spec ->
            spec.slots.forEach { slotSpec ->
                slotSpec.workflows.forEach { workflowSpec ->
                    log("Adding ${workflowSpec.trigger} workflow to slot ${slotName(spec, slotSpec)}")
                    slots.getValue(Triple(spec.name, slotSpec.project, slotSpec.qualifier))
                        .addWorkflow(workflowSpec)
                }
            }
        }

        // Once everything else of their builds is there - deployments included - so that the
        // `validation.deleted` entries of the cascade come last in their trails, as a stamp retired
        // after the release would
        dataset.projects.forEach { project ->
            project.branches.forEach { branch ->
                branch.validationStamps.filter { it.deleted }.forEach { stamp ->
                    log("Deleting the validation stamp ${stamp.name} of ${project.name}/${branch.name}")
                    branches.getValue(project.name to branch.name).deleteValidationStamp(stamp.name)
                }
            }
        }

        // Last of the trails: an entry is rewritten once it exists, and the ones after it are left
        // alone, so that the verification breaks at the tampered one and nowhere else
        dataset.projects.forEach { project ->
            project.branches.forEach { branch ->
                branch.builds.forEach { build ->
                    build.tampering?.let { tampering ->
                        log("Tampering with entry ${tampering.seq} of the trail of ${project.name}/${branch.name}/${build.name}")
                        builds.getValue(BuildRef(project.name, branch.name, build.name)).tamper(tampering)
                    }
                }
            }
        }

        dataset.dashboard?.let { dashboard ->
            log("Saving dashboard ${dashboard.name}")
            target.saveDashboard(dashboard)
        }

        // Last but one: an estate reads the projects carrying its labels, and their builds,
        // promotions, test runs and deployments - all of which exist by now.
        dataset.estates.forEach { spec ->
            log("Creating estate ${spec.name}")
            target.createEstate(spec)
        }

        // Last: the readings are computed by a daily job, so without this the scorecard of every
        // project reads "not computed" until the next night. A project's recompute covers every set
        // it is in, the estates' included.
        dataset.projects.forEach { spec ->
            log("Computing the scorecard of ${spec.name}")
            projects.getValue(spec.name).recomputeScorecard()
        }
    }

    /**
     * The steps of a deployment starting at [start], a quarter of an hour apart - squeezed, like the
     * ladder of a build, into whatever time there is between [start] and the reset, so that a
     * deployment of the newest build does not end in the future.
     */
    private fun deploymentTimes(start: LocalDateTime, now: LocalDateTime): DeploymentTimes {
        val available = Duration.between(start, now).coerceAtLeast(Duration.ZERO)
        val step = minOf(Duration.ofMinutes(15), available.dividedBy(3))
        return DeploymentTimes(
            start = start,
            running = start.plus(step),
            end = start.plus(step.multipliedBy(2)),
        )
    }

    private fun createBranch(
        projectSpec: ProjectSpec,
        spec: BranchSpec,
        project: DemoProject,
        now: LocalDateTime,
        builds: MutableMap<BuildRef, DemoBuild>,
        tokens: Map<String, DemoToken>,
        evidence: Boolean,
    ): DemoBranch {
        val branch = project.createBranch(spec.name, spec.description)
        spec.scmBranch?.let { branch.configureScmBranch(it) }
        if (spec.favourite) branch.markAsFavourite()
        spec.promotionLevels.forEach { branch.createPromotionLevel(it.name, it.description, it.workflow) }
        spec.validationStamps.forEach { branch.createValidationStamp(it.name, it.description, it.findings, it.tests, it.chml) }
        spec.validationStamps.filter { it.nonAgentEvidence }.forEach { branch.restrictEvidenceToNonAgents(it.name) }
        // A third pass, after both: auto promotion and promotion dependencies name other promotion
        // levels and validation stamps of the same branch, and the property is written with their
        // ids, so all of them have to exist first. Before the builds, so that a build promoted here
        // is promoted against the configuration the demo ships with rather than against a branch
        // still being configured.
        spec.promotionLevels.forEach { promotionLevel ->
            promotionLevel.autoPromotion?.let { branch.setAutoPromotion(promotionLevel.name, it) }
            promotionLevel.dependsOn.takeIf { it.isNotEmpty() }
                ?.let { branch.setPromotionDependencies(promotionLevel.name, it) }
            // The condition names nothing, but it reads the branch's promotion level order, so it
            // belongs in this pass with the two properties which do name things
            if (promotionLevel.requiresPreviousPromotion) {
                branch.setPreviousPromotionCondition(promotionLevel.name, true)
            }
            if (promotionLevel.agentsAdmitted) {
                branch.admitAgents(promotionLevel.name)
            }
            // Names stamps of the branch, like the auto promotion
            promotionLevel.assistedBuildsRequire.takeIf { it.isNotEmpty() }
                ?.let { branch.setAssistedBuildsRequire(promotionLevel.name, it) }
        }
        // What an agent does itself, and what a person does for it: the agent policy, read off the dataset
        val agentsAdmitted = spec.promotionLevels.filter { it.agentsAdmitted }.map { it.name }.toSet()
        val nonAgentStamps = spec.validationStamps.filter { it.nonAgentEvidence }.map { it.name }.toSet()
        // The condition fails closed, so a promotion to a gated level waits for the assisted change
        val gated = spec.promotionLevels.filter { it.assistedBuildsRequire.isNotEmpty() }.map { it.name }.toSet()
        // Only where an auto promotion can fire - see below
        val promotionsFirst = spec.promotionLevels.any { it.autoPromotion != null }
        spec.builds.forEach { buildSpec ->
            val creation = buildSpec.creation.resolve(now)
            val build = branch.createBuild(
                name = buildSpec.name,
                description = buildSpec.description,
                creation = creation,
                token = buildSpec.token?.let { tokens.getValue(it) }
                    ?: buildSpec.agent?.let { tokens.getValue(sessionKey(it)) },
            )
            val byAgent = buildSpec.agent != null
            builds[BuildRef(projectSpec.name, spec.name, buildSpec.name)] = build
            buildSpec.release?.let { build.setRelease(it) }
            // The build is built from the last commit declared for it; the ones before are
            // the work that went into it, and are what the change log with the previous
            // build shows.
            buildSpec.commits
                .map { message -> branch.registerCommit(message) }
                .lastOrNull()
                ?.let { build.setCommit(it) }
            // One hour per step, so the validations and the promotions of a build are ordered and
            // the lead time charts have something other than a flat zero to draw - but squeezed
            // into whatever time the build actually has behind it, because the newest build of the
            // dataset is hours old and an hour per step would date its ladder in the FUTURE. A
            // checkpoint saying a build was promoted in four hours' time reads as a defect in
            // Yontrack.
            //
            // Validations take the lower steps and the promotions climb on top of them: a
            // validation is what grants the promotions naming it, so a run dated after them -
            // which is what every run was, being stamped at the moment of the reset (#1718) -
            // reads as the stamp having run hours after the promotion it granted.
            // A scan is a validation like any other, and takes its rung above the plain ones - and
            // above the test runs, which are validations as well
            val validationCount = buildSpec.validations.size + buildSpec.tests.size + buildSpec.scans.size
            val promotionCount = buildSpec.promotionLevels.size
            val steps = validationCount + promotionCount
            val available = Duration.between(creation, now).coerceAtLeast(Duration.ZERO)
            // One step MORE than the ladder has, when the hour has to give: the squeeze otherwise
            // lands the top rung exactly on the reset, and the newest build of the demo - the one
            // every visitor looks at first - reads as having been promoted a few seconds ago.
            val step = if (steps > 0) {
                minOf(Duration.ofHours(1), available.dividedBy(steps + 1L))
            } else {
                Duration.ZERO
            }
            // On a branch with an auto promotion, the promotions are still CREATED before the
            // validations, whatever the times say. `AutoPromotionEventListener` promotes a build the
            // moment a run completes the set a level names, and it stamps that run with the time of
            // the call rather than with the time of the validation - so seeding the runs first would
            // hand the demo a second, same-level promotion dated at the reset, which is the very
            // reading this is fixing.
            //
            // Everywhere else they come after, as they would in a real pipeline: the trail of a
            // build records its changes in the order they are made, whatever times they claim, and
            // a trail where a build is promoted to GOLD before any of its validations ran is the
            // wrong story to tell an auditor.
            val promote = {
                if (buildSpec.commits.isNotEmpty() && buildSpec.promotionLevels.any { it in gated }) {
                    build.awaitAssistedChange()
                }
                buildSpec.promotionLevels.forEachIndexed { index, promotionLevel ->
                    build.promote(
                        promotionLevel,
                        "",
                        creation.plus(step.multipliedBy(validationCount + index + 1L)),
                        byPerson = byAgent && promotionLevel !in agentsAdmitted,
                    )
                }
            }
            if (promotionsFirst) promote()
            buildSpec.validations.forEachIndexed { index, validation ->
                val run = build.validate(
                    validation.validationStamp,
                    validation.status,
                    validation.description,
                    creation.plus(step.multipliedBy(index + 1L)),
                    byPerson = byAgent && validation.validationStamp in nonAgentStamps,
                )
                // Posted by the pipeline with the run, the evidence comes first; a person then looks
                // at the run, which is when its status changes and a file attached by mistake goes
                val attached = if (evidence) {
                    validation.evidence.map { spec ->
                        log("Attaching ${spec.fileName} to ${validation.validationStamp} of ${projectSpec.name}/${buildSpec.name}")
                        spec to run.attachEvidence(spec, EvidenceFiles.content(spec.resource))
                    }
                } else {
                    emptyList()
                }
                validation.statusChanges.forEach { run.changeStatus(it) }
                attached.filter { (spec, _) -> spec.deleted }.forEach { (spec, attachedEvidence) ->
                    log("Deleting the evidence ${spec.fileName} of ${validation.validationStamp} of ${projectSpec.name}/${buildSpec.name}")
                    attachedEvidence.delete()
                }
            }
            // In the order they are declared, each on its own rung: a failed run followed by a passed
            // one of the same stamp is what makes the build flaky, and the order of the runs is all
            // the server reads it from
            buildSpec.tests.forEachIndexed { index, run ->
                build.validateWithTests(
                    run = run,
                    at = creation.plus(step.multipliedBy(buildSpec.validations.size + index + 1L)),
                )
            }
            buildSpec.scans.forEachIndexed { index, scan ->
                build.scan(
                    scan = scan,
                    report = FindingsReports.render(scan, now.toLocalDate()),
                    at = creation.plus(
                        step.multipliedBy(buildSpec.validations.size + buildSpec.tests.size + index + 1L)
                    ),
                )
            }
            if (!promotionsFirst) promote()
        }
        return branch
    }

    // validate() has already ruled out a reference the dataset does not create.
    private fun Map<BuildRef, DemoBuild>.resolve(ref: BuildRef): DemoBuild =
        getValue(ref)

    /** How a slot reads in the log: "production/petclinic", or "production/petclinic [canary]". */
    private fun slotName(spec: EnvironmentSpec, slotSpec: SlotSpec): String =
        "${spec.name}/${slotSpec.project}" +
                (slotSpec.qualifier.takeIf { it.isNotBlank() }?.let { " [$it]" } ?: "")

    companion object {
        /**
         * Name of the token each agent of the dataset acts through - kept, see [DemoAgent.generateToken].
         */
        const val AGENT_TOKEN = "demo-seed"

        /**
         * How an agent session keys the tokens of the seed, beside the names of the tokens of the
         * account it runs as: a token name has no `[`, which an agent identifier always has.
         */
        fun sessionKey(session: AgentSessionSpec): String = "${session.agent}[agent]#${session.id}"
    }
}
