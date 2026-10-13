package net.nemerosa.ontrack.extension.git.service

import net.nemerosa.ontrack.git.GitRepository
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.support.StorageService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest

/**
 * The generation of a repository is the sum of its own generation and of the generation of all the
 * repositories, each in `STORAGE`: a reset of either increases it.
 */
@Service
@Transactional
class GitCloneGenerationServiceImpl(
    private val storageService: StorageService,
) : GitCloneGenerationService {

    @Transactional(readOnly = true)
    override fun getGeneration(repository: GitRepository): Long =
        read(ALL) + read(key(repository))

    override fun reset(repository: GitRepository) {
        bump(key(repository))
    }

    override fun resetAll() {
        bump(ALL)
    }

    private fun read(key: String): Long =
        storageService.findJson(STORE, key)?.path(GENERATION)?.asLong() ?: 0

    /**
     * Read and written back under a lock of the entry, so that two resets are two bumps.
     */
    private fun bump(key: String) {
        val generation = storageService.findJsonForUpdate(STORE, key)?.path(GENERATION)?.asLong() ?: 0
        storageService.storeJson(STORE, key, mapOf(GENERATION to generation + 1).asJson())
    }

    /**
     * The ID of a repository contains its remote, which can be longer than a key of `STORAGE`.
     */
    private fun key(repository: GitRepository): String =
        MessageDigest.getInstance("SHA-256")
            .digest(repository.id.toByteArray())
            .joinToString("") { "%02x".format(it) }

    companion object {
        private val STORE = GitCloneGenerationService::class.java.name
        private const val ALL = "*"
        private const val GENERATION = "generation"
    }
}
