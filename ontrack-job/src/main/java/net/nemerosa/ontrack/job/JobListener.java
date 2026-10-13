package net.nemerosa.ontrack.job;

public interface JobListener {

    /**
     * This method is called whenever a job is started
     *
     * @param key Key of the job
     */
    void onJobStart(JobKey key);



    /**
     * This method is called whenever a job is finished with success
     *
     * @param key          Key of the job
     * @param milliseconds Execution time of the job
     */
    void onJobEnd(JobKey key, long milliseconds);

    /**
     * This method is called whenever a job is finished with an error
     *
     * @param key Key of the job
     * @param ex  Exception in the job
     */
    void onJobError(JobStatus key, Exception ex);

    /**
     * This method is called whenever a job is finished, with an error or not
     *
     * @param key Key of the job
     */
    void onJobComplete(JobKey key);

    /**
     * This method is called whenever a job sends some progress information
     *
     * @param key      Key of the job
     * @param progress Progress indicator
     */
    void onJobProgress(JobKey key, JobRunProgress progress);

}
