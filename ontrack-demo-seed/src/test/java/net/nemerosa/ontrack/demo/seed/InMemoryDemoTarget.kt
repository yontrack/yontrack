package net.nemerosa.ontrack.demo.seed

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
) : DemoTarget {

    private val projects = mutableListOf<InMemoryProject>()

    /**
     * Mock SCM repositories, held by the instance rather than by the projects — as they are
     * on a real server, where they outlive the projects the reset deletes.
     */
    private val scmRepositories = mutableMapOf<String, InMemoryScmRepository>()
    private val environments = mutableListOf<InMemoryEnvironment>()
    private val dashboards = mutableListOf<InMemoryDashboard>()

    override fun projects(): List<DemoProject> = projects.toList()

    override fun createProject(name: String, description: String): DemoProject {
        checkName(name, "Project")
        require(projects.none { it.name == name }) { "Project $name already exists" }
        return InMemoryProject(name, description).also { projects += it }
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
        return InMemoryEnvironment(name, order, description, tags).also { environments += it }
    }

    override fun checkScmAvailable() {
        check(scmEnabled) { "The mock SCM is not enabled on this instance. Nothing was deleted." }
    }

    override fun dashboards(): List<DemoDashboardHandle> = dashboards.toList()

    override fun saveDashboard(dashboard: DemoDashboard) {
        val sameName = dashboards.find { it.name == dashboard.name }
        require(sameName == null || sameName.dashboard.uuid == dashboard.uuid) {
            "Dashboard ${dashboard.name} already exists under another UUID"
        }
        dashboards.removeIf { it.dashboard.uuid == dashboard.uuid }
        dashboards += InMemoryDashboard(dashboard)
    }

    /**
     * The whole state as text, ordered as it was created — two runs of the seed differ as
     * soon as one line does.
     */
    fun snapshot(): String = buildList {
        projects.forEach { project ->
            add("project ${project.name} \"${project.description}\"")
            if (project.favourite) add("  favourite")
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
                }
                branch.validationStamps.forEach { add("    validation stamp $it") }
                branch.builds.forEach { build ->
                    add("    build ${build.name} \"${build.description}\" at ${build.creation}")
                    build.releaseVersion?.let { add("      release $it") }
                    build.commitId?.let { add("      built from $it") }
                    build.promotions.forEach { add("      promotion ${it.first} at ${it.second}") }
                    build.validations.forEach { add("      validation ${it.stamp} ${it.status} at ${it.at}") }
                    build.links.forEach { add("      uses ${it.branch.project.name}/${it.name}") }
                }
            }
        }
        environments.forEach { environment ->
            add("environment ${environment.name} #${environment.order} \"${environment.description}\" ${environment.tags}")
            environment.slots.forEach { slot ->
                add("  slot ${slot.project.name} \"${slot.description}\"")
                slot.admissionRules.forEach { add("    rule ${it.name} ${it.ruleId} ${it.config}") }
                slot.workflows.forEach { add("    workflow on ${it.trigger}: ${it.yaml.lines().first()}") }
                slot.deployments.forEach {
                    add("    ${if (it.stopAt == DeploymentStop.DONE) "deployed" else "deploying"} ${it.build.name}")
                }
            }
        }
        // Repositories no project points at: the mock SCM holds them on the server, where
        // they outlive the projects the reset deletes.
        scmRepositories.keys
            .filter { name -> projects.none { it.scmRepositoryName == name } }
            .forEach { add("orphan scm $it") }
        dashboards.forEach { held ->
            val dashboard = held.dashboard
            add("dashboard ${dashboard.name} (${dashboard.uuid})")
            dashboard.widgets.forEach { add("  widget ${it.key} ${it.layout} ${it.config}") }
        }
    }.joinToString("\n")

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

        override fun markAsFavourite() {
            favourite = true
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
        val builds = mutableListOf<InMemoryBuild>()
        var scmBranch: String? = null
        val autoPromotions = mutableMapOf<String, AutoPromotionSpec>()
        val promotionDependencies = mutableMapOf<String, List<String>>()
        val previousPromotionRequired = mutableSetOf<String>()

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

        override fun createValidationStamp(name: String, description: String) {
            checkName(name, "Validation stamp")
            require(name !in validationStamps) { "Validation stamp $name already exists in ${project.name}/${this.name}" }
            validationStamps += name
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

        override fun createBuild(name: String, description: String, creation: LocalDateTime): DemoBuild {
            checkName(name, "Build")
            require(builds.none { it.name == name }) { "Build $name already exists in ${project.name}/${this.name}" }
            return InMemoryBuild(this, name, description, creation).also { builds += it }
        }
    }

    inner class InMemoryBuild(
        val branch: InMemoryBranch,
        override val name: String,
        val description: String,
        val creation: LocalDateTime,
    ) : DemoBuild {

        var releaseVersion: String? = null
        // Named for its getter, not for the interface: `commit` would clash with setCommit
        // on the JVM, the same way `releaseVersion` does with setRelease.
        var commitId: String? = null
        val promotions = mutableListOf<Pair<String, LocalDateTime>>()
        val validations = mutableListOf<InMemoryValidation>()
        val links = mutableListOf<InMemoryBuild>()

        override fun setRelease(release: String) {
            releaseVersion = release
        }

        override fun promote(promotionLevel: String, description: String, at: LocalDateTime) {
            require(promotionLevel in branch.promotionLevels) {
                "No promotion level $promotionLevel on ${branch.project.name}/${branch.name}"
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
        }

        override fun validate(
            validationStamp: String,
            status: ValidationStatus,
            description: String,
            at: LocalDateTime,
        ) {
            require(validationStamp in branch.validationStamps) {
                "No validation stamp $validationStamp on ${branch.project.name}/${branch.name}"
            }
            validations += InMemoryValidation(validationStamp, status, at)
        }

        override fun linkTo(build: DemoBuild) {
            links += build as InMemoryBuild
        }

        override fun setCommit(commitId: String) {
            this.commitId = commitId
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

        override fun createSlot(project: DemoProject, description: String): DemoSlot {
            project as InMemoryProject
            require(project in projects) { "Slot points at deleted project ${project.name}" }
            require(slots.none { it.project == project }) { "Slot for ${project.name} already exists in $name" }
            return InMemorySlot(this, project, description).also { slots += it }
        }
    }

    inner class InMemorySlot(
        val environment: InMemoryEnvironment,
        val project: InMemoryProject,
        val description: String,
    ) : DemoSlot {

        val admissionRules = mutableListOf<SlotAdmissionRuleSpec>()
        val workflows = mutableListOf<SlotWorkflowSpec>()
        val deployments = mutableListOf<InMemoryDeployment>()

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
        override fun deploy(build: DemoBuild, stopAt: DeploymentStop) {
            build as InMemoryBuild
            require(build.branch.project == project) {
                "Cannot deploy ${build.branch.project.name} build on the ${project.name} slot"
            }
            admissionRules.forEach { rule -> check(rule, build) }
            deployments += InMemoryDeployment(build, stopAt)
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

                SlotAdmissionRules.ENVIRONMENT -> {
                    val previousName = rule.config["environmentName"] as? String
                    val previous = environments.find { it.name == previousName }
                        ?.slots?.find { it.project == project }
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
    )

    /**
     * One run of a validation stamp on a build, with the time the seed dated it at — which is
     * a fact about the demo the same way a promotion's time is (#1718).
     */
    data class InMemoryValidation(
        val stamp: String,
        val status: ValidationStatus,
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
         * What Yontrack accepts as an entity name — `NameDescription.NAME` on the server
         * side. Repeated here rather than depended on: this module talks to Yontrack over
         * the API, not over its model classes.
         */
        private val NAME = Regex("[A-Za-z0-9._-]+")

        /**
         * `SlotPipelineStatus` on the server side, as far as a slot workflow is concerned.
         * `CANCELLED` is a state a pipeline reaches, never a moment a workflow is fired at.
         */
        private val SLOT_WORKFLOW_TRIGGERS = listOf("CANDIDATE", "RUNNING", "DONE")

        private fun checkName(name: String, what: String) {
            require(NAME.matches(name)) {
                "$what name \"$name\" can only have letters, digits, dots, dashes or underscores."
            }
        }

    }
}
