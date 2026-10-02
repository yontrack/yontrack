package net.nemerosa.ontrack.extension.audittrail.model

/**
 * Types of the entries of a trail.
 *
 * The list of schema version 1 is closed; the types written from the events are added with the
 * listener which writes them.
 */
object TrailEntryTypes {

    /**
     * First entry of the trail of a build which predates it — the build was created before the
     * feature, or while the licence was off. Its payload names the build, gives its creation time,
     * and marks the trail as partial.
     */
    const val TRAIL_OPENED = "trail.opened"

    /**
     * Creation of a build: the first entry of a complete trail.
     */
    const val BUILD_CREATED = "build.created"
}
