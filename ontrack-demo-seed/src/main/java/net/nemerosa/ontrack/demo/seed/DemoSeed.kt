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
 * Settings are covered by CasC and users live in Keycloak, so projects, environments and
 * the demo dashboard are the only things this has to reset.
 *
 * @param clock Read once per run, so every build creation time in one run shares a
 * reference. Injected so that a test can pin it and compare two runs.
 */
class DemoSeed(
    private val target: DemoTarget,
    private val clock: Clock = Clock.systemUTC(),
    private val log: (String) -> Unit = ::println,
) {

    fun run(dataset: DemoDataset) {
        // Before anything is deleted: a dataset the server would reject must not cost the
        // demo its current content.
        dataset.validate()
        // Same reason, one step further: a dataset can be valid and still ask the instance
        // for something it does not run.
        if (dataset.projects.any { it.scm != null }) {
            target.checkScmAvailable()
        }
        val now = LocalDateTime.now(clock)
        reset()
        create(dataset, now)
    }

    /**
     * Environments before projects: a slot belongs to both, and deleting the environment
     * takes its slots with it whatever the project deletion happens to cascade.
     *
     * Dashboards go too. They are not projects, but a dashboard a visitor saved — or one an
     * older seed left under a name this one no longer uses — would otherwise outlive every
     * reset, and the demo's state is meant to be a function of the build.
     */
    private fun reset() {
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
    }

    private fun create(dataset: DemoDataset, now: LocalDateTime) {
        val projects = mutableMapOf<String, DemoProject>()
        val builds = mutableMapOf<BuildRef, DemoBuild>()

        dataset.projects.forEach { spec ->
            log("Creating project ${spec.name}")
            val project = target.createProject(spec.name, spec.description)
            projects[spec.name] = project
            // Before the branches: a branch maps onto a branch of the repository the
            // project is pointed at here, and a commit is linked to its issues as it is
            // registered, so the issues have to be in place first.
            spec.scm?.let { project.configureScm(it) }
            if (spec.favourite) project.markAsFavourite()
            spec.branches.forEach { branchSpec ->
                createBranch(spec, branchSpec, project, now, builds)
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

        val slots = mutableMapOf<Pair<String, String>, DemoSlot>()
        dataset.environments.forEach { spec ->
            log("Creating environment ${spec.name}")
            val environment = target.createEnvironment(
                name = spec.name,
                order = spec.order,
                description = spec.description,
                tags = spec.tags,
            )
            spec.slots.forEach { slotSpec ->
                val slot = environment.createSlot(projects.getValue(slotSpec.project), slotSpec.description)
                // Before any deployment: the rules are what a deployment is checked against, and
                // adding them afterwards would leave the slot holding a build it now refuses
                slotSpec.admissionRules.forEach { ruleSpec ->
                    log("Adding admission rule ${ruleSpec.name} to slot ${spec.name}/${slotSpec.project}")
                    slot.addAdmissionRule(ruleSpec)
                }
                slots[spec.name to slotSpec.project] = slot
            }
        }

        // After every slot exists, and in declaration order: an `environment` admission rule asks
        // what is deployed in another slot at that moment
        dataset.deployments.forEach { spec ->
            val ref = spec.build
            log("Deploying ${ref.build} of ${ref.project}/${ref.branch} to ${spec.environment} (${spec.stopAt})")
            slots.getValue(spec.environment to ref.project).deploy(builds.resolve(ref), spec.stopAt)
        }

        // AFTER the deployments, unlike the admission rules. A `CANDIDATE` or `RUNNING` workflow is
        // a hard gate: the deployment cannot start, or cannot finish, until it has passed, and a
        // workflow runs asynchronously. Configuring one first would leave every deployment below
        // racing a workflow, which is how a reset that has to be reliable becomes flaky.
        dataset.environments.forEach { spec ->
            spec.slots.forEach { slotSpec ->
                slotSpec.workflows.forEach { workflowSpec ->
                    log("Adding ${workflowSpec.trigger} workflow to slot ${spec.name}/${slotSpec.project}")
                    slots.getValue(spec.name to slotSpec.project).addWorkflow(workflowSpec)
                }
            }
        }

        dataset.dashboard?.let { dashboard ->
            log("Saving dashboard ${dashboard.name}")
            target.saveDashboard(dashboard)
        }
    }

    private fun createBranch(
        projectSpec: ProjectSpec,
        spec: BranchSpec,
        project: DemoProject,
        now: LocalDateTime,
        builds: MutableMap<BuildRef, DemoBuild>,
    ) {
        val branch = project.createBranch(spec.name, spec.description)
        spec.scmBranch?.let { branch.configureScmBranch(it) }
        if (spec.favourite) branch.markAsFavourite()
        spec.promotionLevels.forEach { branch.createPromotionLevel(it.name, it.description, it.workflow) }
        spec.validationStamps.forEach { branch.createValidationStamp(it.name, it.description) }
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
        }
        spec.builds.forEach { buildSpec ->
            val creation = buildSpec.creation.resolve(now)
            val build = branch.createBuild(buildSpec.name, buildSpec.description, creation)
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
            val validationCount = buildSpec.validations.size
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
            // The promotions are still CREATED before the validations, whatever the times say.
            // `AutoPromotionEventListener` promotes a build the moment a run completes the set a
            // level names, and it stamps that run with the time of the call rather than with the
            // time of the validation - so seeding the runs first would hand the demo a second,
            // same-level promotion dated at the reset, which is the very reading this is fixing.
            buildSpec.promotionLevels.forEachIndexed { index, promotionLevel ->
                build.promote(promotionLevel, "", creation.plus(step.multipliedBy(validationCount + index + 1L)))
            }
            buildSpec.validations.forEachIndexed { index, validation ->
                build.validate(
                    validation.validationStamp,
                    validation.status,
                    validation.description,
                    creation.plus(step.multipliedBy(index + 1L)),
                )
            }
        }
    }

    // validate() has already ruled out a reference the dataset does not create.
    private fun Map<BuildRef, DemoBuild>.resolve(ref: BuildRef): DemoBuild =
        getValue(ref)
}
