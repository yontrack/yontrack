package net.nemerosa.ontrack.extension.av.processing

/**
 * Status of the processing of an auto-versioning order.
 *
 * @property message Display message
 */
enum class AutoVersioningProcessingOutcome(
    val message: String,
) {

    /**
     * The auto-versioning process was completed successfully.
     */
    CREATED("The auto-versioning process was completed successfully"),

    /**
     * The order was correct, but no PR was created because there was no change in the target version.
     */
    SAME_VERSION("Auto-versioning process not started because no change in version"),

    /**
     * The order was correct, but the process way not be complete because of a timeout.
     */
    TIMEOUT("Auto-versioning process not started because missing configuration"),

    /**
     * The order was correct, but no PR was created because there was some missing configuration (typically
     * missing Git configuration at the level of the target project or branch).
     */
    NO_CONFIG("Auto-versioning process not started because missing configuration"),

}