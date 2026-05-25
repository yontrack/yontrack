package net.nemerosa.ontrack.kdsl.spec.extension.av

/**
 * Mode for pushing the auto-versioning changes.
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
