package net.nemerosa.ontrack.extension.git.service

import net.nemerosa.ontrack.git.GitRepository
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitCloneGenerationServiceIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var gitCloneGenerationService: GitCloneGenerationService

    private fun repository() = GitRepository(
        type = "test",
        name = uid("r"),
        // Longer than a key of the storage
        remote = "https://git.example.com/${"a".repeat(200)}/${uid("r")}.git",
    )

    @Test
    fun `a reset bumps the generation of its repository only`() {
        val repository = repository()
        val other = repository()
        val generation = gitCloneGenerationService.getGeneration(repository)
        val otherGeneration = gitCloneGenerationService.getGeneration(other)

        gitCloneGenerationService.reset(repository)
        assertEquals(generation + 1, gitCloneGenerationService.getGeneration(repository))
        gitCloneGenerationService.reset(repository)
        assertEquals(generation + 2, gitCloneGenerationService.getGeneration(repository))

        assertEquals(otherGeneration, gitCloneGenerationService.getGeneration(other), "Other repository not reset")
    }

    @Test
    fun `resetting all the repositories bumps the generation of each`() {
        val repository = repository()
        gitCloneGenerationService.reset(repository)
        val generation = gitCloneGenerationService.getGeneration(repository)
        val never = repository()
        val neverGeneration = gitCloneGenerationService.getGeneration(never)

        gitCloneGenerationService.resetAll()

        assertTrue(gitCloneGenerationService.getGeneration(repository) > generation, "Reset repository")
        assertTrue(gitCloneGenerationService.getGeneration(never) > neverGeneration, "Repository never reset")
    }
}
