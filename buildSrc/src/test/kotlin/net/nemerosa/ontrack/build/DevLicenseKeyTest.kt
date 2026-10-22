package net.nemerosa.ontrack.build

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DevLicenseKeyTest {

    private val home: File = Files.createTempDirectory("dev-license-key").toFile()

    private fun writeFile(content: String) {
        File(home, DevLicenseKey.FILE_PATH).apply {
            parentFile.mkdirs()
            writeText(content)
        }
    }

    @Test
    fun `the variable comes first`() {
        writeFile("from-file")
        assertEquals(
            "from-variable",
            DevLicenseKey.resolve(env = mapOf(DevLicenseKey.VARIABLE to "from-variable")::get, home = home)
        )
    }

    @Test
    fun `the file when the variable is not set`() {
        writeFile("from-file\n")
        assertEquals("from-file", DevLicenseKey.resolve(env = { null }, home = home))
    }

    @Test
    fun `the file when the variable is blank`() {
        writeFile("from-file")
        assertEquals(
            "from-file",
            DevLicenseKey.resolve(env = mapOf(DevLicenseKey.VARIABLE to " ")::get, home = home)
        )
    }

    @Test
    fun `no key when there is neither`() {
        assertNull(DevLicenseKey.resolve(env = { null }, home = home))
    }

    @Test
    fun `no key when the file is blank`() {
        writeFile("\n")
        assertNull(DevLicenseKey.resolve(env = { null }, home = home))
    }

    @Test
    fun `the Compose environment carries the key`() {
        assertEquals(
            mapOf(DevLicenseKey.VARIABLE to "key"),
            DevLicenseKey.composeEnvironment(env = mapOf(DevLicenseKey.VARIABLE to "key")::get, home = home)
        )
    }

    @Test
    fun `the Compose environment is empty without a key`() {
        assertTrue(DevLicenseKey.composeEnvironment(env = { null }, home = home).isEmpty())
    }

    /**
     * The Compose files of the stacks running the `dev` profile read the key from the variable,
     * which Gradle passes them.
     */
    @Test
    fun `the dev Compose files pass the key to Yontrack`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "compose/docker-compose-kdsl.yml").isFile }
        listOf(
            "compose/docker-compose-kdsl.yml",
            "compose/docker-compose-kdsl-ldap.yml",
            "compose/docker-compose-kdsl-oidc.yml",
            "compose/docker-compose-kdsl-ha.yml",
            "security/dast/compose/docker-compose-dast.yml",
        ).forEach { path ->
            val text = File(root, path).readText()
            val profiles = Regex("SPRING_PROFILES_ACTIVE: dev").findAll(text).count()
            val keys = Regex("""ONTRACK_CONFIG_LICENSE_KEY: "\$\{YONTRACK_DEV_LICENSE_KEY:-}"""").findAll(text).count()
            assertEquals(profiles, keys, "Every dev backend of $path gets the development key")
        }
    }
}
