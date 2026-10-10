package net.nemerosa.ontrack.demo.seed

import java.time.LocalDateTime

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

    // Labels first: a project names them, so what is declared here has to be known before the
    // projects are walked.
    val labelDisplays = mutableSetOf<String>()
    labels.forEach { label ->
        label.category?.let { category ->
            if (!LABEL_NAME.matches(category)) {
                problems += "Label category \"$category\" can only have letters, digits, dots, " +
                        "dashes or underscores."
            }
        }
        if (!LABEL_NAME.matches(label.name)) {
            problems += "Label name \"${label.name}\" can only have letters, digits, dots, " +
                    "dashes or underscores."
        }
        if (!LABEL_COLOR.matches(label.color)) {
            problems += "The ${label.display} label is coloured \"${label.color}\"; " +
                    "a label colour is a #RRGGBB string."
        }
        if (!labelDisplays.add(label.display)) {
            problems += "The dataset declares the ${label.display} label twice, and Yontrack " +
                    "refuses a second label of the same category and name."
        }
    }

    // Agents next: builds and deployments name them, by slug
    val agentSlugs = mutableSetOf<String>()
    agents.forEach { agent ->
        if (!AGENT_SLUG.matches(agent.slug)) {
            problems += "Agent slug \"${agent.slug}\" must have 1 to 32 lowercase letters, digits or dashes."
        }
        if (!agentSlugs.add(agent.slug)) {
            problems += "The dataset declares the agent ${agent.identifier} twice."
        }
        if (agent.displayName.isBlank() || agent.tool.isBlank()) {
            problems += "Agent ${agent.identifier} needs a display name and a tool."
        }
    }

    /**
     * What the server would make of an agent session: an agent it knows, an identifier it keeps, and
     * a link it does not drop - it drops one which is not an absolute `https` URL, with a warning only.
     */
    fun checkSession(session: AgentSessionSpec, where: String) {
        if (session.agent !in agentSlugs) {
            problems += "$where is run by the agent ${session.agent}, which the dataset never registers."
        }
        if (session.id.isBlank() || session.id.length > 255) {
            problems += "$where names an agent session of 1 to 255 characters, not \"${session.id}\"."
        }
        session.link?.let { link ->
            if (!isAbsoluteHttps(link)) {
                problems += "$where links to the agent session \"$link\", which is not an absolute https URL: " +
                        "the server would drop it."
            }
        }
    }

    // Creation times are resolved against one arbitrary but fixed instant: `DaysAgo` is
    // monotonic in it, so which instant it is does not change the order it yields. Only a
    // branch mixing `DaysAgo` and `At` builds could read differently under another one, and
    // a branch doing that has no stable order to check in the first place.
    val reference = LocalDateTime.of(2000, 1, 1, 0, 0)

    projects.forEach { project ->
        checkName(project.name, "Project")
        // A label naming nothing is a typo here for the same reason a promotion dependency is:
        // the seed would fail half-way through the reset, with the demo already deleted.
        project.labels.forEach { label ->
            if (label !in labelDisplays) {
                problems += "Project ${project.name} carries the $label label, " +
                        "which the dataset never creates."
            }
        }
        project.labels.groupingBy { it }.eachCount()
            .filterValues { it > 1 }
            .keys
            .forEach { label ->
                problems += "Project ${project.name} carries the $label label twice."
            }
        project.scm?.issues?.forEach { issue ->
            if (!ISSUE_KEY.matches(issue.key)) {
                problems += "The ${project.name} project declares the issue \"${issue.key}\", " +
                        "which the mock issue service would not recognise in a commit message: " +
                        "it reads keys of the ABC-123 shape only."
            }
        }
        project.branches.forEach { branch ->
            checkName(branch.name, "Branch")
            if (branch.scmBranch != null && project.scm == null) {
                problems += "Branch ${branch.name} of ${project.name} follows the SCM branch " +
                        "\"${branch.scmBranch}\", but the project declares no SCM."
            }
            val promotionLevels = branch.promotionLevels.map { it.name }.toSet()
            val validationStamps = branch.validationStamps.map { it.name }.toSet()
            branch.promotionLevels.forEach { checkName(it.name, "Promotion level") }
            branch.validationStamps.forEach { checkName(it.name, "Validation stamp") }
            val findingsStamps = branch.validationStamps.filter { it.findings != null }.map { it.name }.toSet()
            val testsStamps = branch.validationStamps.filter { it.tests }.map { it.name }.toSet()
            (findingsStamps intersect testsStamps).forEach { stamp ->
                problems += "Validation stamp $stamp of ${project.name}/${branch.name} is both a " +
                        "security-findings stamp and a tests one; a stamp has one data type."
            }
            branch.validationStamps.forEach { stamp ->
                val thresholds = stamp.findings ?: return@forEach
                // UNKNOWN is counted and shown, but never trips a threshold: one set at UNKNOWN
                // would read as a rule and never fire
                if (FindingSeverity.UNKNOWN in listOf(thresholds.warningLevel, thresholds.failedLevel)) {
                    problems += "Validation stamp ${stamp.name} of ${project.name}/${branch.name} has " +
                            "a threshold at UNKNOWN, which never trips."
                }
            }
            // The two promotion properties name other entities of the same branch, and a name
            // matching nothing there is a typo rather than a demonstration. The product accepts
            // such a name - it is exactly what the delivery map draws as an unresolved checkpoint,
            // see #1705 - but curated content must not carry one: nobody looking at the demo can
            // tell a deliberate one from a mistake.
            // A deleted stamp takes its runs with it, and an auto promotion naming it would name an
            // entity which is gone by the end of the reset
            val deletedStamps = branch.validationStamps.filter { it.deleted }.map { it.name }.toSet()
            branch.promotionLevels.forEach { promotionLevel ->
                promotionLevel.autoPromotion?.validationStamps?.filter { it in deletedStamps }?.forEach { stamp ->
                    problems += "Promotion level ${promotionLevel.name} of ${project.name}/${branch.name} is " +
                            "auto promoted by $stamp, which the dataset deletes."
                }
            }
            branch.promotionLevels.forEach { promotionLevel ->
                val where = "Promotion level ${promotionLevel.name} of ${project.name}/${branch.name}"
                promotionLevel.dependsOn.forEach { dependency ->
                    if (dependency == promotionLevel.name) {
                        problems += "$where requires itself."
                    } else if (dependency !in promotionLevels) {
                        problems += "$where requires $dependency, which the branch does not declare."
                    }
                }
                promotionLevel.autoPromotion?.let { autoPromotion ->
                    // The server treats an auto promotion naming nothing as absent -
                    // `AutoPromotionProperty.isEmpty` and the listener returns before promoting
                    // anything - so this is dead configuration rather than a hazard. Curated
                    // content must not carry it either way: it draws no edge and grants nothing,
                    // and a reader would take it for a rule that does.
                    if (autoPromotion.validationStamps.isEmpty() &&
                        autoPromotion.promotionLevels.isEmpty() &&
                        autoPromotion.include.isBlank()
                    ) {
                        problems += "$where is auto promoted by nothing at all."
                    }
                    listOf(
                        autoPromotion.validationStamps to validationStamps,
                        autoPromotion.promotionLevels to promotionLevels,
                    ).forEach { (named, declared) ->
                        named.filterNot { it in declared }.forEach { missing ->
                            problems += "$where is auto promoted by $missing, " +
                                    "which the branch does not declare."
                        }
                    }
                    // A pattern selecting nothing draws no aggregate checkpoint and grants the
                    // promotion the moment anything else it names is satisfied - it reads as
                    // configuration and behaves as none.
                    if (autoPromotion.include.isNotBlank() &&
                        validationStamps.none { autoPromotionSelectsStamp(it, autoPromotion) }
                    ) {
                        problems += "$where is auto promoted by validation stamps matching " +
                                "\"${autoPromotion.include}\", which selects none of the branch's."
                    }
                }
            }
            // The condition constrains the level immediately below in the branch's own order, so
            // the dataset's declaration order IS the configuration here
            val promotionOrder = branch.promotionLevels.map { it.name }
            branch.promotionLevels.forEachIndexed { index, promotionLevel ->
                if (!promotionLevel.requiresPreviousPromotion) return@forEachIndexed
                val where = "Promotion level ${promotionLevel.name} of ${project.name}/${branch.name}"
                if (index == 0) {
                    problems += "$where requires the previous promotion, but it is the first of the " +
                            "branch and has none: the condition constrains nothing and draws nothing."
                } else {
                    val previous = promotionOrder[index - 1]
                    // Decision 5 of #1710: a requires duplicating an unlocks is not drawn, so a demo
                    // showing the condition on a pair which auto promotes would show nothing at all
                    if (previous in promotionLevel.autoPromotion?.promotionLevels.orEmpty()) {
                        problems += "$where requires the previous promotion, $previous, which also " +
                                "auto promotes into it: the delivery map draws the unlocks only."
                    }
                }
            }
            val previousRequiredBy = branch.promotionLevels
                .filter { it.requiresPreviousPromotion }
                .mapNotNull { spec ->
                    promotionOrder.indexOf(spec.name).takeIf { it > 0 }
                        ?.let { spec.name to promotionOrder[it - 1] }
                }
                .toMap()
            val dependenciesOf = branch.promotionLevels.associate { it.name to it.dependsOn }
            // The condition is written with the names of stamps of the branch, and one naming nothing
            // there can never pass: every assisted build would be blocked for a reason nobody can fix
            branch.promotionLevels.forEach { promotionLevel ->
                promotionLevel.assistedBuildsRequire.forEach { stamp ->
                    if (stamp !in validationStamps || stamp in deletedStamps) {
                        problems += "Promotion level ${promotionLevel.name} of ${project.name}/${branch.name} " +
                                "requires $stamp of assisted builds, which the branch does not keep."
                    }
                }
            }
            val nonAgentStamps = branch.validationStamps.filter { it.nonAgentEvidence }.map { it.name }.toSet()
            branch.builds.forEach { build ->
                checkName(build.name, "Build")
                buildRefs += BuildRef(project.name, branch.name, build.name)
                // Promotions are granted in the order they are declared, and the server refuses one
                // whose dependencies are not already granted - so the order is load-bearing here in
                // the same way the order of the deployments is.
                val promoted = mutableListOf<String>()
                build.promotionLevels.forEach { promotionLevel ->
                    missingPromotionDependency(promoted, dependenciesOf[promotionLevel].orEmpty())
                        ?.let { missing ->
                            problems += "Build ${build.name} of ${project.name}/${branch.name} " +
                                    "is promoted to $promotionLevel before $missing, which it requires."
                        }
                    previousRequiredBy[promotionLevel]?.takeIf { it !in promoted }?.let { previous ->
                        problems += "Build ${build.name} of ${project.name}/${branch.name} " +
                                "is promoted to $promotionLevel before $previous, which comes " +
                                "before it on the branch."
                    }
                    promoted += promotionLevel
                }
                build.agent?.let { session ->
                    val where = "Build ${build.name} of ${project.name}/${branch.name}"
                    checkSession(session, where)
                    if (build.token != null) {
                        problems += "$where is created both through the token ${build.token} and by the " +
                                "agent ${session.agent}."
                    }
                    // Only the plain runs are handed over to a person: a test run or a scan goes through
                    // whoever created the build, and the server would refuse the agent's
                    (build.tests.map { it.validationStamp } + build.scans.map { it.validationStamp })
                        .filter { it in nonAgentStamps }
                        .forEach { stamp ->
                            problems += "$where posts a run of $stamp as the agent ${session.agent}, " +
                                    "and the stamp takes evidence from non-agents only."
                        }
                }
                if (build.token != null && !TOKEN_NAME.matches(build.token)) {
                    problems += "Build ${build.name} of ${project.name}/${branch.name} is created through " +
                            "the token \"${build.token}\"; a token name has letters, digits, dots, dashes " +
                            "or underscores only."
                }
                build.tampering?.let { tampering ->
                    val where = "Build ${build.name} of ${project.name}/${branch.name}"
                    // Without the switch the project is left out; without the requirement it would
                    // be seeded, and the reset would fail on the tampering, at its very end
                    if (DemoCapability.TRAIL_TAMPERING !in project.requires) {
                        problems += "$where tampers with its trail, but ${project.name} does not require " +
                                "${DemoCapability.TRAIL_TAMPERING.display}."
                    }
                    if (tampering.seq < 1) {
                        problems += "$where tampers with the entry ${tampering.seq} of its trail; entries " +
                                "are numbered from 1."
                    }
                    if (tampering.payload.isEmpty()) {
                        problems += "$where tampers with the entry ${tampering.seq} of its trail, and " +
                                "changes nothing in it."
                    }
                }
                build.validations.forEach { validation ->
                    val where = "The ${validation.validationStamp} validation of build ${build.name} of " +
                            "${project.name}/${branch.name}"
                    if (!isInitialStatus(validation.status)) {
                        problems += "$where is created ${validation.status}, which is only a status a run " +
                                "is given afterwards."
                    }
                    validation.statusChanges.fold(validation.status) { from, change ->
                        if (!statusChangeAllowed(from, change.status)) {
                            problems += "$where goes from $from to ${change.status}, which Yontrack does not allow."
                        }
                        change.status
                    }
                    validation.evidence.forEach { evidence ->
                        if (evidence.fileName.isBlank() || '/' in evidence.fileName || '\\' in evidence.fileName) {
                            problems += "$where attaches an evidence named \"${evidence.fileName}\"; " +
                                    "an evidence is named by a file name, not a path."
                        }
                        if (!MEDIA_TYPE.matches(evidence.mediaType)) {
                            problems += "$where attaches ${evidence.fileName} as \"${evidence.mediaType}\", " +
                                    "which is not a type/subtype media type."
                        }
                        if (!EvidenceFiles.exists(evidence.resource)) {
                            problems += "$where attaches ${evidence.fileName} from ${evidence.resource}, " +
                                    "which is not in the evidence files of the demo seed."
                        }
                    }
                }
                if (build.commits.isNotEmpty() && branch.scmBranch == null) {
                    problems += "Build ${build.name} of ${project.name}/${branch.name} declares " +
                            "commits, but the branch follows no SCM branch."
                }
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
                    } else if (validation.validationStamp in findingsStamps) {
                        // The server computes the status of a findings run from its report, and
                        // refuses one given a status and no report
                        problems += "Build ${build.name} of ${project.name}/${branch.name} " +
                                "is validated against ${validation.validationStamp} with a status, " +
                                "but it is a security-findings stamp: declare a scan instead."
                    } else if (validation.validationStamp in testsStamps) {
                        // A run with a status and no counts is invisible to the test readings, which
                        // is the only reason the stamp is a tests one
                        problems += "Build ${build.name} of ${project.name}/${branch.name} " +
                                "is validated against ${validation.validationStamp} with a status, " +
                                "but it is a tests stamp: declare a test run instead."
                    }
                }
                build.tests.forEach { run ->
                    val where = "The ${run.validationStamp} test run of build ${build.name} of " +
                            "${project.name}/${branch.name}"
                    if (run.validationStamp !in validationStamps) {
                        problems += "$where names a validation stamp the branch does not declare."
                    } else if (run.validationStamp !in testsStamps) {
                        problems += "$where is posted on a stamp which is not a tests one."
                    }
                    if (run.passed < 0 || run.skipped < 0 || run.failed < 0) {
                        problems += "$where counts a negative number of tests."
                    }
                }
                build.scans.forEach { scan ->
                    val where = "The ${scan.validationStamp} scan of build ${build.name} of " +
                            "${project.name}/${branch.name}"
                    if (scan.validationStamp !in validationStamps) {
                        problems += "$where names a validation stamp the branch does not declare."
                    } else if (scan.validationStamp !in findingsStamps) {
                        problems += "$where is posted on a stamp which is not a security-findings one."
                    }
                    scan.findings.forEach { finding ->
                        if (finding.externalId.isBlank() || finding.title.isBlank()) {
                            problems += "$where reports a finding without an external ID or a title."
                        }
                        // SARIF has no field for an expiry: the report would silently tell the
                        // server the acceptance never lapses
                        if (scan.format == ScanFormat.SARIF && finding.acceptance?.expiresInDays != null) {
                            problems += "$where is in SARIF, which has no field for the expiry of the " +
                                    "acceptance of ${finding.externalId}."
                        }
                    }
                }
            }
            // Yontrack orders the builds of a branch by creation ORDER, newest first: the
            // build created last is the one every view shows first, whatever creation time
            // it carries. A dataset declaring its builds newest first therefore reads
            // backwards everywhere - the pipeline timeline and the builds table alike.
            branch.builds.map { it.creation.resolve(reference) }
                .zipWithNext()
                .forEachIndexed { index, (previous, next) ->
                    if (next < previous) {
                        problems += "Builds of ${project.name}/${branch.name} must be declared " +
                                "oldest first: ${branch.builds[index + 1].name} is older than " +
                                "${branch.builds[index].name}."
                    }
                }
        }
    }

    // A project requiring a capability may be left out of the reset, and a link to one of its builds
    // would then point at nothing - from another project, that is: its own links go with it
    val optionalProjects = projects.filter { it.requires.isNotEmpty() }.map { it.name }.toSet()

    // Links and deployments point at builds by name, and are only resolvable once every
    // project has been walked.
    projects.forEach { project ->
        project.branches.forEach { branch ->
            branch.builds.forEach { build ->
                build.links.forEach { ref ->
                    if (ref.project in optionalProjects && ref.project != project.name) {
                        problems += "Build ${build.name} of ${project.name}/${branch.name} uses " +
                                "${ref.build} of ${ref.project}, which is left out of the reset on an " +
                                "instance which cannot offer what it requires."
                    }
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
        environment.slots
            .groupBy { it.project to it.qualifier }
            .filterValues { it.size > 1 }
            .keys
            .forEach { (project, qualifier) ->
                // The server refuses the second one - ENV_SLOTS is unique on the three - and the
                // seed would stop half way through, leaving the demo deleted and not rebuilt.
                problems += "The ${environment.name} environment declares the $project slot" +
                        (qualifier.takeIf { it.isNotBlank() }?.let { " [$it]" } ?: "") +
                        " more than once."
            }
        environment.slots.forEach { slot ->
            if (slot.project !in projectNames) {
                problems += "The ${environment.name} environment has a slot for ${slot.project}, " +
                        "which the dataset never creates."
            }
            slot.admissionRules.forEach { rule ->
                if (!ADMISSION_RULE_NAME.matches(rule.name)) {
                    problems += "The ${environment.name}/${slot.project} slot names an admission " +
                            "rule \"${rule.name}\"; a rule name starts with a letter and then has " +
                            "letters, digits or dashes only."
                }
            }
            slot.workflows.forEach { workflow ->
                if (workflow.trigger !in SLOT_WORKFLOW_TRIGGERS) {
                    problems += "The ${environment.name}/${slot.project} slot has a workflow on " +
                            "\"${workflow.trigger}\", which is not one of $SLOT_WORKFLOW_TRIGGERS."
                }
            }
        }
    }

    // A deployment the server would refuse is the expensive kind of mistake: the demo would be
    // deleted, rebuilt, and left with an empty slot. The two rules checkable from the dataset
    // alone are checked here. The `environment` rule is not - it depends on what is deployed at
    // that point in the sequence, which is the server's own reading of its own state.
    val builds = projects.flatMap { project ->
        project.branches.flatMap { branch ->
            branch.builds.map { BuildRef(project.name, branch.name, it.name) to it }
        }
    }.toMap()
    deployments.forEach { deployment ->
        val ref = deployment.build
        val environment = environments.find { it.name == deployment.environment }
        // Matched on the qualifier too: two slots of the same project in the same environment are
        // two different slots, and deploying to the wrong one is exactly the mistake this catches.
        val slot = environment?.slots?.find {
            it.project == ref.project && it.qualifier == deployment.qualifier
        }
        when {
            environment == null ->
                problems += "A deployment names the ${deployment.environment} environment, " +
                        "which the dataset never creates."

            slot == null ->
                problems += "A deployment puts a ${ref.project} build in ${deployment.environment}" +
                        (deployment.qualifier.takeIf { it.isNotBlank() }?.let { " [$it]" } ?: "") +
                        ", which has no slot for that project and qualifier."

            ref !in buildRefs ->
                problems += "The ${deployment.environment} environment deploys ${ref.build} of " +
                        "${ref.project}/${ref.branch}, which the dataset never creates."

            // A deployment left as a candidate is the one case where the rules are ALLOWED to
            // refuse the build: being blocked is what the dataset is asking for (#1792). The
            // checks below say "the server would accept this deployment", which is a question
            // about starting one, and nothing starts here.
            deployment.stopAt == DeploymentStop.CANDIDATE || deployment.stopAt == DeploymentStop.CANCELLED -> Unit

            else -> {
                val build = builds.getValue(ref)
                val overridden = deployment.overrides.map { it.rule }.toSet()
                slot.admissionRules.filter { it.name !in overridden }.forEach { rule ->
                    // Nothing in the dataset answers an approval: unless it is overridden, a manual
                    // rule leaves the deployment a candidate whatever it was asked to be
                    if (rule.ruleId == SlotAdmissionRules.MANUAL) {
                        problems += "The ${deployment.environment}/${ref.project} slot waits for the " +
                                "approval ${rule.name}, and ${ref.build} is deployed past it without " +
                                "overriding it."
                    }
                    val required = rule.config["promotion"] as? String
                    if (rule.ruleId == SlotAdmissionRules.PROMOTION && required != null &&
                        required !in build.promotionLevels
                    ) {
                        problems += "The ${deployment.environment}/${ref.project} slot only admits " +
                                "builds promoted to $required, and ${ref.build} of ${ref.branch} " +
                                "is not."
                    }
                    if (rule.ruleId == SlotAdmissionRules.BRANCH_PATTERN && !branchIncludedByPattern(ref.branch, rule.config)) {
                        problems += "The ${deployment.environment}/${ref.project} slot admits no " +
                                "build of ${ref.branch}, and ${ref.build} is one."
                    }
                }
            }
        }
    }

    deployments.forEach { deployment ->
        val ref = deployment.build
        val slot = environments.find { it.name == deployment.environment }
            ?.slots?.find { it.project == ref.project && it.qualifier == deployment.qualifier }
            ?: return@forEach
        deployment.agent?.let { session ->
            val where = "A deployment of ${ref.build} on ${deployment.environment}/${ref.project}"
            checkSession(session, where)
            if (!slot.agentsAdmitted) {
                problems += "$where is run by the agent ${session.agent}, and the slot does not admit agents."
            }
            // An agent never answers an approval - nor overrides one, which the agent policy denies too
            if (deployment.overrides.isNotEmpty()) {
                problems += "$where is run by the agent ${session.agent}, and overrides admission rules, " +
                        "which an agent may not."
            }
        }
        deployment.overrides.forEach { override ->
            if (slot.admissionRules.none { it.name == override.rule }) {
                problems += "A deployment of ${ref.build} on ${deployment.environment}/${ref.project} " +
                        "overrides the admission rule ${override.rule}, which the slot does not have."
            }
        }
        if (deployment.overrides.isNotEmpty() &&
            (deployment.stopAt == DeploymentStop.CANDIDATE || deployment.stopAt == DeploymentStop.CANCELLED)
        ) {
            problems += "A deployment of ${ref.build} on ${deployment.environment}/${ref.project} " +
                    "overrides admission rules, and never starts: an override is for a deployment which does."
        }
    }

    // The server refuses a deployment dated before its build, and a pipeline starting before the
    // latest start of its slot - and a deployment at the reset is the latest start there is. So on
    // one slot the dated deployments come first, in the order of their dates.
    deployments.groupBy { Triple(it.environment, it.build.project, it.qualifier) }
        .forEach { (slot, slotDeployments) ->
            val (environment, project, qualifier) = slot
            val where = "the $environment/$project" +
                    (qualifier.takeIf { it.isNotBlank() }?.let { " [$it]" } ?: "") + " slot"
            var latest: LocalDateTime? = null
            var atTheReset = false
            slotDeployments.forEach { deployment ->
                val ref = deployment.build
                val at = deployment.at?.resolve(reference)
                if (at == null) {
                    atTheReset = true
                } else {
                    if (atTheReset) {
                        problems += "A deployment of ${ref.build} on $where is dated, and follows a " +
                                "deployment at the reset: the server refuses a start before the latest one."
                    }
                    latest?.takeIf { at < it }?.let {
                        problems += "A deployment of ${ref.build} on $where is dated before the " +
                                "deployment declared before it: the server refuses a start before the latest one."
                    }
                    latest = at
                    builds[ref]?.creation?.resolve(reference)?.takeIf { at < it }?.let {
                        problems += "A deployment of ${ref.build} on $where is dated before the " +
                                "creation of the build."
                    }
                }
            }
        }

    // Estates name labels, a marker and readings, all of which the server checks by name
    val estateNames = mutableSetOf<String>()
    val environmentNames = environments.map { it.name }.toSet()
    estates.forEach { estate ->
        val where = "Estate \"${estate.name}\""
        if (estate.name.isBlank() || estate.name.length > 100) {
            problems += "$where needs a name of 1 to 100 characters."
        }
        if (!estateNames.add(estate.name)) {
            problems += "The dataset declares the estate \"${estate.name}\" twice."
        }
        if (estate.labels.isEmpty()) {
            problems += "$where selects its projects by no label at all."
        }
        estate.labels.filterNot { it in labelDisplays }.forEach { label ->
            problems += "$where selects the projects carrying the $label label, which the dataset never creates."
        }
        // An estate of no project shows an empty column nowhere, which reads as a defect
        val selected = projects.filter { project -> project.labels.containsAll(estate.labels) }
        if (estate.labels.isNotEmpty() && selected.isEmpty()) {
            problems += "$where selects no project of the dataset."
        }
        when (val marker = estate.marker) {
            null -> Unit
            is EstateMarkerSpec.Promotion -> if (selected.none { project ->
                    project.branches.any { branch -> branch.promotionLevels.any { it.name == marker.level } }
                }) {
                problems += "$where reads up to ${marker.level}, which none of its projects declares."
            }

            is EstateMarkerSpec.Environment -> if (marker.environment !in environmentNames) {
                problems += "$where reads up to the ${marker.environment} environment, which the dataset never creates."
            }
        }
        estate.readings.groupingBy { it.key }.eachCount().forEach { (key, count) ->
            if (key !in ReadingKeys.ALL) {
                problems += "$where configures the reading $key, which does not exist."
            }
            if (count > 1) {
                problems += "$where configures the reading $key more than once."
            }
        }
        estate.readings.forEach { reading ->
            if (reading.windowDays != null && reading.windowDays <= 0) {
                problems += "$where reads ${reading.key} over a window of ${reading.windowDays} days."
            }
        }
        // The server's own checks of the security fields, before anything is deleted
        val security = estate.security
        if (security.freshnessDays != null && security.freshnessDays <= 0) {
            problems += "$where keeps its security scans fresh for ${security.freshnessDays} days."
        }
        if (security.criticalTargetDays != null && security.criticalTargetDays < 0) {
            problems += "$where gives its CRITICAL findings a target of ${security.criticalTargetDays} days."
        }
        if (security.highTargetDays != null && security.highTargetDays < 0) {
            problems += "$where gives its HIGH findings a target of ${security.highTargetDays} days."
        }
        if (security.expectedKinds.size != security.expectedKinds.toSet().size) {
            problems += "$where expects the same kind of scan more than once."
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

/**
 * What the mock issue service reads out of a commit message — `MockSCMExtension.issueRegex`
 * on the server side. An issue keyed anything else is one no commit is ever linked to, and
 * an issues section that silently stays empty.
 */
private val ISSUE_KEY = Regex("[A-Z]+-\\d+")

/**
 * What the dataset names its tokens by. The server takes any name; the dataset keeps to the names
 * of its entities, because the name is what the trail shows as the actor, `token:<name>`.
 */
private val TOKEN_NAME = Regex("[A-Za-z0-9._-]+")

/**
 * What the server accepts as the slug of an agent - `AgentService` on the server side.
 */
private val AGENT_SLUG = Regex("[a-z0-9-]{1,32}")

/**
 * Whether [link] is an absolute `https` URL, which is what the server keeps of an agent session link.
 */
private fun isAbsoluteHttps(link: String): Boolean =
    try {
        val uri = java.net.URI(link)
        uri.isAbsolute && uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
    } catch (_: Exception) {
        false
    }

/**
 * A `type/subtype` media type, without parameters - what the server keeps of a declared one.
 */
private val MEDIA_TYPE = Regex("[a-z0-9][a-z0-9.+-]*/[a-z0-9][a-z0-9.+-]*")
