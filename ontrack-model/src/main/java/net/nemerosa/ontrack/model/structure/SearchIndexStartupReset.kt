package net.nemerosa.ontrack.model.structure

/**
 * Rebuild of the search documents which may be launched in the background at startup.
 */
interface SearchIndexStartupReset {

    /**
     * Blocks until the rebuild launched at startup, if any, is complete, so that it does not
     * interfere with what a test indexes or counts in the meantime.
     */
    fun awaitCompletion()

}
