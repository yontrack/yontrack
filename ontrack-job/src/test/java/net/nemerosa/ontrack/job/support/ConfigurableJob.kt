package net.nemerosa.ontrack.job.support

import net.nemerosa.ontrack.job.*

class ConfigurableJob
@JvmOverloads
constructor(
        val name: String = "test",
        var count: Int = 0,
        var increment: Int = 1,
        val category: JobCategory = Fixtures.TEST_CATEGORY,
        val type: String = "test",
        var fail: Boolean = false,
        var wait: Long = 0,
        var valid: Boolean = true,
        var disabled: Boolean = false,
        var theDescription: String = "Test job"
) : Job {

    var running: Boolean = false

    /**
     * Code run at the start of each run of the job
     */
    var onRun: (() -> Unit)? = null

    override fun isDisabled(): Boolean {
        return disabled
    }

    override fun isValid(): Boolean {
        return valid
    }

    override fun getKey(): JobKey {
        return this.category.getType(this.type).getKey(name)
    }

    override fun getTask(): JobRun {
        return JobRun { listener ->
            running = true
            try {
                onRun?.invoke()
                if (this.wait > 0) {
                    try {
                        Thread.sleep(this.wait)
                    } catch (e: InterruptedException) {
                        throw RuntimeException("Job was interrupted", e)
                    }
                }
                if (fail) {
                    throw RuntimeException("Task failure")
                }
                count += increment
                listener.message("TEST JOB %s Count = %d", name, count)
            } finally {
                running = false
            }
        }
    }

    fun invalidate() {
        this.valid = false
    }

    fun pause() {
        disabled = true
    }

    fun resume() {
        disabled = false
    }

    override fun getDescription(): String {
        return theDescription
    }

}
