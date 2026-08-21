package net.nemerosa.ontrack.service.elasticsearch

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.SearchIndexService
import net.nemerosa.ontrack.model.structure.SearchService
import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import net.nemerosa.ontrack.model.support.StartupService
import net.nemerosa.ontrack.model.support.StorageService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * When migrating to V5, we need to remove all the indexes and start an indexation from scratch.
 *
 * The same mechanism is used whenever the mapping of an index changes: an existing index is never
 * updated in place by [SearchIndexService.initIndex], so bumping [KEY] resets all the indexes once
 * and repopulates them using the new mappings.
 */
@Component
class ElasticSearchV5Migration(
    private val storageService: StorageService,
    private val searchService: SearchService,
    private val searchIndexService: SearchIndexService,
    private val securityService: SecurityService,
    private val ontrackConfigProperties: OntrackConfigProperties,
) : StartupService {

    private val logger: Logger = LoggerFactory.getLogger(ElasticSearchV5Migration::class.java)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun getName(): String? = "ElasticSearch V5 migration"

    /**
     * Runs after the jobs have been registered.
     */
    override fun startupOrder(): Int = StartupService.JOB_REGISTRATION + 1

    override fun start() {
        val migrated = storageService
            .find(store, KEY, ElasticSearchV5MigrationStatus::class)
            ?.migrated ?: false
        if (!migrated || ontrackConfigProperties.search.index.reset) {
            logger.info("Launching the reset of all ElasticSearch indexes for migration to V5 (ES9)...")
            scope.launch {
                logger.info("Removing all ElasticSearch indexes for migration to V5 (ES9)...")
                try {
                    securityService.asAdmin {
                        searchService.indexReset(reindex = true, logErrors = true)
                        logger.info("Removing obsolete indexes")
                        searchIndexService.deleteIndex("git-commit")
                        searchIndexService.deleteIndex("git-issues")
                    }
                    storageService.store(
                        store = store,
                        key = KEY,
                        data = ElasticSearchV5MigrationStatus(migrated = true)
                    )
                    logger.info("Removed and repopulated all ElasticSearch indexes for migration to V5 (ES9).")
                } catch (ex: Exception) {
                    logger.error("Error during ElasticSearch V5 migration", ex)
                }
            }
            logger.info("Launched the reset of all ElasticSearch indexes for migration to V5 (ES9) asynchronously.")
        } else {
            logger.info("ElasticSearch indexes have already been migrated to V5 (ES9).")
        }
    }

    companion object {
        private val store: String = ElasticSearchV5Migration::class.java.name

        /**
         * Bumped whenever the mapping of an index changes, to force a one-off reset of all the
         * indexes. Last bumped for the exact match sub-fields of the build index.
         */
        private const val KEY = "migration-2"
    }

    data class ElasticSearchV5MigrationStatus(val migrated: Boolean)
}