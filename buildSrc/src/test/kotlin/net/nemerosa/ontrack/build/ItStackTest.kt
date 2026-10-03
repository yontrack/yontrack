package net.nemerosa.ontrack.build

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ItStackTest {

    @Test
    fun `instance derives every port from its slot`() {
        val instance = ItStackInstance(slug = "feature-a", slot = 3)
        assertEquals("yontrack-it-feature-a", instance.projectName)
        assertEquals(5732, instance.postgresPort)
        assertEquals(9500, instance.elasticPort)
        assertEquals(5972, instance.rabbitPort)
        assertEquals(15972, instance.rabbitManagementPort)
        assertEquals(8500, instance.vaultPort)
        assertEquals(19300, instance.minioPort)
        assertEquals("jdbc:postgresql://localhost:5732/ontrack", instance.jdbcUrl)
        assertEquals("http://localhost:9500", instance.elasticUri)
        assertEquals("http://localhost:8500", instance.vaultUri)
        assertEquals("http://localhost:19300", instance.minioUrl)
    }

    @Test
    fun `the compose project name needs no slot`() {
        // The Compose project is what the extension is configured with, and
        // configuration must claim no slot and probe no port.
        val names = ItStack.names(File("/home/dev/feature-a"))
        assertEquals("feature-a", names.slug)
        assertEquals("yontrack-it-feature-a", names.projectName)
        assertEquals(names.projectName, ItStackInstance(slug = names.slug, slot = 3).projectName)
    }

    @Test
    fun `slot zero reproduces the historical ports`() {
        val instance = ItStackInstance(slug = "yontrack", slot = 0)
        assertEquals(5432, instance.postgresPort)
        assertEquals(9200, instance.elasticPort)
        assertEquals(5672, instance.rabbitPort)
        assertEquals(8200, instance.vaultPort)
        assertEquals(19000, instance.minioPort)
        assertEquals("jdbc:postgresql://localhost:5432/ontrack", instance.jdbcUrl)
    }

    @Test
    fun `every published port is pointed at by a system property`() {
        // A service published on a per-instance port that nothing reads is a
        // service every worktree still shares.
        val instance = ItStackInstance(slug = "feature-a", slot = 3)
        val properties = instance.systemProperties.values.joinToString(" ")
        listOf(
            instance.postgresPort,
            instance.elasticPort,
            instance.rabbitPort,
            instance.vaultPort,
            instance.minioPort,
        ).forEach { port ->
            assertTrue(properties.contains(port.toString()), "no property points at port $port")
        }
    }

    @Test
    fun `the tests are pointed at the MinIO bucket of the instance`() {
        // The audit trail stores its evidence in S3 (#1962): every integration test runs against
        // the bucket of its own checkout's MinIO.
        val instance = ItStackInstance(slug = "feature-a", slot = 3)
        assertEquals(
            Minio.storageProperties("http://localhost:19300"),
            instance.systemProperties.filterKeys { it.startsWith("ontrack.extension.audit-trail.storage.") },
        )
    }

    @Test
    fun `the compose environment covers every base port`() {
        val instance = ItStackInstance(slug = "feature-a", slot = 3)
        assertEquals(
            ItStack.BASE_PORTS.size,
            instance.composeEnvironment.size,
            "one compose variable per base port",
        )
    }

    @Test
    fun `the recorded instance can be read back`() {
        val dir = java.nio.file.Files.createTempDirectory("it-stack-test").toFile()
        try {
            val file = File(dir, "instance.env")
            ItStackInstance(slug = "feature-a", slot = 6).writeInstanceEnv(file)
            assertEquals(6, StackSlots.readRecordedSlot(file, ItStack.SLOT_KEY))
            val text = file.readText()
            assertTrue(text.contains("YONTRACK_IT_POSTGRES_PORT=6032"), text)
            assertTrue(text.contains("YONTRACK_IT_MINIO_PORT=19600"), text)
            assertTrue(text.contains("IT_PROJECT=yontrack-it-feature-a"), text)
        } finally {
            dir.deleteRecursively()
        }
    }
}
