package net.nemerosa.ontrack.boot.ui

import net.nemerosa.ontrack.boot.Application
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.security.Roles
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals

/**
 * The demonstration tampering switched on: a global administrator rewrites the payload of an
 * entry, anybody else is refused.
 *
 * Its own context, the switch being on for this class only. Not transactional: the server answers
 * on its own threads.
 */
@SpringBootTest(
    classes = [Application::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "ontrack.extension.audit-trail.demo-tampering.enabled=true",
    ],
)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DemoTamperingEnabledHttpIT : DemoTamperingHttpSupport() {

    @Test
    fun `A global administrator rewrites the payload of an entry`() {
        val build = trailedBuild()
        val response = rewritePayload(build, 2, """{"status":"FORGED"}""", adminToken())
        assertEquals(200, response.statusCode(), response.body())
        val entry = response.body().parseAsJson()
        assertEquals(2, entry.path("seq").asInt())
        assertEquals("""{"status":"FORGED"}""".parseAsJson(), entry.path("payload"))
    }

    @Test
    fun `Anybody else is refused`() {
        val build = trailedBuild()
        val response = rewritePayload(build, 2, """{"status":"FORGED"}""", token(Roles.GLOBAL_AUTOMATION))
        assertEquals(403, response.statusCode(), response.body())
    }

    @Test
    fun `A payload a trail does not accept is refused`() {
        val build = trailedBuild()
        val response = rewritePayload(build, 2, """{"coverage":0.85}""", adminToken())
        assertEquals(400, response.statusCode(), response.body())
    }
}
