package net.nemerosa.ontrack.build

import org.gradle.api.artifacts.dsl.LockMode
import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DependencyLockingTest {

    @Test
    fun `locking is strict`() {
        val project = ProjectBuilder.builder().build()
        DependencyLocking.configure(project)
        assertEquals(LockMode.STRICT, project.dependencyLocking.lockMode.get())
    }

    @Test
    fun `resolveAndLockAll is registered`() {
        val project = ProjectBuilder.builder().build()
        DependencyLocking.configure(project)
        val task = project.tasks.findByName(DependencyLocking.RESOLVE_AND_LOCK_ALL)
        assertNotNull(task)
    }

    @Test
    fun `every exclusion carries its reason`() {
        DependencyLocking.EXCLUDED_CONFIGURATIONS.forEach { (name, reason) ->
            assertTrue(reason.isNotBlank(), "Configuration $name is excluded without a reason")
        }
    }

    @Test
    fun `a subproject locks its plugin classpath strictly`() {
        val root = ProjectBuilder.builder().build()
        val sub = ProjectBuilder.builder().withParent(root).withName("sub").build()
        DependencyLocking.configure(sub)
        assertEquals(LockMode.STRICT, sub.buildscript.dependencyLocking.lockMode.get())
    }
}
