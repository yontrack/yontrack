package net.nemerosa.ontrack.extension.audittrail.export

import net.nemerosa.ontrack.extension.audittrail.verification.TrailVerificationProblemType
import net.nemerosa.ontrack.extension.audittrail.verification.TrailVerifier
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.json.parseAsJson
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Offline verification of the shared exports of `audit-trail/test-vectors/exports/`, which the CLI
 * verifies too.
 */
class TrailExportTest {

    private fun text(name: String): String =
        TrailExportTest::class.java.getResource("/audit-trail/test-vectors/exports/$name")!!.readText(Charsets.UTF_8)

    private fun export(name: String): TrailExport = text(name).parseAsJson().parse()

    @Test
    fun `An untouched export is intact and validly endorsed`() {
        val verification = TrailVerifier.verify(export("01-build-created.json"))
        assertEquals(true, verification.chainIntact)
        assertEquals(true, verification.endorsementsValid)
        assertEquals(false, verification.partial)
        assertEquals(null, verification.unendorsedFromSeq)
        assertEquals(emptyList(), verification.problems)
    }

    @Test
    fun `An export whose payload was edited is broken at its entry`() {
        val verification = TrailVerifier.verify(export("02-tampered-payload.json"))
        assertEquals(false, verification.chainIntact)
        assertEquals(2, verification.firstBrokenSeq)
        assertEquals(
            listOf(2 to TrailVerificationProblemType.HASH),
            verification.problems.map { it.seq to it.type },
        )
    }

    @Test
    fun `An export is bound to the build it names`() {
        val export = export("01-build-created.json")
        val verification = TrailVerifier.verify(export.copy(build = export.build.copy(id = 1043)))
        assertEquals(1, verification.firstBrokenSeq)
        assertEquals(TrailVerificationProblemType.BUILD, verification.problems.single().type)
    }

    @Test
    fun `An export is written as it is read`() {
        assertEquals(text("01-build-created.json").parseAsJson(), export("01-build-created.json").asJson())
    }
}
