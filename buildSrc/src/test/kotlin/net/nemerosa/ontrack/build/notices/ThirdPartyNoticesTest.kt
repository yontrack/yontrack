package net.nemerosa.ontrack.build.notices

import java.io.File
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThirdPartyNoticesTest {

    private val texts = mapOf(
        "MIT" to "The MIT license text",
        "Apache-2.0" to "The Apache license text",
        "Go License" to "The Go license text",
    )

    private fun render(vararg libraries: NoticeLibrary) =
        ThirdPartyNotices.render("Yontrack 6.0.0 - backend image", libraries.toList()) { texts[it] }

    private val lib = NoticeLibrary(
        name = "org.example:lib",
        version = "1.2.3",
        licenses = listOf("Apache-2.0"),
        purl = "pkg:maven/org.example/lib@1.2.3?type=jar",
        files = listOf(
            NoticeFile("lib-1.2.3.jar!/META-INF/LICENSE", "Copyright 2026 Example\nApache stuff"),
            NoticeFile("lib-1.2.3.jar!/META-INF/NOTICE", "This product includes Example."),
        ),
    )

    @Test
    fun `an entry gives the name, version, licenses, package and every packaged file verbatim`() {
        val notices = render(lib)
        assertTrue(notices.startsWith("Yontrack 6.0.0 - backend image\n"), notices)
        assertTrue(notices.contains("org.example:lib 1.2.3\nLicense: Apache-2.0\nPackage: pkg:maven/org.example/lib@1.2.3?type=jar\n"), notices)
        assertTrue(notices.contains("lib-1.2.3.jar!/META-INF/LICENSE\n${"-".repeat(80)}\nCopyright 2026 Example\nApache stuff\n"), notices)
        assertTrue(notices.contains("This product includes Example."), notices)
    }

    @Test
    fun `the libraries are in name order`() {
        val notices = render(lib.copy(name = "org.zzz:last"), lib.copy(name = "com.aaa:first"))
        assertTrue(notices.indexOf("com.aaa:first 1.2.3") < notices.indexOf("org.zzz:last 1.2.3"), notices)
    }

    @Test
    fun `the appendix has the text of the licenses of the libraries packaging no file of their own`() {
        val bare = lib.copy(name = "org.example:bare", licenses = listOf("MIT"), files = emptyList())
        val notices = render(lib, bare)
        val appendix = notices.substringAfter("APPENDIX")
        assertTrue(notices.contains("org.example:bare 1.2.3\nLicense: MIT\n"), notices)
        assertTrue(notices.contains("packages no license file of its own: the text of its license is in the appendix"), notices)
        assertTrue(appendix.contains("MIT\n${"-".repeat(80)}\nThe MIT license text\n"), appendix)
        // The library with its own files needs no text from the appendix
        assertFalse(appendix.contains("The Apache license text"), appendix)
    }

    @Test
    fun `no appendix when every library packages its own files`() {
        assertFalse(render(lib).contains("APPENDIX"))
    }

    @Test
    fun `a missing license text fails, naming the license and the library`() {
        val bare = lib.copy(name = "org.example:bare", licenses = listOf("EPL-2.0"), files = emptyList())
        val error = assertFailsWith<IllegalStateException> { render(bare) }
        assertTrue(error.message!!.contains("EPL-2.0"), error.message)
        assertTrue(error.message!!.contains("org.example:bare 1.2.3"), error.message)
    }

    @Test
    fun `a library with neither a file nor a declared license fails`() {
        val bare = lib.copy(licenses = emptyList(), files = emptyList())
        val error = assertFailsWith<IllegalStateException> { render(bare) }
        assertTrue(error.message!!.contains("org.example:lib 1.2.3"), error.message)
    }

    @Test
    fun `a file identical to one already given refers to it`() {
        val other = lib.copy(
            name = "org.example:other",
            files = listOf(NoticeFile("other-1.2.3.jar!/LICENSE.txt", "Copyright 2026 Example\r\nApache stuff\n")),
        )
        val notices = render(lib, other)
        assertEquals(1, Regex("Apache stuff").findAll(notices).count(), notices)
        assertTrue(notices.contains("other-1.2.3.jar!/LICENSE.txt: same text as lib-1.2.3.jar!/META-INF/LICENSE, above"), notices)
    }

    @Test
    fun `license keys of ids, names and expressions`() {
        assertEquals(listOf("MIT"), ThirdPartyNotices.licenseKeys(listOf("MIT")))
        assertEquals(listOf("Go License"), ThirdPartyNotices.licenseKeys(listOf("Go License")))
        assertEquals(listOf("MPL-2.0", "Apache-2.0"), ThirdPartyNotices.licenseKeys(listOf("(MPL-2.0 OR Apache-2.0)")))
        assertEquals(listOf("MIT", "ISC"), ThirdPartyNotices.licenseKeys(listOf("MIT AND ISC")))
        assertEquals(
            listOf("GPL-2.0-only", "Classpath-exception-2.0"),
            ThirdPartyNotices.licenseKeys(listOf("GPL-2.0-only WITH Classpath-exception-2.0")),
        )
        assertEquals(listOf("EPL-2.0", "LGPL-2.1-only"), ThirdPartyNotices.licenseKeys(listOf("EPL-2.0", "LGPL-2.1-only")))
    }

    @Test
    fun `notice files of a jar`() {
        listOf(
            "LICENSE", "NOTICE.txt", "META-INF/LICENSE", "META-INF/LICENSE.txt", "META-INF/NOTICE.md",
            "META-INF/license/LICENSE.jzlib.txt", "META-INF/licenses/asm.txt", "META-INF/COPYING",
            "META-INF/LICENSE-notice.md",
        ).forEach { assertTrue(ThirdPartyNotices.isJarNoticeFile(it), it) }
        listOf(
            "META-INF/", "META-INF/license/", "META-INF/MANIFEST.MF", "META-INF/maven/org.example/lib/pom.xml",
            "org/example/License.class", "org/example/LICENSE", "META-INF/licensed.properties",
        ).forEach { assertFalse(ThirdPartyNotices.isJarNoticeFile(it), it) }
    }

    @Test
    fun `reading the notice files of a jar`() {
        val jar = Files.createTempFile("lib-1.2.3", ".jar").toFile()
        try {
            JarOutputStream(jar.outputStream()).use { out ->
                mapOf(
                    "META-INF/NOTICE" to "Notice\r\n",
                    "META-INF/LICENSE" to "License",
                    "org/example/License.class" to "binary",
                ).forEach { (path, content) ->
                    out.putNextEntry(JarEntry(path))
                    out.write(content.toByteArray())
                    out.closeEntry()
                }
            }
            assertEquals(
                listOf(
                    NoticeFile("${jar.name}!/META-INF/LICENSE", "License"),
                    NoticeFile("${jar.name}!/META-INF/NOTICE", "Notice\n"),
                ),
                ThirdPartyNotices.readJar(jar),
            )
        } finally {
            jar.delete()
        }
    }

    @Test
    fun `licenses declared by a CycloneDX SBOM`() {
        val sbom = """
            {"components": [
              {"group": "org.example", "name": "lib", "version": "1.2.3", "purl": "pkg:maven/org.example/lib@1.2.3",
               "licenses": [{"license": {"id": "EPL-2.0"}}, {"license": {"name": "Go License"}}]},
              {"name": "solo", "version": "1", "licenses": [{"expression": "MIT OR ISC"}]},
              {"group": "org.example", "name": "none", "version": "2"}
            ]}
        """.trimIndent()
        val declared = ThirdPartyNotices.sbomLicenses(sbom)
        assertEquals(
            DeclaredLicenses(listOf("EPL-2.0", "Go License"), "pkg:maven/org.example/lib@1.2.3"),
            declared["org.example:lib:1.2.3"],
        )
        assertEquals(DeclaredLicenses(listOf("MIT OR ISC"), null), declared[":solo:1"])
        assertEquals(DeclaredLicenses(emptyList(), null), declared["org.example:none:2"])
    }

    @Test
    fun `the license texts kept for the backend image are SPDX's`() {
        val dir = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "ontrack-ui/src/notices/licenses") }
            .first { it.isDirectory }
        val mit = File(dir, "MIT.txt").readText()
        assertTrue(mit.startsWith("MIT License"), mit)
    }
}
