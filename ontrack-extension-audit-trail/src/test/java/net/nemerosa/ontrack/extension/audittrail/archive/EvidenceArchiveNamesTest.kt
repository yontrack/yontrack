package net.nemerosa.ontrack.extension.audittrail.archive

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Paths of the files of an evidence archive: whatever the names of the stamps and the evidence, no
 * file escapes its folder once unzipped.
 */
class EvidenceArchiveNamesTest {

    @Test
    fun `Path of an evidence, under its stamp and its run`() {
        assertEquals("scan/2/42-trivy.json", EvidenceArchiveNames.path("scan", 2, 42, "trivy.json"))
    }

    @Test
    fun `Plain names are kept, accents and spaces included`() {
        assertEquals("rapport été.pdf", EvidenceArchiveNames.segment("rapport été.pdf"))
        assertEquals("build-1.2_x", EvidenceArchiveNames.segment("build-1.2_x"))
    }

    @Test
    fun `Path separators are replaced`() {
        assertEquals("_.._.._etc_passwd", EvidenceArchiveNames.segment("/../../etc/passwd"))
        assertEquals("_.._Windows_evil.exe", EvidenceArchiveNames.segment("\\..\\Windows\\evil.exe"))
    }

    @Test
    fun `Leading dots are replaced, so that no segment is a parent nor a current directory`() {
        assertEquals("__", EvidenceArchiveNames.segment(".."))
        assertEquals("_", EvidenceArchiveNames.segment("."))
        assertEquals("_hidden", EvidenceArchiveNames.segment(".hidden"))
        assertEquals("___evil", EvidenceArchiveNames.segment("../evil"))
    }

    @Test
    fun `Control characters and bidirectional overrides are replaced`() {
        assertEquals("a_b_c", EvidenceArchiveNames.segment("a\nb\u0000c"))
        assertEquals("report_fdp.exe", EvidenceArchiveNames.segment("report\u202Efdp.exe"))
    }

    @Test
    fun `Characters refused by Windows file systems are replaced`() {
        assertEquals("a_b_c_d_e_f_g", EvidenceArchiveNames.segment("a:b*c?d\"e<f>g"))
        assertEquals("a_b", EvidenceArchiveNames.segment("a|b"))
    }

    @Test
    fun `An empty or blank name is replaced`() {
        assertEquals("_", EvidenceArchiveNames.segment(""))
        assertEquals("_", EvidenceArchiveNames.segment("   "))
    }

    @Test
    fun `A path built from hostile names stays in its folder`() {
        val path = EvidenceArchiveNames.path("../..", 1, 7, "../../../etc/passwd")
        assertEquals("___../1/7-___.._.._etc_passwd", path)
        val segments = path.split('/')
        assertEquals(3, segments.size)
        segments.forEach { segment ->
            assertFalse(segment == "." || segment == "..", "No relative segment: $path")
            assertFalse(segment.isEmpty(), "No empty segment: $path")
        }
        assertFalse(path.startsWith("/"), "Not absolute: $path")
        assertTrue(path.startsWith("___../"), "Under its stamp: $path")
    }
}
