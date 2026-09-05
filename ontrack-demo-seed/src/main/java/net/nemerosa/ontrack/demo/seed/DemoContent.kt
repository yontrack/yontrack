package net.nemerosa.ontrack.demo.seed

import net.nemerosa.ontrack.demo.seed.BuildCreation.At
import net.nemerosa.ontrack.demo.seed.BuildCreation.DaysAgo
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

    const val STAGING = "staging"
    const val PRODUCTION = "production"

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

    private val buildStamp = ValidationStampSpec(BUILD, "Compilation and packaging.")
    private val unitTests = ValidationStampSpec(UNIT_TESTS, "Unit tests.")
    private val integrationTests = ValidationStampSpec(INTEGRATION_TESTS, "Integration tests against a real database.")
    private val securityScan = ValidationStampSpec(SECURITY_SCAN, "Dependency and container scan.")

    /** The full ladder, for the projects that show the whole delivery pipeline. */
    private val fullPromotions = listOf(bronze, silver, gold)

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
                promotionLevels = listOf(bronze, silver),
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
     * The main demo project: a branch that reads like a real one, with a maintenance
     * branch beside it and a history of promotions to chart.
     */
    private fun service() = ProjectSpec(
        name = SERVICE,
        description = "Sample application - the demo's main project.",
        branches = listOf(
            BranchSpec(
                name = MAIN,
                description = "Main development branch.",
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
                    ),
                    BuildSpec(
                        name = "107",
                        release = "1.4.6",
                        description = "Owner export as CSV, canary rollout.",
                        creation = DaysAgo(0),
                        promotionLevels = listOf(BRONZE, SILVER, CANARY, GOLD),
                        validations = listOf(
                            ValidationSpec(BUILD, PASSED),
                            ValidationSpec(UNIT_TESTS, PASSED),
                            ValidationSpec(INTEGRATION_TESTS, PASSED),
                            ValidationSpec(SECURITY_SCAN, PASSED),
                        ),
                        links = listOf(BuildRef(LIBRARY, MAIN, "42")),
                    ),
                ),
            ),
            BranchSpec(
                name = MAINTENANCE,
                description = "Maintenance of the previous minor version.",
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
                promotionLevels = fullPromotions,
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
                    deployed = BuildRef(SERVICE, MAIN, "105"),
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
                    deployed = BuildRef(SERVICE, MAIN, "104"),
                ),
            ),
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
        ),
    )
}
