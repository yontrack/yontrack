package net.nemerosa.ontrack.extension.av.graphql

/**
 * What the reference of an auto versioning query is, compared to the build or branch it is asked on.
 */
enum class AutoVersioningRefRole {
    /** The reference uses the build or branch */
    CONSUMER,

    /** The reference is used by the build or branch */
    DEPENDENCY
}
