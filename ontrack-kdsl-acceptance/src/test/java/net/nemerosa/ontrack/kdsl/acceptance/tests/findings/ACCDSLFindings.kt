package net.nemerosa.ontrack.kdsl.acceptance.tests.findings

import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.kdsl.acceptance.tests.AbstractACCDSLTestSupport
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.resourceAsText
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.uid
import net.nemerosa.ontrack.kdsl.connector.graphql.GraphQLClientException
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.FindingExposureState
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.FindingKind
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.FindingSeverity
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.FindingState
import net.nemerosa.ontrack.kdsl.spec.Branch
import net.nemerosa.ontrack.kdsl.spec.ValidationRun
import net.nemerosa.ontrack.kdsl.spec.extension.findings.FindingsReportFormat
import net.nemerosa.ontrack.kdsl.spec.extension.findings.createFindingsValidationStamp
import net.nemerosa.ontrack.kdsl.spec.extension.findings.findings
import net.nemerosa.ontrack.kdsl.spec.extension.findings.validateWithFindings
import net.nemerosa.ontrack.kdsl.spec.extension.license.devLicense
import net.nemerosa.ontrack.kdsl.spec.settings.security
import net.nemerosa.ontrack.kdsl.spec.settings.settings
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Security findings, posted and read through the KDSL: the three formats, the licence of the
 * native ones, the resolution across branches, the filters and the view of the findings.
 */
class ACCDSLFindings : AbstractACCDSLTestSupport() {

    @Test
    fun `A report in the neutral format is ingested and its findings are read back`() {
        project {
            branch {
                val vs = createFindingsValidationStamp(name = uid("vs-"))
                val run = build {
                    validateWithFindings(
                        validation = vs.name,
                        format = FindingsReportFormat.FINDINGS,
                        report = neutralReport(
                            scanner = "zap",
                            kind = "DAST",
                            entry("10038", "MEDIUM", title = "Content Security Policy (CSP) Header Not Set"),
                            entry("10202", "HIGH", title = "Absence of Anti-CSRF Tokens"),
                            entry(
                                "10096", "LOW", title = "Timestamp Disclosure",
                                acceptance = mapOf(
                                    "statement" to "Build timestamps are public",
                                    "source" to "security/dast/suppressions.yaml",
                                ),
                            ),
                        ),
                    )
                }
                // A HIGH is over the warning threshold
                assertEquals("WARNING", run.lastStatus.id)
                assertEquals(
                    mapOf(
                        "levels" to mapOf("CRITICAL" to 0, "HIGH" to 1, "MEDIUM" to 1, "LOW" to 0),
                        "unknown" to 0,
                        "accepted" to 1,
                    ).asJson(),
                    run.data?.data
                )

                val page = project.findings()
                assertEquals(3, page.totalSize)
                // The most severe first
                assertEquals(listOf("10202", "10038", "10096"), page.items.map { it.externalId })
                assertTrue(page.items.all { it.scanner == "zap" && it.kind == FindingKind.DAST && it.location == "" })
                val csp = page.items.single { it.externalId == "10038" }
                assertEquals("Content Security Policy (CSP) Header Not Set", csp.title)
                assertEquals(FindingSeverity.MEDIUM, csp.maxSeverity)
                assertEquals(FindingState.OPEN, csp.state)
                assertEquals(FindingExposureState.EXPOSED, csp.exposureOn(name)?.state)
                assertEquals(vs.name, csp.exposureOn(name)?.validationStamp)
                val timestamp = page.items.single { it.externalId == "10096" }
                assertEquals(FindingState.ACCEPTED, timestamp.state)
                assertEquals(FindingExposureState.ACCEPTED, timestamp.exposureOn(name)?.state)
                assertNotNull(timestamp.acceptance) {
                    assertEquals("Build timestamps are public", it.statement)
                    assertEquals("security/dast/suppressions.yaml", it.source)
                    assertTrue(it.effective)
                }

                // The findings of the run
                assertEquals(
                    listOf("10202", "10038", "10096"),
                    run.findings.map { it.finding.externalId }
                )
            }
        }
    }

    @Test
    fun `A SARIF report is ingested and its findings are read back`() {
        project {
            branch {
                val vs = createFindingsValidationStamp(name = uid("vs-"))
                val run = build {
                    validateWithFindings(
                        validation = vs.name,
                        format = FindingsReportFormat.SARIF,
                        kind = FindingKind.CODE,
                        report = sarifReport(),
                    )
                }
                assertEquals("WARNING", run.lastStatus.id)

                val findings = project.findings().items
                // Two results of one rule at one location are one finding
                assertEquals(4, findings.size)
                assertTrue(findings.all { it.scanner == "codeql" && it.kind == FindingKind.CODE })
                val sqlInjection = findings.single { it.externalId == "java/sql-injection" }
                assertEquals("src/main/java/com/example/OrderDao.java", sqlInjection.location)
                assertEquals("Query built from user-controlled sources", sqlInjection.title)
                assertEquals(
                    "https://codeql.github.com/codeql-query-help/java/java-sql-injection/",
                    sqlInjection.url
                )
                assertEquals(FindingSeverity.HIGH, sqlInjection.maxSeverity)
                assertEquals(FindingState.OPEN, sqlInjection.state)
                // The suppressed result is accepted
                assertEquals(FindingState.ACCEPTED, findings.single { it.externalId == "java/insecure-cookie" }.state)

                // The severity, as the scanner gave it, is on the observation
                assertEquals(
                    "security-severity=8.8",
                    run.findings.single { it.finding.externalId == "java/sql-injection" }.rawSeverity
                )
            }
        }
    }

    @Test
    fun `A Trivy JSON report is ingested and its findings are read back`() {
        project {
            branch {
                val vs = createFindingsValidationStamp(name = uid("vs-"))
                val run = build {
                    validateWithFindings(
                        validation = vs.name,
                        format = FindingsReportFormat.TRIVY,
                        kind = FindingKind.IMAGE,
                        report = trivyReport(),
                    )
                }
                // A CRITICAL is over the failure threshold
                assertEquals("FAILED", run.lastStatus.id)

                val page = project.findings()
                assertEquals(9, page.totalSize)
                assertTrue(page.items.all { it.scanner == "trivy" && it.kind == FindingKind.IMAGE })
                // The same CVE in two packages is two findings
                assertEquals(
                    setOf("pkg:deb/debian/libssl3", "pkg:deb/debian/openssl"),
                    page.items.filter { it.externalId == "CVE-2023-5363" }.map { it.location }.toSet()
                )
                val log4j = page.items.single { it.externalId == "CVE-2021-44228" }
                assertEquals("pkg:maven/org.apache.logging.log4j/log4j-core", log4j.location)
                assertEquals(FindingSeverity.CRITICAL, log4j.maxSeverity)
                // The most severe first
                assertEquals(FindingSeverity.CRITICAL, page.items.first().maxSeverity)

                // The versions are on the observation
                val observation = run.findings.single { it.finding.externalId == "CVE-2021-44228" }
                assertEquals("CRITICAL (ghsa)", observation.rawSeverity)
                assertEquals("2.14.1", observation.installedVersion)
                assertEquals("2.15.0, 2.3.1, 2.12.2", observation.fixedVersion)

                // The suppressed finding is accepted
                val zlib = page.items.single { it.externalId == "CVE-2023-45853" }
                assertEquals(FindingState.ACCEPTED, zlib.state)
                assertEquals(".trivyignore.yaml", zlib.acceptance?.source)
            }
        }
    }

    @Test
    fun `Without the licence, SARIF and Trivy JSON reports are rejected naming the feature, and no run is created`() {
        project {
            branch {
                val vs = createFindingsValidationStamp(name = uid("vs-"))
                build {
                    ontrack.devLicense.withoutFeature(FEATURE_NATIVE_FORMATS) {
                        listOf(
                            Triple(FindingsReportFormat.SARIF, FindingKind.CODE, sarifReport()),
                            Triple(FindingsReportFormat.TRIVY, FindingKind.IMAGE, trivyReport()),
                        ).forEach { (format, kind, report) ->
                            val ex = assertFailsWith<GraphQLClientException> {
                                validateWithFindings(
                                    validation = vs.name,
                                    format = format,
                                    kind = kind,
                                    report = report,
                                )
                            }
                            val message = ex.message ?: ""
                            assertTrue(
                                "Findings report format `${format.id}` needs the licensed feature \"Native scanner formats\"" in message &&
                                        FEATURE_NATIVE_FORMATS in message,
                                "The feature is named in the error: $message"
                            )
                        }
                        assertTrue(getValidationRuns(vs.name).isEmpty(), "No run is created")
                        assertEquals(0, project.findings().totalSize, "No finding is created")

                        // The neutral format needs no licence
                        val run = validateWithFindings(
                            validation = vs.name,
                            format = FindingsReportFormat.FINDINGS,
                            report = neutralReport(
                                scanner = "codeql",
                                kind = "CODE",
                                entry("java/sql-injection", "HIGH", location = "src/main/java/com/example/OrderDao.java"),
                            ),
                        )
                        assertEquals("WARNING", run.lastStatus.id)
                        assertEquals(1, project.findings().totalSize)
                    }
                }
            }
        }
    }

    @Test
    fun `A finding is resolved on each branch, and in the project once resolved on all of them`() {
        project {
            val main = createBranch(uid("main-"), "")
            val release = createBranch(uid("release-"), "")
            listOf(main, release).forEach {
                it.createFindingsValidationStamp(name = STAMP)
                it.scan(entry("CVE-2021-44228", "CRITICAL"), entry("CVE-2023-5363", "HIGH"))
            }
            assertEquals(2, findings(state = FindingState.OPEN).totalSize)

            // Fixed on main only
            main.scan(entry("CVE-2023-5363", "HIGH"))
            val log4j = findings().items.single { it.externalId == "CVE-2021-44228" }
            assertEquals(FindingExposureState.RESOLVED, log4j.exposureOn(main.name)?.state)
            assertNotNull(log4j.exposureOn(main.name)?.resolvedAt)
            assertEquals(FindingExposureState.EXPOSED, log4j.exposureOn(release.name)?.state)
            assertNull(log4j.exposureOn(release.name)?.resolvedAt)
            // Still exposed on release, so still open in the project
            assertEquals(FindingState.OPEN, log4j.state)
            assertNull(log4j.resolvedAt)
            // The state on a branch
            assertEquals(
                listOf("CVE-2021-44228"),
                findings(branch = main.name, state = FindingState.RESOLVED).items.map { it.externalId }
            )
            assertEquals(
                setOf("CVE-2021-44228", "CVE-2023-5363"),
                findings(branch = release.name, state = FindingState.OPEN).items.map { it.externalId }.toSet()
            )

            // Fixed on release too
            release.scan(entry("CVE-2023-5363", "HIGH"))
            val resolved = findings(state = FindingState.RESOLVED).items.single()
            assertEquals("CVE-2021-44228", resolved.externalId)
            assertEquals(FindingExposureState.RESOLVED, resolved.exposureOn(release.name)?.state)
            assertNotNull(resolved.resolvedAt)
            assertEquals(
                listOf("CVE-2023-5363"),
                findings(state = FindingState.OPEN).items.map { it.externalId }
            )
        }
    }

    @Test
    fun `The findings of a project are filtered by severity, scanner and kind`() {
        project {
            branch {
                val dast = createFindingsValidationStamp(name = uid("dast-"))
                val code = createFindingsValidationStamp(name = uid("code-"))
                build {
                    validateWithFindings(
                        validation = dast.name,
                        format = FindingsReportFormat.FINDINGS,
                        report = neutralReport(
                            scanner = "zap",
                            kind = "DAST",
                            entry("10038", "MEDIUM"),
                            entry("10202", "HIGH"),
                        ),
                    )
                    validateWithFindings(
                        validation = code.name,
                        format = FindingsReportFormat.SARIF,
                        kind = FindingKind.CODE,
                        report = sarifReport(),
                    )
                }
                assertEquals(6, project.findings().totalSize)
                assertEquals(
                    setOf("10038", "10202"),
                    project.findings(scanner = "zap").items.map { it.externalId }.toSet()
                )
                assertEquals(4, project.findings(kind = FindingKind.CODE).totalSize)
                assertEquals(
                    setOf("10202", "java/sql-injection", "java/log-injection"),
                    project.findings(severity = FindingSeverity.HIGH).items.map { it.externalId }.toSet()
                )
                assertEquals(
                    listOf("10202"),
                    project.findings(severity = FindingSeverity.HIGH, kind = FindingKind.DAST).items.map { it.externalId }
                )
                // Pagination
                val page = project.findings(offset = 0, size = 2)
                assertEquals(6, page.totalSize)
                assertEquals(2, page.items.size)
            }
        }
    }

    @Test
    fun `The findings of a project are not shown to a user who can see the project but not its findings`() {
        ontrack.settings.security.with(
            update = { it.withGrantProjectViewToAll(true) },
        ) {
            project {
                branch {
                    createFindingsValidationStamp(name = STAMP)
                    build {
                        val run = validateWithFindings(
                            validation = STAMP,
                            format = FindingsReportFormat.FINDINGS,
                            report = neutralReport(
                                scanner = "trivy",
                                kind = "IMAGE",
                                entry("CVE-2021-44228", "CRITICAL"),
                            ),
                        )
                        // The administrator sees the findings
                        assertEquals(1, project.findings().totalSize)
                        assertEquals(1, run.findings.size)

                        // A user without any role sees every project, but not its findings
                        withUser { _ ->
                            val build = assertNotNull(
                                ontrack.findBuildByName(project.name, branch.name, name),
                                "The build is visible"
                            )
                            val page = build.branch.project.findings()
                            assertEquals(0, page.totalSize)
                            assertTrue(page.items.isEmpty())
                            val visibleRun = assertNotNull(
                                build.getValidationRuns(STAMP).singleOrNull(),
                                "The run is visible"
                            )
                            assertEquals(run.id, visibleRun.id)
                            assertTrue(visibleRun.findings.isEmpty())
                        }
                    }
                }
            }
        }
    }

    /**
     * Posts a neutral report on a new build of this branch, for the [STAMP] validation stamp.
     */
    private fun Branch.scan(vararg entries: Map<String, Any?>): ValidationRun =
        createBuild(uid("bd-")).validateWithFindings(
            validation = STAMP,
            format = FindingsReportFormat.FINDINGS,
            report = neutralReport(scanner = "trivy", kind = "IMAGE", *entries),
        )

    private fun neutralReport(scanner: String, kind: String, vararg entries: Map<String, Any?>): JsonNode =
        mapOf(
            "scanner" to scanner,
            "kind" to kind,
            "findings" to entries.toList(),
        ).asJson()

    private fun entry(
        externalId: String,
        severity: String,
        location: String = "",
        title: String = externalId,
        acceptance: Map<String, String>? = null,
    ): Map<String, Any?> = listOfNotNull(
        "externalId" to externalId,
        "location" to location,
        "severity" to severity,
        "title" to title,
        acceptance?.let { "acceptance" to it },
    ).toMap()

    private fun sarifReport(): JsonNode = resourceAsText("/findings/codeql.sarif").parseAsJson()

    private fun trivyReport(): JsonNode = resourceAsText("/findings/trivy-image.json").parseAsJson()

    companion object {
        /**
         * Licensed feature of the native scanner formats
         */
        private const val FEATURE_NATIVE_FORMATS = "extension.findings.native-formats"

        /**
         * Name of the findings stamp, when a test creates one per branch
         */
        private const val STAMP = "SECURITY"
    }
}

