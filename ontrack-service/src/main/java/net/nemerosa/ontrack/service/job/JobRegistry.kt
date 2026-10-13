package net.nemerosa.ontrack.service.job

import net.nemerosa.ontrack.job.JobKey
import net.nemerosa.ontrack.job.JobRegistration
import net.nemerosa.ontrack.job.orchestrator.JobOrchestratorSupplier
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.support.JobProvider
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.quartz.JobKey as QuartzJobKey

/**
 * Jobs known by this replica: those of every [JobProvider] and [JobOrchestratorSupplier], which
 * every replica evaluates, so that any replica can run any job of the cluster.
 *
 * Refreshed at startup, then periodically, and when a job unknown here is fired. The jobs are read
 * as an administrator, in a transaction - joining the one of the caller, if any, so that a caller
 * having just changed what a supplier gives sees its change.
 *
 * The providers and suppliers are looked up when refreshing, since many of them depend on the
 * job scheduler.
 */
@Component
class JobRegistry(
    private val applicationContext: ApplicationContext,
    private val securityService: SecurityService,
    transactionManager: PlatformTransactionManager,
) {

    private val logger: Logger = LoggerFactory.getLogger(JobRegistry::class.java)

    private val transactionTemplate = TransactionTemplate(transactionManager)

    @Volatile
    private var registrations: Map<JobKey, JobRegistration> = emptyMap()

    @Volatile
    private var byQuartzKey: Map<QuartzJobKey, JobRegistration> = emptyMap()

    /**
     * Time of the last refresh, in milliseconds
     */
    val lastRefresh: Long get() = lastRefreshMs

    @Volatile
    private var lastRefreshMs: Long = 0

    /**
     * Did every provider and supplier give its jobs at the last refresh? When one failed, its jobs
     * are missing, and must not be removed from the cluster.
     */
    val complete: Boolean get() = completeRefresh

    @Volatile
    private var completeRefresh: Boolean = true

    /**
     * All the jobs known by this replica
     */
    val all: Collection<JobRegistration> get() = registrations.values

    fun find(key: JobKey): JobRegistration? = registrations[key]

    fun find(key: QuartzJobKey): JobRegistration? = byQuartzKey[key]

    /**
     * Reads again the jobs of the providers and of the suppliers.
     */
    @Synchronized
    fun refresh() {
        var failures = 0
        val collected = securityService.asAdmin {
            transactionTemplate.execute { collect { failures++ } }
        } ?: emptyList()
        val indexed = LinkedHashMap<JobKey, JobRegistration>()
        collected.forEach { registration ->
            val key = registration.job.key
            if (indexed.containsKey(key)) {
                logger.warn("[job][registry] Job $key given more than once, the last one is kept")
            }
            indexed[key] = registration
        }
        registrations = indexed
        byQuartzKey = indexed.values.associateBy { QuartzSchedules.jobKey(it.job.key) }
        completeRefresh = failures == 0
        lastRefreshMs = System.currentTimeMillis()
        logger.debug("[job][registry] {} jobs", indexed.size)
    }

    private fun collect(onFailure: () -> Unit): List<JobRegistration> {
        val providers = applicationContext.getBeansOfType(JobProvider::class.java).values
            .flatMap { provider ->
                collect(provider::class.java.name, onFailure) { provider.startingJobs }
            }
        val suppliers = applicationContext.getBeansOfType(JobOrchestratorSupplier::class.java).values
            .flatMap { supplier ->
                collect(supplier::class.java.name, onFailure) { supplier.jobRegistrations }
            }
        return providers + suppliers
    }

    /**
     * Collects the jobs of a provider or a supplier: one failing is logged, and gives no job for
     * now, the others still giving theirs.
     */
    private fun collect(
        source: String,
        onFailure: () -> Unit,
        code: () -> Collection<JobRegistration>,
    ): Collection<JobRegistration> =
        try {
            code()
        } catch (any: Exception) {
            logger.error("[job][registry] Cannot collect the jobs of $source", any)
            onFailure()
            emptyList()
        }
}
