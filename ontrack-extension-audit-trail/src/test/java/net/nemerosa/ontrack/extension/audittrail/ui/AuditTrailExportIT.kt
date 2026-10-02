package net.nemerosa.ontrack.extension.audittrail.ui

import net.nemerosa.ontrack.extension.audittrail.AbstractAuditTrailITSupport
import net.nemerosa.ontrack.extension.audittrail.endorsement.InstanceKeyService
import net.nemerosa.ontrack.extension.audittrail.export.TrailExport
import net.nemerosa.ontrack.extension.audittrail.license.AuditTrailLicensedFeatureProvider.Companion.FEATURE_AUDIT_TRAIL
import net.nemerosa.ontrack.extension.audittrail.model.TrailEntryTypes
import net.nemerosa.ontrack.extension.audittrail.service.TrailService
import net.nemerosa.ontrack.extension.audittrail.verification.TrailVerificationProblemType
import net.nemerosa.ontrack.extension.audittrail.verification.TrailVerificationService
import net.nemerosa.ontrack.extension.audittrail.verification.TrailVerifier
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.structure.Build
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import tools.jackson.databind.JsonNode
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

/**
 * The JSON export of a trail, on `GET /rest/extension/audit-trail/builds/{buildId}/export`,
 * verified offline from its own content.
 */
class AuditTrailExportIT : AbstractAuditTrailITSupport() {

    @Autowired
    private lateinit var auditTrailExportController: AuditTrailExportController

    @Autowired
    private lateinit var instanceKeyService: InstanceKeyService

    @Autowired
    private lateinit var trailService: TrailService

    @Autowired
    private lateinit var trailVerificationService: TrailVerificationService

    /**
     * A build with build.created, validation.run and promotion.added.
     */
    private fun trailedBuild(code: Build.() -> Unit) {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp()
                    val pl = promotionLevel()
                    build {
                        validate(vs)
                        promote(pl)
                        code()
                    }
                }
            }
        }
    }

    /**
     * Downloads the export of the trail of a build, as a user who can only see it, as the JSON
     * document a client receives.
     */
    private fun Build.download(): JsonNode =
        asUserWithView(this).call { auditTrailExportController.export(id()) }.body!!.asJson()

    @Test
    fun `The export is a JSON file to download, named after the build`() {
        trailedBuild {
            val response = asUserWithView(this).call { auditTrailExportController.export(id()) }
            val disposition = response.headers.getFirst(HttpHeaders.CONTENT_DISPOSITION)!!
            assertTrue(disposition.startsWith("attachment"), "Download: $disposition")
            assertTrue(
                disposition.contains("audit-trail-${project.name}-${branch.name}-${name}.json"),
                "File name: $disposition"
            )
        }
    }

    @Test
    fun `The export holds the build, the public keys, and every entry with its endorsements`() {
        trailedBuild {
            val json = download()
            assertEquals(TrailExport.EXPORT_VERSION, json.path("exportVersion").asInt())
            assertEquals(id(), json.path("build").path("id").asInt())
            assertEquals(project.name, json.path("build").path("project").asString())
            assertEquals(branch.name, json.path("build").path("branch").asString())
            assertEquals(name, json.path("build").path("name").asString())

            val key = instanceKeyService.getPublicKeys().single()
            assertEquals(listOf(key).asJson(), json.path("keys"))

            val entries = json.path("entries").toList()
            assertEquals(
                listOf(TrailEntryTypes.BUILD_CREATED, TrailEntryTypes.VALIDATION_RUN, TrailEntryTypes.PROMOTION_ADDED),
                entries.map { it.path("type").asString() },
            )
            val first = entries.first()
            assertTrue(first.has("prevHash") && first.path("prevHash").isNull, "Explicit null previous hash for seq 1")
            val stored = asAdmin { trailService.getEntries(this) }
            assertEquals(stored.map { it.hash }, entries.map { it.path("hash").asString() })
            entries.forEach { entry ->
                assertEquals(1, entry.path("schemaVersion").asInt())
                val endorsement = entry.path("endorsements").single()
                assertEquals(key.keyId, endorsement.path("keyId").asString())
            }
        }
    }

    @Test
    fun `The export is verified offline from its own content, as the server verifies the trail`() {
        trailedBuild {
            val export: TrailExport = download().parse()
            val verification = TrailVerifier.verify(export)
            assertEquals(true, verification.chainIntact)
            assertEquals(true, verification.endorsementsValid)
            assertEquals(emptyList(), verification.problems)
            assertEquals(asAdmin { trailVerificationService.verify(this) }, verification)
        }
    }

    @Test
    fun `The export of a trail tampered with in the database fails its offline verification`() {
        trailedBuild {
            val entry = asAdmin { trailService.getEntries(this) }.single { it.seq == 2 }
            namedParameterJdbcTemplate.update(
                "UPDATE BUILD_TRAIL_ENTRY SET ACTOR = :actor WHERE ID = :id",
                mapOf("id" to entry.id, "actor" to """{"account":"someone-else","via":"ui"}"""),
            )
            val verification = TrailVerifier.verify(download().parse<TrailExport>())
            assertEquals(false, verification.chainIntact)
            assertEquals(2, verification.firstBrokenSeq)
            assertEquals(
                listOf(2 to TrailVerificationProblemType.HASH),
                verification.problems.map { it.seq to it.type },
            )
        }
    }

    @Test
    fun `The export stays downloadable after the licence lapses`() {
        trailedBuild {
            testLicenseService.withoutFeature(FEATURE_AUDIT_TRAIL) {
                val export: TrailExport = download().parse()
                assertEquals(3, export.entries.size)
                assertEquals(true, TrailVerifier.verify(export).chainIntact)
            }
        }
    }

    @Test
    fun `No export for a build with no trail while the licence is off`() {
        asAdmin {
            project {
                branch {
                    untrailedBuild {
                        withoutTrail {
                            assertThrows<AuditTrailNotFoundException> { download() }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `No export for a user who cannot see the build`() {
        trailedBuild {
            withNoGrantViewToAll {
                asUser().call {
                    assertFails { auditTrailExportController.export(id()) }
                }
            }
        }
    }
}
