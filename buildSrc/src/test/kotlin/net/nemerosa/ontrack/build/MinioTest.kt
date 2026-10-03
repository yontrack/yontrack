package net.nemerosa.ontrack.build

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The MinIO of the development, integration test and KDSL acceptance stacks (#1962) is declared in
 * three Compose files and configured from three places -- [ItStackInstance], the KDSL Compose file
 * and `scripts/dev-stack.sh` -- none of which can read the others. These checks keep them on one
 * image, one bucket and one set of credentials.
 */
class MinioTest {

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "compose/docker-compose-it.yml").isFile }
            ?: error("compose/docker-compose-it.yml not found above ${File("").absolutePath}")
    }

    private val composeFiles = listOf(
        "compose/docker-compose-dev.yml",
        "compose/docker-compose-it.yml",
        "compose/docker-compose-kdsl.yml",
    )

    private fun text(path: String): String = File(root, path).readText()

    @Test
    fun `the storage properties point the application at the bucket`() {
        assertEquals(
            mapOf(
                "ontrack.extension.audit-trail.storage.endpoint" to "http://localhost:19300",
                "ontrack.extension.audit-trail.storage.bucket" to "yontrack-audit-trail",
                "ontrack.extension.audit-trail.storage.region" to "us-east-1",
                "ontrack.extension.audit-trail.storage.path-style" to "true",
                "ontrack.extension.audit-trail.storage.access-key" to Minio.ACCESS_KEY,
                "ontrack.extension.audit-trail.storage.secret-key" to Minio.SECRET_KEY,
            ),
            Minio.storageProperties("http://localhost:19300"),
        )
    }

    @Test
    fun `the image is pinned by version and by digest`() {
        // The upstream minio/minio image left Docker Hub in 2026: a floating tag is how a stack
        // stops starting one morning.
        assertTrue(Minio.IMAGE.contains(":RELEASE."), "pinned to a release tag: ${Minio.IMAGE}")
        assertTrue(Minio.IMAGE.contains("@sha256:"), "pinned to a digest: ${Minio.IMAGE}")
    }

    @Test
    fun `every stack runs the pinned image`() {
        composeFiles.forEach { path ->
            assertTrue(text(path).contains("image: \"${Minio.IMAGE}\""), "$path does not run ${Minio.IMAGE}")
        }
    }

    @Test
    fun `every stack creates the same bucket with the same credentials`() {
        composeFiles.forEach { path ->
            val compose = text(path)
            listOf(Minio.BUCKET, Minio.ACCESS_KEY, Minio.SECRET_KEY).forEach { value ->
                assertTrue(compose.contains(value), "$path does not mention $value")
            }
        }
    }

    @Test
    fun `the development backend is pointed at the same bucket`() {
        val script = text("scripts/dev-stack.sh")
        listOf(Minio.BUCKET, Minio.ACCESS_KEY, Minio.SECRET_KEY, Minio.REGION).forEach { value ->
            assertTrue(script.contains(value), "scripts/dev-stack.sh does not mention $value")
        }
    }

    @Test
    fun `the S3 port sits clear of every other port of the test stacks`() {
        assertTrue(Minio.BASE_PORT in ItStack.BASE_PORTS, "the IT stack probes the MinIO port")
        assertTrue(Minio.BASE_PORT in KdslStack.BASE_PORTS, "the KDSL stack probes the MinIO port")
    }
}
