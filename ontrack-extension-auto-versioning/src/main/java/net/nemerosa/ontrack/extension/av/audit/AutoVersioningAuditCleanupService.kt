package net.nemerosa.ontrack.extension.av.audit

interface AutoVersioningAuditCleanupService {

    /**
     * Removes all non-running entries which are older than a given retention period.
     *
     * Needs the global `ApplicationManagement` function: the `AutoVersioningAuditCleanupJob` runs it
     * as an administrator.
     *
     * @return Summary of the cleanup
     */
    fun cleanup(): AutoVersioningAuditCleanupResult

    /**
     * Removes ALL entries, whatever their state.
     *
     * Needs the global `ApplicationManagement` function.
     */
    fun purge()

}