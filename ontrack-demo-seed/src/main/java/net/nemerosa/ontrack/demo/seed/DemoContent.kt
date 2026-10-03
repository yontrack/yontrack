package net.nemerosa.ontrack.demo.seed

import net.nemerosa.ontrack.demo.seed.BuildCreation.At
import net.nemerosa.ontrack.demo.seed.BuildCreation.DaysAgo
import net.nemerosa.ontrack.demo.seed.BuildCreation.HoursAgo
import net.nemerosa.ontrack.demo.seed.ValidationStatus.FAILED
import net.nemerosa.ontrack.demo.seed.ValidationStatus.FIXED
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

    /**
     * The project of the security findings. A project of its own rather than stamps added to
     * [SERVICE]: the story of a finding needs every build of two branches scanned, and
     * [SERVICE]'s builds already carry curated readings - the failed build, the auto promotions,
     * the deployments - which a new stamp on each of them would have to be checked against.
     */
    const val SECURITY = "petclinic-billing"

    /**
     * The project of the delivery scorecard: three months of weekly releases, promotions, test runs
     * and production deployments, where every other project of the demo has three weeks. A project
     * of its own for the same reason as [SECURITY]: a scorecard reads a HISTORY, and giving
     * [SERVICE] one would bury its curated builds - the failed build, the canary, the deployments
     * the delivery map reads - under a dozen more.
     */
    const val VISITS = "petclinic-visits"

    /**
     * The project whose tables are long enough to scroll (#1932): the end-to-end suites of the
     * sample application, one validation stamp per suite and a nightly build on [MAIN], and a
     * feature branch per change in flight. A project of its own because it is there for its SIZE -
     * what a sticky table header needs to be seen at all - and anything that size added to the
     * curated projects would bury their readings. Trim it here rather than elsewhere.
     */
    const val E2E = "petclinic-e2e"

    /** The suite of [E2E] run on every build, so its validation stamp has a long history. */
    const val E2E_SMOKE = "E2E.SMOKE"

    /** The branch statuses widget listing every branch of [E2E] - fixed, like [DASHBOARD_UUID]. */
    const val E2E_WIDGET_UUID = "1c1f9c3e-8bfa-4a1f-8a0b-4e2f0b0d1a15"

    /**
     * The release branch of [SECURITY], where the HIGH fixed on [MAIN] is still exposed. Named as
     * a release branch - the project has no SCM, so no branch model, and every branch counts for
     * the state of its findings, which is what keeps that HIGH open for the project.
     */
    const val SECURITY_RELEASE = "release-2.3"

    /** The findings widget of [SECURITY] on the demo dashboard - fixed, like [DASHBOARD_UUID]. */
    const val FINDINGS_PROJECT_WIDGET_UUID = "1c1f9c3e-8bfa-4a1f-8a0b-4e2f0b0d1a17"

    /** The findings widget of [SECURITY_RELEASE] on the demo dashboard - fixed, like [DASHBOARD_UUID]. */
    const val FINDINGS_BRANCH_WIDGET_UUID = "1c1f9c3e-8bfa-4a1f-8a0b-4e2f0b0d1a18"

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
     * The `tests` stamp of [VISITS], of the test summary data type: its runs carry the counts of the
     * tests, and are what the test readings of the scorecard read. Not matched by [TESTS_PATTERN] by
     * accident only: [VISITS] has no auto promotion at all.
     */
    const val TEST_SUMMARY = "TESTS"

    /** The `security-findings` stamp of [SECURITY] scanning its dependencies, in the neutral format. */
    const val SECURITY_DEPENDENCIES = "SECURITY.DEPENDENCIES"

    /** The `security-findings` stamp of [SECURITY] scanning its code, in SARIF. */
    const val SECURITY_CODE = "SECURITY.CODE"

    /**
     * The HIGH of the demo: reported by a few builds of [MAIN] then fixed there, and still exposed
     * on [SECURITY_RELEASE]. The CVE the search finds.
     */
    const val CVE_FIXED_ON_MAIN = "CVE-2024-38816"

    /** The CRITICAL of the demo, tolerated under an acceptance which has not expired. */
    const val CVE_ACCEPTED = "CVE-2022-1471"

    /**
     * A CRITICAL reported by one build of [MAIN] of [SECURITY], whose dependency scan it fails, and
     * fixed by the next build: a resolved finding for the remediation time, and the failed scan which
     * makes [SECURITY] gating.
     */
    const val CVE_CRITICAL_FIXED = "CVE-2024-1597"

    /**
     * A HIGH reported by the first build of [SECURITY_RELEASE] and fixed by the next: the other
     * resolved finding of the remediation time, which reads the median of the two.
     */
    const val CVE_HIGH_FIXED = "CVE-2024-7254"

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
     * The demo's one slot qualifier, and the reason the matrix has anything to nest.
     *
     * A qualifier names one of a project's parallel deployments into the same environment. Every
     * other slot of the dataset uses the default one - the empty string - which is what a project
     * deploying once per environment has; [SERVICE] has this one as well, so the matrix draws a
     * project row with a row under it, and the drawer, the slot page and the delivery map all have
     * a slot whose name carries a qualifier to render.
     *
     * Deliberately named after the [CANARY] promotion level and deliberately *not* that constant: a
     * promotion level says something about a build, a qualifier says which deployment of a project
     * a slot is, and the demo happens to spell both "canary" because that is what a canary release
     * is called on both sides.
     */
    const val CANARY_QUALIFIER = "canary"

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
     * Two label categories, not one. A single category would put the same word on every chip
     * and make the `category:name` form look like noise; two of them show what a category is
     * for — who owns a project, and what it is written in — and make the label filter's AND
     * worth trying, since "the platform team's Java projects" selects a subset of each.
     */
    const val LABEL_TEAM_PLATFORM = "team:platform"
    const val LABEL_TEAM_APPS = "team:apps"
    const val LABEL_LANGUAGE_JAVA = "language:java"
    const val LABEL_LANGUAGE_JAVASCRIPT = "language:javascript"
    const val LABEL_LANGUAGE_KOTLIN = "language:kotlin"

    /**
     * The labels the two estates of the delivery scorecard select their projects by. A category each,
     * named for what an estate is - a set of projects someone answers for - rather than for the
     * estate itself, so that the chips read as facts about a project.
     */
    const val LABEL_PORTFOLIO_PRODUCT = "portfolio:product"
    const val LABEL_RUNS_IN_PRODUCTION = "runs-in:production"

    /** The estate read up to [GOLD]. */
    const val ESTATE_PRODUCTS = "Demo products"

    /** The estate read up to the deployments in [PRODUCTION]. */
    const val ESTATE_PRODUCTION = "Demo production"

    /**
     * The project of the audit trail (#1970): a release build with its whole story, from the token of
     * its pipeline to its deployment in production, with evidence of every kind. A project of its own
     * for the reason [SECURITY] is one: its trail is read entry by entry, and the curated readings of
     * [SERVICE] are not a story worth reading that way.
     */
    const val AUDIT_TRAIL = "audit-trail-demo"

    /**
     * The project whose trail is deliberately tampered with, through the demonstration tampering
     * switch: its verification breaks at [AUDIT_TRAIL_TAMPERED_SEQ]. Left out of the reset on an
     * instance without the switch - which is every instance tracking real deliveries.
     */
    const val AUDIT_TRAIL_TAMPERED = "audit-trail-tampered"

    /** The release build of [AUDIT_TRAIL], the one whose trail is worth opening. */
    const val AUDIT_TRAIL_RELEASE = "121"

    /** The build of [AUDIT_TRAIL_TAMPERED]. */
    const val AUDIT_TRAIL_TAMPERED_BUILD = "7"

    /** Where the trail of [AUDIT_TRAIL_TAMPERED_BUILD] breaks. */
    const val AUDIT_TRAIL_TAMPERED_SEQ = 4

    /**
     * The API token the pipeline of [AUDIT_TRAIL] posts through, which its trail shows as the actor
     * of everything the pipeline did - `token:ci-demo`.
     */
    const val CI_TOKEN = "ci-demo"

    const val UI_TESTS = "UI.TESTS"
    const val SBOM = "SBOM"
    const val DAST = "DAST"

    /**
     * The stamp of [AUDIT_TRAIL] deleted at the end of the reset, which is what puts
     * `validation.deleted` with the reason `cascade/validation-stamp-deleted` on both its builds.
     */
    const val LEGACY_LINT = "LEGACY.LINT"

    /** The admission rule of the production slot of [AUDIT_TRAIL] the deployment overrides. */
    const val CHANGE_APPROVAL = "change-approval"

    /**
     * The whole dataset, curated part and changelog project together.
     *
     * @param changelog Commits since the last release, one build each.
     */
    fun dataset(changelog: List<ChangelogEntry>) = DemoDataset(
        labels = labels(),
        projects = listOf(
            library(),
            service(),
            ui(),
            security(),
            visits(),
            e2e(),
            auditTrail(),
            auditTrailTampered(),
            changelogProject(changelog),
        ),
        environments = environments(),
        deployments = deployments() + visitsDeployments() + auditTrailDeployments(),
        dashboard = dashboard(),
        estates = estates(),
    )

    /**
     * The labels every project of the demo carries. Every one of them is on at least one
     * project: a label nobody carries shows an empty label page and is the one thing a
     * reader would take for a bug rather than for a demonstration.
     *
     * The colours are the chips' background; Yontrack computes a readable foreground from
     * each, which is why a dark and a light one both appear here.
     */
    private fun labels() = listOf(
        LabelSpec(
            category = "team",
            name = "platform",
            description = "Owned by the platform team.",
            color = "#2F54EB",
        ),
        LabelSpec(
            category = "team",
            name = "apps",
            description = "Owned by the application team.",
            color = "#FA8C16",
        ),
        LabelSpec(
            category = "language",
            name = "java",
            description = "Written in Java.",
            color = "#B7292E",
        ),
        LabelSpec(
            category = "language",
            name = "javascript",
            description = "Written in JavaScript.",
            color = "#F7DF1E",
        ),
        LabelSpec(
            category = "language",
            name = "kotlin",
            description = "Written in Kotlin.",
            color = "#7F52FF",
        ),
        LabelSpec(
            category = "portfolio",
            name = "product",
            description = "Part of what the customers buy. Selects the projects of the \"$ESTATE_PRODUCTS\" estate.",
            color = "#13A8A8",
        ),
        LabelSpec(
            category = "runs-in",
            name = "production",
            description = "Runs in production. Selects the projects of the \"$ESTATE_PRODUCTION\" estate.",
            color = "#389E0D",
        ),
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
     * [UI] keeps [bronzeAuto] and the plain [silver], and [LIBRARY] the plain [bronze] and
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

    /**
     * The security scan of [UI], as a CHML stamp which lets a warning through to the auto promotion
     * (#1943): any critical issue fails it, any high one only warns, and a warning still counts as
     * passed for [bronzeAuto] - and only for it.
     *
     * Kept apart from [securityScan], which carries no data type and whose warning on [SERVICE] build
     * 104 has to keep reading as the warning it is.
     */
    private val tolerantSecurityScan = ValidationStampSpec(
        SECURITY_SCAN,
        "Dependency and container scan. A warning does not hold BRONZE back.",
        chml = CHMLSpec(
            failedLevel = CHML.CRITICAL,
            failedValue = 1,
            warningLevel = CHML.HIGH,
            warningValue = 1,
            warningPassesAutoPromotion = true,
        ),
    )

    /**
     * BRONZE on [UI]: granted by itself once the build has built and its security scan is not red -
     * a [tolerantSecurityScan] in WARNING counts as passed, which is what build 59 shows.
     *
     * It reproduces the promotions the dataset declares rather than adding any: both builds of [UI]
     * are BRONZE, with `BUILD` green and the scan PASSED or WARNING.
     */
    private val bronzeAuto = bronze.copy(
        autoPromotion = AutoPromotionSpec(
            validationStamps = listOf(BUILD, SECURITY_SCAN),
        ),
    )

    /** The full set of checks, for the same projects. */
    private val fullValidationStamps = listOf(buildStamp, unitTests, integrationTests, securityScan)

    /**
     * A library everything else depends on — the bottom of the dependency graph.
     */
    private fun library() = ProjectSpec(
        name = LIBRARY,
        description = "Shared library, used by the other demo projects.",
        labels = listOf(LABEL_TEAM_PLATFORM, LABEL_LANGUAGE_JAVA),
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
     * The dependency scan of a build of [SERVICE] on [MAIN] (#1982). [CVE_FIXED_ON_MAIN] is reported on
     * spring-webmvc by 1.4.0 to 1.4.2, which use common-library 3.2.0, and no longer by 1.4.3, whose bump
     * of common-library to 3.2.1 brings spring-webmvc 6.1.13: eight days, within the remediation target of
     * "Demo products". [MAIN] is the only branch it is scanned on, so the finding is resolved for the
     * project as a whole - the third state of the fan-out of "Demo products", beside [SECURITY], where it
     * is still exposed, and [VISITS], where it is accepted.
     *
     * The scan reads WARNING while the HIGH is open and PASSED after: nothing on [SERVICE] requires it,
     * so its promotions are the curated ones, and its security maturity is 2 in "Demo production" -
     * covered - and 1 in "Demo products", which expects a code scan as well.
     *
     * @param webMvcFixed Whether the build carries spring-webmvc 6.1.13
     */
    private fun serviceScans(webMvcFixed: Boolean) = listOf(
        ScanSpec(
            validationStamp = SECURITY_DEPENDENCIES,
            format = ScanFormat.FINDINGS,
            kind = ScanKind.DEPENDENCIES,
            scanner = "trivy",
            findings = listOfNotNull(springWebMvcPath.takeUnless { webMvcFixed }),
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
        // In both estates, and the reason they overlap: its scorecard reads it twice, up to GOLD and
        // up to production, and the two columns do not say the same thing.
        labels = listOf(LABEL_TEAM_APPS, LABEL_LANGUAGE_JAVA, LABEL_PORTFOLIO_PRODUCT, LABEL_RUNS_IN_PRODUCTION),
        branches = listOf(
            BranchSpec(
                name = MAIN,
                description = "Main development branch.",
                scmBranch = SCM_MAIN,
                favourite = true,
                promotionLevels = fullPromotions + canaryPass,
                // The dependency scan on this branch only: the maintenance branch has none, and a branch
                // which never scanned exposes nothing, so the project is resolved as a whole (#1982)
                validationStamps = fullValidationStamps + dependencyScan,
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
                        scans = serviceScans(webMvcFixed = false),
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
                        scans = serviceScans(webMvcFixed = false),
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
                        scans = serviceScans(webMvcFixed = false),
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
                        scans = serviceScans(webMvcFixed = true),
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
                        scans = serviceScans(webMvcFixed = true),
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
                        scans = serviceScans(webMvcFixed = true),
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
                        scans = serviceScans(webMvcFixed = true),
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
        // Same team as the service, another language: the two categories cut the demo's
        // projects in two different ways, which is what makes filtering on both interesting.
        // In production, so in the "Demo production" estate - where nothing has ever reached its slot,
        // whose rules are broken on purpose, and every delivery reading is unknown for want of a deployment.
        labels = listOf(LABEL_TEAM_APPS, LABEL_LANGUAGE_JAVASCRIPT, LABEL_RUNS_IN_PRODUCTION),
        branches = listOf(
            BranchSpec(
                name = MAIN,
                description = "Main development branch.",
                promotionLevels = listOf(bronzeAuto, silver),
                validationStamps = listOf(buildStamp, unitTests, tolerantSecurityScan),
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
                            ValidationSpec(SECURITY_SCAN, PASSED),
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
                            // Accepted by the stamp: BRONZE is granted all the same
                            ValidationSpec(SECURITY_SCAN, WARNING, "One high advisory in a transitive dependency."),
                        ),
                        links = listOf(BuildRef(SERVICE, MAIN, "105")),
                    ),
                ),
            ),
        ),
    )

    /**
     * The two security stamps of [SECURITY], on both of its branches. The thresholds are the
     * default ones - a warning for a HIGH, a failure for a CRITICAL - so a build reads WARNING
     * while the HIGH is open, and the accepted CRITICAL, which never counts, fails nothing.
     */
    private val securityStamps = listOf(
        buildStamp,
        ValidationStampSpec(
            SECURITY_DEPENDENCIES,
            "Vulnerabilities of the dependencies, from Trivy.",
            findings = FindingsThresholdsSpec(),
        ),
        ValidationStampSpec(
            SECURITY_CODE,
            "Code scanning, from CodeQL.",
            findings = FindingsThresholdsSpec(),
        ),
    )

    private val springWebMvcPath = FindingSpec(
        externalId = CVE_FIXED_ON_MAIN,
        location = "pkg:maven/org.springframework/spring-webmvc",
        severity = FindingSeverity.HIGH,
        title = "Path traversal vulnerability in functional web frameworks",
        url = "https://nvd.nist.gov/vuln/detail/$CVE_FIXED_ON_MAIN",
        installedVersion = "6.1.12",
        fixedVersion = "6.1.13",
    )

    /**
     * Accepted for 90 days from the reset, whenever the reset runs: the demo shows an acceptance
     * with an expiry which is always ahead of it, rather than one which lapsed because the
     * dataset got old.
     */
    private val snakeYamlConstructor = FindingSpec(
        externalId = CVE_ACCEPTED,
        location = "pkg:maven/org.yaml/snakeyaml",
        severity = FindingSeverity.CRITICAL,
        title = "SnakeYaml Constructor deserialization remote code execution",
        url = "https://nvd.nist.gov/vuln/detail/$CVE_ACCEPTED",
        installedVersion = "1.33",
        fixedVersion = "2.0",
        acceptance = AcceptanceSpec(
            statement = "SnakeYAML only ever parses the configuration files shipped with the " +
                    "service, never input from a request. Removed with the move to Spring Boot 3.4.",
            source = ".trivyignore.yaml",
            expiresInDays = 90,
        ),
    )

    private val commonsIoXmlStreamReader = FindingSpec(
        externalId = "CVE-2024-47554",
        location = "pkg:maven/commons-io/commons-io",
        severity = FindingSeverity.MEDIUM,
        title = "Possible denial of service attack on untrusted input to XmlStreamReader",
        url = "https://nvd.nist.gov/vuln/detail/CVE-2024-47554",
        installedVersion = "2.11.0",
        fixedVersion = "2.14.0",
    )

    /**
     * Reported by [CVE_CRITICAL_FIXED]'s build only, and not accepted: it fails the dependency scan of
     * that build, which is what makes the security maturity of [SECURITY] reach "gating".
     */
    private val pgjdbcSqlInjection = FindingSpec(
        externalId = CVE_CRITICAL_FIXED,
        location = "pkg:maven/org.postgresql/postgresql",
        severity = FindingSeverity.CRITICAL,
        title = "SQL injection in pgjdbc when using the simple query mode",
        url = "https://nvd.nist.gov/vuln/detail/$CVE_CRITICAL_FIXED",
        installedVersion = "42.7.1",
        fixedVersion = "42.7.2",
    )

    private val protobufStackOverflow = FindingSpec(
        externalId = CVE_HIGH_FIXED,
        location = "pkg:maven/com.google.protobuf/protobuf-java",
        severity = FindingSeverity.HIGH,
        title = "Stack overflow in protobuf-java when parsing nested groups",
        url = "https://nvd.nist.gov/vuln/detail/$CVE_HIGH_FIXED",
        installedVersion = "3.25.3",
        fixedVersion = "3.25.5",
    )

    private val insecureCookie = FindingSpec(
        externalId = "java/insecure-cookie",
        location = "src/main/java/org/springframework/samples/petclinic/billing/web/InvoiceController.java",
        severity = FindingSeverity.MEDIUM,
        title = "Failure to use secure cookies",
        url = "https://codeql.github.com/codeql-query-help/java/java-insecure-cookie/",
    )

    /**
     * Accepted through a SARIF suppression, which has no expiry: the other kind of acceptance.
     */
    private val csrfDisabled = FindingSpec(
        externalId = "java/spring-disabled-csrf-protection",
        location = "src/main/java/org/springframework/samples/petclinic/billing/config/SecurityConfig.java",
        severity = FindingSeverity.HIGH,
        title = "Disabled Spring CSRF protection",
        url = "https://codeql.github.com/codeql-query-help/java/java-spring-disabled-csrf-protection/",
        acceptance = AcceptanceSpec(
            statement = "The billing API is stateless and only accepts bearer tokens: there is no " +
                    "session for a forged request to ride on.",
            source = ".github/codeql/suppressions.sarif",
        ),
    )

    /**
     * The two scans of every build of [SECURITY]: its dependencies in the neutral format, whose
     * acceptances can carry an expiry, and its code in SARIF - the native format the demo shows,
     * with the dev licence.
     *
     * @param highFixed Whether the build carries the fix of [CVE_FIXED_ON_MAIN]
     * @param alsoReports Findings the dependency scan of this build reports besides the usual ones
     */
    private fun securityScans(highFixed: Boolean, alsoReports: List<FindingSpec>) = listOf(
        ScanSpec(
            validationStamp = SECURITY_DEPENDENCIES,
            format = ScanFormat.FINDINGS,
            kind = ScanKind.DEPENDENCIES,
            scanner = "trivy",
            findings = listOfNotNull(
                snakeYamlConstructor,
                springWebMvcPath.takeUnless { highFixed },
                commonsIoXmlStreamReader,
            ) + alsoReports,
        ),
        ScanSpec(
            validationStamp = SECURITY_CODE,
            format = ScanFormat.SARIF,
            kind = ScanKind.CODE,
            scanner = "codeql",
            findings = listOf(csrfDisabled, insecureCookie),
        ),
    )

    private fun securityBuild(
        name: String,
        release: String,
        description: String,
        creation: BuildCreation,
        highFixed: Boolean = false,
        alsoReports: List<FindingSpec> = emptyList(),
        promoted: Boolean = true,
    ) = BuildSpec(
        name = name,
        release = release,
        description = description,
        creation = creation,
        promotionLevels = if (promoted) listOf(BRONZE) else emptyList(),
        validations = listOf(ValidationSpec(BUILD, PASSED)),
        scans = securityScans(highFixed, alsoReports),
    )

    /**
     * The security findings, on two branches (#1867).
     *
     * * [CVE_FIXED_ON_MAIN], a HIGH, is reported by the first three builds of [MAIN], fixed by the
     *   fourth - which bumps spring-webmvc - and stays exposed on [SECURITY_RELEASE], which never
     *   got the bump. It is resolved on [MAIN] and open for the project, and it is the CVE the
     *   search finds.
     * * [CVE_ACCEPTED], a CRITICAL, is reported everywhere under an acceptance which expires in
     *   90 days: it counts as accepted, never as open, and fails no build.
     * * The code scan is posted in SARIF, and carries a HIGH accepted by a suppression - an
     *   acceptance without expiry, which is all SARIF can say.
     * * [CVE_HIGH_FIXED], a HIGH, is reported by the first build of [SECURITY_RELEASE] and fixed by
     *   the next one; [CVE_CRITICAL_FIXED], a CRITICAL, is reported by one build of [MAIN] - whose
     *   dependency scan it FAILS, so the build is not promoted - and fixed by the next one. Each is
     *   reported on one branch only, so resolved for the project as soon as its branch fixes it:
     *   eleven days and three days, the two remediations whose median the remediation time reads
     *   (#1912).
     *
     * What the security readings of the scorecard read here, in "Demo products" (#1912): a maturity
     * of 3 - both expected kinds scanned the day before, and a scan which failed in the window - a
     * remediation time of seven days, and one overdue finding: [CVE_FIXED_ON_MAIN], open on
     * [SECURITY_RELEASE] for sixteen days against a target of fourteen for a HIGH. The two accepted
     * findings are counted apart, in the details of both remediation readings.
     *
     * The release branch is declared - and so scanned - FIRST, because its builds are the oldest:
     * a finding is first seen by the first scan reporting it, whatever the date of a later one,
     * and the release branch is where [CVE_FIXED_ON_MAIN] was reported first.
     */
    private fun security() = ProjectSpec(
        name = SECURITY,
        description = "Billing service of the sample application - the demo's security findings.",
        // A product, so in the "Demo products" estate, whose security readings read its findings. Up
        // to GOLD, which it does not have: its delivery readings there are unknown, and say so.
        labels = listOf(LABEL_TEAM_APPS, LABEL_LANGUAGE_JAVA, LABEL_PORTFOLIO_PRODUCT),
        branches = listOf(
            BranchSpec(
                name = SECURITY_RELEASE,
                description = "Maintenance of the 2.3 line, still on spring-webmvc 6.1.12.",
                promotionLevels = listOf(bronze),
                validationStamps = securityStamps,
                builds = listOf(
                    securityBuild(
                        "305", "2.3.4", "Invoice numbering fix.", DaysAgo(16),
                        alsoReports = listOf(protobufStackOverflow),
                    ),
                    securityBuild("309", "2.3.5", "Rounding of the VAT amounts, and protobuf-java 3.25.5.", DaysAgo(5)),
                ),
            ),
            BranchSpec(
                name = MAIN,
                description = "Main development branch.",
                promotionLevels = listOf(bronze),
                validationStamps = securityStamps,
                builds = listOf(
                    securityBuild("310", "2.4.0", "Invoices as PDF.", DaysAgo(13)),
                    // Its dependency scan FAILS on an unaccepted CRITICAL: not promoted
                    securityBuild(
                        "311", "2.4.1", "Payment reminders.", DaysAgo(10),
                        alsoReports = listOf(pgjdbcSqlInjection),
                        promoted = false,
                    ),
                    securityBuild("312", "2.4.2", "Invoice search by owner, and pgjdbc 42.7.2.", DaysAgo(7)),
                    securityBuild(
                        "313", "2.4.3", "Bump of spring-webmvc to 6.1.13.", DaysAgo(4),
                        highFixed = true,
                    ),
                    securityBuild(
                        "314", "2.4.4", "Credit notes.", DaysAgo(1),
                        highFixed = true,
                    ),
                ),
            ),
        ),
    )

    private val testSummary = ValidationStampSpec(
        TEST_SUMMARY,
        "Unit and integration tests, with their counts.",
        tests = true,
    )

    /**
     * The dependency scan of [VISITS] and of [SERVICE], the only kind of scan they run: enough for "Demo
     * production", which expects nothing else, and short of "Demo products", which expects a code scan
     * as well.
     */
    private val dependencyScan = ValidationStampSpec(
        SECURITY_DEPENDENCIES,
        "Vulnerabilities of the dependencies, from Trivy.",
        findings = FindingsThresholdsSpec(),
    )

    /**
     * [CVE_FIXED_ON_MAIN] again, in [VISITS] this time, on spring-webflux - which the visit scheduler
     * only uses for its HTTP client - and accepted there: it serves nothing through the functional
     * endpoints. Another location, so another finding than the one on spring-webmvc.
     */
    private val springWebFluxPathAccepted = springWebMvcPath.copy(
        location = "pkg:maven/org.springframework/spring-webflux",
        acceptance = AcceptanceSpec(
            statement = "spring-webflux is only on the class path for the WebClient: the visit " +
                    "scheduler serves nothing through RouterFunctions, and the vulnerable path is never reached.",
            source = ".trivyignore.yaml",
            expiresInDays = 30,
        ),
    )

    /**
     * The dependency scan of a build of [VISITS]. [CVE_FIXED_ON_MAIN] is reported twice: on
     * spring-webflux under an acceptance, and on spring-webmvc until a bump fixes it. With [SECURITY],
     * which still exposes it on [SECURITY_RELEASE], it is the finding the fan-out of "Demo products"
     * shows open, accepted and resolved (#1912).
     *
     * @param webMvcFixed Whether the build carries the bump of spring-webmvc
     */
    private fun visitsScans(webMvcFixed: Boolean) = listOf(
        ScanSpec(
            validationStamp = SECURITY_DEPENDENCIES,
            format = ScanFormat.FINDINGS,
            kind = ScanKind.DEPENDENCIES,
            scanner = "trivy",
            findings = listOfNotNull(
                springWebMvcPath.takeUnless { webMvcFixed },
                springWebFluxPathAccepted,
            ),
        ),
    )

    /**
     * SILVER on [VISITS]: granted by itself to a BRONZE build whose tests and dependency scan are green.
     * Requiring a security stamp through the auto promotion is what makes a project *gating* by policy
     * (#1982) - the route teams should aim for, where [SECURITY] only gets there through a scan which
     * failed. Its security maturity is 3 wherever it is covered: in "Demo production" and with no estate.
     *
     * It reproduces the promotions the dataset declares rather than adding any. The builds before 1.5.1
     * have no scan and 1.1.0 failed its tests, so none of them satisfies it; 1.5.1 and 1.6.0 carry the
     * open HIGH, which makes their scan a WARNING the rule does not let through, and were promoted by a
     * human; 1.6.1 and 1.6.2 satisfy it, and declare SILVER.
     */
    private val visitsSilver = silver.copy(
        autoPromotion = AutoPromotionSpec(
            validationStamps = listOf(TEST_SUMMARY, SECURITY_DEPENDENCIES),
            promotionLevels = listOf(BRONZE),
        ),
    )

    /**
     * A release of [VISITS]: built, tested, and promoted up the whole ladder unless it says otherwise.
     *
     * @param tests The runs of [TEST_SUMMARY], in order
     */
    private fun visitsBuild(
        name: String,
        release: String,
        description: String,
        creation: BuildCreation,
        tests: List<TestRunSpec>,
        promotionLevels: List<String> = listOf(BRONZE, SILVER, GOLD),
        scans: List<ScanSpec> = emptyList(),
    ) = BuildSpec(
        name = name,
        release = release,
        description = description,
        creation = creation,
        promotionLevels = promotionLevels,
        validations = listOf(ValidationSpec(BUILD, PASSED)),
        tests = tests,
        scans = scans,
    )

    private fun passing(passed: Int) = listOf(TestRunSpec(TEST_SUMMARY, passed = passed))

    /**
     * The delivery scorecard's project: about ninety days of weekly releases, each built, tested,
     * promoted to GOLD a few hours later and deployed to production the day after (#1906).
     *
     * What each of its readings has to show is a build of the list below:
     *
     * * **1.1.0** fails its tests and is never promoted: the only break in the GOLD history, and the
     *   time to restore up to GOLD, until 1.1.1;
     * * **1.2.1** is the flaky build - its tests fail, then pass on the same build;
     * * **1.3.0** reaches GOLD but fails its production deployment, which **1.3.1** restores the next
     *   day: the time to restore in production, and the one failed deployment of the success rate;
     * * **1.4.1** has its deployment cancelled, which every reading leaves out;
     * * **1.6.2**, the head, is hours old and still on its way to GOLD - in flight, so the success
     *   rate leaves it out rather than counting it as a failure.
     *
     * Every promotion follows the build by a few hours and every deployment by a day, so the lead
     * time reads in hours in "Demo products" and in days in "Demo production": one project, two
     * different answers, which is what the two estate columns are for.
     *
     * Its last four builds scan their dependencies (#1912): [CVE_FIXED_ON_MAIN] is reported on
     * spring-webflux under an acceptance, and on spring-webmvc by **1.5.1** and **1.6.0**, until
     * **1.6.1** bumps it - fourteen days, its remediation time. Its SILVER requires that scan
     * ([visitsSilver]), which makes it gating by policy (#1982), and its security maturity reads
     * differently in each set: 1 in "Demo products", which also expects a code scan - a rung needs the
     * ones below it - and 3 in "Demo production" and with no estate, where a fresh dependency scan is
     * enough to be covered.
     */
    private fun visits() = ProjectSpec(
        name = VISITS,
        description = "Visit scheduling service of the sample application - the demo's delivery scorecard.",
        labels = listOf(LABEL_TEAM_APPS, LABEL_LANGUAGE_KOTLIN, LABEL_PORTFOLIO_PRODUCT, LABEL_RUNS_IN_PRODUCTION),
        branches = listOf(
            BranchSpec(
                name = MAIN,
                description = "Main development branch.",
                promotionLevels = listOf(bronze, visitsSilver, gold),
                validationStamps = listOf(buildStamp, testSummary, dependencyScan),
                builds = listOf(
                    visitsBuild("201", "1.0.0", "First release of the visit scheduler.", DaysAgo(88), passing(380)),
                    visitsBuild("202", "1.0.1", "Reminder e-mails for upcoming visits.", DaysAgo(81), passing(386)),
                    visitsBuild(
                        "203", "1.1.0", "Recurring visits. Three tests fail on the month boundaries.", DaysAgo(74),
                        tests = listOf(TestRunSpec(TEST_SUMMARY, passed = 389, failed = 3)),
                        promotionLevels = listOf(BRONZE),
                    ),
                    visitsBuild("204", "1.1.1", "Recurring visits across the month boundaries.", DaysAgo(71), passing(395)),
                    visitsBuild("205", "1.2.0", "Visits listed by vet.", DaysAgo(63), passing(401)),
                    visitsBuild(
                        "206", "1.2.1", "Visit calendar export.", DaysAgo(56),
                        // FLAKY: the same build, the same stamp, failed then passed - a retry, nothing
                        // changed in between
                        tests = listOf(
                            TestRunSpec(TEST_SUMMARY, passed = 406, failed = 1, description = "VisitCalendarIT timed out."),
                            TestRunSpec(TEST_SUMMARY, passed = 407, description = "Retried."),
                        ),
                    ),
                    visitsBuild("207", "1.3.0", "Online booking.", DaysAgo(49), passing(412)),
                    visitsBuild(
                        "208", "1.3.1", "Online booking, hotfix for the time zones.", DaysAgo(48, hour = 16),
                        passing(414),
                    ),
                    visitsBuild("209", "1.4.0", "Waiting list.", DaysAgo(39), passing(420)),
                    visitsBuild("210", "1.4.1", "Waiting list notifications.", DaysAgo(32), passing(424)),
                    visitsBuild("211", "1.5.0", "Visit notes.", DaysAgo(25), passing(431)),
                    visitsBuild(
                        "212", "1.5.1", "Attachments on the visit notes.", DaysAgo(18), passing(436),
                        scans = visitsScans(webMvcFixed = false),
                    ),
                    visitsBuild(
                        "213", "1.6.0", "Vet availability.", DaysAgo(11), passing(440),
                        scans = visitsScans(webMvcFixed = false),
                    ),
                    visitsBuild(
                        "214", "1.6.1", "Availability shown in the calendar, and spring-webmvc 6.1.13.", DaysAgo(4),
                        passing(446),
                        scans = visitsScans(webMvcFixed = true),
                    ),
                    // In flight: younger than the lead time to GOLD, so not a failure yet
                    visitsBuild(
                        "215", "1.6.2", "Booking confirmation page.", HoursAgo(3),
                        passing(449),
                        promotionLevels = listOf(BRONZE, SILVER),
                        scans = visitsScans(webMvcFixed = true),
                    ),
                ),
            ),
        ),
    )

    /**
     * The production history of [VISITS], oldest first as the server requires of one slot: the day
     * after each GOLD release, in the afternoon.
     *
     * 1.3.0 FAILS and 1.3.1 is deployed the next morning, which is the one outage of production; 1.4.1
     * is cancelled, superseded before it went out. 1.1.0 was never GOLD and is not here - the slot's
     * rule would refuse it anyway.
     */
    private fun visitsDeployments(): List<DeploymentSpec> {
        fun deployment(
            build: String,
            at: BuildCreation,
            stopAt: DeploymentStop = DeploymentStop.DONE,
            message: String? = null,
        ) = DeploymentSpec(
            environment = PRODUCTION,
            build = BuildRef(VISITS, MAIN, build),
            stopAt = stopAt,
            at = at,
            message = message,
        )
        return listOf(
            deployment("201", DaysAgo(87, hour = 14)),
            deployment("202", DaysAgo(80, hour = 14)),
            deployment("204", DaysAgo(70, hour = 14)),
            deployment("205", DaysAgo(62, hour = 14)),
            deployment("206", DaysAgo(55, hour = 14)),
            deployment(
                "207", DaysAgo(48, hour = 14), DeploymentStop.FAILED,
                message = "Smoke tests failed: bookings are refused outside of UTC.",
            ),
            deployment("208", DaysAgo(47, hour = 10)),
            deployment("209", DaysAgo(38, hour = 14)),
            deployment(
                "210", DaysAgo(31, hour = 14), DeploymentStop.CANCELLED,
                message = "Superseded: the waiting list notifications go out with the visit notes.",
            ),
            deployment("211", DaysAgo(24, hour = 14)),
            deployment("212", DaysAgo(17, hour = 14)),
            deployment("213", DaysAgo(10, hour = 14)),
            deployment("214", DaysAgo(3, hour = 14)),
        )
    }

    /**
     * The two estates of the delivery scorecard, over projects which overlap: [SERVICE] and [VISITS]
     * are in both, so their scorecards have two estate columns beside the project's own, and read
     * differently in each.
     *
     * The targets are chosen so that each estate has readings meeting them and readings missing
     * them, and one reading - the flakiness of the tests - has none, and is shown without a verdict.
     * Durations in seconds, frequencies per week, rates from 0 to 100, as the API takes them.
     *
     * * "Demo products" (up to GOLD): [VISITS] meets its lead time, frequency and success rate, and
     *   misses its time to restore - three days from 1.1.0 to 1.1.1 - and its test pass rate. [SECURITY]
     *   is in it and has no GOLD, so its delivery readings are unknown, with the reason.
     * * "Demo production" (up to the production deployments): [VISITS] meets its lead time, success
     *   rate and time to restore, and misses the frequency over the estate's own 30-day window;
     *   [SERVICE] has had no failure there, so its time to restore reads "no failure" rather than a
     *   verdict; [UI] has never deployed.
     *
     * Their security readings (#1912), against the scans each estate expects and its remediation
     * targets in days:
     *
     * * "Demo products" expects a dependency AND a code scan within a week, and gives a CRITICAL a
     *   week and a HIGH two. Its maturity reads two rungs: [SECURITY] at 3 (gating, by a failed scan),
     *   [VISITS] and [SERVICE] at 1 (reported, no code scan) - [VISITS] gates its SILVER on its scan,
     *   but is not covered here, and a rung needs the ones below it.
     *   [SECURITY] meets its remediation time and misses its overdue target, with one HIGH open past
     *   its two weeks; [VISITS] misses its remediation time, fourteen days for its one HIGH, and has
     *   nothing overdue; [SERVICE] meets it, eight days for the CVE it resolved (#1982).
     * * "Demo production" expects a dependency scan only, fresh by the settings' freshness: its maturity
     *   reads three rungs - [VISITS] at 3 (gating by policy), [SERVICE] at 2 (covered), both meeting the
     *   target, and [UI] at 0, missing it: its `SECURITY.SCAN` is a plain stamp, not a scan. Its remediation
     *   targets are set but its overdue reading is judged against nothing: a count of zero shown
     *   without a verdict.
     */
    private fun estates() = listOf(
        EstateSpec(
            name = ESTATE_PRODUCTS,
            description = "What the customers buy, read up to the GOLD promotion.",
            labels = listOf(LABEL_PORTFOLIO_PRODUCT),
            marker = EstateMarkerSpec.Promotion(GOLD),
            readings = listOf(
                EstateReadingSpec(ReadingKeys.DELIVERY_LEAD_TIME, target = DAY_SECONDS),
                EstateReadingSpec(ReadingKeys.DELIVERY_FREQUENCY, target = 0.5),
                EstateReadingSpec(ReadingKeys.DELIVERY_SUCCESS_RATE, target = 80.0),
                EstateReadingSpec(ReadingKeys.DELIVERY_MTTR, target = 2 * DAY_SECONDS),
                EstateReadingSpec(ReadingKeys.QUALITY_TEST_PASS_RATE, target = 95.0),
                EstateReadingSpec(ReadingKeys.SECURITY_MATURITY, target = 2.0),
                EstateReadingSpec(ReadingKeys.SECURITY_REMEDIATION_TIME, target = 10 * DAY_SECONDS),
                EstateReadingSpec(ReadingKeys.SECURITY_OVERDUE, target = 0.0),
            ),
            security = EstateSecuritySpec(
                expectedKinds = listOf(ScanKind.DEPENDENCIES, ScanKind.CODE),
                freshnessDays = 7,
                criticalTargetDays = 7,
                highTargetDays = 14,
            ),
        ),
        EstateSpec(
            name = ESTATE_PRODUCTION,
            description = "What runs in production, read up to its deployments there.",
            labels = listOf(LABEL_RUNS_IN_PRODUCTION),
            marker = EstateMarkerSpec.Environment(PRODUCTION),
            readings = listOf(
                EstateReadingSpec(ReadingKeys.DELIVERY_LEAD_TIME, target = 2 * DAY_SECONDS),
                EstateReadingSpec(ReadingKeys.DELIVERY_FREQUENCY, windowDays = 30, target = 1.0),
                EstateReadingSpec(ReadingKeys.DELIVERY_SUCCESS_RATE, target = 90.0),
                EstateReadingSpec(ReadingKeys.DELIVERY_MTTR, target = DAY_SECONDS),
                EstateReadingSpec(ReadingKeys.SECURITY_MATURITY, target = 2.0),
            ),
            // No freshness of its own: the one of the settings
            security = EstateSecuritySpec(
                expectedKinds = listOf(ScanKind.DEPENDENCIES),
                criticalTargetDays = 7,
                highTargetDays = 30,
            ),
        ),
    )

    private const val DAY_SECONDS = 86_400.0

    // The tables showcase (#1932) -------------------------------------------------------------

    /**
     * The suites of [E2E], one validation stamp each, [E2E_SMOKE] first. Thirty of them: enough for
     * the branch matrix to scroll sideways on a wide screen, and for the validations of a build
     * which ran them all to overflow their section of the build page.
     */
    private val e2eSuites = listOf(E2E_SMOKE) + listOf(
        "OWNERS", "PETS", "VISITS", "VETS", "SPECIALTIES", "BOOKING", "CALENDAR", "AVAILABILITY",
        "REMINDERS", "NOTIFICATIONS", "BILLING", "INVOICES", "PAYMENTS", "REFUNDS", "PHARMACY",
        "PRESCRIPTIONS", "VACCINATIONS", "LABS", "NOTES", "ATTACHMENTS", "SEARCH", "LOGIN", "PROFILE",
        "SETTINGS", "REPORTS", "EXPORTS", "IMPORTS", "INVENTORY", "AUDIT",
    ).map { "E2E.$it" }

    private val e2eStamps = e2eSuites.map { ValidationStampSpec(it, "End-to-end suite ${it.removePrefix("E2E.").lowercase()}.") }

    /**
     * The suites the feature branches run, and the branch statuses widget shows: a few, so that the
     * widget overflows by its rows rather than by its columns.
     */
    private val e2eFeatureSuites = e2eSuites.take(4)

    /** The one suite failing on the latest nightly build of [E2E]. */
    private const val E2E_FAILING_SUITE = "E2E.PAYMENTS"

    /**
     * The nightly builds of [MAIN], oldest first. Each runs [E2E_SMOKE], which fails twice; the
     * latest runs every suite, and [E2E_FAILING_SUITE] fails. BRONZE is the smoke suite passing.
     */
    private fun e2eNightlyBuilds(): List<BuildSpec> {
        val count = 30
        val smokeFailures = setOf(9, 21)
        return (1..count).map { number ->
            val smoke = if (number in smokeFailures) FAILED else PASSED
            val latest = number == count
            BuildSpec(
                name = "nightly-%02d".format(number),
                description = if (latest) "Nightly build, full end-to-end run." else "Nightly build, smoke run.",
                creation = DaysAgo((count - number + 1).toLong(), hour = 2),
                promotionLevels = if (smoke == PASSED) listOf(BRONZE) else emptyList(),
                validations = if (latest) {
                    e2eSuites.map { suite -> ValidationSpec(suite, if (suite == E2E_FAILING_SUITE) FAILED else PASSED) }
                } else {
                    listOf(ValidationSpec(E2E_SMOKE, smoke))
                },
            )
        }
    }

    /** The changes in flight on [E2E], one branch each. */
    private val e2eFeatures = listOf(
        "owner-search", "pet-photos", "visit-notes", "vet-schedule", "online-booking", "calendar-sync",
        "reminder-sms", "invoice-pdf", "card-payments", "refunds", "prescriptions", "vaccination-plan",
        "lab-results", "audit-log",
    )

    /**
     * A feature branch of [E2E], with the one build it was pushed with. One in five fails one of its
     * suites and is not promoted, so the widget is not a wall of green.
     */
    private fun e2eFeatureBranch(index: Int, feature: String): BranchSpec {
        val failing = index % 5 == 2
        return BranchSpec(
            name = "feature-$feature",
            description = "Feature branch for $feature.",
            promotionLevels = listOf(bronze),
            validationStamps = e2eStamps.take(e2eFeatureSuites.size),
            builds = listOf(
                BuildSpec(
                    name = "1",
                    description = "First push of $feature.",
                    creation = DaysAgo((index % 7 + 1).toLong(), hour = 14),
                    promotionLevels = if (failing) emptyList() else listOf(BRONZE),
                    validations = e2eFeatureSuites.mapIndexed { suiteIndex, suite ->
                        ValidationSpec(suite, if (failing && suiteIndex == e2eFeatureSuites.lastIndex) FAILED else PASSED)
                    },
                ),
            ),
        )
    }

    /**
     * The tables showcase (#1932): enough builds, stamps, runs and branches for the tables which show
     * them to scroll, and their headers to stay in place while they do.
     *
     * * the branch matrix of [MAIN] scrolls down once more builds are loaded, and sideways;
     * * the history of [E2E_SMOKE] scrolls after a few "Load more";
     * * the validations of the latest build overflow their section of the build page;
     * * the branch statuses widget of the demo dashboard lists every branch, and overflows.
     */
    private fun e2e() = ProjectSpec(
        name = E2E,
        description = "End-to-end test suites of the sample application - the demo's long tables.",
        labels = listOf(LABEL_TEAM_APPS, LABEL_LANGUAGE_JAVASCRIPT),
        branches = listOf(
            BranchSpec(
                name = MAIN,
                description = "Main development branch, built and tested every night.",
                promotionLevels = listOf(bronze),
                validationStamps = e2eStamps,
                builds = e2eNightlyBuilds(),
            ),
        ) + e2eFeatures.mapIndexed { index, feature -> e2eFeatureBranch(index, feature) },
    )

    // ---------------------------------------------------------------------------------------------

    /**
     * Yontrack itself, one build per commit since the last release.
     *
     * The point is not realism: it is that the demo keeps showing this month's work
     * without anyone having to remember to update the curated dataset.
     */
    // ---------------------------------------------------------------------------------------------
    // The audit trail (#1970)
    // ---------------------------------------------------------------------------------------------

    /**
     * The story of a release, as its trail records it: [AUDIT_TRAIL_RELEASE] is created by its
     * pipeline through the [CI_TOKEN] token, which sets its release and commit properties, runs seven
     * validations with their evidence, promotes it BRONZE, SILVER then GOLD and links it to two
     * dependency builds. A person then passes the failed unit tests with a comment and deletes a log
     * attached by mistake - the trail shows both under another actor than the pipeline - and deploys
     * it to staging, then to production, overriding the change approval. Last, [LEGACY_LINT] is
     * deleted, and both builds of the project record the run they lose.
     *
     * The evidence is one file of each kind the evidence page handles: a PDF and a PNG shown inline,
     * a CycloneDX SBOM in JSON and a JUnit summary in plain text, and a ZAP report in HTML, which is
     * always downloaded, never rendered. Without an evidence storage, it is all left out and the rest
     * of the story is seeded.
     */
    private fun auditTrail() = ProjectSpec(
        name = AUDIT_TRAIL,
        description = "The audit trail of a release: who did what to build $AUDIT_TRAIL_RELEASE (2.4.0), with " +
                "the evidence, in a chain anyone can verify. Open its Audit trail page.",
        labels = listOf(LABEL_TEAM_PLATFORM, LABEL_LANGUAGE_KOTLIN),
        requires = listOf(DemoCapability.AUDIT_TRAIL),
        scm = ScmSpec(
            repository = AUDIT_TRAIL,
            issues = listOf(
                IssueSpec("AUDIT-12", "Record who changed what on a release", type = "feature"),
                IssueSpec("AUDIT-13", "Keep the evidence of a deleted validation run", type = "defect"),
            ),
        ),
        branches = listOf(
            BranchSpec(
                name = MAIN,
                description = "Main development branch, released from.",
                scmBranch = SCM_MAIN,
                promotionLevels = plainPromotions,
                validationStamps = listOf(
                    buildStamp,
                    unitTests,
                    ValidationStampSpec(UI_TESTS, "End-to-end tests of the user interface, with screenshots."),
                    ValidationStampSpec(SBOM, "Software bill of materials, in CycloneDX."),
                    securityScan,
                    ValidationStampSpec(DAST, "Dynamic application security testing, against staging."),
                    ValidationStampSpec(
                        LEGACY_LINT,
                        "The old linter, retired after the release: deleting the stamp takes its runs with it.",
                        deleted = true,
                    ),
                ),
                builds = listOf(
                    BuildSpec(
                        name = "120",
                        release = "2.4.0-rc.1",
                        description = "Release candidate of 2.4.0.",
                        creation = DaysAgo(3),
                        token = CI_TOKEN,
                        commits = listOf("feat(audit): record who changed what on a release (AUDIT-12)"),
                        promotionLevels = listOf(BRONZE),
                        validations = listOf(
                            ValidationSpec(BUILD, PASSED),
                            ValidationSpec(UNIT_TESTS, PASSED),
                            ValidationSpec(LEGACY_LINT, PASSED),
                        ),
                    ),
                    BuildSpec(
                        name = AUDIT_TRAIL_RELEASE,
                        release = "2.4.0",
                        description = "Release 2.4.0.",
                        // Hours rather than a day: the newest build, and room for its ten rungs
                        creation = HoursAgo(14),
                        token = CI_TOKEN,
                        commits = listOf(
                            "fix(audit): keep the evidence of a deleted validation run (AUDIT-13)",
                            "chore(release): 2.4.0",
                        ),
                        promotionLevels = listOf(BRONZE, SILVER, GOLD),
                        validations = listOf(
                            ValidationSpec(BUILD, PASSED),
                            // The run a person looks at: FAILED by the pipeline, PASSED by somebody
                            // who read the failure, with the reason - the trail has both, by two actors
                            ValidationSpec(
                                UNIT_TESTS,
                                FAILED,
                                description = "1 failure out of 1284 tests.",
                                evidence = listOf(
                                    EvidenceSpec(
                                        fileName = "junit-summary.txt",
                                        mediaType = "text/plain",
                                        resource = "junit-summary.txt",
                                        sourceTool = "junit",
                                        sourceVersion = "5.11.3",
                                    ),
                                ),
                                // FIXED, the passed status a FAILED run can be given: Yontrack does
                                // not allow FAILED to PASSED
                                statusChanges = listOf(
                                    StatusChangeSpec(
                                        FIXED,
                                        "Reviewed: the one failure is ClockSkewTest, a known flaky test " +
                                                "fixed in AUDIT-14 - not a defect of the release.",
                                    ),
                                ),
                            ),
                            ValidationSpec(
                                UI_TESTS,
                                PASSED,
                                evidence = listOf(
                                    EvidenceSpec(
                                        fileName = "checkout-page.png",
                                        mediaType = "image/png",
                                        resource = "checkout-page.png",
                                        sourceTool = "playwright",
                                        sourceVersion = "1.49.1",
                                    ),
                                ),
                            ),
                            ValidationSpec(
                                SBOM,
                                PASSED,
                                evidence = listOf(
                                    EvidenceSpec(
                                        fileName = "sbom.cdx.json",
                                        mediaType = "application/vnd.cyclonedx+json",
                                        resource = "sbom.cdx.json",
                                        sourceTool = "cyclonedx-gradle-plugin",
                                        sourceVersion = "1.10.0",
                                    ),
                                ),
                            ),
                            ValidationSpec(
                                SECURITY_SCAN,
                                PASSED,
                                evidence = listOf(
                                    EvidenceSpec(
                                        fileName = "trivy-report.pdf",
                                        mediaType = "application/pdf",
                                        resource = "trivy-report.pdf",
                                        sourceTool = "trivy",
                                        sourceVersion = "0.56.2",
                                    ),
                                    // Attached by mistake with the report, and deleted: kept, marked
                                    // as deleted, and its deletion in the trail
                                    EvidenceSpec(
                                        fileName = "trivy-debug.log",
                                        mediaType = "text/plain",
                                        resource = "trivy-debug.log",
                                        sourceTool = "trivy",
                                        sourceVersion = "0.56.2",
                                        deleted = true,
                                    ),
                                ),
                            ),
                            ValidationSpec(
                                DAST,
                                PASSED,
                                evidence = listOf(
                                    // HTML: downloaded, never rendered in the origin of Yontrack
                                    EvidenceSpec(
                                        fileName = "zap-report.html",
                                        mediaType = "text/html",
                                        resource = "zap-report.html",
                                        sourceTool = "zap",
                                        sourceVersion = "2.15.0",
                                    ),
                                ),
                            ),
                            ValidationSpec(LEGACY_LINT, PASSED),
                        ),
                        links = listOf(
                            BuildRef(LIBRARY, MAIN, "42"),
                            BuildRef(SERVICE, MAIN, "107"),
                        ),
                    ),
                ),
            ),
        ),
    )

    /**
     * One build and a handful of entries, one of which - [AUDIT_TRAIL_TAMPERED_SEQ], the validation
     * run of the failed scan - is rewritten to read PASSED once everything is seeded. Its evidence
     * still says FAILED, and its trail breaks at that entry: what tampering looks like, on an
     * instance which allows it for that purpose only.
     */
    private fun auditTrailTampered() = ProjectSpec(
        name = AUDIT_TRAIL_TAMPERED,
        description = "DELIBERATELY TAMPERED: entry $AUDIT_TRAIL_TAMPERED_SEQ of the trail of build " +
                "$AUDIT_TRAIL_TAMPERED_BUILD was rewritten after the fact, through the demonstration tampering " +
                "switch, to turn a failed scan into a passed one. Its verification breaks there - which is the point.",
        labels = listOf(LABEL_TEAM_PLATFORM, LABEL_LANGUAGE_KOTLIN),
        requires = listOf(DemoCapability.AUDIT_TRAIL, DemoCapability.TRAIL_TAMPERING),
        branches = listOf(
            BranchSpec(
                name = MAIN,
                description = "Main development branch.",
                validationStamps = listOf(securityScan, unitTests),
                builds = listOf(
                    // Its trail: build.created, build.updated (backdated), property.set (release),
                    // then the scan at 4 - the entry rewritten - its evidence and the unit tests
                    BuildSpec(
                        name = AUDIT_TRAIL_TAMPERED_BUILD,
                        release = "1.0.0",
                        description = "A build whose failed scan was rewritten as passed in its trail.",
                        creation = DaysAgo(1),
                        validations = listOf(
                            ValidationSpec(
                                SECURITY_SCAN,
                                FAILED,
                                description = "1 HIGH vulnerability.",
                                evidence = listOf(
                                    EvidenceSpec(
                                        fileName = "scan-report.pdf",
                                        mediaType = "application/pdf",
                                        resource = "tampered-scan-report.pdf",
                                        sourceTool = "trivy",
                                        sourceVersion = "0.56.2",
                                    ),
                                ),
                            ),
                            ValidationSpec(UNIT_TESTS, PASSED),
                        ),
                        tampering = TamperingSpec(
                            seq = AUDIT_TRAIL_TAMPERED_SEQ,
                            type = "validation.run",
                            payload = mapOf("status" to "PASSED"),
                        ),
                    ),
                ),
            ),
        ),
    )

    /**
     * Staging, then production, where the change approval is overridden: the change was approved
     * outside Yontrack, and the override says where. Both at the reset - their slots are the
     * project's own, so no dated history is in their way.
     */
    private fun auditTrailDeployments() = listOf(
        DeploymentSpec(STAGING, BuildRef(AUDIT_TRAIL, MAIN, AUDIT_TRAIL_RELEASE)),
        DeploymentSpec(
            PRODUCTION,
            BuildRef(AUDIT_TRAIL, MAIN, AUDIT_TRAIL_RELEASE),
            overrides = listOf(
                RuleOverrideSpec(
                    rule = CHANGE_APPROVAL,
                    message = "Approved by the change advisory board in CHG-2041, outside Yontrack.",
                ),
            ),
        ),
    )

    private fun changelogProject(changelog: List<ChangelogEntry>) = ProjectSpec(
        name = CHANGELOG,
        description = "Yontrack itself, seeded from the changelog since the last release.",
        labels = listOf(LABEL_TEAM_PLATFORM, LABEL_LANGUAGE_KOTLIN),
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
                    // deployed, and the map is where that becomes visible - as is, since #1737, the
                    // mobile deployment screen, which draws all three triggers for the same reason.
                    // Workflows that have RUN are shown on the promotion side, by [canaryPass] and
                    // [canaryFail], and reach a phone through the build screen's promotion rows.
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
                // The audit trail's release goes through staging before production (#1970)
                SlotSpec(
                    project = AUDIT_TRAIL,
                    description = "Audit trail demo on staging.",
                    admissionRules = listOf(
                        SlotAdmissionRuleSpec(
                            name = "silver",
                            ruleId = SlotAdmissionRules.PROMOTION,
                            config = mapOf("promotion" to SILVER),
                        ),
                    ),
                ),
                // The upstream half of the `canary` story - see the production slot below for
                // why the qualifier is in the demo at all. It holds 107, the head of `main`,
                // which is newer than anything the canary production slot holds (nothing), and
                // that is what makes production [canary] read "behind".
                SlotSpec(
                    project = SERVICE,
                    qualifier = CANARY_QUALIFIER,
                    description = "Sample application on the staging canary.",
                    admissionRules = listOf(
                        SlotAdmissionRuleSpec(
                            name = "silver",
                            ruleId = SlotAdmissionRules.PROMOTION,
                            config = mapOf("promotion" to SILVER),
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
                // The production slot of the delivery scorecard's project, on the default qualifier,
                // which is what an estate read up to production reads. GOLD only, like [SERVICE]'s, and
                // NO workflow: a workflow fires at the moment of the reset, and a slot whose history is
                // backdated would then refuse the dated deployments following it.
                SlotSpec(
                    project = VISITS,
                    description = "Visit scheduling in production.",
                    admissionRules = listOf(
                        SlotAdmissionRuleSpec(
                            name = "gold",
                            ruleId = SlotAdmissionRules.PROMOTION,
                            config = mapOf("promotion" to GOLD),
                        ),
                    ),
                ),
                // GOLD, out of staging, and a change approval nobody gives in Yontrack: the deployment
                // overrides it, and the trail of the build records the override with its message
                SlotSpec(
                    project = AUDIT_TRAIL,
                    description = "Audit trail demo in production.",
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
                        SlotAdmissionRuleSpec(
                            name = CHANGE_APPROVAL,
                            ruleId = SlotAdmissionRules.MANUAL,
                            config = mapOf("message" to "Approve the change in production."),
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
                // The one slot of the demo which has NEVER been deployed, and the reason the
                // matrix has anything to nest. It earns its place three times over (#1791):
                //
                // * [SERVICE] now has two qualifiers, so the matrix draws a project row with a
                //   `canary` row under it - the only nesting in the dataset;
                // * nothing has ever reached it, so a cell reads "Never deployed", which no
                //   other slot shows;
                // * the staging canary slot beside it holds 107 while this one holds nothing,
                //   so it reads **behind** - which the demo could not show at all before. The
                //   default qualifier cannot: staging holds 89 and production 104, so
                //   production is *ahead* of the slot upstream of it, which is what a pair of
                //   environments mid-release actually looks like and is worth keeping.
                //
                // Its rules are the production ones minus the branch pattern, so it is a
                // plausible canary and not a slot with nothing configured: a build has to be
                // GOLD and to be what the staging canary is holding.
                SlotSpec(
                    project = SERVICE,
                    qualifier = CANARY_QUALIFIER,
                    description = "Sample application on the production canary. Nothing has gone out to it yet.",
                    admissionRules = listOf(
                        SlotAdmissionRuleSpec(
                            name = "gold",
                            ruleId = SlotAdmissionRules.PROMOTION,
                            config = mapOf("promotion" to GOLD),
                        ),
                        SlotAdmissionRuleSpec(
                            name = "stagingCanary",
                            ruleId = SlotAdmissionRules.ENVIRONMENT,
                            config = mapOf("environmentName" to STAGING, "qualifier" to CANARY_QUALIFIER),
                        ),
                        // The one rule of the demo which asks a PERSON for something, and the
                        // reason the deployment page's "What's blocking" has a row with an
                        // Answer button on it (#1792). Every other rule of the dataset either
                        // passes or is broken beyond repair; a manual approval is the third
                        // kind - blocking, legitimate, and something the reader can clear
                        // from the screen they are already on.
                        SlotAdmissionRuleSpec(
                            name = "approval",
                            ruleId = SlotAdmissionRules.MANUAL,
                            config = mapOf(
                                "message" to "Confirm the canary window is open before rolling out.",
                            ),
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
        // Last, and on a slot of its own: the [CANARY_QUALIFIER] qualifier has its own history, its own
        // graph and its own "only the last pipeline can be deployed", so nothing above is
        // disturbed by it. 107 carries SILVER, which is all the staging canary asks for, and
        // being DONE is what makes the slot *hold* 1.4.6 - the thing the production canary is
        // then behind.
        DeploymentSpec(STAGING, BuildRef(SERVICE, MAIN, "107"), qualifier = CANARY_QUALIFIER),
        // The demo's one BLOCKED candidate, and the only deployment that stops at
        // [DeploymentStop.CANDIDATE]. Without it the dataset has nothing for "What's blocking" to
        // show but green ticks - which is the one thing the deployment page exists for (#1792).
        //
        // 1.4.3 rather than the head of `main`, and on this slot rather than another, for three
        // reasons that all have to hold at once:
        //
        // * it is GOLD, so the production canary's promotion rule ADMITS it - a candidate refused
        //   by every rule would say nothing about a rule that is merely waiting;
        // * the staging canary holds 107 and this one is 104, so the `stagingCanary` rule refuses
        //   it and the list has a blocking check with an Override beside the approval's Answer;
        // * 104 < 107 keeps the slot reading **behind** the one upstream of it, which `Slot.behind`
        //   computes from the in-flight build as well as the deployed one. A candidate of 107 here
        //   would silently take that reading away from the matrix (#1791).
        //
        // It leaves `lastDeployedPipeline` empty, so the slot still reads "Never deployed" too.
        DeploymentSpec(
            PRODUCTION,
            BuildRef(SERVICE, MAIN, "104"),
            stopAt = DeploymentStop.CANDIDATE,
            qualifier = CANARY_QUALIFIER,
        ),
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
            // Every branch of the tables showcase: more rows than the widget has room for, so it
            // scrolls with its header in place (#1932).
            DemoWidget(
                uuid = E2E_WIDGET_UUID,
                key = "home/BranchStatuses",
                config = mapOf(
                    "title" to "End-to-end suites",
                    "promotionConfigs" to listOf(mapOf("promotionLevel" to BRONZE)),
                    "validationConfigs" to e2eFeatureSuites.map { mapOf("validationStamp" to it) },
                    "branches" to e2e().branches.map { mapOf("project" to E2E, "branch" to it.name) },
                ).asJson(),
                layout = DemoWidgetLayout(x = 0, y = 70, w = 12, h = 30),
            ),
            // The scorecard of the project in both estates (#1938), with no set: it opens on its
            // default one, the first estate by name - "Demo production", before "Demo products".
            DemoWidget(
                uuid = "1c1f9c3e-8bfa-4a1f-8a0b-4e2f0b0d1a16",
                key = "extension/scorecard/ProjectScorecard",
                config = mapOf("project" to VISITS).asJson(),
                layout = DemoWidgetLayout(x = 0, y = 100, w = 4, h = 24),
            ),
            // The security findings: the project with its branches, and the release branch on its
            // own, where the HIGH fixed on main is still open.
            DemoWidget(
                uuid = FINDINGS_PROJECT_WIDGET_UUID,
                key = "extension/findings/ProjectFindings",
                config = mapOf(
                    "project" to SECURITY,
                    "showBranches" to true,
                ).asJson(),
                layout = DemoWidgetLayout(x = 0, y = 124, w = 6, h = 20),
            ),
            DemoWidget(
                uuid = FINDINGS_BRANCH_WIDGET_UUID,
                key = "extension/findings/BranchFindings",
                config = mapOf(
                    "project" to SECURITY,
                    "branch" to SECURITY_RELEASE,
                ).asJson(),
                layout = DemoWidgetLayout(x = 6, y = 124, w = 6, h = 20),
            ),
        ),
    )
}
