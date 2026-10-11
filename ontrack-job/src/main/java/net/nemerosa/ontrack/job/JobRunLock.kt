package net.nemerosa.ontrack.job

/**
 * Lock guarding every run of a job — scheduled, cron or manual — so that at most one instance of
 * Yontrack runs a given job at a time.
 *
 * The job scheduler is per instance and in memory: when several instances run side by side, as
 * during a rolling upgrade, each one schedules the same jobs.
 */
fun interface JobRunLock {

    /**
     * Tries to acquire the lock for the job identified by [key], without waiting.
     *
     * The lock must be identified by the [string form][JobKey.toString] of the key only, so that
     * it is the same across instances and versions.
     *
     * @param key Key of the job about to run
     * @return A handle to [release][JobRunLockHandle.release] once the run is over, or `null` if
     * the lock is already held elsewhere
     */
    fun tryLock(key: JobKey): JobRunLockHandle?

}

/**
 * Lock acquired by [JobRunLock.tryLock], held for the whole run of a job.
 */
fun interface JobRunLockHandle {

    /**
     * Releases the lock.
     */
    fun release()

}

/**
 * Lock which is always acquired: for a single instance, or for tests.
 */
object NOPJobRunLock : JobRunLock {

    private val handle = JobRunLockHandle { }

    override fun tryLock(key: JobKey): JobRunLockHandle = handle

}
