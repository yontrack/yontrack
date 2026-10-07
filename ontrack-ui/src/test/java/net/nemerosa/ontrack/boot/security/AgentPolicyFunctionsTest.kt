package net.nemerosa.ontrack.boot.security

import net.nemerosa.ontrack.model.security.AgentPolicy
import net.nemerosa.ontrack.model.security.GlobalFunction
import net.nemerosa.ontrack.model.security.ProjectFunction
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.type.filter.AssignableTypeFilter
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Every global and project function of the application - the core ones and those of every extension
 * shipped with it, all on the classpath of this module - has been decided upon by the agent policy:
 * it is either allowed or denied to agents.
 *
 * A new function fails this test until someone adds it to `AgentPolicy.allowedFunctions` or to
 * `AgentPolicy.deniedFunctions`. Until then, the policy denies it anyway.
 */
class AgentPolicyFunctionsTest {

    /**
     * All the functions declared in the main code of Yontrack, extensions included.
     */
    private val functions: Set<String> by lazy {
        val scanner = object : ClassPathScanningCandidateComponentProvider(false) {
            // The functions are interfaces
            override fun isCandidateComponent(beanDefinition: AnnotatedBeanDefinition): Boolean =
                beanDefinition.metadata.isIndependent
        }
        scanner.addIncludeFilter(AssignableTypeFilter(GlobalFunction::class.java))
        scanner.addIncludeFilter(AssignableTypeFilter(ProjectFunction::class.java))
        scanner.findCandidateComponents("net.nemerosa.ontrack")
            .mapNotNull { it.beanClassName }
            .filter { it != GlobalFunction::class.java.name && it != ProjectFunction::class.java.name }
            // Functions declared by the tests
            .filter { name ->
                val location = Class.forName(name).protectionDomain?.codeSource?.location?.toString() ?: ""
                !location.contains("/test/") && !location.contains("test-fixtures") && !location.contains("testFixtures")
            }
            .toSet()
    }

    @Test
    fun `The functions are found`() {
        assertTrue("net.nemerosa.ontrack.model.security.ProjectView" in functions, "Core function found")
        assertTrue(
            "net.nemerosa.ontrack.extension.environments.security.SlotPipelineStart" in functions,
            "Extension function found"
        )
    }

    @Test
    fun `Every function is decided upon by the agent policy`() {
        val undecided = functions - AgentPolicy.allowedFunctions - AgentPolicy.deniedFunctions
        if (undecided.isNotEmpty()) {
            fail(
                "These functions are neither allowed nor denied to agents. " +
                        "Add each of them to AgentPolicy.allowedFunctions or AgentPolicy.deniedFunctions:\n" +
                        undecided.sorted().joinToString("\n") { "* $it" }
            )
        }
    }

    @Test
    fun `Every function of the policy exists`() {
        val unknown = (AgentPolicy.allowedFunctions + AgentPolicy.deniedFunctions) - functions
        if (unknown.isNotEmpty()) {
            fail(
                "These functions of the agent policy do not exist (renamed or removed?):\n" +
                        unknown.sorted().joinToString("\n") { "* $it" }
            )
        }
    }
}
