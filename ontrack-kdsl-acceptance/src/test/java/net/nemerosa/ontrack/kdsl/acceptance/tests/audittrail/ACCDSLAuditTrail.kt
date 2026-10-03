package net.nemerosa.ontrack.kdsl.acceptance.tests.audittrail

import net.nemerosa.ontrack.kdsl.acceptance.tests.AbstractACCDSLTestSupport
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.uid
import net.nemerosa.ontrack.kdsl.spec.extension.audittrail.EvidenceFile
import net.nemerosa.ontrack.kdsl.spec.extension.audittrail.EvidenceRefusedException
import net.nemerosa.ontrack.kdsl.spec.extension.audittrail.Trail
import net.nemerosa.ontrack.kdsl.spec.extension.audittrail.TrailVerification
import net.nemerosa.ontrack.kdsl.spec.extension.audittrail.attachEvidence
import net.nemerosa.ontrack.kdsl.spec.extension.audittrail.evidence
import net.nemerosa.ontrack.kdsl.spec.extension.audittrail.exportTrail
import net.nemerosa.ontrack.kdsl.spec.extension.audittrail.trail
import net.nemerosa.ontrack.kdsl.spec.extension.audittrail.validateWithEvidence
import net.nemerosa.ontrack.kdsl.spec.extension.audittrail.verifyTrail
import net.nemerosa.ontrack.kdsl.spec.extension.license.devLicense
import org.apache.commons.codec.digest.DigestUtils
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Audit trail and evidence, through the KDSL, on the stack's MinIO: the trail of a build and its
 * verification, its JSON export, the upload, download and deletion of evidence, its refusals, and
 * what is left of it all once the licence lapses.
 *
 * A refusal because no storage is configured cannot be reached here — the stack always has its
 * MinIO, and the storage cannot be switched off at run time: `EvidenceKdslTest` in `ontrack-kdsl`
 * covers how the KDSL reports it, and the integration tests of the extension how the server
 * answers it.
 */
class ACCDSLAuditTrail : AbstractACCDSLTestSupport() {

    @Test
    fun `The story of a build is recorded in its trail, which verifies`() {
        project {
            branch {
                val dependency = createBuild(uid("dep-"))
                val vs = validationStamp()
                val pl = promotion()
                build(runTime = 30) {
                    linkTo(dependency)
                    validate(vs.name, status = "PASSED")
                    promote(pl.name)

                    val trail = assertNotNull(trail, "The build has a trail")
                    assertEquals(
                        listOf("build.created", "runinfo.set", "link.added", "validation.run", "promotion.added"),
                        trail.types,
                    )
                    trail.assertChained()
                    assertEquals(
                        trail.entries.map { it.seq },
                        trail.endorsements.map { it.seq },
                        "Every entry is endorsed",
                    )
                    assertEquals(name, trail.entries.first().payload.path("build").path("name").asString())

                    verifyTrail().assertVerified(partial = false)
                }
            }
        }
    }

    @Test
    fun `The JSON export of a trail holds its entries, their endorsements and the public keys`() {
        project {
            branch {
                val vs = validationStamp()
                build {
                    validate(vs.name, status = "PASSED")
                    val trail = assertNotNull(trail)

                    val export = exportTrail()

                    assertEquals(1, export.path("exportVersion").asInt())
                    assertEquals(project.name, export.path("build").path("project").asString())
                    assertEquals(branch.name, export.path("build").path("branch").asString())
                    assertEquals(name, export.path("build").path("name").asString())
                    val entries = export.path("entries").toList()
                    assertEquals(trail.entries.map { it.seq }, entries.map { it.path("seq").asInt() })
                    assertEquals(trail.types, entries.map { it.path("type").asString() })
                    assertEquals(trail.entries.map { it.hash }, entries.map { it.path("hash").asString() })
                    val keyIds: Set<String> = export.path("keys").toList().map { it.path("keyId").asString() }.toSet()
                    assertTrue(keyIds.isNotEmpty(), "The export holds the public keys of the instance")
                    entries.forEach { entry ->
                        val endorsements = entry.path("endorsements").toList()
                        assertEquals(1, endorsements.size, "Entry ${entry.path("seq")} is endorsed once")
                        assertTrue(
                            keyIds.contains(endorsements.first().path("keyId").asString()),
                            "The key of the endorsement is in the export"
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `Evidence is uploaded to a validation run, listed, downloaded and recorded in the trail`() {
        project {
            branch {
                val vs = validationStamp()
                build {
                    val run = validate(vs.name, status = "PASSED")
                    val content = """{"bomFormat":"CycloneDX","specVersion":"1.6"}""".toByteArray()
                    val sha256 = DigestUtils.sha256Hex(content)

                    val evidence = run.attachEvidence(
                        EvidenceFile(
                            fileName = "sbom.json",
                            content = content,
                            mediaType = "application/json",
                            sourceTool = "syft",
                            sourceVersion = "1.14.0",
                            sourceUrl = "https://ci.example.com/jobs/42",
                            externalDigest = sha256,
                        )
                    )

                    assertEquals(run.id.toInt(), evidence.validationRunId)
                    assertEquals("sbom.json", evidence.fileName)
                    assertEquals("application/json", evidence.mediaType)
                    assertEquals(content.size.toLong(), evidence.size)
                    assertEquals(sha256, evidence.sha256)
                    assertEquals(sha256, evidence.externalDigest)
                    assertEquals("syft", evidence.source?.tool)
                    assertEquals("1.14.0", evidence.source?.version)
                    assertEquals("https://ci.example.com/jobs/42", evidence.source?.url)
                    assertNull(evidence.deletedAt)

                    assertEquals(listOf(evidence.id), run.evidence.map { it.id })
                    assertContentEquals(content, evidence.download())

                    val trail = assertNotNull(trail)
                    val last = trail.entries.last()
                    assertEquals("evidence.attached", last.type)
                    assertEquals(evidence.id, last.payload.path("evidence").path("id").asInt())
                    assertEquals(sha256, last.payload.path("evidence").path("sha256").asString())

                    verifyTrail(includeEvidence = true).assertVerified(partial = false, evidenceChecked = true)
                }
            }
        }
    }

    @Test
    fun `A deleted evidence is kept, marked as deleted, and its deletion is recorded in the trail`() {
        project {
            branch {
                val vs = validationStamp()
                build {
                    val run = validate(vs.name, status = "PASSED")
                    val evidence = run.attachEvidence(
                        EvidenceFile(
                            fileName = "summary.txt",
                            content = "Tests: 12 passed, 0 failed - ${uid("")}".toByteArray(),
                            mediaType = "text/plain",
                        )
                    )

                    val deleted = evidence.delete()

                    assertEquals(evidence.id, deleted.id)
                    assertNotNull(deleted.deletedAt, "The evidence is marked as deleted")
                    assertNull(deleted.downloadUrl, "A deleted evidence cannot be downloaded")
                    val listed = run.evidence.single()
                    assertEquals(evidence.id, listed.id)
                    assertNotNull(listed.deletedAt, "The deleted evidence is still listed")

                    val trail = assertNotNull(trail)
                    assertEquals(
                        listOf("evidence.attached", "evidence.deleted"),
                        trail.types.takeLast(2),
                    )
                    assertEquals(evidence.id, trail.entries.last().payload.path("evidence").path("id").asInt())

                    verifyTrail(includeEvidence = true).assertVerified(partial = false, evidenceChecked = true)
                }
            }
        }
    }

    @Test
    fun `Validating with evidence creates the run and attaches each evidence to it`() {
        project {
            branch {
                val vs = validationStamp()
                build {
                    val run = validateWithEvidence(
                        validationStamp = vs.name,
                        status = "PASSED",
                        evidence = listOf(
                            EvidenceFile("junit.txt", "Tests: 3 passed".toByteArray(), "text/plain"),
                            EvidenceFile("report.html", "<html><body>OK</body></html>".toByteArray(), "text/html"),
                        ),
                    )

                    assertEquals("PASSED", run.lastStatus.id)
                    assertEquals(listOf("junit.txt", "report.html"), run.evidence.map { it.fileName })
                    assertEquals(
                        listOf("validation.run", "evidence.attached", "evidence.attached"),
                        assertNotNull(trail).types.takeLast(3),
                    )
                }
            }
        }
    }

    @Test
    fun `An evidence whose digest does not match is refused with its code, and nothing is attached`() {
        project {
            branch {
                val vs = validationStamp()
                build {
                    val run = validate(vs.name, status = "PASSED")
                    val sizeBefore = assertNotNull(trail).entries.size

                    val ex = assertFailsWith<EvidenceRefusedException> {
                        run.attachEvidence(
                            EvidenceFile(
                                fileName = "scan.json",
                                content = "{}".toByteArray(),
                                mediaType = "application/json",
                                externalDigest = DigestUtils.sha256Hex("something else"),
                            )
                        )
                    }

                    assertEquals(EvidenceRefusedException.DIGEST_MISMATCH, ex.code)
                    assertEquals(422, ex.status)
                    assertTrue(run.evidence.isEmpty(), "No evidence is attached")
                    assertEquals(sizeBefore, assertNotNull(trail).entries.size, "Nothing is written to the trail")
                }
            }
        }
    }

    @Test
    fun `After the licence lapses, trails stay readable and verifiable, evidence is refused, and new trails are partial`() {
        project {
            branch {
                val vs = validationStamp()
                val recorded = createBuild(uid("recorded-"))
                val run = recorded.validate(vs.name, status = "PASSED")
                val content = "Tests: 5 passed".toByteArray()
                val evidence = run.attachEvidence(EvidenceFile("tests.txt", content, "text/plain"))
                val before = assertNotNull(recorded.trail)

                val unrecorded = ontrack.devLicense.withoutFeature(FEATURE_AUDIT_TRAIL) {
                    // An existing trail is still read, verified, exported and its evidence downloaded
                    val trail = assertNotNull(recorded.trail, "The trail is still readable")
                    assertEquals(before.entries.map { it.hash }, trail.entries.map { it.hash })
                    recorded.verifyTrail(includeEvidence = true).assertVerified(partial = false, evidenceChecked = true)
                    assertEquals(before.entries.size, recorded.exportTrail().path("entries").size())
                    assertContentEquals(content, evidence.download())

                    // ... but nothing new is recorded, and no evidence is attached
                    recorded.validate(vs.name, status = "FAILED")
                    assertEquals(before.entries.size, assertNotNull(recorded.trail).entries.size)
                    val ex = assertFailsWith<EvidenceRefusedException> {
                        run.attachEvidence(EvidenceFile("late.txt", "late".toByteArray(), "text/plain"))
                    }
                    assertEquals(EvidenceRefusedException.NOT_LICENSED, ex.code)
                    assertEquals(403, ex.status)
                    assertEquals(listOf(evidence.id), run.evidence.map { it.id })

                    // A build created now has no trail
                    createBuild(uid("unrecorded-")).also {
                        assertNull(it.trail, "No trail without the licence")
                    }
                }

                // Back with the licence, the trail of the build created without it opens partial
                unrecorded.validate(vs.name, status = "PASSED")
                val trail = assertNotNull(unrecorded.trail)
                assertEquals(listOf("trail.opened", "validation.run"), trail.types)
                unrecorded.verifyTrail().assertVerified(partial = true)
            }
        }
    }

    private val Trail.types: List<String> get() = entries.map { it.type }

    private fun Trail.assertChained() {
        assertEquals((1..entries.size).toList(), entries.map { it.seq }, "The seqs run from 1 without gap")
        assertNull(entries.first().prevHash, "The first entry has no previous hash")
        entries.zipWithNext().forEach { (previous, next) ->
            assertEquals(previous.hash, next.prevHash, "Entry ${next.seq} is chained to the one before it")
        }
    }

    private fun TrailVerification?.assertVerified(partial: Boolean, evidenceChecked: Boolean = false) {
        val verification = assertNotNull(this, "The build has a trail to verify")
        assertTrue(verification.chainIntact, "The chain is intact: ${verification.problems}")
        assertNull(verification.firstBrokenSeq)
        assertTrue(verification.endorsementsValid, "The endorsements are valid: ${verification.problems}")
        assertNull(verification.firstInvalidEndorsementSeq)
        assertNull(verification.unendorsedFromSeq, "Every entry is endorsed")
        assertEquals(partial, verification.partial)
        assertTrue(verification.problems.isEmpty(), "No problem: ${verification.problems}")
        if (evidenceChecked) {
            assertEquals(emptyList(), verification.missingEvidence, "No evidence is missing")
            assertEquals(emptyList(), verification.alteredEvidence, "No evidence is altered")
        } else {
            assertNull(verification.missingEvidence, "The evidence is not checked")
            assertNull(verification.alteredEvidence, "The evidence is not checked")
        }
    }

    companion object {
        /**
         * ID of the licensed feature of the audit trail
         */
        private const val FEATURE_AUDIT_TRAIL = "extension.audit-trail"
    }
}
