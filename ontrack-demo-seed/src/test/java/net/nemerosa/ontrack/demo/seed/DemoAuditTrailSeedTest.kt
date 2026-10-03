package net.nemerosa.ontrack.demo.seed

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The two projects of the audit trail (#1970): `audit-trail-demo`, a release build with its whole
 * story, and `audit-trail-tampered`, whose trail breaks at one entry - and what the seed leaves out
 * on an instance which cannot offer what they need.
 *
 * The trails are the in-memory target's, written in the order the server writes them, so that what
 * the dataset says about an entry - the one tampered with above all - is checked against the order
 * the seed makes its calls in. The real server is checked by seeding the dev stack.
 */
class DemoAuditTrailSeedTest {

    private val clock = Clock.fixed(Instant.parse("2026-09-01T10:15:30Z"), ZoneOffset.UTC)

    private fun seeded(
        unavailable: Map<DemoCapability, String> = emptyMap(),
        log: (String) -> Unit = {},
    ): InMemoryDemoTarget =
        InMemoryDemoTarget(unavailableCapabilities = unavailable).also {
            DemoSeed(it, clock, log = log).run(DemoContent.dataset(emptyList()))
        }

    private fun InMemoryDemoTarget.project(name: String): InMemoryDemoTarget.InMemoryProject? =
        projects().filterIsInstance<InMemoryDemoTarget.InMemoryProject>().singleOrNull { it.name == name }

    private fun InMemoryDemoTarget.build(project: String, build: String): InMemoryDemoTarget.InMemoryBuild =
        project(project)!!.branches.single { it.name == DemoContent.MAIN }.builds.single { it.name == build }

    private fun InMemoryDemoTarget.release() = build(DemoContent.AUDIT_TRAIL, DemoContent.AUDIT_TRAIL_RELEASE)

    @Test
    fun `the release build is created and validated through the token of its pipeline, which is revoked at the end`() {
        val target = seeded()
        val trail = target.release().trail

        val pipeline = "token:${DemoContent.CI_TOKEN}"
        listOf("build.created", "property.set", "validation.run", "evidence.attached", "promotion.added", "link.added")
            .forEach { type ->
                assertTrue(
                    trail.filter { it.type == type }.all { it.actor == pipeline },
                    "Every $type entry is the pipeline's: $trail",
                )
            }
        assertTrue(target.tokens.isEmpty(), "The token of the pipeline is revoked")
    }

    @Test
    fun `a person passes the failed unit tests with a comment, and deletes the log attached by mistake`() {
        val trail = seeded().release().trail

        val status = trail.single { it.type == "validation.status" }
        assertEquals(InMemoryDemoTarget.SEED_ACTOR, status.actor)
        assertEquals(DemoContent.UNIT_TESTS, status.payload["validationStamp"])
        assertEquals(ValidationStatus.FIXED, status.payload["status"])
        assertTrue((status.payload["description"] as String).isNotBlank())

        val deleted = trail.single { it.type == "evidence.deleted" }
        assertEquals(InMemoryDemoTarget.SEED_ACTOR, deleted.actor)
        assertEquals("trivy-debug.log", deleted.payload["fileName"])
    }

    @Test
    fun `the release build has six validations, the failed one fixed afterwards`() {
        val release = seeded().release()

        assertEquals(6, release.validations.size)
        val unitTests = release.validations.single { it.stamp == DemoContent.UNIT_TESTS }
        assertEquals(listOf(ValidationStatus.FIXED), unitTests.statusChanges.map { it.status })
        assertTrue(
            release.validations.all { it.status == ValidationStatus.PASSED || it.status == ValidationStatus.FIXED },
            "Every validation of the release passes in the end",
        )
        assertTrue(
            release.trail.any {
                it.type == "validation.run" && it.payload["validationStamp"] == DemoContent.UNIT_TESTS &&
                        it.payload["status"] == ValidationStatus.FAILED
            },
            "The trail keeps the run as the pipeline posted it, FAILED",
        )
    }

    @Test
    fun `the release build is promoted BRONZE, SILVER then GOLD, after its validations`() {
        val trail = seeded().release().trail

        assertEquals(
            listOf(DemoContent.BRONZE, DemoContent.SILVER, DemoContent.GOLD),
            trail.filter { it.type == "promotion.added" }.map { it.payload["promotionLevel"] },
        )
        val lastValidation = trail.last { it.type == "validation.run" || it.type == "evidence.attached" }.seq
        val firstPromotion = trail.first { it.type == "promotion.added" }.seq
        assertTrue(lastValidation < firstPromotion, "Validated, then promoted: $trail")
    }

    @Test
    fun `the release build carries its release and commit properties, and uses two dependency builds`() {
        val release = seeded().release()

        assertEquals("2.4.0", release.releaseVersion)
        assertTrue(release.commitId != null)
        assertEquals(
            listOf("${DemoContent.LIBRARY}/42", "${DemoContent.SERVICE}/107"),
            release.links.map { "${it.branch.project.name}/${it.name}" },
        )
    }

    @Test
    fun `the release build carries evidence of every kind, and one deleted evidence`() {
        val release = seeded().release()

        val evidence = release.validations.flatMap { it.evidence }
        assertEquals(
            setOf("application/pdf", "application/vnd.cyclonedx+json", "text/html", "text/plain", "image/png"),
            evidence.map { it.spec.mediaType }.toSet(),
        )
        assertEquals(listOf("trivy-debug.log"), evidence.filter { it.deleted }.map { it.spec.fileName })
        assertTrue(
            evidence.single { it.spec.mediaType == "text/html" }.spec.fileName.endsWith(".html"),
            "The ZAP report is an HTML file, which the server only ever serves as a download",
        )
    }

    @Test
    fun `the deleted validation stamp is recorded as a cascade on both builds of the project`() {
        val target = seeded()
        val branch = target.project(DemoContent.AUDIT_TRAIL)!!.branches.single()

        assertEquals(listOf(DemoContent.LEGACY_LINT), branch.deletedValidationStamps)
        assertEquals(2, branch.builds.size)
        branch.builds.forEach { build ->
            val entry = build.trail.single { it.type == "validation.deleted" }
            assertEquals(DemoContent.LEGACY_LINT, entry.payload["validationStamp"])
            assertEquals("cascade/validation-stamp-deleted", entry.payload["reason"])
            assertTrue(build.validations.none { it.stamp == DemoContent.LEGACY_LINT })
        }
    }

    @Test
    fun `the release build is deployed through two slots, overriding the change approval in production`() {
        val target = seeded()
        val slots = target.environments().flatMap { (it as InMemoryDemoTarget.InMemoryEnvironment).slots }
            .filter { it.project.name == DemoContent.AUDIT_TRAIL }

        assertEquals(listOf(DemoContent.STAGING, DemoContent.PRODUCTION), slots.map { it.environment.name })
        slots.forEach { slot ->
            assertEquals(listOf(DemoContent.AUDIT_TRAIL_RELEASE), slot.heldBuilds.map { it.name })
        }
        val production = slots.single { it.environment.name == DemoContent.PRODUCTION }
        assertEquals(
            listOf(DemoContent.CHANGE_APPROVAL),
            production.deployments.single().overrides.map { it.rule },
        )
        val overridden = target.release().trail.single { it.type == "deployment.rule-overridden" }
        assertEquals(DemoContent.CHANGE_APPROVAL, overridden.payload["rule"])
    }

    @Test
    fun `the tampered trail breaks at its fourth entry, the failed scan rewritten as passed`() {
        val build = seeded().build(DemoContent.AUDIT_TRAIL_TAMPERED, DemoContent.AUDIT_TRAIL_TAMPERED_BUILD)

        assertEquals(DemoContent.AUDIT_TRAIL_TAMPERED_SEQ, build.firstBrokenSeq)
        val entry = build.trail[DemoContent.AUDIT_TRAIL_TAMPERED_SEQ - 1]
        assertEquals("validation.run", entry.type)
        assertEquals(DemoContent.SECURITY_SCAN, entry.payload["validationStamp"])
        assertEquals("PASSED", entry.payload["status"])
        // The run itself still says what the pipeline posted, and so does its evidence
        assertEquals(
            ValidationStatus.FAILED,
            build.validations.single { it.stamp == DemoContent.SECURITY_SCAN }.status,
        )
        assertTrue(build.trail.size in 5..7, "About six entries: ${build.trail}")
    }

    @Test
    fun `the tampered project says it is tampered on purpose`() {
        val project = DemoContent.dataset(emptyList()).projects.single { it.name == DemoContent.AUDIT_TRAIL_TAMPERED }

        assertTrue(project.description.contains("DELIBERATELY TAMPERED"), project.description)
    }

    @Test
    fun `the trail of the release build is intact`() {
        assertNull(seeded().release().firstBrokenSeq)
    }

    @Test
    fun `without an evidence storage, the evidence is left out and says so, and the rest is seeded`() {
        val logs = mutableListOf<String>()
        val target = seeded(
            unavailable = mapOf(DemoCapability.EVIDENCE to "its evidence storage is NOT_CONFIGURED"),
            log = logs::add,
        )

        assertTrue(
            logs.any { it.contains("Leaving out the evidence") && it.contains("NOT_CONFIGURED") },
            logs.joinToString("\n"),
        )
        val release = target.release()
        assertTrue(release.validations.all { it.evidence.isEmpty() })
        assertEquals(6, release.validations.size)
        // Seq 4 does not depend on the evidence: the tampered project is still seeded and broken there
        assertEquals(
            DemoContent.AUDIT_TRAIL_TAMPERED_SEQ,
            target.build(DemoContent.AUDIT_TRAIL_TAMPERED, DemoContent.AUDIT_TRAIL_TAMPERED_BUILD).firstBrokenSeq,
        )
    }

    @Test
    fun `without the tampering switch, the tampered project is left out and says so`() {
        val logs = mutableListOf<String>()
        val target = seeded(
            unavailable = mapOf(DemoCapability.TRAIL_TAMPERING to "the demonstration tampering switch is off"),
            log = logs::add,
        )

        assertNull(target.project(DemoContent.AUDIT_TRAIL_TAMPERED))
        assertTrue(target.project(DemoContent.AUDIT_TRAIL) != null)
        assertTrue(
            logs.any {
                it.contains("Leaving out the project ${DemoContent.AUDIT_TRAIL_TAMPERED}") &&
                        it.contains("switch is off")
            },
            logs.joinToString("\n"),
        )
    }

    @Test
    fun `without the licence of the audit trail, both projects are left out with their slots, and the rest is seeded`() {
        val logs = mutableListOf<String>()
        val target = seeded(
            unavailable = mapOf(DemoCapability.AUDIT_TRAIL to "the licence of the instance does not enable it"),
            log = logs::add,
        )

        assertNull(target.project(DemoContent.AUDIT_TRAIL))
        assertNull(target.project(DemoContent.AUDIT_TRAIL_TAMPERED))
        assertTrue(target.project(DemoContent.SERVICE) != null)
        assertTrue(
            target.environments().flatMap { (it as InMemoryDemoTarget.InMemoryEnvironment).slots }
                .none { it.project.name == DemoContent.AUDIT_TRAIL }
        )
        assertTrue(logs.any { it.contains("Leaving out the project ${DemoContent.AUDIT_TRAIL}:") }, logs.joinToString("\n"))
        assertTrue(target.tokens.isEmpty())
    }

    // ---------------------------------------------------------------------------------------------
    // What `validate` catches before the reset
    // ---------------------------------------------------------------------------------------------

    private fun oneProject(
        requires: List<DemoCapability> = emptyList(),
        stamps: List<ValidationStampSpec> = listOf(ValidationStampSpec("BUILD", "")),
        promotionLevels: List<PromotionLevelSpec> = emptyList(),
        build: BuildSpec = BuildSpec(name = "1", description = "", creation = BuildCreation.DaysAgo(1)),
    ) = ProjectSpec(
        name = "one",
        description = "",
        requires = requires,
        branches = listOf(
            BranchSpec(
                name = "main",
                description = "",
                promotionLevels = promotionLevels,
                validationStamps = stamps,
                builds = listOf(build),
            ),
        ),
    )

    private fun assertRefused(dataset: DemoDataset, expected: String) {
        val target = InMemoryDemoTarget()
        val ex = assertFailsWith<IllegalArgumentException> {
            DemoSeed(target, clock, log = {}).run(dataset)
        }
        assertTrue(ex.message!!.contains(expected), ex.message)
    }

    @Test
    fun `tampering in a project which does not require the switch is caught before anything is deleted`() {
        assertRefused(
            DemoDataset(
                projects = listOf(
                    oneProject(
                        build = BuildSpec(
                            name = "1",
                            description = "",
                            creation = BuildCreation.DaysAgo(1),
                            tampering = TamperingSpec(1, "build.created", mapOf("name" to "2")),
                        ),
                    ),
                ),
            ),
            "does not require trail tampering",
        )
    }

    @Test
    fun `an evidence whose file is not in the resources is caught before anything is deleted`() {
        assertRefused(
            DemoDataset(
                projects = listOf(
                    oneProject(
                        build = BuildSpec(
                            name = "1",
                            description = "",
                            creation = BuildCreation.DaysAgo(1),
                            validations = listOf(
                                ValidationSpec(
                                    "BUILD",
                                    ValidationStatus.PASSED,
                                    evidence = listOf(EvidenceSpec("report.pdf", "application/pdf", "missing.pdf")),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            "which is not in the evidence files",
        )
    }

    @Test
    fun `a link to a project which may be left out is caught before anything is deleted`() {
        val optional = oneProject(requires = listOf(DemoCapability.AUDIT_TRAIL))
        assertRefused(
            DemoDataset(
                projects = listOf(
                    optional,
                    ProjectSpec(
                        name = "two",
                        description = "",
                        branches = listOf(
                            BranchSpec(
                                name = "main",
                                description = "",
                                builds = listOf(
                                    BuildSpec(
                                        name = "1",
                                        description = "",
                                        creation = BuildCreation.DaysAgo(1),
                                        links = listOf(BuildRef("one", "main", "1")),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            "which is left out of the reset",
        )
    }

    @Test
    fun `an auto promotion on a stamp the dataset deletes is caught before anything is deleted`() {
        assertRefused(
            DemoDataset(
                projects = listOf(
                    oneProject(
                        stamps = listOf(ValidationStampSpec("BUILD", "", deleted = true)),
                        promotionLevels = listOf(
                            PromotionLevelSpec("BRONZE", "", autoPromotion = AutoPromotionSpec(validationStamps = listOf("BUILD"))),
                        ),
                    ),
                ),
            ),
            "which the dataset deletes",
        )
    }

    private fun deploymentDataset(rules: List<SlotAdmissionRuleSpec>, overrides: List<RuleOverrideSpec>) = DemoDataset(
        projects = listOf(oneProject()),
        environments = listOf(
            EnvironmentSpec(
                name = "production",
                order = 1,
                description = "",
                slots = listOf(SlotSpec(project = "one", description = "", admissionRules = rules)),
            ),
        ),
        deployments = listOf(DeploymentSpec("production", BuildRef("one", "main", "1"), overrides = overrides)),
    )

    private val approval = SlotAdmissionRuleSpec("approval", SlotAdmissionRules.MANUAL, mapOf("message" to "Go?"))

    @Test
    fun `a deployment past an approval nobody gives is caught before anything is deleted`() {
        assertRefused(deploymentDataset(listOf(approval), emptyList()), "waits for the approval approval")
    }

    @Test
    fun `a deployment overriding an approval goes past it`() {
        val target = InMemoryDemoTarget()
        DemoSeed(target, clock, log = {}).run(
            deploymentDataset(listOf(approval), listOf(RuleOverrideSpec("approval", "Approved elsewhere.")))
        )

        val slot = (target.environments().single() as InMemoryDemoTarget.InMemoryEnvironment).slots.single()
        assertEquals(listOf("1"), slot.heldBuilds.map { it.name })
    }

    @Test
    fun `a change of status Yontrack does not allow is caught before anything is deleted`() {
        assertRefused(
            DemoDataset(
                projects = listOf(
                    oneProject(
                        build = BuildSpec(
                            name = "1",
                            description = "",
                            creation = BuildCreation.DaysAgo(1),
                            validations = listOf(
                                ValidationSpec(
                                    "BUILD",
                                    ValidationStatus.FAILED,
                                    statusChanges = listOf(StatusChangeSpec(ValidationStatus.PASSED, "Fine.")),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            "goes from FAILED to PASSED, which Yontrack does not allow",
        )
    }

    @Test
    fun `an override of a rule the slot does not have is caught before anything is deleted`() {
        assertRefused(
            deploymentDataset(emptyList(), listOf(RuleOverrideSpec("approval", "Approved elsewhere."))),
            "which the slot does not have",
        )
    }
}
