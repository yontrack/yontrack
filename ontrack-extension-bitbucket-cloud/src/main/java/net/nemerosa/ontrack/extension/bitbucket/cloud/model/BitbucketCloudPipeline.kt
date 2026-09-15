package net.nemerosa.ontrack.extension.bitbucket.cloud.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * A Bitbucket pipeline, as returned by `POST` and `GET /2.0/repositories/{workspace}/{repository}/pipelines/…`.
 *
 * @property uuid UUID of the pipeline, between braces
 * @property buildNumber Number of the pipeline in its repository, used in its web URL
 * @property state `PENDING`, `IN_PROGRESS` or `COMPLETED`, with a result once completed
 * @property durationInSeconds Wall-clock duration, once completed
 * @property buildSecondsUsed Build minutes consumed, in seconds
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class BitbucketCloudPipeline(
    val uuid: String,
    @JsonProperty("build_number")
    val buildNumber: Int,
    val state: BitbucketCloudPipelineState? = null,
    @JsonProperty("duration_in_seconds")
    val durationInSeconds: Int? = null,
    @JsonProperty("build_seconds_used")
    val buildSecondsUsed: Int? = null,
) {
    /**
     * Is the pipeline over?
     */
    val completed: Boolean get() = state?.name == STATE_COMPLETED

    /**
     * Result name once completed (`SUCCESSFUL`, `FAILED`, `ERROR`, `STOPPED`, `EXPIRED`), the state name before.
     */
    val stateName: String
        get() = if (completed) {
            state?.result?.name ?: STATE_COMPLETED
        } else {
            state?.name ?: STATE_UNKNOWN
        }

    companion object {
        const val STATE_COMPLETED = "COMPLETED"
        const val STATE_UNKNOWN = "UNKNOWN"
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class BitbucketCloudPipelineState(
    val name: String? = null,
    val result: BitbucketCloudPipelineResult? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BitbucketCloudPipelineResult(
    val name: String? = null,
)
