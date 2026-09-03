package net.nemerosa.ontrack.demo.seed

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
class InMemoryDemoTarget : DemoTarget {

    private val projects = mutableListOf<InMemoryProject>()
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
            project.branches.forEach { branch ->
                add("  branch ${branch.name} \"${branch.description}\"")
                branch.promotionLevels.forEach { add("    promotion level $it") }
                branch.validationStamps.forEach { add("    validation stamp $it") }
                branch.builds.forEach { build ->
                    add("    build ${build.name} \"${build.description}\" at ${build.creation}")
                    build.releaseVersion?.let { add("      release $it") }
                    build.promotions.forEach { add("      promotion ${it.first} at ${it.second}") }
                    build.validations.forEach { add("      validation ${it.first} ${it.second}") }
                    build.links.forEach { add("      uses ${it.branch.project.name}/${it.name}") }
                }
            }
        }
        environments.forEach { environment ->
            add("environment ${environment.name} #${environment.order} \"${environment.description}\" ${environment.tags}")
            environment.slots.forEach { slot ->
                add("  slot ${slot.project.name} \"${slot.description}\"")
                slot.deployments.forEach { add("    deployed ${it.name}") }
            }
        }
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

        override fun delete() {
            projects -= this
        }

        override fun createBranch(name: String, description: String): DemoBranch {
            checkName(name, "Branch")
            require(branches.none { it.name == name }) { "Branch $name already exists in ${this.name}" }
            return InMemoryBranch(this, name, description).also { branches += it }
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
        val promotions = mutableListOf<Pair<String, LocalDateTime>>()
        val validations = mutableListOf<Pair<String, ValidationStatus>>()
        val links = mutableListOf<InMemoryBuild>()

        override fun setRelease(release: String) {
            releaseVersion = release
        }

        override fun promote(promotionLevel: String, description: String, at: LocalDateTime) {
            require(promotionLevel in branch.promotionLevels) {
                "No promotion level $promotionLevel on ${branch.project.name}/${branch.name}"
            }
            promotions += promotionLevel to at
        }

        override fun validate(validationStamp: String, status: ValidationStatus, description: String) {
            require(validationStamp in branch.validationStamps) {
                "No validation stamp $validationStamp on ${branch.project.name}/${branch.name}"
            }
            validations += validationStamp to status
        }

        override fun linkTo(build: DemoBuild) {
            links += build as InMemoryBuild
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
            return InMemorySlot(project, description).also { slots += it }
        }
    }

    inner class InMemorySlot(
        val project: InMemoryProject,
        val description: String,
    ) : DemoSlot {

        val deployments = mutableListOf<InMemoryBuild>()

        override fun deploy(build: DemoBuild) {
            build as InMemoryBuild
            require(build.branch.project == project) {
                "Cannot deploy ${build.branch.project.name} build on the ${project.name} slot"
            }
            deployments += build
        }
    }

    companion object {

        /**
         * What Yontrack accepts as an entity name — `NameDescription.NAME` on the server
         * side. Repeated here rather than depended on: this module talks to Yontrack over
         * the API, not over its model classes.
         */
        private val NAME = Regex("[A-Za-z0-9._-]+")

        private fun checkName(name: String, what: String) {
            require(NAME.matches(name)) {
                "$what name \"$name\" can only have letters, digits, dots, dashes or underscores."
            }
        }
    }
}
