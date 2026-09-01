package net.nemerosa.ontrack.demo.seed

/**
 * Checks a dataset against the rules Yontrack would enforce, before anything is deleted.
 *
 * The seed is destructive by design, and it deletes before it creates. Without this, a
 * dataset the server rejects half-way through — an illegal name, a promotion level the
 * branch never declares — leaves the demo wiped and partly rebuilt, which is worse than
 * either the old demo or the new one. "Destructive by design" must not mean "blank on
 * failure".
 *
 * @throws IllegalArgumentException with every problem found, not just the first: fixing a
 * dataset one error per run is a poor way to spend a reset.
 */
fun DemoDataset.validate() {
    val problems = mutableListOf<String>()

    fun checkName(name: String, what: String) {
        if (!ENTITY_NAME.matches(name)) {
            problems += "$what name \"$name\" can only have letters, digits, dots, dashes or underscores."
        }
    }

    val buildRefs = mutableSetOf<BuildRef>()

    projects.forEach { project ->
        checkName(project.name, "Project")
        project.branches.forEach { branch ->
            checkName(branch.name, "Branch")
            val promotionLevels = branch.promotionLevels.map { it.name }.toSet()
            val validationStamps = branch.validationStamps.map { it.name }.toSet()
            branch.promotionLevels.forEach { checkName(it.name, "Promotion level") }
            branch.validationStamps.forEach { checkName(it.name, "Validation stamp") }
            branch.builds.forEach { build ->
                checkName(build.name, "Build")
                buildRefs += BuildRef(project.name, branch.name, build.name)
                build.promotionLevels.forEach { promotionLevel ->
                    if (promotionLevel !in promotionLevels) {
                        problems += "Build ${build.name} of ${project.name}/${branch.name} " +
                                "is promoted to $promotionLevel, which the branch does not declare."
                    }
                }
                build.validations.forEach { validation ->
                    if (validation.validationStamp !in validationStamps) {
                        problems += "Build ${build.name} of ${project.name}/${branch.name} " +
                                "is validated against ${validation.validationStamp}, " +
                                "which the branch does not declare."
                    }
                }
            }
        }
    }

    // Links and deployments point at builds by name, and are only resolvable once every
    // project has been walked.
    projects.forEach { project ->
        project.branches.forEach { branch ->
            branch.builds.forEach { build ->
                build.links.forEach { ref ->
                    if (ref !in buildRefs) {
                        problems += "Build ${build.name} of ${project.name}/${branch.name} " +
                                "uses ${ref.build} of ${ref.project}/${ref.branch}, " +
                                "which the dataset never creates."
                    }
                }
            }
        }
    }

    val projectNames = projects.map { it.name }.toSet()
    environments.forEach { environment ->
        checkName(environment.name, "Environment")
        environment.slots.forEach { slot ->
            if (slot.project !in projectNames) {
                problems += "The ${environment.name} environment has a slot for ${slot.project}, " +
                        "which the dataset never creates."
            }
            slot.deployed?.let { ref ->
                if (ref !in buildRefs) {
                    problems += "The ${environment.name} environment deploys ${ref.build} of " +
                            "${ref.project}/${ref.branch}, which the dataset never creates."
                } else if (ref.project != slot.project) {
                    problems += "The ${environment.name} environment deploys a ${ref.project} build " +
                            "on the ${slot.project} slot."
                }
            }
        }
    }

    require(problems.isEmpty()) {
        "The demo dataset is not valid, and nothing was deleted:\n" +
                problems.joinToString("\n") { "- $it" }
    }
}

/**
 * What Yontrack accepts as an entity name — `NameDescription.NAME` on the server side.
 * Repeated here rather than depended on: this module talks to Yontrack over its API, not
 * over its model classes.
 */
private val ENTITY_NAME = Regex("[A-Za-z0-9._-]+")
