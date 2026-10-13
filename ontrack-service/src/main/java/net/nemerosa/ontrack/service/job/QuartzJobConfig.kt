package net.nemerosa.ontrack.service.job

import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import org.quartz.DisallowConcurrentExecution
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.quartz.Scheduler
import org.quartz.simpl.SimpleInstanceIdGenerator
import org.quartz.spi.InstanceIdGenerator
import org.quartz.spi.JobFactory
import org.quartz.spi.TriggerFiredBundle
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.quartz.autoconfigure.QuartzProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.quartz.SchedulerFactoryBean
import org.springframework.transaction.PlatformTransactionManager
import java.util.Properties
import javax.sql.DataSource

/**
 * The Quartz scheduler of the jobs (#2077): a JDBC job store on the main data source, clustered,
 * so that all the replicas sharing the database share the jobs, their triggers and their pauses.
 *
 * The replicas of one installation form one cluster by sharing the name of the scheduler,
 * `spring.quartz.scheduler-name` ([DEFAULT_NAME] by default). Each replica is a Quartz instance,
 * named by its host name - the pod name in Kubernetes.
 *
 * Defined here rather than by the auto-configuration of Spring Boot, so that it is the same
 * wherever Yontrack runs, integration tests included, whatever `spring.quartz.job-store-type`.
 * `spring.quartz.properties.*` still overrides the Quartz properties set here. The tables of Quartz
 * are created by Flyway, which runs before the scheduler is created.
 */
@Configuration
class QuartzJobConfig(
    private val ontrackConfigProperties: OntrackConfigProperties,
) {

    @Bean
    fun jobSchedulerName(quartzProperties: QuartzProperties): JobSchedulerName =
        JobSchedulerName(quartzProperties.schedulerName?.takeIf { it.isNotBlank() } ?: DEFAULT_NAME)

    @Bean
    fun quartzScheduler(
        dataSource: DataSource,
        transactionManager: PlatformTransactionManager,
        quartzProperties: QuartzProperties,
        jobSchedulerName: JobSchedulerName,
        jobScheduler: ObjectProvider<QuartzJobScheduler>,
    ): SchedulerFactoryBean = SchedulerFactoryBean().apply {
        setSchedulerName(jobSchedulerName.value)
        setDataSource(dataSource)
        setTransactionManager(transactionManager)
        setQuartzProperties(Properties().apply {
            putAll(quartzProperties())
            putAll(quartzProperties.properties)
        })
        setJobFactory(QuartzJobFactory(jobScheduler))
        // Started once the jobs of this replica are reconciled with the cluster, see QuartzJobScheduler
        isAutoStartup = true
        // A run lost with its replica is not run again: the job runs at its next fire
        setWaitForJobsToCompleteOnShutdown(false)
    }

    private fun quartzProperties() = mapOf(
        "org.quartz.scheduler.instanceId" to "AUTO",
        "org.quartz.scheduler.instanceIdGenerator.class" to HostNameInstanceIdGenerator::class.java.name,
        // A trigger created by another replica is picked up within this delay
        "org.quartz.scheduler.idleWaitTime" to "5000",
        "org.quartz.scheduler.skipUpdateCheck" to "true",
        "org.quartz.threadPool.threadCount" to ontrackConfigProperties.jobs.poolSize.toString(),
        "org.quartz.threadPool.threadNamePrefix" to "job",
        "org.quartz.threadPool.makeThreadsDaemons" to "true",
        "org.quartz.jobStore.isClustered" to "true",
        "org.quartz.jobStore.clusterCheckinInterval" to "7500",
        "org.quartz.jobStore.driverDelegateClass" to "org.quartz.impl.jdbcjobstore.PostgreSQLDelegate",
        "org.quartz.jobStore.tablePrefix" to "QRTZ_",
        "org.quartz.jobStore.misfireThreshold" to "60000",
    )

    companion object {
        /**
         * Name of the Quartz scheduler shared by the replicas of an installation
         */
        const val DEFAULT_NAME = "yontrack"
    }
}

/**
 * Name of the Quartz scheduler: the cluster of the replicas, and the scope of the rows of `JOBS`.
 */
data class JobSchedulerName(val value: String)

/**
 * Names the Quartz instance of a replica after its host name, the name of its pod in Kubernetes -
 * or, without one, after the host and the time, as Quartz does.
 */
class HostNameInstanceIdGenerator : InstanceIdGenerator {
    override fun generateInstanceId(): String =
        System.getenv("HOSTNAME")?.takeIf { it.isNotBlank() }
            ?: SimpleInstanceIdGenerator().generateInstanceId()
}

/**
 * Every fire runs the jobs of Yontrack through [QuartzJobScheduler].
 */
class QuartzJobFactory(
    private val jobScheduler: ObjectProvider<QuartzJobScheduler>,
) : JobFactory {
    override fun newJob(bundle: TriggerFiredBundle, scheduler: Scheduler): Job =
        QuartzJobRunner(jobScheduler.getObject())
}

/**
 * The one Quartz job class: it runs the job of Yontrack identified by the key of the Quartz job.
 *
 * No two runs of the same job at the same time in the cluster.
 */
@DisallowConcurrentExecution
class QuartzJobRunner(
    private val jobScheduler: QuartzJobScheduler,
) : Job {
    override fun execute(context: JobExecutionContext) {
        jobScheduler.execute(context.jobDetail.key, context.trigger.key)
    }
}
