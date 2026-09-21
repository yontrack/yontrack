package net.nemerosa.ontrack.extension.config.ci.conditions

import tools.jackson.databind.node.StringNode
import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.config.ci.engine.CIEngine
import net.nemerosa.ontrack.extension.config.ci.model.CIConditionConfig
import net.nemerosa.ontrack.json.asJson
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OrConditionTest {

    private val condition = OrCondition()

    private lateinit var ciEngine: CIEngine
    private lateinit var conditionRegistry: ConditionRegistry

    @BeforeEach
    fun before() {
        ciEngine = mockk()

        conditionRegistry = mockk()
        every { conditionRegistry.getCondition("branch") } returns BranchCondition()
        every { conditionRegistry.getCondition("environment-regex") } returns EnvironmentRegexCondition()
    }

    @Test
    fun `No condition does not match`() {
        val config = emptyList<Any>()
        assertFalse(
            condition.matches(
                conditionRegistry,
                ciEngine,
                config.asJson(),
                emptyMap()
            ),
        )
    }

    @Test
    fun `First matching condition`() {
        every { ciEngine.getBranchName(any()) } returns "release/1.0"
        val config = listOf(
            CIConditionConfig(
                name = "branch",
                config = StringNode("release/.*"),
            ),
            CIConditionConfig(
                name = "environment-regex",
                config = EnvironmentRegexConditionConfig(
                    name = "VERSION",
                    regex = "\\d+\\.\\d+\\.\\d+"
                ).asJson()
            ),
        )
        assertTrue(
            condition.matches(
                conditionRegistry,
                ciEngine,
                config.asJson(),
                emptyMap()
            ),
        )
    }

    @Test
    fun `Second matching condition`() {
        every { ciEngine.getBranchName(any()) } returns "main"
        val config = listOf(
            CIConditionConfig(
                name = "branch",
                config = StringNode("release/.*"),
            ),
            CIConditionConfig(
                name = "environment-regex",
                config = EnvironmentRegexConditionConfig(
                    name = "VERSION",
                    regex = "\\d+\\.\\d+\\.\\d+"
                ).asJson()
            ),
        )
        assertTrue(
            condition.matches(
                conditionRegistry,
                ciEngine,
                config.asJson(),
                mapOf(
                    "VERSION" to "5.0.2"
                )
            ),
        )
    }

    @Test
    fun `No matching condition`() {
        every { ciEngine.getBranchName(any()) } returns "main"
        val config = listOf(
            CIConditionConfig(
                name = "branch",
                config = StringNode("release/.*"),
            ),
            CIConditionConfig(
                name = "environment-regex",
                config = EnvironmentRegexConditionConfig(
                    name = "VERSION",
                    regex = "\\d+\\.\\d+\\.\\d+"
                ).asJson()
            ),
        )
        assertFalse(
            condition.matches(
                conditionRegistry,
                ciEngine,
                config.asJson(),
                mapOf(
                    "VERSION" to "5.0-beta.2"
                )
            ),
        )
    }

}