package net.nemerosa.ontrack.extension.audittrail.evidence

import net.nemerosa.ontrack.model.structure.Build
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `BuildAuditTrail.evidence` in GraphQL: the evidence of every validation run of a build, against
 * the MinIO of the integration test stack.
 */
class BuildEvidenceIT : AbstractEvidenceITSupport() {

    private val query = """
        query BuildEvidence(${'$'}id: Int!) {
            build(id: ${'$'}id) {
                auditTrail {
                    evidence {
                        id
                        fileName
                        deletedAt
                        downloadUrl
                        validationRun {
                            id
                            runOrder
                            validationStamp {
                                name
                            }
                        }
                    }
                }
            }
        }
    """

    /**
     * The `auditTrail.evidence` field of a build, as a user who can only see it.
     */
    private fun Build.evidence(): JsonNode {
        var evidence: JsonNode? = null
        asUserWithView(this).call {
            run(query, mapOf("id" to id())) { data ->
                evidence = data.path("build").path("auditTrail").path("evidence")
            }
        }
        return evidence!!
    }

    @Test
    fun `Evidence of every validation run of the build, deleted ones included, in the order of their upload`() {
        asAdmin {
            project {
                branch {
                    val scan = validationStamp("scan")
                    val tests = validationStamp("tests")
                    // Another build, whose evidence is not listed
                    build {
                        validate(scan).upload(pdf(), fileName = "other.pdf")
                    }
                    build {
                        val firstScan = validate(scan)
                        val testRun = validate(tests)
                        val secondScan = validate(scan)
                        // Interleaved, so that the order of the upload is not the one of the runs
                        val a = firstScan.upload(pdf(), fileName = "a.pdf")
                        val b = testRun.upload(png(), fileName = "b.png", partType = "image/png")
                        val c = secondScan.upload(pdf(), fileName = "c.pdf")
                        val d = firstScan.upload(pdf(), fileName = "d.pdf")
                        evidenceService.delete(b.id)

                        val evidence = evidence().toList()
                        assertEquals(listOf(a.id, b.id, c.id, d.id), evidence.map { it.path("id").asInt() })
                        assertEquals(listOf("a.pdf", "b.png", "c.pdf", "d.pdf"), evidence.map { it.path("fileName").asString() })

                        // The deleted one is listed, without its download
                        val deleted = evidence[1]
                        assertTrue(deleted.path("deletedAt").asString().isNotBlank())
                        assertTrue(deleted.path("downloadUrl").isNull)
                        assertEquals(
                            "/rest/extension/audit-trail/evidence/${a.id}/download",
                            evidence[0].path("downloadUrl").asString(),
                        )

                        // Each with its validation run
                        assertEquals(
                            listOf(
                                Triple(firstScan.id(), 1, "scan"),
                                Triple(testRun.id(), 1, "tests"),
                                Triple(secondScan.id(), 2, "scan"),
                                Triple(firstScan.id(), 1, "scan"),
                            ),
                            evidence.map {
                                val run = it.path("validationRun")
                                Triple(
                                    run.path("id").asInt(),
                                    run.path("runOrder").asInt(),
                                    run.path("validationStamp").path("name").asString(),
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `No evidence for a build whose validation runs have none`() {
        validationRun {
            assertEquals(0, build.evidence().size())
        }
    }
}
