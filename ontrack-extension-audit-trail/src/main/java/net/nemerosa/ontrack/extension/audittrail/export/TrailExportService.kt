package net.nemerosa.ontrack.extension.audittrail.export

import net.nemerosa.ontrack.model.structure.Build

/**
 * JSON export of the trails of the builds.
 */
interface TrailExportService {

    /**
     * Export of the trail of a build, whether the licence is on or not.
     *
     * @param build Build, which the caller must be allowed to see
     * @return Self-sufficient export of its trail
     */
    fun export(build: Build): TrailExport
}
