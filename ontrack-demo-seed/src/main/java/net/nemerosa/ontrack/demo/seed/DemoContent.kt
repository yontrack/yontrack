package net.nemerosa.ontrack.demo.seed

import net.nemerosa.ontrack.demo.seed.BuildCreation.At
import net.nemerosa.ontrack.demo.seed.BuildCreation.DaysAgo
import net.nemerosa.ontrack.demo.seed.BuildCreation.HoursAgo
import net.nemerosa.ontrack.demo.seed.ValidationStatus.FAILED
import net.nemerosa.ontrack.demo.seed.ValidationStatus.PASSED
import net.nemerosa.ontrack.demo.seed.ValidationStatus.WARNING
import net.nemerosa.ontrack.json.asJson

/**
 * What the demo shows, and the file a feature adds itself to — see the definition of done
 * in `CLAUDE.md` and `doc/dev-guide/demo-seed.md`.
 *
 * Everything here is fixed: no counters, no random data, no wall-clock names. Build
 * creation times are the one exception and are expressed relative to the run, so the demo
 * always reads as recent work.
 */
object DemoContent {

    const val LIBRARY = "common-library"
    const val SERVICE = "petclinic"
    const val UI = "petclinic-ui"
    const val CHANGELOG = "yontrack"

    const val MAIN = "main"
    /**
     * A slash would be rejected: Yontrack entity names allow letters, digits, dots, dashes
     * and underscores, and nothing else.
     */
    const val MAINTENANCE = "release-1.3"

    const val BRONZE = "BRONZE"
    const val SILVER = "SILVER"
    const val GOLD = "GOLD"
    const val CANARY = "CANARY"

    const val BUILD = "BUILD"
    const val UNIT_TESTS = "UNIT.TESTS"
    const val INTEGRATION_TESTS = "INTEGRATION.TESTS"
    const val SECURITY_SCAN = "SECURITY.SCAN"

    /**
     * What [silverAuto] selects its validation stamps by. A constant because the demo is read
     * against it in more than one place, and because it is the one piece of the dataset's
     * vocabulary that is a pattern rather than a name: it must keep matching [UNIT_TESTS] and
     * [INTEGRATION_TESTS] and keep missing [SECURITY_SCAN].
     */
    const val TESTS_PATTERN = ".*TESTS"

    const val STAGING = "staging"
    const val PRODUCTION = "production"

    /**
     * The SCM branch [MAIN] of [SERVICE] follows. The mock SCM's branches are named as a
     * real repository's, so a slash is fine here where it is not in a Yontrack entity name.
     */
    const val SCM_MAIN = "main"
    const val SCM_MAINTENANCE = "release/1.3"

    /**
     * Fixed so that re-seeding updates the demo dashboard rather than colliding with the
     * one the previous run saved under the same name.
     */
    const val DASHBOARD_UUID = "1c1f9c3e-8bfa-4a1f-8a0b-4e2f0b0d1a01"

    /**
     * The whole dataset, curated part and changelog project together.
     *
     * @param changelog Commits since the last release, one build each.
     */
    fun dataset(changelog: List<ChangelogEntry>) = DemoDataset(
        projects = listOf(
            library(),
            service(),
            ui(),
            changelogProject(changelog),
        ),
        environments = environments(),
        deployments = deployments(),
        dashboard = dashboard(),
    )

    private val bronze = PromotionLevelSpec(BRONZE, "The build is green and can be looked at.")
    private val silver = PromotionLevelSpec(SILVER, "The build is deployed somewhere and was verified there.")
    private val gold = PromotionLevelSpec(GOLD, "A human approved the build for release.")

    /**
     * Same shape for both variants — one starting node, two parallel checks and a node
     * joining them — so the demo shows what a passing and a failing workflow run each look
     * like on a promotion run.
     *
     * @param performanceGateFails Whether the performance-gate node reports failure, which
     * blocks the join node from running.
     */
    private fun canaryWorkflow(performanceGateFails: Boolean) = WorkflowSpec(
        """
            name: Canary verification
            nodes:
              - id: start
                executorId: mock
                data:
                    text: Start canary verification
              - id: smoke-tests
                parents: [{id: start}]
                executorId: mock
                data:
                    text: Run smoke tests
                    waitMs: 500
              - id: performance-gate
                parents: [{id: start}]
                executorId: mock
                data:
                    text: Check performance budget
                    waitMs: 500
                    error: $performanceGateFails
              - id: promote-canary
                parents: [{id: smoke-tests}, {id: performance-gate}]
                executorId: mock
                data:
                    text: Promote canary to full rollout
        """.trimIndent()
    )

    /** Used on [MAIN]: the performance gate passes and the canary is promoted. */
    private val canaryPass = PromotionLevelSpec(
        CANARY,
        "Automated canary verification workflow.",
        workflow = canaryWorkflow(performanceGateFails = false),
    )

    /** Used on [MAINTENANCE]: the performance gate fails and the canary is blocked. */
    private val canaryFail = PromotionLevelSpec(
        CANARY,
        "Automated canary verification workflow.",
        workflow = canaryWorkflow(performanceGateFails = true),
    )

    /**
     * A one-node workflow for a slot, kept deliberately smaller than [canaryWorkflow]: what a slot
     * workflow demonstrates on the delivery map is its TRIGGER and the direction of its edge, not
     * the shape of its graph, which the promotion side already shows.
     */
    private fun slotWorkflow(name: String, text: String) = """
        name: $name
        nodes:
          - id: check
            executorId: mock
            data:
                text: $text
    """.trimIndent()

    private val buildStamp = ValidationStampSpec(BUILD, "Compilation and packaging.")
    private val unitTests = ValidationStampSpec(UNIT_TESTS, "Unit tests.")
    private val integrationTests = ValidationStampSpec(INTEGRATION_TESTS, "Integration tests against a real database.")
    private val securityScan = ValidationStampSpec(SECURITY_SCAN, "Dependency and container scan.")

    /**
     * SILVER as the full ladder carries it: granted by itself once the build is BRONZE, has built,
     * and has passed every stamp whose name ends in TESTS.
     *
     * This is what gives the delivery map its *unlocks* edges, and auto promotion is the only thing
     * that puts a validation stamp on the map at all. It says three different things on purpose:
     * a promotion level granting a promotion, a stamp named explicitly, and a PATTERN - which the
     * map collapses into one aggregate checkpoint labelled with the pattern, standing for the stamps it
     * matches rather than drawing one edge each.
     *
     * `SECURITY.SCAN` is deliberately outside the pattern, so that the demo also shows a stamp the
     * map leaves out: only the stamps taking part in a dependency are drawn.
     *
     * #1716 sketched this differently - `BUILD` *and* `UNIT.TESTS` named, and the pattern on BRONZE -
     * and both departures are deliberate. Naming a stamp is the more specific statement of the two,
     * so a stamp named AND matched by the pattern stays its own checkpoint and leaves the aggregate
     * standing for one thing: naming `UNIT.TESTS` here would produce a one-member aggregate, which
     * teaches nothing about what an aggregate is for. And BRONZE has to stay manual, because the
     * only rule that would reproduce the BRONZE promotions the dataset already declares is the same
     * one SILVER carries - both sets are identical, and a level granted by the very rule below it
     * would be a second edge saying what the first already says.
     *
     * It reproduces the promotions the dataset already declares rather than adding any. Every build
     * below carrying SILVER is BRONZE with `BUILD` and both TESTS green, and the two that are not -
     * 103 with a failed integration test, 106 with a failed unit test - satisfy neither rule. Auto
     * promotion only ever fires for a build not already promoted, so the demo's promotions stay the
     * curated ones, at their curated times.
     */
    private val silverAuto = silver.copy(
        autoPromotion = AutoPromotionSpec(
            validationStamps = listOf(BUILD),
            promotionLevels = listOf(BRONZE),
            include = TESTS_PATTERN,
        ),
    )

    /**
     * GOLD as the full ladder carries it: a human still grants it, but never before SILVER.
     *
     * The map's other edge kind, and the counterpart to [silverAuto]: a dependency CONSTRAINS where
     * auto promotion ACTS, and the two are drawn differently because a map showing them alike would
     * say that a configuration grants a promotion when it only permits it.
     *
     * The server refuses a promotion whose dependencies are not already granted, so every build
     * declaring GOLD below declares SILVER before it. `validate` checks that before a reset.
     */
    private val goldAfterSilver = gold.copy(dependsOn = listOf(SILVER))

    /**
     * The map's third source of a *requires*, on [LIBRARY] alone: SILVER cannot be granted before
     * BRONZE, and nothing on SILVER names BRONZE to say so.
     *
     * The `PreviousPromotionConditionPropertyType` property is a bare boolean; the server reads the
     * predecessor off the branch's promotion level ORDER. The demo sets it on one promotion level of
     * one branch rather than on a project or in the settings, which is where it is far more usually
     * set in real life - and where it would put the same chain on every ladder of every demo project,
     * arriving as a side effect rather than as something to look at.
     *
     * [LIBRARY] is the branch for it because its two rungs carry nothing else: BRONZE does not auto
     * promote into SILVER, so the edge is not suppressed by the rule in ADR 0010, and no promotion
     * dependency names the same pair, so the line the demo exists to show is this property's own. On
     * [SERVICE], every consecutive pair is already spoken for - BRONZE unlocks SILVER, SILVER is
     * required by GOLD - and the condition would draw nothing new anywhere.
     *
     * Both builds below are promoted BRONZE then SILVER, in that order, so the condition never
     * refuses one. `validate` checks that before a reset.
     */
    private val silverAfterBronze = silver.copy(requiresPreviousPromotion = true)

    /**
     * The full ladder, for the projects that show the whole delivery pipeline.
     *
     * [UI] keeps the plain [bronze] and [silver], and [LIBRARY] the plain [bronze] and
     * [silverAfterBronze]: neither declares the stamps [silverAuto] names, nor a GOLD for
     * [goldAfterSilver] to sit above.
     */
    private val fullPromotions = listOf(bronze, silverAuto, goldAfterSilver)

    /**
     * The same three rungs carrying no configuration at all, for [CHANGELOG].
     *
     * Its builds are one per commit and stop at BRONZE with both of their stamps green, which is
     * exactly what [silverAuto] grants SILVER for: given [fullPromotions], the whole branch would
     * promote itself to SILVER on the next reset. `DemoSeedTest` fails on that rather than letting
     * it reach a server. The promotion story is [SERVICE]'s.
     */
    private val plainPromotions = listOf(bronze, silver, gold)

    /** The full set of checks, for the same projects. */
    private val fullValidationStamps = listOf(buildStamp, unitTests, integrationTests, securityScan)

    /**
     * A library everything else depends on — the bottom of the dependency graph.
     */
    private fun library() = ProjectSpec(
        name = LIBRARY,
        description = "Shared library, used by the other demo projects.",
        branches = listOf(
            BranchSpec(
                name = MAIN,
                description = "Main development branch.",
                promotionLevels = listOf(bronze, silverAfterBronze),
                validationStamps = listOf(buildStamp, unitTests),
                builds = listOf(
                    BuildSpec(
                        name = "41",
                        release = "3.2.0",
                        description = "Retry policy for the HTTP client.",
                        creation = DaysAgo(18),
                        promotionLevels = listOf(BRONZE, SILVER),
                        validations = listOf(
                            ValidationSpec(BUILD, PASSED),
                            ValidationSpec(UNIT_TESTS, PASSED),
                        ),
                    ),
                    BuildSpec(
                        name = "42",
                        release = "3.2.1",
                        description = "Connection pool sizing fix.",
                        creation = DaysAgo(9),
                        promotionLevels = listOf(BRONZE, SILVER),
                        validations = listOf(
                            ValidationSpec(BUILD, PASSED),
                            ValidationSpec(UNIT_TESTS, PASSED),
                        ),
                    ),
                ),
            ),
        ),
    )

    /**
     * The change log of [SERVICE], which is the only project the demo gives an SCM.
     *
     * The commit subjects are conventional-commit ones on purpose: the semantic change log
     * groups commits by their type and drops every commit that carries none, so a project
     * writing subjects any other way would demonstrate an empty semantic view. That is also
     * why the change log is here rather than on [CHANGELOG], whose subjects come from
     * Yontrack's own history and are overwhelmingly `#1234 Some message`.
     *
     * The mock SCM keeps all of this in memory, on the bean: the demo's change log does not
     * survive a backend restart. The builds keep their commit properties, which are in the
     * database, so the change log then fails on a repository the mock SCM no longer knows
     * about, until the next reset. See `doc/dev-guide/demo-seed.md`.
     */
    private fun serviceScm() = ScmSpec(
        repository = SERVICE,
        issues = listOf(
            IssueSpec("PETCLINIC-142", "Search owners by phone number", type = "feature"),
            // Its own key rather than a second commit on PETCLINIC-142: the mock issue
            // service points an issue at the LAST commit registered for it, and the
            // maintenance branch is seeded after `main`, so sharing the key would show
            // PETCLINIC-142 on release/1.3 alone.
            IssueSpec("PETCLINIC-149", "Backport the owner search to 1.3", type = "bug"),
            IssueSpec("PETCLINIC-157", "Paginate the visit history", type = "feature"),
            IssueSpec("PETCLINIC-163", "Visit scheduling test is flaky", type = "bug"),
            IssueSpec("PETCLINIC-165", "Administer vet specialities", type = "feature"),
            IssueSpec("PETCLINIC-171", "Pet type look-up is slow", type = "performance"),
            IssueSpec("PETCLINIC-178", "Export owners as CSV", type = "feature"),
            IssueSpec("PETCLINIC-181", "CSV export mangles accented names", type = "bug"),
        ),
    )

    /**
     * The main demo project: a branch that reads like a real one, with a maintenance
     * branch beside it and a history of promotions to chart.
     */
    private fun service() = ProjectSpec(
        name = SERVICE,
        description = "Sample application - the demo's main project.",
        scm = serviceScm(),
        // The demo's favourites, which is what the mobile UI opens on. Both branches of the
        // main project, so the home screen shows the project each branch belongs to doing
        // real work - two branches called differently under one project name.
        favourite = true,
        branches = listOf(
            BranchSpec(
                name = MAIN,
                description = "Main development branch.",
                scmBranch = SCM_MAIN,
                favourite = true,
                promotionLevels = fullPromotions + canaryPass,
                validationStamps = fullValidationStamps,
                builds = listOf(
                    BuildSpec(
                        name = "101",
                        release = "1.4.0",
                        description = "Owner search by phone number.",
                        creation = DaysAgo(14),
                        promotionLevels = listOf(BRONZE, SILVER, GOLD),
                        validations = listOf(
                            ValidationSpec(BUILD, PASSED),
                            ValidationSpec(UNIT_TESTS, PASSED),
                            ValidationSpec(INTEGRATION_TESTS, PASSED),
                            ValidationSpec(SECURITY_SCAN, PASSED),
                        ),
                        links = listOf(BuildRef(LIBRARY, MAIN, "41")),
                        commits = listOf(
                            "feat(api): search owners by their phone number, closes PETCLINIC-142",
                            "test: cover the owner search endpoint",
                        ),
                    ),
                    BuildSpec(
                        name = "102",
                        release = "1.4.1",
                        description = "Visit history pagination.",
                        creation = DaysAgo(11),
                        promotionLevels = listOf(BRONZE, SILVER),
                        validations = listOf(
                            ValidationSpec(BUILD, PASSED),
                            ValidationSpec(UNIT_TESTS, PASSED),
                            ValidationSpec(INTEGRATION_TESTS, PASSED),
                            ValidationSpec(SECURITY_SCAN, WARNING, "Two medium advisories in transitive dependencies."),
                        ),
                        links = listOf(BuildRef(LIBRARY, MAIN, "41")),
                        commits = listOf(
                            "feat(ui): paginate the visit history, closes PETCLINIC-157",
                            "docs: describe the visit history endpoint",
                        ),
                    ),
                    BuildSpec(
                        name = "103",
                        release = "1.4.2",
                        description = "Vet specialities admin screen.",
                        creation = DaysAgo(8),
                        validations = listOf(
                            ValidationSpec(BUILD, PASSED),
                            ValidationSpec(UNIT_TESTS, PASSED),
                            ValidationSpec(INTEGRATION_TESTS, FAILED, "Flaky visit scheduling test."),
                        ),
                        links = listOf(BuildRef(LIBRARY, MAIN, "41")),
                        commits = listOf(
                            "feat(admin): administer the vet specialities, closes PETCLINIC-165",
                            "refactor: extract the speciality repository",
                        ),
                    ),
                    BuildSpec(
                        name = "104",
                        release = "1.4.3",
                        description = "Visit scheduling test stabilised.",
                        creation = DaysAgo(6),
                        promotionLevels = listOf(BRONZE, SILVER, GOLD),
                        validations = listOf(
                            ValidationSpec(BUILD, PASSED),
                            ValidationSpec(UNIT_TESTS, PASSED),
                            ValidationSpec(INTEGRATION_TESTS, PASSED),
                            ValidationSpec(SECURITY_SCAN, PASSED),
                        ),
                        links = listOf(BuildRef(LIBRARY, MAIN, "42")),
                        commits = listOf(
                            "fix(tests): stabilise the visit scheduling test, closes PETCLINIC-163",
                            "chore(deps): bump common-library to 3.2.1",
                        ),
                    ),
                    BuildSpec(
                        name = "105",
                        release = "1.4.4",
                        description = "Pet type reference data cached.",
                        creation = DaysAgo(3),
                        // SILVER twice, and the only build here promoted twice to one level. It is
                        // what the pipeline view's promotions panel needs in order to show anything
                        // at all about re-promotion: one row per RUN, each with its own actions.
                        // It also makes the stage cards' claim checkable - they count promoted
                        // BUILDS, so the SILVER card must still say 5 builds, not 6 runs.
                        promotionLevels = listOf(BRONZE, SILVER, SILVER),
                        validations = listOf(
                            ValidationSpec(BUILD, PASSED),
                            ValidationSpec(UNIT_TESTS, PASSED),
                            ValidationSpec(INTEGRATION_TESTS, PASSED),
                            ValidationSpec(SECURITY_SCAN, PASSED),
                        ),
                        links = listOf(BuildRef(LIBRARY, MAIN, "42")),
                        commits = listOf(
                            "perf(api): cache the pet type reference data, closes PETCLINIC-171",
                            "docs: note when the pet type cache is evicted",
                        ),
                    ),
                    BuildSpec(
                        name = "106",
                        release = "1.4.5",
                        description = "Owner export as CSV.",
                        creation = DaysAgo(1),
                        validations = listOf(
                            ValidationSpec(BUILD, PASSED),
                            ValidationSpec(UNIT_TESTS, FAILED, "Export encoding test."),
                        ),
                        links = listOf(BuildRef(LIBRARY, MAIN, "42")),
                        commits = listOf(
                            "feat(export): export the owners as CSV, closes PETCLINIC-178",
                            "style: reformat the export writer",
                        ),
                    ),
                    BuildSpec(
                        name = "107",
                        release = "1.4.6",
                        description = "Owner export as CSV, canary rollout.",
                        // Hours rather than `DaysAgo(0)`: the head of the demo's busiest branch
                        // carries four promotions, and an offset is the only way to be sure they
                        // fit behind the reset whatever zone it runs in
                        creation = HoursAgo(5),
                        promotionLevels = listOf(BRONZE, SILVER, CANARY, GOLD),
                        validations = listOf(
                            ValidationSpec(BUILD, PASSED),
                            ValidationSpec(UNIT_TESTS, PASSED),
                            ValidationSpec(INTEGRATION_TESTS, PASSED),
                            ValidationSpec(SECURITY_SCAN, PASSED),
                        ),
                        links = listOf(BuildRef(LIBRARY, MAIN, "42")),
                        commits = listOf(
                            "fix(export): write the CSV in UTF-8, closes PETCLINIC-181",
                            "ci: run the export tests on the canary pipeline",
                        ),
                    ),
                ),
            ),
            BranchSpec(
                name = MAINTENANCE,
                description = "Maintenance of the previous minor version.",
                scmBranch = SCM_MAINTENANCE,
                favourite = true,
                promotionLevels = fullPromotions + canaryFail,
                validationStamps = fullValidationStamps,
                builds = listOf(
                    BuildSpec(
                        name = "87",
                        release = "1.3.7",
                        description = "Backport of the owner search fix.",
                        creation = DaysAgo(20),
                        promotionLevels = listOf(BRONZE, SILVER, GOLD),
                        validations = listOf(
                            ValidationSpec(BUILD, PASSED),
                            ValidationSpec(UNIT_TESTS, PASSED),
                            ValidationSpec(INTEGRATION_TESTS, PASSED),
                        ),
                        commits = listOf(
                            "feat(api): backport the owner search by phone number, closes PETCLINIC-149",
                        ),
                    ),
                    BuildSpec(
                        name = "88",
                        release = "1.3.8",
                        description = "Security patch for the session cookie.",
                        creation = DaysAgo(4),
                        promotionLevels = listOf(BRONZE, SILVER),
                        validations = listOf(
                            ValidationSpec(BUILD, PASSED),
                            ValidationSpec(UNIT_TESTS, PASSED),
                            ValidationSpec(INTEGRATION_TESTS, PASSED),
                            ValidationSpec(SECURITY_SCAN, PASSED),
                        ),
                        commits = listOf(
                            "fix(security): mark the session cookie as SameSite",
                            "test: cover the session cookie attributes",
                        ),
                    ),
                    BuildSpec(
                        name = "89",
                        release = "1.3.9",
                        description = "Second session cookie backport, canary rollout.",
                        creation = DaysAgo(2),
                        promotionLevels = listOf(BRONZE, SILVER, CANARY),
                        validations = listOf(
                            ValidationSpec(BUILD, PASSED),
                            ValidationSpec(UNIT_TESTS, PASSED),
                            ValidationSpec(INTEGRATION_TESTS, PASSED),
                            ValidationSpec(SECURITY_SCAN, PASSED),
                        ),
                        commits = listOf(
                            "fix(security): shorten the session cookie lifetime",
                            "docs: record the session cookie settings",
                        ),
                    ),
                    // The head of this branch, and the only build of the demo which ARRIVED
                    // SOMEWHERE AND FAILED. That reading is the delivery map's central subtlety -
                    // a promotion level names a build which was promoted and a slot one which was
                    // deployed, so a validation stamp is the only checkpoint which can show it -
                    // and it belongs on the branch which is already carrying the map's other
                    // awkward readings rather than on [MAIN], whose picture is what the rest of
                    // the demo is read against.
                    //
                    // Nothing else runs: [BUILD] failing is what stops the tests from running at
                    // all, which is why this build declares one validation and not four. On the
                    // map it leaves BUILD showing a failed run at the branch head, the aggregate
                    // and every promotion level one build behind, and the whole branch stuck -
                    // which is what a broken build looks like, drawn.
                    BuildSpec(
                        name = "90",
                        release = "1.3.10",
                        description = "Cookie lifetime made configurable. The build does not compile.",
                        creation = DaysAgo(1),
                        validations = listOf(
                            ValidationSpec(BUILD, FAILED, "Unresolved symbol in the session config."),
                        ),
                        commits = listOf(
                            "feat(security): make the session cookie lifetime configurable",
                        ),
                    ),
                ),
            ),
        ),
    )

    /**
     * A consumer of [SERVICE], so the demo has a dependency graph to walk and not just a
     * list of projects.
     */
    private fun ui() = ProjectSpec(
        name = UI,
        description = "Front-end for the sample application.",
        // A second favourite project, so the home screen is a list rather than one row.
        favourite = true,
        branches = listOf(
            BranchSpec(
                name = MAIN,
                description = "Main development branch.",
                promotionLevels = listOf(bronze, silver),
                validationStamps = listOf(buildStamp, unitTests),
                builds = listOf(
                    BuildSpec(
                        name = "58",
                        release = "2.0.3",
                        description = "Owner search results layout.",
                        creation = DaysAgo(7),
                        promotionLevels = listOf(BRONZE, SILVER),
                        validations = listOf(
                            ValidationSpec(BUILD, PASSED),
                            ValidationSpec(UNIT_TESTS, PASSED),
                        ),
                        links = listOf(BuildRef(SERVICE, MAIN, "104")),
                    ),
                    BuildSpec(
                        name = "59",
                        release = "2.0.4",
                        description = "Dark mode for the visit calendar.",
                        creation = DaysAgo(2),
                        promotionLevels = listOf(BRONZE),
                        validations = listOf(
                            ValidationSpec(BUILD, PASSED),
                            ValidationSpec(UNIT_TESTS, PASSED),
                        ),
                        links = listOf(BuildRef(SERVICE, MAIN, "105")),
                    ),
                ),
            ),
        ),
    )

    /**
     * Yontrack itself, one build per commit since the last release.
     *
     * The point is not realism: it is that the demo keeps showing this month's work
     * without anyone having to remember to update the curated dataset.
     */
    private fun changelogProject(changelog: List<ChangelogEntry>) = ProjectSpec(
        name = CHANGELOG,
        description = "Yontrack itself, seeded from the changelog since the last release.",
        branches = listOf(
            BranchSpec(
                name = MAIN,
                description = "Commits since the last release.",
                promotionLevels = plainPromotions,
                validationStamps = listOf(buildStamp, unitTests),
                // Reversed: the entries arrive newest first - [ChangelogSource] sorts them,
                // rather than leaving them in whatever order `git log` printed - and Yontrack
                // orders the builds of a branch by creation ORDER rather than by creation
                // time. Seeded as they come, the last commit created would be the oldest one
                // and every view would read the branch backwards (#1647).
                builds = changelog.reversed().map { entry ->
                    BuildSpec(
                        name = entry.id,
                        description = entry.message,
                        creation = At(entry.time),
                        promotionLevels = listOf(BRONZE),
                        validations = listOf(
                            ValidationSpec(BUILD, PASSED),
                            ValidationSpec(UNIT_TESTS, PASSED),
                        ),
                    )
                },
            ),
        ),
    )

    /**
     * Two environments, and the admission rules which say how a build gets into each.
     *
     * The rules are the point, not decoration. They are what the delivery map reads to join
     * the slots to the rest of the map, and without them the demo would draw two slots
     * floating unconnected beside the promotion levels - which is exactly the picture the
     * map is meant to make you go and fix.
     *
     * Together they give the map of [SERVICE] one of each thing a slot checkpoint can be:
     *
     * * on [MAIN], staging holds a [MAINTENANCE] build, so the map shows a slot naming a
     *   build of another branch and saying so;
     * * on [MAINTENANCE], production is drawn **unreachable**, because its branch pattern
     *   admits `main` alone and no build of the maintenance branch can ever deploy there;
     * * both branches show the promotion edges into the slots, and the staging to production
     *   edge between them.
     *
     * [UI] then contributes the one case [SERVICE] cannot: a production slot whose rules name
     * things which do not exist, drawn as **unresolved** checkpoints. It is the dataset's only
     * deliberately broken configuration, and the comment beside it says why it is there.
     */
    private fun environments() = listOf(
        EnvironmentSpec(
            name = STAGING,
            order = 100,
            description = "Where a build is verified before anyone sees it.",
            tags = listOf("non-production"),
            slots = listOf(
                SlotSpec(
                    project = SERVICE,
                    description = "Sample application on staging.",
                    admissionRules = listOf(
                        SlotAdmissionRuleSpec(
                            name = "silver",
                            ruleId = SlotAdmissionRules.PROMOTION,
                            config = mapOf("promotion" to SILVER),
                        ),
                    ),
                    // The one slot with workflows, on two triggers, so that the delivery map shows
                    // a slot straddling its own column: the CANDIDATE workflow is a hard gate and
                    // is drawn as a *requires* running INTO the slot, while the DONE workflow runs
                    // once the deployment is over and is drawn as an *emits* running OUT of it.
                    //
                    // Both read "Not started", because the seed configures slot workflows after the
                    // deployments it asks for - see `DemoSeed`. That is the state worth showing
                    // anyway: a gate nobody has run is very often the reason nothing newer has been
                    // deployed, and the map is where that becomes visible. Workflows that have RUN
                    // are shown on the promotion side, by [canaryPass] and [canaryFail].
                    workflows = listOf(
                        SlotWorkflowSpec(
                            trigger = "CANDIDATE",
                            yaml = slotWorkflow("Staging readiness", "Check the staging window is open"),
                        ),
                        SlotWorkflowSpec(
                            trigger = "DONE",
                            yaml = slotWorkflow("Staging announcement", "Announce the deployment"),
                        ),
                    ),
                ),
            ),
        ),
        EnvironmentSpec(
            name = PRODUCTION,
            order = 200,
            description = "What the customers are running.",
            tags = listOf("production"),
            slots = listOf(
                SlotSpec(
                    project = SERVICE,
                    description = "Sample application in production.",
                    admissionRules = listOf(
                        SlotAdmissionRuleSpec(
                            name = "gold",
                            ruleId = SlotAdmissionRules.PROMOTION,
                            config = mapOf("promotion" to GOLD),
                        ),
                        // What draws the staging to production edge on the delivery map.
                        // Nothing else does: the map never joins two slots by the order of
                        // their environments.
                        SlotAdmissionRuleSpec(
                            name = "staging",
                            ruleId = SlotAdmissionRules.ENVIRONMENT,
                            config = mapOf("environmentName" to STAGING, "qualifier" to ""),
                        ),
                        // Releases go out from `main` only, which is what makes production
                        // unreachable from the maintenance branch.
                        SlotAdmissionRuleSpec(
                            name = "mainOnly",
                            ruleId = SlotAdmissionRules.BRANCH_PATTERN,
                            config = mapOf("includes" to listOf(MAIN)),
                        ),
                    ),
                ),
                // DELIBERATELY BROKEN, and the one thing in the dataset which is. Both rules
                // below name something [UI] does not have: the project declares BRONZE and
                // SILVER and never GOLD, and it has no staging slot at all. They are the two
                // ways a slot admission rule can point at nothing, and the delivery map draws
                // each as an unresolved checkpoint carrying the name that was asked for.
                //
                // It is a copy of the [SERVICE] slot beside it, because that is how the
                // mistake is actually made: the rules were pasted from a project which does
                // have a GOLD promotion and a staging slot. Nothing else surfaces it - the
                // deployment answers "Promotion not existing" the day somebody first tries to
                // deploy the UI, and not before - which is the argument for the whole feature.
                SlotSpec(
                    project = UI,
                    description = "Front-end in production. Its admission rules are broken on purpose.",
                    admissionRules = listOf(
                        SlotAdmissionRuleSpec(
                            name = "gold",
                            ruleId = SlotAdmissionRules.PROMOTION,
                            config = mapOf("promotion" to GOLD),
                        ),
                        SlotAdmissionRuleSpec(
                            name = "staging",
                            ruleId = SlotAdmissionRules.ENVIRONMENT,
                            config = mapOf("environmentName" to STAGING, "qualifier" to ""),
                        ),
                    ),
                ),
            ),
        ),
    )

    /**
     * The demo's deployment history, in order. The order is load-bearing: production admits
     * only what staging is holding at the time, so 1.4.3 has to pass through staging before
     * it can go to production, and the maintenance build lands on staging afterwards.
     *
     * It leaves production on 1.4.3 while [MAIN] is already at 1.4.6, and staging occupied
     * by a maintenance build under test - which is what a real pair of environments usually
     * looks like, and is also the only arrangement in which the delivery map has all three
     * of its slot readings to show.
     *
     * The last one stops at [DeploymentStop.RUNNING] and is the only one that does. Without
     * it the dataset contains no deployment in a state where completing or cancelling one is
     * possible at all, so the mobile UI's Complete and Cancel (#1736) would ship with nothing
     * in the demo to try them on.
     *
     * It fits without reshaping the composed picture, and every reason is load-bearing:
     *
     * * 107 (1.4.6) carries [SILVER], staging's only admission rule, so the pipeline
     *   legitimately reaches `RUNNING` rather than being stuck as a candidate;
     * * it is the head of [MAIN] and reads as the next thing that would go to staging;
     * * being last on the slot it satisfies "only the last pipeline can be deployed", so it
     *   is actually completable rather than a button that refuses;
     * * staging's *held* build stays `maintenance/89` - `lastDeployedPipeline` is the last
     *   DONE one - so the delivery map's slot checkpoint, the environment widget and
     *   production's `environment` admission rule all read exactly as before.
     *
     * None of the three above it could stop at `RUNNING` instead: `STAGING <- main/104` must
     * be `DONE` for production's `environment` rule to admit 104, production must stay on
     * 1.4.3 for the delivery map, and `STAGING <- maintenance/89` is what "staging occupied by
     * a build under test" means.
     */
    private fun deployments() = listOf(
        DeploymentSpec(STAGING, BuildRef(SERVICE, MAIN, "104")),
        DeploymentSpec(PRODUCTION, BuildRef(SERVICE, MAIN, "104")),
        DeploymentSpec(STAGING, BuildRef(SERVICE, MAINTENANCE, "89")),
        DeploymentSpec(STAGING, BuildRef(SERVICE, MAIN, "107"), stopAt = DeploymentStop.RUNNING),
    )

    /**
     * A dashboard shared with every user, showing the demo's own data.
     *
     * Shared means available in every visitor's dashboard picker, not selected for them:
     * Yontrack only ever selects a dashboard for the account doing the saving, so a visitor
     * still lands on the built-in dashboard and picks this one.
     *
     * The grid is 12 columns wide; heights are in the grid's own row units.
     */
    private fun dashboard() = DemoDashboard(
        uuid = DASHBOARD_UUID,
        name = "Yontrack demo",
        widgets = listOf(
            DemoWidget(
                uuid = "1c1f9c3e-8bfa-4a1f-8a0b-4e2f0b0d1a11",
                key = "home/BranchStatuses",
                config = mapOf(
                    "title" to "Sample application",
                    "promotionConfigs" to listOf(
                        mapOf("promotionLevel" to BRONZE),
                        mapOf("promotionLevel" to SILVER),
                        mapOf("promotionLevel" to GOLD),
                    ),
                    "validationConfigs" to listOf(
                        mapOf("validationStamp" to BUILD),
                        mapOf("validationStamp" to UNIT_TESTS),
                        mapOf("validationStamp" to INTEGRATION_TESTS),
                    ),
                    "branches" to listOf(
                        mapOf("project" to SERVICE, "branch" to MAIN),
                        mapOf("project" to SERVICE, "branch" to MAINTENANCE),
                        mapOf("project" to UI, "branch" to MAIN),
                    ),
                ).asJson(),
                layout = DemoWidgetLayout(x = 0, y = 0, w = 12, h = 30),
            ),
            DemoWidget(
                uuid = "1c1f9c3e-8bfa-4a1f-8a0b-4e2f0b0d1a12",
                key = "extension/environments/EnvironmentList",
                config = mapOf(
                    "title" to "Deployments",
                    "tags" to emptyList<String>(),
                    "projects" to emptyList<String>(),
                ).asJson(),
                layout = DemoWidgetLayout(x = 0, y = 30, w = 6, h = 40),
            ),
            DemoWidget(
                uuid = "1c1f9c3e-8bfa-4a1f-8a0b-4e2f0b0d1a13",
                key = "home/LastActiveProjects",
                config = mapOf("count" to 10).asJson(),
                layout = DemoWidgetLayout(x = 6, y = 30, w = 6, h = 20),
            ),
            // The one chart widget of the demo: the family was missing entirely, so nothing in the
            // demo showed a chart title - the place where the promotion level, branch and project
            // are now links.
            DemoWidget(
                uuid = "1c1f9c3e-8bfa-4a1f-8a0b-4e2f0b0d1a14",
                key = "home/PromotionFrequencyChart",
                config = mapOf(
                    "project" to SERVICE,
                    "branch" to MAIN,
                    "promotionLevel" to GOLD,
                    // Not the chart defaults (3m / 1w): the demo's oldest build is 20 days old, so
                    // a three-month window bucketed by week would draw one mostly empty chart.
                    "interval" to "1m",
                    "period" to "3d",
                ).asJson(),
                layout = DemoWidgetLayout(x = 6, y = 50, w = 6, h = 20),
            ),
        ),
    )
}
