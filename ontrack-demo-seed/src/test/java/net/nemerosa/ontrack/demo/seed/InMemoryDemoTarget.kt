package net.nemerosa.ontrack.demo.seed

import tools.jackson.databind.JsonNode
import java.security.MessageDigest
import java.time.LocalDateTime

/**
 * A Yontrack instance, as far as [DemoSeed] can tell.
 *
 * Enforces the rules the real server enforces and that the seed has to work with — entity
 * names are legal and unique, a build can only be promoted to a level its branch declares
 * — so that a dataset Yontrack would reject is rejected here too.
 *
 * [snapshot] renders the whole state as text, which is what the idempotency test compares
 * between two runs.
 */
class InMemoryDemoTarget(
    /**
     * Whether the instance runs the mock SCM. `false` models the one thing the seed cannot
     * check from the dataset alone: an instance that never enabled it.
     */
    private val scmEnabled: Boolean = true,
    /**
     * Whether the licence of the instance enables the native formats of the security scans.
     * `false` models an instance whose licence does not.
     */
    private val nativeFindingsFormats: Boolean = true,
    /**
     * Whether the licence of the instance enables the estates of the delivery scorecard. `false`
     * models an instance whose licence does not.
     */
    private val scorecardLicensed: Boolean = true,
    /**
     * What the instance does not offer, with the reason [unavailable] gives. Everything is offered
     * by default, as on the local dev stack with its storage and the tampering switch on.
     */
    private val unavailableCapabilities: Map<DemoCapability, String> = emptyMap(),
) : DemoTarget {

    /**
     * Names of the API tokens of the seeding account which are still valid.
     */
    val tokens = mutableSetOf<String>()

    private val projects = mutableListOf<InMemoryProject>()

    /**
     * Mock SCM repositories, held by the instance rather than by the projects — as they are
     * on a real server, where they outlive the projects the reset deletes.
     */
    private val scmRepositories = mutableMapOf<String, InMemoryScmRepository>()
    private val environments = mutableListOf<InMemoryEnvironment>()
    private val dashboards = mutableListOf<InMemoryDashboard>()

    /**
     * Labels, held by the instance rather than by the projects — as they are on a real
     * server, where they outlive the projects the reset deletes.
     */
    private val labels = mutableListOf<InMemoryLabel>()

    /**
     * Estates of the delivery scorecard, held by the instance and naming labels by their display.
     */
    private val estates = mutableListOf<InMemoryEstate>()

    /**
     * What was created and computed, in order - the snapshot is a state and says nothing of the
     * order it was reached in, and the scorecard has to be computed once everything else exists.
     */
    val journal = mutableListOf<String>()

    override fun projects(): List<DemoProject> = projects.toList()

    override fun createProject(name: String, description: String): DemoProject {
        checkName(name, "Project")
        require(projects.none { it.name == name }) { "Project $name already exists" }
        journal += "project $name"
        return InMemoryProject(name, description).also { projects += it }
    }

    override fun labels(): List<DemoLabel> = labels.toList()

    override fun createLabel(spec: LabelSpec): DemoLabel {
        spec.category?.let {
            require(LABEL_NAME.matches(it)) {
                "Label category \"$it\" can only have letters, digits, dots, dashes or underscores."
            }
        }
        require(LABEL_NAME.matches(spec.name)) {
            "Label name \"${spec.name}\" can only have letters, digits, dots, dashes or underscores."
        }
        require(LABEL_COLOR.matches(spec.color)) {
            "Label colour \"${spec.color}\" is not a #RRGGBB string."
        }
        require(labels.none { it.display == spec.display }) {
            "Label ${spec.display} already exists"
        }
        return InMemoryLabel(spec).also { labels += it }
    }

    override fun environments(): List<DemoEnvironment> = environments.toList()

    override fun createEnvironment(
        name: String,
        order: Int,
        description: String,
        tags: List<String>,
    ): DemoEnvironment {
        checkName(name, "Environment")
        require(environments.none { it.name == name }) { "Environment $name already exists" }
        journal += "environment $name"
        return InMemoryEnvironment(name, order, description, tags).also { environments += it }
    }

    override fun unavailable(capability: DemoCapability): String? = unavailableCapabilities[capability]

    override fun openToken(name: String): DemoToken {
        tokens -= name
        tokens += name
        return InMemoryToken(name)
    }

    inner class InMemoryToken(override val name: String) : DemoToken {
        override fun revoke() {
            tokens -= name
        }
    }

    /**
     * Registered agents, held by the instance: they are accounts, and outlive the projects the reset
     * deletes.
     */
    val agents = mutableListOf<InMemoryAgent>()

    override fun agents(): List<DemoAgent> = agents.toList()

    override fun registerAgent(spec: AgentSpec): DemoAgent {
        require(Regex("[a-z0-9-]{1,32}").matches(spec.slug)) { "Invalid agent slug: ${spec.slug}" }
        // The identifier is the email of the account, unique on the instance
        require(agents.none { it.identifier == spec.identifier }) { "Agent ${spec.identifier} already exists" }
        journal += "agent ${spec.identifier}"
        return InMemoryAgent(spec).also { agents += it }
    }

    inner class InMemoryAgent(val spec: AgentSpec) : DemoAgent {

        override val identifier: String get() = spec.identifier

        /** Names of the tokens of the agent which are still valid. */
        val tokens = mutableSetOf<String>()

        override fun delete() {
            agents -= this
        }

        override fun generateToken(name: String): DemoAgentToken {
            require(name !in tokens) { "The agent $identifier already has a token $name" }
            tokens += name
            return object : DemoAgentToken {
                override fun inSession(session: AgentSessionSpec): DemoToken {
                    require(session.agent == spec.slug) { "A session of ${session.agent} through a token of $identifier" }
                    return InMemoryAgentToken(this@InMemoryAgent, name, session)
                }
            }
        }
    }

    /**
     * A token of an agent, sent with the headers of a session: what is done through it is the agent's.
     */
    inner class InMemoryAgentToken(
        val agent: InMemoryAgent,
        override val name: String,
        val session: AgentSessionSpec,
    ) : DemoToken {
        override fun revoke() {
            agent.tokens -= name
        }
    }

    /**
     * Whether the trails are written: only while the licence allows the audit trail.
     */
    private val trailsWritten: Boolean get() = DemoCapability.AUDIT_TRAIL !in unavailableCapabilities

    override fun checkScmAvailable() {
        check(scmEnabled) { "The mock SCM is not enabled on this instance. Nothing was deleted." }
    }

    override fun checkNativeFindingsFormats() {
        check(nativeFindingsFormats) { "The native scanner formats are not licensed on this instance. Nothing was deleted." }
    }

    override fun checkScorecardLicensed() {
        check(scorecardLicensed) { "The delivery scorecard is not licensed on this instance. Nothing was deleted." }
    }

    /** None when unlicensed, as `KdslDemoTarget` answers: the server refuses the listing there. */
    override fun estates(): List<DemoEstate> = if (scorecardLicensed) estates.toList() else emptyList()

    override fun createEstate(spec: EstateSpec) {
        check(scorecardLicensed) { "Feature not allowed by the license: extension.scorecard" }
        require(spec.name.isNotBlank()) { "The name of an estate is required." }
        require(estates.none { it.spec.name == spec.name }) { "Estate ${spec.name} already exists" }
        require(spec.labels.isNotEmpty()) { "An estate needs one label at least, to select its projects." }
        spec.labels.forEach { display ->
            require(labels.any { it.display == display }) { "Label $display does not exist." }
        }
        estates += InMemoryEstate(spec)
        journal += "estate ${spec.name}"
    }

    override fun dashboards(): List<DemoDashboardHandle> = dashboards.toList()

    override fun saveDashboard(dashboard: DemoDashboard) {
        val sameName = dashboards.find { it.name == dashboard.name }
        require(sameName == null || sameName.dashboard.uuid == dashboard.uuid) {
            "Dashboard ${dashboard.name} already exists under another UUID"
        }
        journal += "dashboard ${dashboard.name}"
        dashboards.removeIf { it.dashboard.uuid == dashboard.uuid }
        dashboards += InMemoryDashboard(dashboard)
    }

    /**
     * The whole state as text, ordered as it was created — two runs of the seed differ as
     * soon as one line does.
     */
    fun snapshot(): String = buildList {
        // First, as the seed creates them: a project below names them.
        labels.forEach { label ->
            add("label ${label.display} ${label.spec.color} \"${label.spec.description}\"")
        }
        agents.forEach { agent ->
            add("agent ${agent.identifier} \"${agent.spec.displayName}\" ${agent.spec.tool} \"${agent.spec.description}\"")
            agent.tokens.sorted().forEach { add("  token $it") }
        }
        projects.forEach { project ->
            add("project ${project.name} \"${project.description}\"")
            if (project.favourite) add("  favourite")
            if (project.labels.isNotEmpty()) add("  labels ${project.labels.joinToString(", ")}")
            if (project.scorecardComputed) add("  scorecard computed")
            project.scmRepositoryName?.let { repositoryName ->
                val repository = scmRepositories.getValue(repositoryName)
                add("  scm ${repository.name}")
                repository.issues.forEach { add("    issue ${it.key} \"${it.summary}\" ${it.type}") }
                repository.commits.forEach { add("    commit ${it.id} \"${it.message}\"") }
            }
            project.branches.forEach { branch ->
                add("  branch ${branch.name} \"${branch.description}\"")
                if (branch.favourite) add("    favourite")
                branch.scmBranch?.let { add("    scm branch $it") }
                branch.promotionLevels.forEach { promotionLevel ->
                    add("    promotion level $promotionLevel")
                    branch.autoPromotions[promotionLevel]?.let { add("      auto promotion $it") }
                    branch.promotionDependencies[promotionLevel]?.let { add("      depends on $it") }
                    if (promotionLevel in branch.previousPromotionRequired) add("      requires the previous promotion")
                    if (promotionLevel in branch.agentsAdmitted) add("      admits agents")
                    branch.assistedBuildsRequire[promotionLevel]?.let { add("      assisted builds require $it") }
                }
                branch.validationStamps.forEach { stamp ->
                    add("    validation stamp $stamp")
                    branch.findingsStamps[stamp]?.let { add("      findings $it") }
                    branch.chmlStamps[stamp]?.let { add("      chml $it") }
                    if (stamp in branch.nonAgentStamps) add("      evidence from non-agents only")
                }
                branch.deletedValidationStamps.forEach { add("    deleted validation stamp $it") }
                branch.builds.forEach { build ->
                    add("    build ${build.name} \"${build.description}\" at ${build.creation}")
                    build.token?.let { add("      through ${describe(it)}") }
                    build.assistedChange?.let { add("      assisted change $it") }
                    build.releaseVersion?.let { add("      release $it") }
                    build.commitId?.let { add("      built from $it") }
                    build.promotions.forEachIndexed { index, it ->
                        add("      promotion ${it.first} at ${it.second} by ${build.promotionActors[index]}")
                    }
                    build.validations.forEach { validation ->
                        add("      validation ${validation.stamp} ${validation.status} at ${validation.at} by ${validation.actor}")
                        validation.statusChanges.forEach { add("        status ${it.status} \"${it.description}\"") }
                        validation.evidence.forEach {
                            add("        evidence ${it.spec.fileName} ${it.spec.mediaType} ${it.sha256}" + if (it.deleted) " deleted" else "")
                        }
                    }
                    build.testRuns.forEach {
                        add("      tests ${it.run.validationStamp} ${it.run.passed}/${it.run.skipped}/${it.run.failed} ${it.status} at ${it.at}")
                    }
                    build.scans.forEach { scan ->
                        add("      scan ${scan.spec.validationStamp} ${scan.spec.format} ${scan.spec.kind} ${scan.spec.scanner} at ${scan.at}")
                        add("        report ${scan.report}")
                    }
                    build.links.forEach { add("      uses ${it.branch.project.name}/${it.name}") }
                    build.trail.forEach { add("      trail $it") }
                }
            }
        }
        environments.forEach { environment ->
            add("environment ${environment.name} #${environment.order} \"${environment.description}\" ${environment.tags}")
            environment.slots.forEach { slot ->
                add("  slot ${slot.project.name}${qualifierSuffix(slot.qualifier)} \"${slot.description}\"")
                if (slot.agentsAdmitted) add("    admits agents")
                slot.admissionRules.forEach { add("    rule ${it.name} ${it.ruleId} ${it.config}") }
                slot.workflows.forEach { add("    workflow on ${it.trigger}: ${it.yaml.lines().first()}") }
                slot.deployments.forEach {
                    it.overrides.forEach { override -> add("    overriding ${override.rule} \"${override.message}\"") }
                    val what = when (it.stopAt) {
                        DeploymentStop.DONE -> "deployed"
                        DeploymentStop.FAILED -> "failed"
                        DeploymentStop.CANCELLED -> "cancelled"
                        else -> "deploying"
                    }
                    add(
                        "    $what ${it.build.name}" +
                                (it.times?.let { times -> " at ${times.start}..${times.end}" } ?: "") +
                                (it.message?.let { message -> " \"$message\"" } ?: "") +
                                " by ${it.actor}"
                    )
                }
            }
        }
        // Repositories no project points at: the mock SCM holds them on the server, where
        // they outlive the projects the reset deletes.
        scmRepositories.keys
            .filter { name -> projects.none { it.scmRepositoryName == name } }
            .forEach { add("orphan scm $it") }
        tokens.sorted().forEach { add("token $it") }
        estates.forEach { held ->
            val estate = held.spec
            add("estate ${estate.name} \"${estate.description}\" ${estate.labels} ${estate.marker}")
            estate.readings.forEach { add("  reading ${it.key} window ${it.windowDays} target ${it.target}") }
            add("  security ${estate.security}")
        }
        dashboards.forEach { held ->
            val dashboard = held.dashboard
            add("dashboard ${dashboard.name} (${dashboard.uuid})")
            dashboard.widgets.forEach { add("  widget ${it.key} ${it.layout} ${it.config}") }
        }
    }.joinToString("\n")

    inner class InMemoryEstate(val spec: EstateSpec) : DemoEstate {

        override val name: String get() = spec.name

        override fun delete() {
            estates -= this
        }
    }

    inner class InMemoryLabel(val spec: LabelSpec) : DemoLabel {

        override val display: String get() = spec.display

        override fun delete() {
            // As `LabelDeletionGuard` does on the server: a label an estate selects its projects by
            // cannot go, which is what makes the reset delete the estates first
            val users = estates.filter { display in it.spec.labels }.map { it.name }
            check(users.isEmpty()) {
                "Label $display cannot be deleted: it selects the projects of the estate(s) ${users.joinToString(", ")}."
            }
            labels -= this
            // As the server does, by cascade on PROJECT_LABEL: a deleted label is gone from
            // every project that carried it.
            projects.forEach { it.removeLabel(display) }
        }
    }

    inner class InMemoryDashboard(val dashboard: DemoDashboard) : DemoDashboardHandle {

        override val name: String get() = dashboard.name

        override fun delete() {
            dashboards -= this
        }
    }

    inner class InMemoryProject(
        override val name: String,
        val description: String,
    ) : DemoProject {

        val branches = mutableListOf<InMemoryBranch>()

        /**
         * The project only points at a repository — the repository itself belongs to the
         * instance, as it does on a real server.
         */
        var scmRepositoryName: String? = null

        /** A favourite of the seeding account, which is the only account this fake has. */
        var favourite: Boolean = false
            private set

        /** The labels this project carries, by their display form, in assignment order. */
        var labels: List<String> = emptyList()
            private set

        override fun markAsFavourite() {
            favourite = true
        }

        /** Whether the scorecard of the project was recomputed after it was seeded. */
        var scorecardComputed: Boolean = false
            private set

        override fun recomputeScorecard() {
            scorecardComputed = true
            journal += "recompute $name"
        }

        /**
         * Replaces the whole set, as `setProjectLabels` does on the server.
         */
        override fun setLabels(labels: List<DemoLabel>) {
            labels.forEach { label ->
                require(this@InMemoryDemoTarget.labels.any { it.display == label.display }) {
                    "Label ${label.display} does not exist"
                }
            }
            this.labels = labels.map { it.display }
        }

        fun removeLabel(display: String) {
            labels = labels - display
        }

        override fun delete() {
            projects -= this
        }

        override fun createBranch(name: String, description: String): DemoBranch {
            checkName(name, "Branch")
            require(branches.none { it.name == name }) { "Branch $name already exists in ${this.name}" }
            return InMemoryBranch(this, name, description).also { branches += it }
        }

        /**
         * Starts the repository over, the way the seed does on a real instance: the mock
         * SCM keeps a repository until someone deletes it, so a second run registering the
         * same commits again would number them on top of the first run's.
         */
        override fun configureScm(scm: ScmSpec) {
            val repository = InMemoryScmRepository(scm.repository)
            scmRepositories[scm.repository] = repository
            scm.issues.forEach { repository.registerIssue(it) }
            scmRepositoryName = scm.repository
        }
    }

    inner class InMemoryBranch(
        val project: InMemoryProject,
        override val name: String,
        val description: String,
    ) : DemoBranch {

        val promotionLevels = mutableListOf<String>()
        val validationStamps = mutableListOf<String>()
        val deletedValidationStamps = mutableListOf<String>()
        /** Thresholds of the `security-findings` stamps, by name. */
        val findingsStamps = mutableMapOf<String, FindingsThresholdsSpec>()
        /** The `tests` stamps, of the test summary data type. */
        val testsStamps = mutableSetOf<String>()
        /** CHML configuration of the CHML stamps, by name. */
        val chmlStamps = mutableMapOf<String, CHMLSpec>()
        val builds = mutableListOf<InMemoryBuild>()
        var scmBranch: String? = null
        val autoPromotions = mutableMapOf<String, AutoPromotionSpec>()
        val promotionDependencies = mutableMapOf<String, List<String>>()
        val previousPromotionRequired = mutableSetOf<String>()
        /** The promotion levels admitting agents. */
        val agentsAdmitted = mutableSetOf<String>()
        /** The stamps an assisted build requires, by promotion level. */
        val assistedBuildsRequire = mutableMapOf<String, List<String>>()
        /** The stamps taking evidence from non-agents only. */
        val nonAgentStamps = mutableSetOf<String>()

        /** A favourite of the seeding account, as [InMemoryProject.favourite] is. */
        var favourite: Boolean = false
            private set

        override fun markAsFavourite() {
            favourite = true
        }

        override fun configureScmBranch(scmBranch: String) {
            requireNotNull(project.scmRepositoryName) {
                "No SCM configured on ${project.name}"
            }
            this.scmBranch = scmBranch
        }

        override fun registerCommit(message: String): String {
            val repository = requireNotNull(project.scmRepositoryName?.let(scmRepositories::get)) {
                "No SCM configured on ${project.name}"
            }
            val scmBranch = requireNotNull(scmBranch) {
                "No SCM branch configured on ${project.name}/$name"
            }
            return repository.registerCommit(scmBranch, message)
        }

        override fun createPromotionLevel(name: String, description: String, workflow: WorkflowSpec?) {
            checkName(name, "Promotion level")
            require(name !in promotionLevels) { "Promotion level $name already exists in ${project.name}/${this.name}" }
            promotionLevels += name
        }

        override fun createValidationStamp(
            name: String,
            description: String,
            findings: FindingsThresholdsSpec?,
            tests: Boolean,
            chml: CHMLSpec?,
        ) {
            checkName(name, "Validation stamp")
            require(name !in validationStamps) { "Validation stamp $name already exists in ${project.name}/${this.name}" }
            require(listOf(findings != null, tests, chml != null).count { it } <= 1) { "A validation stamp has one data type" }
            validationStamps += name
            findings?.let { findingsStamps[name] = it }
            if (tests) testsStamps += name
            chml?.let { chmlStamps[name] = it }
        }

        override fun setAutoPromotion(promotionLevel: String, spec: AutoPromotionSpec) {
            requirePromotionLevel(promotionLevel)
            // The property is written with entity ids, so the server cannot record a name it has
            // nothing behind - which is what makes the ordering of the seed's passes load-bearing.
            spec.promotionLevels.forEach(::requirePromotionLevel)
            spec.validationStamps.forEach { stamp ->
                require(stamp in validationStamps) {
                    "Validation stamp $stamp does not exist in ${project.name}/${this.name}"
                }
            }
            autoPromotions[promotionLevel] = spec
        }

        override fun setPromotionDependencies(promotionLevel: String, dependencies: List<String>) {
            requirePromotionLevel(promotionLevel)
            // The property names its dependencies rather than referencing them, so the server DOES
            // accept a name matching nothing - #1705 draws it. The dataset refuses one anyway, in
            // `validate`, and this fake stays as permissive as the server it stands for.
            promotionDependencies[promotionLevel] = dependencies
        }

        override fun setPreviousPromotionCondition(promotionLevel: String, required: Boolean) {
            requirePromotionLevel(promotionLevel)
            if (required) {
                previousPromotionRequired += promotionLevel
            } else {
                previousPromotionRequired -= promotionLevel
            }
        }

        override fun admitAgents(promotionLevel: String) {
            requirePromotionLevel(promotionLevel)
            agentsAdmitted += promotionLevel
        }

        override fun setAssistedBuildsRequire(promotionLevel: String, validationStamps: List<String>) {
            requirePromotionLevel(promotionLevel)
            assistedBuildsRequire[promotionLevel] = validationStamps
        }

        override fun restrictEvidenceToNonAgents(validationStamp: String) {
            require(validationStamp in validationStamps) {
                "Validation stamp $validationStamp does not exist in ${project.name}/${this.name}"
            }
            nonAgentStamps += validationStamp
        }

        /**
         * The promotion level immediately below [promotionLevel] in this branch's order, which is
         * what the condition names - and `null` for the first level, which has none.
         */
        fun previousPromotionLevel(promotionLevel: String): String? =
            promotionLevels.indexOf(promotionLevel).takeIf { it > 0 }?.let { promotionLevels[it - 1] }

        private fun requirePromotionLevel(name: String) {
            require(name in promotionLevels) {
                "Promotion level $name does not exist in ${project.name}/${this.name}"
            }
        }

        override fun createBuild(name: String, description: String, creation: LocalDateTime, token: DemoToken?): DemoBuild {
            checkName(name, "Build")
            require(builds.none { it.name == name }) { "Build $name already exists in ${project.name}/${this.name}" }
            token?.let { requireValid(it) }
            return InMemoryBuild(this, name, description, creation, token).also { build ->
                builds += build
                // Two calls, as the seed makes them: Yontrack stamps a build with the time it is
                // created, and the seed backdates it in a second one - which its trail records. An
                // agent may not edit a build, so a person backdates the agent's.
                build.entry("build.created")
                build.entry(
                    "build.updated",
                    "creation" to creation,
                    actor = if (token is InMemoryAgentToken) SEED_ACTOR else build.actorOf(token),
                )
            }
        }

        /**
         * As the server does: the runs of the stamp go with it, and each of their builds records it
         * - the evidence of a run which is not deleted already first, then the run.
         */
        override fun deleteValidationStamp(name: String) {
            require(name in validationStamps) { "No validation stamp $name in ${project.name}/${this.name}" }
            builds.forEach { build ->
                build.validations.filter { it.stamp == name }.forEach { run ->
                    build.validations -= run
                    run.evidence.filter { !it.deleted }.forEach { evidence ->
                        build.entry(
                            "evidence.deleted",
                            "validationStamp" to name,
                            "fileName" to evidence.spec.fileName,
                            "reason" to "cascade/validation-stamp-deleted",
                            actor = SEED_ACTOR,
                        )
                    }
                    build.entry(
                        "validation.deleted",
                        "validationStamp" to name,
                        "status" to run.status,
                        "reason" to "cascade/validation-stamp-deleted",
                        actor = SEED_ACTOR,
                    )
                }
            }
            validationStamps -= name
            deletedValidationStamps += name
        }
    }

    private fun requireValid(token: DemoToken) {
        if (token is InMemoryAgentToken) {
            check(token.agent in agents) { "The agent ${token.agent.identifier} is deleted" }
            check(token.name in token.agent.tokens) { "The token ${token.name} of ${token.agent.identifier} is revoked" }
        } else {
            check(token.name in tokens) { "The token ${token.name} is revoked" }
        }
    }

    /**
     * The actor of what is done through [token]: an agent in its session, the token, or the account the
     * seed runs as. A revoked token is refused, as the server refuses it.
     */
    fun actorOf(token: DemoToken?): String {
        token?.let { requireValid(it) }
        return describe(token)
    }

    /**
     * How [token] reads as an actor - `agent:<identifier> session <id>`, `token:<name>`, or the account
     * the seed runs as - whether it is still valid or not.
     */
    fun describe(token: DemoToken?): String =
        when (token) {
            null -> SEED_ACTOR
            is InMemoryAgentToken -> "agent:${token.agent.identifier} session ${token.session.id}"
            else -> "token:${token.name}"
        }

    inner class InMemoryBuild(
        val branch: InMemoryBranch,
        override val name: String,
        val description: String,
        val creation: LocalDateTime,
        /** What the build was created through, and what everything done through this handle is. */
        val token: DemoToken? = null,
    ) : DemoBuild {

        /**
         * The trail of the build, as the server writes it - only while the licence allows it.
         */
        val trail = mutableListOf<InMemoryEntry>()

        /**
         * The actor of what is done through this handle: the token of the build, or the account the
         * seed runs as. A revoked token is refused, as the server refuses it.
         */
        private val actor: String
            get() = actorOf(token)

        fun actorOf(token: DemoToken?): String = this@InMemoryDemoTarget.actorOf(token)

        /** Whether what is done through this handle is done by an agent. */
        private val byAgent: Boolean get() = token is InMemoryAgentToken

        /** Who made each promotion, in the order of [promotions]. */
        val promotionActors = mutableListOf<String>()

        /**
         * The assisted change of the build, as the server computes it once its commit is set - see
         * [computeAssistedChange].
         */
        var assistedChange: InMemoryAssistedChange? = null
            private set

        fun entry(type: String, vararg payload: Pair<String, Any?>, actor: String = this.actor) {
            if (trailsWritten) {
                trail += InMemoryEntry(trail.size + 1, type, actor, payload.toMap())
            }
        }

        /**
         * Position of the first entry of the trail whose payload was rewritten - where its
         * verification breaks - `null` when none was.
         */
        val firstBrokenSeq: Int? get() = trail.firstOrNull { it.tampered }?.seq

        var releaseVersion: String? = null
        // Named for its getter, not for the interface: `commit` would clash with setCommit
        // on the JVM, the same way `releaseVersion` does with setRelease.
        var commitId: String? = null
        val promotions = mutableListOf<Pair<String, LocalDateTime>>()
        val validations = mutableListOf<InMemoryValidation>()
        val scans = mutableListOf<InMemoryScan>()
        val testRuns = mutableListOf<InMemoryTestRun>()
        val links = mutableListOf<InMemoryBuild>()

        override fun setRelease(release: String) {
            releaseVersion = release
            entry("property.set", "propertyType" to "release")
        }

        override fun tamper(spec: TamperingSpec) {
            check(DemoCapability.TRAIL_TAMPERING !in unavailableCapabilities) {
                "The demonstration tampering end point does not exist on this instance"
            }
            val index = spec.seq - 1
            val entry = trail.getOrNull(index)
                ?: error("The trail of build $name has no entry ${spec.seq} to tamper with")
            check(entry.type == spec.type) {
                "Entry ${spec.seq} of the trail of build $name is ${entry.type}, not ${spec.type}"
            }
            trail[index] = entry.copy(payload = entry.payload + spec.payload, tampered = true)
        }

        override fun promote(promotionLevel: String, description: String, at: LocalDateTime, byPerson: Boolean) {
            require(promotionLevel in branch.promotionLevels) {
                "No promotion level $promotionLevel on ${branch.project.name}/${branch.name}"
            }
            val promoter = if (byPerson) SEED_ACTOR else actor
            // The agent policy (#2026): an agent promotes on a level admitting agents, and nowhere else
            check(byPerson || !byAgent || promotionLevel in branch.agentsAdmitted) {
                "$promoter may not promote to $promotionLevel: the promotion level does not admit agents (agent policy)"
            }
            // `AssistedBuildsRequireCheckExtension`, which fails closed: a build whose assisted change is
            // absent or unknown counts as assisted
            branch.assistedBuildsRequire[promotionLevel]?.let { stamps ->
                if (assistedChange?.assisted != false) {
                    stamps.forEach { stamp ->
                        check(validations.lastOrNull { it.stamp == stamp }?.status in PASSING) {
                            "Assisted build: $stamp must pass first."
                        }
                    }
                }
            }
            // `PromotionRunDependenciesCheckExtension` refuses the promotion outright, as the
            // promotion is created, so a build promoted to GOLD before SILVER is refused even
            // though it ends up carrying both. One reading of the rule, shared with `validate`.
            missingPromotionDependency(
                alreadyPromoted = promotions.map { it.first },
                dependencies = branch.promotionDependencies[promotionLevel].orEmpty(),
            )?.let { missing ->
                throw IllegalStateException(
                    "$name of ${branch.project.name}/${branch.name} cannot be promoted to " +
                            "$promotionLevel before $missing, which it requires"
                )
            }
            // `PreviousPromotionConditionCheckExtension` refuses the promotion the same way, and
            // reads the predecessor off the branch's promotion level ORDER rather than off a name
            if (promotionLevel in branch.previousPromotionRequired) {
                branch.previousPromotionLevel(promotionLevel)
                    ?.takeIf { previous -> previous !in promotions.map { it.first } }
                    ?.let { previous ->
                        throw IllegalStateException(
                            "$name of ${branch.project.name}/${branch.name} cannot be promoted to " +
                                    "$promotionLevel before $previous, which comes before it"
                        )
                    }
            }
            promotions += promotionLevel to at
            promotionActors += promoter
            entry("promotion.added", "promotionLevel" to promotionLevel, actor = promoter)
        }

        /** `NonAgentEvidenceCheck`: an agent's run on such a stamp is refused, never ignored. */
        private fun checkNotAgentEvidence(validationStamp: String, byPerson: Boolean = false) {
            check(byPerson || !byAgent || validationStamp !in branch.nonAgentStamps) {
                "$actor may not record a run of $validationStamp: its evidence must come from a person"
            }
        }

        override fun awaitAssistedChange() {
            checkNotNull(assistedChange) { "The assisted change of $name is never computed: it has no commit" }
        }

        /**
         * As `AssistedChangeServiceImpl` computes it: the change log from the previous build of the branch
         * with a commit, and UNKNOWN for the first one.
         */
        private fun computeAssistedChange(commitId: String) {
            val repository = scmRepositories.getValue(branch.project.scmRepositoryName!!)
            val previous = branch.builds.takeWhile { it != this }.lastOrNull { it.commitId != null }
            assistedChange = if (previous == null) {
                InMemoryAssistedChange(basis = "UNKNOWN")
            } else {
                val onBranch = repository.commits.filter { it.scmBranch == branch.scmBranch }
                val from = onBranch.indexOfFirst { it.id == previous.commitId }
                val to = onBranch.indexOfFirst { it.id == commitId }
                val commits = onBranch.subList(from + 1, to + 1)
                val assistants = commits.map { assistantsOf(it.message) }
                InMemoryAssistedChange(
                    basis = "COMPUTED",
                    assistants = assistants.flatten().toSortedSet().toList(),
                    assistedCommits = assistants.count { it.isNotEmpty() },
                    totalCommits = commits.size,
                )
            }
        }

        override fun validate(
            validationStamp: String,
            status: ValidationStatus,
            description: String,
            at: LocalDateTime,
            byPerson: Boolean,
        ): DemoValidationRun {
            checkNotAgentEvidence(validationStamp, byPerson)
            require(validationStamp in branch.validationStamps) {
                "No validation stamp $validationStamp on ${branch.project.name}/${branch.name}"
            }
            // `validateBuildWithFindings` is the only door of a findings stamp: a run with a status
            // and no report is refused by its data type
            require(validationStamp !in branch.findingsStamps) {
                "$validationStamp on ${branch.project.name}/${branch.name} is a security-findings stamp, and takes a report"
            }
            // The data of a typed stamp is required, and a run with a status only has none
            require(validationStamp !in branch.testsStamps) {
                "$validationStamp on ${branch.project.name}/${branch.name} is a tests stamp, and takes the counts of its tests"
            }
            val validator = if (byPerson) SEED_ACTOR else actor
            entry("validation.run", "validationStamp" to validationStamp, "status" to status, actor = validator)
            return InMemoryValidation(this, validationStamp, status, at, validator).also { validations += it }
        }

        override fun validateWithTests(run: TestRunSpec, at: LocalDateTime) {
            checkNotAgentEvidence(run.validationStamp)
            require(run.validationStamp in branch.testsStamps) {
                "No tests stamp ${run.validationStamp} on ${branch.project.name}/${branch.name}"
            }
            require(run.passed >= 0 && run.skipped >= 0 && run.failed >= 0) {
                "Counts of tests must be >= 0"
            }
            // `TestSummaryValidationConfig.computeStatus`, for a stamp with the default configuration
            val status = if (run.failed > 0) ValidationStatus.FAILED else ValidationStatus.PASSED
            testRuns += InMemoryTestRun(run, status, at)
            entry("validation.run", "validationStamp" to run.validationStamp, "status" to status)
        }

        override fun scan(scan: ScanSpec, report: JsonNode, at: LocalDateTime) {
            checkNotAgentEvidence(scan.validationStamp)
            require(scan.validationStamp in branch.findingsStamps) {
                "No security-findings stamp ${scan.validationStamp} on ${branch.project.name}/${branch.name}"
            }
            // The licence is checked on every post, and nothing is created without it
            check(scan.format != ScanFormat.SARIF || nativeFindingsFormats) {
                "Findings report format `sarif` needs the licensed feature \"Native scanner formats\""
            }
            scans += InMemoryScan(scan, report, at)
            entry("validation.run", "validationStamp" to scan.validationStamp)
        }

        override fun linkTo(build: DemoBuild) {
            links += build as InMemoryBuild
            entry("link.added", "target" to "${build.branch.project.name}/${build.name}")
        }

        override fun setCommit(commitId: String) {
            this.commitId = commitId
            entry("property.set", "propertyType" to "commit")
            computeAssistedChange(commitId)
        }
    }

    /**
     * One run of a validation stamp on a build, with the time the seed dated it at — which is
     * a fact about the demo the same way a promotion's time is (#1718).
     *
     * @property status Its last status
     */
    inner class InMemoryValidation(
        val build: InMemoryBuild,
        val stamp: String,
        status: ValidationStatus,
        val at: LocalDateTime,
        /** Who recorded the run. */
        val actor: String,
    ) : DemoValidationRun {

        var status: ValidationStatus = status
            private set

        val statusChanges = mutableListOf<StatusChangeSpec>()
        val evidence = mutableListOf<InMemoryEvidence>()

        override fun attachEvidence(spec: EvidenceSpec, content: ByteArray): DemoEvidence {
            // The storage is checked on every upload, and nothing is attached without it
            unavailableCapabilities[DemoCapability.EVIDENCE]?.let { reason -> error("Evidence refused: $reason") }
            val sha256 = MessageDigest.getInstance("SHA-256").digest(content).joinToString("") { "%02x".format(it) }
            build.entry("evidence.attached", "validationStamp" to stamp, "fileName" to spec.fileName, "sha256" to sha256)
            return InMemoryEvidence(this, spec, sha256).also { evidence += it }
        }

        override fun changeStatus(change: StatusChangeSpec) {
            require(statusChangeAllowed(status, change.status)) {
                "[$status] --> [${change.status}] change is not allowed."
            }
            status = change.status
            statusChanges += change
            build.entry(
                "validation.status",
                "validationStamp" to stamp,
                "status" to change.status,
                "description" to change.description,
                actor = SEED_ACTOR,
            )
        }
    }

    inner class InMemoryEvidence(
        val run: InMemoryValidation,
        val spec: EvidenceSpec,
        val sha256: String,
    ) : DemoEvidence {

        var deleted: Boolean = false
            private set

        override fun delete() {
            check(!deleted) { "Evidence ${spec.fileName} is already deleted" }
            deleted = true
            run.build.entry(
                "evidence.deleted",
                "validationStamp" to run.stamp,
                "fileName" to spec.fileName,
                actor = SEED_ACTOR,
            )
        }
    }

    inner class InMemoryEnvironment(
        override val name: String,
        val order: Int,
        val description: String,
        val tags: List<String>,
    ) : DemoEnvironment {

        val slots = mutableListOf<InMemorySlot>()

        override fun delete() {
            environments -= this
        }

        override fun createSlot(project: DemoProject, qualifier: String, description: String): DemoSlot {
            project as InMemoryProject
            require(project in projects) { "Slot points at deleted project ${project.name}" }
            // Unique on the *three*, as ENV_SLOTS is on the server: a project can have several
            // slots in one environment as long as each carries a different qualifier.
            require(slots.none { it.project == project && it.qualifier == qualifier }) {
                "Slot for ${project.name}${qualifierSuffix(qualifier)} already exists in $name"
            }
            return InMemorySlot(this, project, qualifier, description).also { slots += it }
        }
    }

    inner class InMemorySlot(
        val environment: InMemoryEnvironment,
        val project: InMemoryProject,
        val qualifier: String,
        val description: String,
    ) : DemoSlot {

        val admissionRules = mutableListOf<SlotAdmissionRuleSpec>()
        val workflows = mutableListOf<SlotWorkflowSpec>()
        val deployments = mutableListOf<InMemoryDeployment>()

        /** Whether agents may deploy on this slot. */
        var agentsAdmitted: Boolean = false
            private set

        override fun admitAgents() {
            agentsAdmitted = true
        }

        /**
         * What the slot is actually *holding*, which is the last deployment that reached
         * `DONE` and not simply the last one. The server reads the same distinction -
         * `lastDeployedPipeline` against `currentPipeline` - and a slot with a deployment
         * still running is exactly where the two part company.
         */
        val heldBuilds: List<InMemoryBuild>
            get() = deployments.filter { it.stopAt == DeploymentStop.DONE }.map { it.build }

        override fun addAdmissionRule(spec: SlotAdmissionRuleSpec) {
            require(ADMISSION_RULE_NAME.matches(spec.name)) {
                "Admission rule name \"${spec.name}\" starts with a letter and then has letters, " +
                        "digits or dashes only."
            }
            require(admissionRules.none { it.name == spec.name }) {
                "Admission rule ${spec.name} already exists in ${environment.name}/${project.name}"
            }
            admissionRules += spec
        }

        /**
         * The trigger is checked rather than merely recorded: it is a server enum, and a typo in
         * the dataset would only be found by a reset failing against a real instance.
         */
        override fun addWorkflow(spec: SlotWorkflowSpec) {
            require(spec.trigger in SLOT_WORKFLOW_TRIGGERS) {
                "Slot workflow trigger \"${spec.trigger}\" is not one of $SLOT_WORKFLOW_TRIGGERS " +
                        "in ${environment.name}/${project.name}"
            }
            workflows += spec
        }

        /**
         * The rules are checked, not merely recorded. The demo's deployments are a SEQUENCE -
         * an `environment` rule asks what the other slot is holding at that moment - and the
         * one mistake it is easy to make is putting them in an order the server refuses,
         * which on a real instance leaves the demo deleted and the slot empty.
         */
        override fun deploy(
            build: DemoBuild,
            stopAt: DeploymentStop,
            times: DeploymentTimes?,
            message: String?,
            overrides: List<RuleOverrideSpec>,
            token: DemoToken?,
        ) {
            build as InMemoryBuild
            require(build.branch.project == project) {
                "Cannot deploy ${build.branch.project.name} build on the ${project.name} slot"
            }
            val deployer = actorOf(token)
            // The agent policy (#2026): an agent deploys on a slot admitting agents, never overrides a
            // rule, and never answers an approval - a manual rule leaves its deployment a candidate
            if (token is InMemoryAgentToken) {
                check(agentsAdmitted) { "$deployer may not deploy on ${environment.name}/${project.name}: the slot does not admit agents (agent policy)" }
                check(overrides.isEmpty()) { "$deployer may not override an admission rule (agent policy)" }
            }
            // The server's checks of a backdated pipeline: never before its build, never starting
            // before the latest start of the slot - and a deployment at the reset is the latest one
            if (times != null) {
                require(!times.start.isBefore(build.creation)) {
                    "The deployment of ${build.name} on ${environment.name}/${project.name} starts before the build was created"
                }
                require(!times.running.isBefore(times.start) && !times.end.isBefore(times.running)) {
                    "The steps of the deployment of ${build.name} on ${environment.name}/${project.name} go back in time"
                }
                deployments.lastOrNull()?.let { latest ->
                    require(latest.times != null && !times.start.isBefore(latest.times.start)) {
                        "The deployment of ${build.name} on ${environment.name}/${project.name} starts before the latest one of the slot"
                    }
                }
            }
            // A candidate is deliberately allowed to be refused - see [DeploymentStop.CANDIDATE].
            // The checks below ask whether the server would let this deployment START, and a
            // candidate never does - nor does one cancelled before it started.
            overrides.forEach { override ->
                require(admissionRules.any { it.name == override.rule }) {
                    "No admission rule ${override.rule} in ${environment.name}/${project.name}"
                }
            }
            if (stopAt != DeploymentStop.CANDIDATE && stopAt != DeploymentStop.CANCELLED) {
                val overridden = overrides.map { it.rule }.toSet()
                admissionRules.filter { it.name !in overridden }.forEach { rule -> check(rule, build) }
            }
            deployments += InMemoryDeployment(build, stopAt, times, message, overrides, deployer)
            // As `DeploymentTrailEventMapper` writes them, by whoever runs the deployment
            val deployment = "${environment.name}/${project.name}${qualifierSuffix(qualifier)}"
            fun entry(type: String, vararg payload: Pair<String, Any?>) =
                build.entry(type, "deployment" to deployment, *payload, actor = deployer)
            entry("deployment.created")
            overrides.forEach { entry("deployment.rule-overridden", "rule" to it.rule, "message" to it.message) }
            when (stopAt) {
                DeploymentStop.CANDIDATE -> Unit
                DeploymentStop.CANCELLED -> entry("deployment.cancelled")
                DeploymentStop.RUNNING -> entry("deployment.running")
                DeploymentStop.DONE -> {
                    entry("deployment.running")
                    entry("deployment.done")
                }

                DeploymentStop.FAILED -> {
                    entry("deployment.running")
                    entry("deployment.failed")
                }
            }
        }

        private fun check(rule: SlotAdmissionRuleSpec, build: InMemoryBuild) {
            val where = "${environment.name}/${project.name}"
            when (rule.ruleId) {
                SlotAdmissionRules.PROMOTION -> {
                    val promotion = rule.config["promotion"] as? String
                    require(promotion != null && build.promotions.any { it.first == promotion }) {
                        "$where only admits builds promoted to $promotion, and ${build.name} is not."
                    }
                }

                SlotAdmissionRules.BRANCH_PATTERN -> require(branchIncludedByPattern(build.branch.name, rule.config)) {
                    "$where admits no build of ${build.branch.name}, and ${build.name} is one."
                }

                // Nothing in the dataset answers an approval, so the deployment waits on it
                SlotAdmissionRules.MANUAL -> error("$where waits for the approval ${rule.name}, which nobody gave.")

                SlotAdmissionRules.ENVIRONMENT -> {
                    val previousName = rule.config["environmentName"] as? String
                    // The rule names a qualifier as well as an environment, and the default one is
                    // the empty string: with two slots of the same project in one environment,
                    // matching on the project alone would read the wrong slot's history.
                    val previousQualifier = rule.config["qualifier"] as? String ?: ""
                    val previous = environments.find { it.name == previousName }
                        ?.slots?.find { it.project == project && it.qualifier == previousQualifier }
                    // What the other slot is HOLDING, not what it last started: the server's
                    // `environment` rule refuses a previous pipeline which has not reached DONE
                    require(previous?.heldBuilds?.lastOrNull() == build) {
                        "$where only admits what $previousName is holding, which is not ${build.name}."
                    }
                }
            }
        }
    }

    /**
     * One deployment recorded on a slot, and how far the seed took it.
     *
     * The stop is kept rather than collapsed into "deployed": a deployment left `RUNNING` is
     * present on the slot - it is what `currentPipeline` answers with, and what a phone can
     * complete or cancel - but it is not what the slot is holding.
     */
    data class InMemoryDeployment(
        val build: InMemoryBuild,
        val stopAt: DeploymentStop,
        val times: DeploymentTimes? = null,
        val message: String? = null,
        val overrides: List<RuleOverrideSpec> = emptyList(),
        /** Who runs the deployment. */
        val actor: String = SEED_ACTOR,
    )

    /**
     * The assisted change of a build - `AssistedChangeProperty` on the server, without its session links.
     *
     * @property assisted Whether the build is assisted, `null` when it is not known
     */
    data class InMemoryAssistedChange(
        val basis: String,
        val assistants: List<String> = emptyList(),
        val assistedCommits: Int = 0,
        val totalCommits: Int = 0,
    ) {
        val assisted: Boolean? get() = if (basis == "UNKNOWN") null else assistedCommits > 0
    }

    /**
     * One entry of the trail of a build: its type, its actor - `token:<name>`, or [SEED_ACTOR] - and
     * the part of its payload the tests read.
     *
     * @property tampered Whether its payload was rewritten after it was written, which is where the
     * verification of the trail breaks
     */
    data class InMemoryEntry(
        val seq: Int,
        val type: String,
        val actor: String,
        val payload: Map<String, Any?>,
        val tampered: Boolean = false,
    ) {
        override fun toString(): String =
            "#$seq $type by $actor $payload" + if (tampered) " (tampered)" else ""
    }

    /**
     * One run of a `tests` stamp on a build, with the status the server computes from its counts.
     */
    data class InMemoryTestRun(
        val run: TestRunSpec,
        val status: ValidationStatus,
        val at: LocalDateTime,
    )

    /**
     * One security scan posted on a build: what the dataset said, the report the seed rendered
     * from it, and the time the seed dated it at.
     */
    data class InMemoryScan(
        val spec: ScanSpec,
        val report: JsonNode,
        val at: LocalDateTime,
    )

    /**
     * A mock SCM repository, reproducing the only part of `MockSCMExtension` the seed can
     * observe: the ids it derives from the branch and the position of the commit on it.
     */
    class InMemoryScmRepository(val name: String) {

        val issues = mutableListOf<IssueSpec>()
        val commits = mutableListOf<Commit>()

        fun registerIssue(issue: IssueSpec) {
            issues += issue
        }

        fun registerCommit(scmBranch: String, message: String): String {
            val index = commits.count { it.scmBranch == scmBranch } + 1
            val prefix = "${scmBranch.replace("[^a-zA-Z0-9.]".toRegex(), "-")}-$index"
            val digest = MessageDigest.getInstance("SHA-1")
                .digest(prefix.toByteArray())
                .joinToString("") { "%02x".format(it) }
                .take(7)
            val id = "$prefix-$digest"
            commits += Commit(scmBranch, id, message)
            return id
        }

        data class Commit(val scmBranch: String, val id: String, val message: String)
    }

    companion object {

        /**
         * The actor of what the account the seed runs as does by itself, without a token of the
         * dataset.
         */
        const val SEED_ACTOR = "seed"

        /** The statuses a stamp an assisted build requires has to be at - PASSED, or FIXED. */
        private val PASSING = setOf(ValidationStatus.PASSED, ValidationStatus.FIXED)

        /**
         * The assistants of a commit, as the built-in conventions of `SCMCommitAssistants` read them -
         * the part of them the dataset uses: the trailers of the last paragraph, Claude and Codex as
         * co-authors by their addresses, and a Claude session.
         */
        fun assistantsOf(message: String): Set<String> {
            val paragraphs = message.trim().split(Regex("\\n\\s*\\n"))
            if (paragraphs.size < 2) return emptySet()
            return paragraphs.last().lines().mapNotNull { line ->
                val key = line.substringBefore(':', "").trim().lowercase()
                val value = line.substringAfter(':', "").trim()
                when {
                    key == "co-authored-by" && value.endsWith("<noreply@anthropic.com>") -> "Claude Code"
                    key == "co-authored-by" && value.endsWith("<codex@openai.com>") -> "Codex"
                    key == "claude-session" -> "Claude Code"
                    else -> null
                }
            }.toSet()
        }

        /**
         * What Yontrack accepts as an entity name — `NameDescription.NAME` on the server
         * side. Repeated here rather than depended on: this module talks to Yontrack over
         * the API, not over its model classes.
         */
        private val NAME = Regex("[A-Za-z0-9._-]+")

        /**
         * `SlotPipelineStatus` on the server side, as far as a slot workflow is concerned.
         * `CANCELLED` is a state a pipeline reaches, never a moment a workflow is fired at.
         */
        private val SLOT_WORKFLOW_TRIGGERS = listOf("CANDIDATE", "RUNNING", "DONE", "FAILED")

        private fun checkName(name: String, what: String) {
            require(NAME.matches(name)) {
                "$what name \"$name\" can only have letters, digits, dots, dashes or underscores."
            }
        }

    }
}

/**
 * How a qualifier reads beside a slot: " [canary]", or nothing at all for the default one.
 *
 * The empty qualifier is not drawn, here as on screen: every slot has one, and printing "[]" beside
 * every slot of every environment would be noise in a snapshot whose whole value is that a diff
 * means something changed.
 */
private fun qualifierSuffix(qualifier: String): String =
    qualifier.takeIf { it.isNotBlank() }?.let { " [$it]" } ?: ""
