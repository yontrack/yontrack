package net.nemerosa.ontrack.model.structure

/**
 * Reset of the search indexes which may be launched in the background at startup.
 */
interface SearchIndexStartupReset {

    /**
     * Blocks until the reset launched at startup, if any, is complete. The reset deletes and
     * recreates every index, so anything indexed in the meantime may be lost.
     */
    fun awaitCompletion()

}
