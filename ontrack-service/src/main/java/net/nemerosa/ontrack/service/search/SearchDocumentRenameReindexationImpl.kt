package net.nemerosa.ontrack.service.search

import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.SearchDocumentIndexer
import net.nemerosa.ontrack.model.structure.SearchDocumentRenameReindexation
import net.nemerosa.ontrack.model.structure.StructureService
import org.springframework.stereotype.Component
import javax.sql.DataSource

/**
 * Re-indexes the search documents carrying the name of a renamed project or branch in what they are
 * matched on — their title or identifiers — as declared by the
 * [rename scopes][SearchDocumentIndexer.renameScopes] of the indexers.
 *
 * Called from the `search-rename` queue, after the commit of the rename (#2083): a rename never
 * carries a bulk rewrite, and never fails because of the search. A failure is logged and counted in
 * `ontrack_search_index_errors`. The names shown by the results do not wait for it, since they are
 * resolved when searching.
 *
 * The re-indexations of an entity run one at a time, across the replicas, under an advisory lock on
 * the entity: each one reads the current name and deletes the documents indexed before its own
 * start, so that two of them interleaving could leave the documents of a former name. Two quick
 * renames of a project end with the documents of the last one.
 */
@Component
class SearchDocumentRenameReindexationImpl(
    private val searchDocumentIndexers: List<SearchDocumentIndexer>,
    private val searchDocumentService: SearchDocumentServiceImpl,
    private val structureService: StructureService,
    private val securityService: SecurityService,
    private val dataSource: DataSource,
) : SearchDocumentRenameReindexation {

    override fun reindexRenamed(type: ProjectEntityType, id: ID) {
        val scopes = searchDocumentIndexers.flatMap { indexer ->
            indexer.renameScopes
                .filter { it.type == type }
                .map { indexer to it }
        }
        if (scopes.isEmpty()) {
            return
        }
        withLock(type, id) {
            // Read after the lock is taken, so that it is the name of the last rename
            val entity: ProjectEntity? = securityService.asAdmin {
                when (type) {
                    ProjectEntityType.PROJECT -> structureService.findProjectByID(id)
                    ProjectEntityType.BRANCH -> structureService.findBranchByID(id)
                    else -> error("Only projects and branches are renamed: $type")
                }
            }
            if (entity != null) {
                scopes.forEach { (indexer, scope) ->
                    searchDocumentService.reindexRenamed(indexer, scope, entity)
                }
            }
        }
    }

    /**
     * Runs [code] while holding the advisory lock on the entity, waiting for it if needed.
     *
     * The lock is held by a transaction of its own on a dedicated connection, so that it neither
     * joins nor holds the transactions of the re-indexation.
     */
    private fun withLock(type: ProjectEntityType, id: ID, code: () -> Unit) {
        dataSource.connection.use { connection ->
            val autoCommit = connection.autoCommit
            connection.autoCommit = false
            try {
                connection.prepareStatement("SELECT pg_advisory_xact_lock(?, hashtext(?))").use { statement ->
                    statement.setInt(1, LOCK_CLASS)
                    statement.setString(2, "${type.name}#${id.value}")
                    // pg_advisory_xact_lock returns void: the row is read and ignored
                    statement.executeQuery().close()
                }
                code()
            } finally {
                // Ending the transaction releases the lock
                try {
                    connection.rollback()
                } finally {
                    connection.autoCommit = autoCommit
                }
            }
        }
    }

    companion object {
        /**
         * First key of the advisory locks on the re-indexation of a renamed project or branch, the
         * second being a hash of its type and ID: "SRNM" in ASCII — next to the locks on the trails
         * ("TRAI"), on the blobs of the evidence ("EVID"), on the reconciliation of the jobs ("JOBS"), on the
         * processing of the auto-versioning orders ("AVQU", "AVMG"), on the startup ("STRT") and on
         * the assisted changes ("ASCH").
         */
        const val LOCK_CLASS: Int = 0x53524E4D
    }
}
