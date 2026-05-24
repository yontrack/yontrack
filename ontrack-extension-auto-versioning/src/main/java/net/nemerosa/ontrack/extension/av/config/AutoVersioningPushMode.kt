package net.nemerosa.ontrack.extension.av.config

/**
 * How to handle the push of the auto-versioning updates.
 */
enum class AutoVersioningPushMode {

    /**
     * Using a pull request to push the change.
     */
    PR,

    /**
     * Pushing the change directly to the target branch.
     */
    PUSH,

}