package net.nemerosa.ontrack.job;

import net.nemerosa.ontrack.job.support.JobNotScheduledException;

import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Scheduler of the jobs, shared by all the replicas of Yontrack (#2077).
 * <p>
 * The jobs are not scheduled one by one: every job comes from a {@code JobProvider} or a
 * {@link net.nemerosa.ontrack.job.orchestrator.JobOrchestratorSupplier}, which every replica
 * evaluates, so that any replica can run any job. See {@link #refresh()}.
 * <p>
 * A job runs once per period in the cluster, on any replica. Its pause, and the pause of all the
 * jobs, are stored, and apply to every replica. Its ID is the same on every replica.
 * <p>
 * Its status is the same on every replica (#2079): whether it is running, on which replica and
 * since when, from the runs Quartz is executing; its progress, last run and counters, written by the
 * replica running it.
 */
public interface JobScheduler {

    /**
     * Reads again the jobs of the providers and the suppliers on this replica, then reconciles the
     * jobs of the cluster with them: the new jobs are created, the jobs whose schedule has changed
     * are rescheduled, and the jobs which no longer exist are removed.
     * <p>
     * This is done at startup, then periodically: a change in what the suppliers give is picked
     * up at the next refresh, or at once by calling this method.
     */
    void refresh();

    /**
     * Removes a job from the cluster. A run of the job on this replica is stopped.
     * <p>
     * A job still given by a provider or a supplier comes back at the next {@link #refresh()}.
     *
     * @param key Key of the job to remove
     * @return True if the job existed
     */
    boolean unschedule(JobKey key);

    /**
     * Pauses all the jobs, on every replica, until {@link #resume()}.
     */
    void pause();

    /**
     * Resumes all the jobs, but the ones paused on their own.
     */
    void resume();

    /**
     * Pauses the execution of a job, on every replica
     *
     * @param key Key of the job to pause
     * @return <code>true</code> if the job was paused
     * @throws JobNotScheduledException If the job is not scheduled
     */
    boolean pause(JobKey key);

    /**
     * Resumes the execution of a job, on every replica
     *
     * @param key Key of the job to resume
     * @return <code>true</code> if the job was resumed
     * @throws JobNotScheduledException If the job is not scheduled
     */
    boolean resume(JobKey key);

    /**
     * Gets the status for a job, the same on every replica: whether it runs, on which replica and
     * since when ({@link JobStatus#isRunning()}, {@link JobStatus#getRunningOn()},
     * {@link JobStatus#getRunningSince()}), its last run and its last error.
     *
     * @param key Key of the job to get a status for
     * @return The job status or empty if not found.
     */
    Optional<JobStatus> getJobStatus(JobKey key);

    /**
     * Gets all the job keys
     *
     * @return List of all job keys, never null
     */
    Collection<JobKey> getAllJobKeys();

    /**
     * Gets all the job keys for a type of job
     *
     * @param type Job type
     * @return List of job keys for this type
     */
    @SuppressWarnings("unused")
    Collection<JobKey> getJobKeysOfType(JobType type);

    /**
     * Gets all the job keys for a category of jobs
     *
     * @param category Job category
     * @return List of job keys for this category
     */
    Collection<JobKey> getJobKeysOfCategory(JobCategory category);

    /**
     * Gets the statuses of the jobs of a category, the same on every replica, in one read: for
     * example, whether some of them are running.
     *
     * @param category Job category
     * @return Statuses of the jobs of this category (never null)
     */
    Collection<JobStatus> getJobStatusesOfCategory(JobCategory category);

    /**
     * Gets the list of job statuses
     *
     * @return All job statuses (never null)
     */
    Collection<JobStatus> getJobStatuses();

    /**
     * Fires a job once, now, on any replica, without waiting for its schedule - unless it is
     * already running, on any replica.
     *
     * @param jobKey Key of the job to fire immediately
     * @return Whether the job was triggered, and if so, a future completed once the run is over. A
     * run on another replica completes it once this replica finds the fire gone.
     * @throws JobNotScheduledException If the job is not scheduled
     */
    JobLaunch launch(JobKey jobKey);

    /**
     * Fires a job once, now, on any replica, without waiting for its schedule.
     *
     * @param jobKey Key of the job to fire immediately
     * @return Future completed once the run is over, or empty if the job cannot run now: invalid,
     * disabled, or already running on any replica. See {@link #launch(JobKey)}.
     * @throws JobNotScheduledException If the job is not scheduled
     */
    Optional<CompletableFuture<?>> fireImmediately(JobKey jobKey);

    /**
     * Fires a job once, after a delay, on any replica, unless such a delayed fire of the job is
     * already pending in the cluster.
     *
     * @param jobKey Key of the job to fire
     * @param delay  Delay before the fire
     * @return Future completed once the run is over, or empty if a delayed fire of the job is
     * already pending
     * @throws JobNotScheduledException If the job is not scheduled
     */
    Optional<CompletableFuture<?>> fireLater(JobKey jobKey, Duration delay);

    /**
     * Gets the job key for a job id
     */
    Optional<JobKey> getJobKey(long id);

    /**
     * Stops the run of a job, on whichever replica it runs: a run on this replica is interrupted at
     * once, a run on another replica within a few seconds, by that replica.
     *
     * @param key Key of the job to stop
     * @return <code>true</code> if the job is running and its stop is requested,
     * <code>false</code> if the job is not running.
     * @throws JobNotScheduledException If the job is not scheduled
     */
    boolean stop(JobKey key);

    /**
     * Checks the general status of the scheduler.
     *
     * @return <code>true</code> if all the jobs are paused.
     */
    boolean isPaused();

    /**
     * Checks for the timeouts of the runs on this replica
     *
     * @return Number of jobs which were stopped because of a timeout
     */
    int checkForTimeouts();
}
