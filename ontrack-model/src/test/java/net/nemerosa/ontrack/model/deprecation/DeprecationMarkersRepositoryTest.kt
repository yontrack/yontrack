package net.nemerosa.ontrack.model.deprecation

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Every deprecation of the repository follows ADR 0018: the `Removed in V6` / `Removed in V7`
 * marker, a replacement, an issue number, and, for an external item, its name on the
 * *Migration to V6* page.
 *
 * The items which did not conform when the policy was adopted are listed in
 * [DeprecationMarkers.BASELINE]. The baseline may only shrink: an entry whose item conforms, or is
 * gone, fails this test until it is removed, and no entry is ever added.
 *
 * Run by `./gradlew test`, through the `deprecationMarkersTest` task of this module, which
 * declares the sources of the whole repository as its inputs.
 */
class DeprecationMarkersRepositoryTest {

    private val root: File = File("..").canonicalFile

    @Test
    fun `Every deprecation follows the policy or is in the baseline`() {
        assertTrue(File(root, "settings.gradle.kts").exists(), "Root of the build found at $root")
        val baseline = DeprecationMarkers.parseBaseline(File(root, DeprecationMarkers.BASELINE).readText())
        val report = DeprecationMarkers.check(root, baseline)
        if (!report.isOk) {
            fail(report.message())
        }
    }

}
