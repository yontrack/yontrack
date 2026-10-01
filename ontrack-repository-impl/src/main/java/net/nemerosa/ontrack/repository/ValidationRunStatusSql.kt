package net.nemerosa.ontrack.repository

object ValidationRunStatusSql {

    /**
     * Joins a validation run `VR` with its current status `VRS`: the last row of its status
     * history. A run has one `VALIDATION_RUN_STATUSES` row per status it has ever had, so joining
     * them all would match its past statuses and repeat the run once per matching row.
     */
    const val LAST_STATUS_JOIN =
        "INNER JOIN VALIDATION_RUN_STATUSES VRS ON VRS.ID = (SELECT ID FROM VALIDATION_RUN_STATUSES WHERE VALIDATIONRUNID = VR.ID ORDER BY ID DESC LIMIT 1) "
}
