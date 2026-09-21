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
        assertEquals(6500, instance.jacocoPort)
        assertEquals("http://localhost:9000/manage", instance.ontrackManagementUrl)
    }

    @Test
    fun `the JaCoCo agent port is offset per slot like every other port`() {
        // The coverage override publishes the agent's tcpserver (#1819). A port in a compose file
        // and not here is not wired, and a hardcoded 6300 would make two checkouts collide.
        assertEquals(6300, KdslStackInstance(slug = "yontrack", slot = 0).jacocoPort)
        assertEquals(6400, KdslStackInstance(slug = "feature-a", slot = 1).jacocoPort)
        assertEquals(6600, KdslStackInstance(slug = "feature-a", slot = 3).jacocoPort)
        assertTrue(KdslStack.BASE_JACOCO in KdslStack.BASE_PORTS, "the agent port must be probed too")
    }

    @Test
    fun `no two base ports collide over the slot range`() {
        // 6300 sits between RabbitMQ's 5672 and Keycloak's 8008, and the slots offset by 100 each:
        // adding a port is only safe as long as no slot of one lands on another slot of another.
        val all = (0..KdslStack.SLOT_MAX).flatMap { StackSlots.ports(KdslStack.BASE_PORTS, it) }
        assertEquals(all.size, all.toSet().size, "two ports of the same or different slots collide")
    }

    @Test
    fun `the compose project names need no slot`() {
        // Naming the three Compose projects is what the Compose extension is
        // configured with, and configuration must claim no slot and probe no
        // port -- so the names come from the checkout path alone.
        val names = KdslStack.names(File("/home/dev/feature-a"))
        assertEquals("feature-a", names.slug)
        assertEquals("yontrack-kdsl-feature-a", names.projectName)
        assertEquals("yontrack-kdsl-ldap-feature-a", names.ldapProjectName)
        assertEquals("yontrack-kdsl-oidc-feature-a", names.oidcProjectName)
    }

    @Test
    fun `a resolved instance carries the same project names`() {
        // The extension is configured from the names and the stack is brought
        // up under the instance's project, so the two must not drift apart.
        val names = KdslStack.names(File("/home/dev/feature-a"))
        val instance = KdslStackInstance(slug = names.slug, slot = 2)
        assertEquals(names.projectName, instance.projectName)
        assertEquals(names.ldapProjectName, instance.ldapProjectName)
        assertEquals(names.oidcProjectName, instance.oidcProjectName)
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
    fun `the Playwright suite is pointed at the slot of the stack`() {
        // Without these, connection.js falls back to the slot-0 ports and every test of a linked
        // worktree fails in its fixture, getting a token from the wrong stack (#1847).
        val instance = KdslStackInstance(slug = "feature-a", slot = 2)
        assertEquals(
            mapOf(
                // The management root: the fixture appends /manage itself.
                "ONTRACK_MGT_URL" to "http://localhost:9000",
                "ONTRACK_UI_URL" to "http://localhost:3200",
                "ONTRACK_BACKEND_URL" to "http://localhost:8280",
            ),
            instance.playwrightEnvironment,
        )
    }

    @Test
    fun `slot zero gives Playwright its historical defaults`() {
        // The defaults of ontrack-web-tests/ontrack/connection.js.
        assertEquals(
            mapOf(
                "ONTRACK_MGT_URL" to "http://localhost:8800",
                "ONTRACK_UI_URL" to "http://localhost:3000",
                "ONTRACK_BACKEND_URL" to "http://localhost:8080",
            ),
            KdslStackInstance(slug = "yontrack", slot = 0).playwrightEnvironment,
        )
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
            assertTrue(text.contains("YONTRACK_KDSL_JACOCO_PORT=6600"), text)
            assertTrue(text.contains("KDSL_OIDC_PROJECT=yontrack-kdsl-oidc-feature-a"), text)
        } finally {
            dir.deleteRecursively()
        }
    }
}
