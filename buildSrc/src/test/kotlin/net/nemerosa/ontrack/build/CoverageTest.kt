package net.nemerosa.ontrack.build

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoverageTest {

    @Test
    fun `the JaCoCo version is a three-part version`() {
        assertTrue(Regex("""\d+\.\d+\.\d+""").matches(Coverage.JACOCO_VERSION))
    }

    @Test
    fun `unit tests are collected as the unit session`() {
        assertEquals("unit", Coverage.sessionId("test", null))
    }

    @Test
    fun `integration tests are collected as the integration session`() {
        assertEquals("integration", Coverage.sessionId("integrationTest", null))
    }

    @Test
    fun `the suffix names the shard`() {
        assertEquals("integration-3", Coverage.sessionId("integrationTest", "3"))
        assertEquals("unit-2", Coverage.sessionId("test", "2"))
    }

    @Test
    fun `a blank suffix is no suffix`() {
        assertEquals("integration", Coverage.sessionId("integrationTest", ""))
        assertEquals("integration", Coverage.sessionId("integrationTest", "   "))
    }

    @Test
    fun `the suffix is trimmed and slugified`() {
        assertEquals("integration-3", Coverage.sessionId("integrationTest", " 3 "))
        assertEquals("integration-shard-3", Coverage.sessionId("integrationTest", "shard 3"))
    }

    @Test
    fun `test tasks of other kinds are not collected in the Gradle JVM`() {
        assertNull(Coverage.sessionId("kdslAcceptanceTest", null))
        assertNull(Coverage.sessionId("kdslAcceptanceTest", "1"))
        assertNull(Coverage.sessionId("uiTest", null))
    }

    @Test
    fun `collected task names`() {
        assertTrue(Coverage.collects("test"))
        assertTrue(Coverage.collects("integrationTest"))
        assertFalse(Coverage.collects("kdslAcceptanceTest"))
    }

    @Test
    fun `one exec file per test task`() {
        assertEquals("jacoco/test.exec", Coverage.execFilePath("test"))
        assertEquals("jacoco/integrationTest.exec", Coverage.execFilePath("integrationTest"))
    }

    // ---------------------------------------------------------------------------------------
    // The backend container of the KDSL/UI acceptance stacks (#1819)
    // ---------------------------------------------------------------------------------------

    @Test
    fun `each acceptance variant has a default container session type`() {
        assertEquals("kdsl", Coverage.containerSessionId("kdslAcceptanceTest", null, null))
        assertEquals("ui-ldap", Coverage.containerSessionId("kdslLdap", null, null))
        assertEquals("ui-oidc", Coverage.containerSessionId("kdslOidc", null, null))
    }

    @Test
    fun `an unknown variant has no container session`() {
        assertNull(Coverage.containerSessionId("kdslSomethingElse", null, null))
    }

    @Test
    fun `the container session suffix names the shard`() {
        assertEquals("kdsl-1", Coverage.containerSessionId("kdslAcceptanceTest", null, "1"))
        assertEquals("kdsl-2", Coverage.containerSessionId("kdslAcceptanceTest", null, "2"))
    }

    @Test
    fun `the main stack serves the Playwright legs under an overridden type`() {
        // The `kdslAcceptanceTest` Compose variant is brought up both by the KDSL suite and by the
        // main Playwright leg, so the type cannot be read off the variant alone: the CI workflow
        // names it, and the shards add the suffix (#1821).
        assertEquals("ui-main-1", Coverage.containerSessionId("kdslAcceptanceTest", "ui-main", "1"))
        assertEquals("ui-main-3", Coverage.containerSessionId("kdslAcceptanceTest", "ui-main", "3"))
        assertEquals("ui-main", Coverage.containerSessionId("kdslAcceptanceTest", "ui-main", null))
    }

    @Test
    fun `an override is slugified and a blank one is no override`() {
        assertEquals("ui-main", Coverage.containerSessionId("kdslAcceptanceTest", " UI Main ", null))
        assertEquals("kdsl", Coverage.containerSessionId("kdslAcceptanceTest", "   ", null))
        assertEquals("ui-ldap", Coverage.containerSessionId("kdslLdap", "", "  "))
    }

    @Test
    fun `only the shared variant is renamed by the override`() {
        // A job that runs several legs sets COVERAGE_SESSION once. If it renamed all three, ldap
        // and oidc would dump over each other's .exec and the merged report would lose a leg
        // without saying so.
        assertEquals("ui-ldap-2", Coverage.containerSessionId("kdslLdap", "ui-main", "2"))
        assertEquals("ui-oidc", Coverage.containerSessionId("kdslOidc", "ui-main", null))
        assertEquals(
            3,
            listOf("kdslAcceptanceTest", "kdslLdap", "kdslOidc")
                .mapNotNull { Coverage.containerSessionId(it, "ui-main", "2") }
                .toSet().size,
            "the three variants must keep three different sessions under one override",
        )
    }

    @Test
    fun `an override does not invent a session for an unknown variant`() {
        assertNull(Coverage.containerSessionId("whatever", "ui-main", null))
    }

    @Test
    fun `the container exec file is named after the session`() {
        assertEquals("jacoco/kdsl.exec", Coverage.containerExecFilePath("kdsl"))
        assertEquals("jacoco/ui-oidc.exec", Coverage.containerExecFilePath("ui-oidc"))
    }

    @Test
    fun `the agent jar is mounted from a fixed place`() {
        assertEquals("jacoco/jacocoagent.jar", Coverage.AGENT_JAR_PATH)
    }

    @Test
    fun `the compose variable carrying the session is the one the override reads`() {
        assertEquals("YONTRACK_COVERAGE_SESSION", Coverage.COMPOSE_SESSION_VARIABLE)
    }

    // ---------------------------------------------------------------------------------------
    // The Compose override repeats several of the constants above, because YAML cannot read
    // them. These are the checks that keep the two halves from drifting apart -- "a port in a
    // compose file and not in KdslStack is not wired" is the failure this guards against.
    // ---------------------------------------------------------------------------------------

    private val composeOverride: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "compose/docker-compose-coverage.yml") }
            .firstOrNull { it.isFile }
            ?: error("compose/docker-compose-coverage.yml not found above ${File("").absolutePath}")
    }

    @Test
    fun `the agent port the override publishes is the one KdslStack allocates`() {
        // The host side is `${YONTRACK_KDSL_JACOCO_PORT:-<base>}`, so the default has to be the
        // base port, which is what slot 0 -- every CI runner and every fresh clone -- gets.
        assertEquals(KdslStack.BASE_JACOCO, Coverage.AGENT_CONTAINER_PORT)
        val text = composeOverride.readText()
        assertTrue(
            text.contains("\"\${YONTRACK_KDSL_JACOCO_PORT:-${KdslStack.BASE_JACOCO}}:${Coverage.AGENT_CONTAINER_PORT}\""),
            "the override does not publish the agent port through KdslStack's variable:\n$text",
        )
    }

    @Test
    fun `the override mounts the agent where the build stages it`() {
        val text = composeOverride.readText()
        assertTrue(
            text.contains("../build/${Coverage.AGENT_JAR_PATH}:${Coverage.AGENT_JAR_CONTAINER_PATH}:ro"),
            "the override does not mount the staged agent jar read-only:\n$text",
        )
    }

    @Test
    fun `the override passes the agent through JAVA_TOOL_OPTIONS as a tcpserver`() {
        val text = composeOverride.readText()
        // JAVA_OPTIONS never reaches a Jib entrypoint; JAVA_TOOL_OPTIONS is the only hook.
        assertTrue(text.contains("JAVA_TOOL_OPTIONS:"), "the override does not set JAVA_TOOL_OPTIONS:\n$text")
        assertTrue(
            text.contains("-javaagent:${Coverage.AGENT_JAR_CONTAINER_PATH}="),
            "the agent is not the one mounted:\n$text",
        )
        // tcpserver, because nothing is written when the container is killed.
        assertTrue(text.contains("output=tcpserver"), "the agent does not serve over TCP:\n$text")
        assertTrue(
            text.contains("port=${Coverage.AGENT_CONTAINER_PORT}"),
            "the agent does not listen on the container port the dump asks for:\n$text",
        )
        assertTrue(
            text.contains("sessionid=\${${Coverage.COMPOSE_SESSION_VARIABLE}"),
            "the session ID does not come from ${Coverage.COMPOSE_SESSION_VARIABLE}:\n$text",
        )
    }

    @Test
    fun `the override touches nothing but the backend service`() {
        // It is applied on top of three different base files, and must not disturb what they
        // start. The released image is not touched at all -- there is no `image:` here.
        val text = composeOverride.readText()
        val services = text.lineSequence()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .filter { Regex("""^ {2}\S+:\s*$""").matches(it) }
            .map { it.trim().removeSuffix(":") }
            .toList()
        assertEquals(listOf("ontrack"), services, "the coverage override must override one service only")
        assertFalse(text.contains(Regex("""^\s*image:""", RegexOption.MULTILINE)), "the released image must not be touched")
    }
}
