package net.nemerosa.ontrack.build

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class BackendImageTest {

    private val buildScript: File =
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "ontrack-ui/build.gradle.kts") }
            .firstOrNull { it.isFile }
            ?: error("ontrack-ui/build.gradle.kts not found above ${File("").absolutePath}")

    /**
     * Mitigation of CVE-2026-114938 (#2158), to be kept until the base image is on JDK 25.0.5.1 or
     * later.
     */
    @Test
    fun `the backend image starts with the Base64 intrinsics disabled`() {
        val flags = buildScript.readLines()
            .map { it.trim() }
            .filterNot { it.startsWith("//") }
            .firstOrNull { it.startsWith("jvmFlags") }
        assertTrue(
            flags != null && flags.contains("\"-XX:-UseBASE64Intrinsics\""),
            "the Jib container of ${buildScript.name} does not disable the Base64 intrinsics: $flags",
        )
    }
}
