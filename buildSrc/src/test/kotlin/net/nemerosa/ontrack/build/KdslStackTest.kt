package net.nemerosa.ontrack.build

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KdslStackTest {

    @Test
    fun `slot zero reproduces the historical ports`() {
        // These are the defaults baked into ACCProperties and into the three
        // compose files, and what every CI runner gets.
        val instance = KdslStackInstance(slug = "yontrack", slot = 0)
        assertEquals(8080, instance.ontrackPort)
        assertEquals(8800, instance.ontrackManagementPort)
        assertEquals(8086, instance.influxdbPort)
        assertEquals(3000, instance.uiPort)
        assertEquals(8008, instance.keycloakPort)
        assertEquals("http://localhost:8080", instance.ontrackUrl)
        assertEquals("http://localhost:8800/manage", instance.ontrackManagementUrl)
        assertEquals("http://localhost:8086", instance.influxdbUrl)
    }

    @Test
    fun `instance derives every port from its slot`() {
        val instance = KdslStackInstance(slug = "feature-a", slot = 2)
        assertEquals("yontrack-kdsl-feature-a", instance.projectName)
        assertEquals("yontrack-kdsl-ldap-feature-a", instance.ldapProjectName)
        assertEquals("yontrack-kdsl-oidc-feature-a", instance.oidcProjectName)
        assertEquals(3200, instance.uiPort)
        assertEquals(589, instance.ldapPort)
        assertEquals(836, instance.ldapsPort)
        assertEquals(5632, instance.postgresPort)
        assertEquals(5872, instance.rabbitPort)
        assertEquals(15872, instance.rabbitManagementPort)
        assertEquals(8208, instance.keycloakPort)
        assertEquals(8280, instance.ontrackPort)
        assertEquals(8286, instance.influxdbPort)
        assertEquals(9000, instance.ontrackManagementPort)
        assertEquals(9400, instance.elasticPort)
        assertEquals("http://localhost:9000/manage", instance.ontrackManagementUrl)
    }

    @Test
    fun `the three variants get three different compose projects`() {
        val instance = KdslStackInstance(slug = "feature-a", slot = 1)
        val projects = setOf(instance.projectName, instance.ldapProjectName, instance.oidcProjectName)
        assertEquals(3, projects.size, "the three variants must not share a Compose project")
    }

    @Test
    fun `the tests are pointed at every port they reach`() {
        val instance = KdslStackInstance(slug = "feature-a", slot = 2)
        val properties = instance.systemProperties.values.joinToString(" ")
        listOf(
            instance.ontrackPort,
            instance.ontrackManagementPort,
            instance.influxdbPort,
        ).forEach { port ->
            assertTrue(properties.contains(port.toString()), "no property points at port $port")
        }
    }

    @Test
    fun `the internal url is never offset`() {
        // ontrack.acceptance.connection.internal.url is how Yontrack reaches
        // itself from inside its own container, where 8080 is always right
        // and the host port means nothing.
        val instance = KdslStackInstance(slug = "feature-a", slot = 2)
        assertFalse(
            instance.systemProperties.containsKey("ontrack.acceptance.connection.internal.url"),
            "the internal URL must be left at its container-local default",
        )
    }

    @Test
    fun `the compose environment covers every base port`() {
        val instance = KdslStackInstance(slug = "feature-a", slot = 2)
        assertEquals(
            KdslStack.BASE_PORTS.size,
            instance.composeEnvironment.size,
            "one compose variable per base port",
        )
    }

    @Test
    fun `the recorded instance can be read back`() {
        val dir = java.nio.file.Files.createTempDirectory("kdsl-stack-test").toFile()
        try {
            val file = File(dir, "instance.env")
            KdslStackInstance(slug = "feature-a", slot = 3).writeInstanceEnv(file)
            assertEquals(3, StackSlots.readRecordedSlot(file, KdslStack.SLOT_KEY))
            val text = file.readText()
            assertTrue(text.contains("YONTRACK_KDSL_ONTRACK_PORT=8380"), text)
            assertTrue(text.contains("KDSL_OIDC_PROJECT=yontrack-kdsl-oidc-feature-a"), text)
        } finally {
            dir.deleteRecursively()
        }
    }
}
