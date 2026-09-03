package net.nemerosa.ontrack.demo.seed

import java.time.Clock
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
                slotSpec.deployed?.let { ref ->
                    log("Deploying ${ref.build} of ${ref.project} to ${spec.name}")
                    slot.deploy(builds.resolve(ref))
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
        spec.promotionLevels.forEach { branch.createPromotionLevel(it.name, it.description, it.workflow) }
        spec.validationStamps.forEach { branch.createValidationStamp(it.name, it.description) }
        spec.builds.forEach { buildSpec ->
            val creation = buildSpec.creation.resolve(now)
            val build = branch.createBuild(buildSpec.name, buildSpec.description, creation)
            builds[BuildRef(projectSpec.name, spec.name, buildSpec.name)] = build
            buildSpec.release?.let { build.setRelease(it) }
            // One hour per rung, so the promotions of a build are ordered and the lead
            // time charts have something other than a flat zero to draw.
            buildSpec.promotionLevels.forEachIndexed { index, promotionLevel ->
                build.promote(promotionLevel, "", creation.plusHours(index + 1L))
            }
            buildSpec.validations.forEach { validation ->
                build.validate(validation.validationStamp, validation.status, validation.description)
            }
        }
    }

    // validate() has already ruled out a reference the dataset does not create.
    private fun Map<BuildRef, DemoBuild>.resolve(ref: BuildRef): DemoBuild =
        getValue(ref)
}
