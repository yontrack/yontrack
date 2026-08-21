package net.nemerosa.ontrack.model.structure

interface ValidationRunService {

    /**
     * Updates an existing validation run with new data.
     *
     * Any existing data will be overridden with the new one or deleted.
     *
     * @param run Existing validation run.
     * @param data New validation data to set (can be null to delete any existing data)
     * @return Updated validation run
     */
    fun updateValidationRunData(
        run: ValidationRun,
        data: ValidationRunData<*>?,
    ): ValidationRun

    /**
     * Checks if the last run for the given [build] and [validation stamp][validationStamp], if
     * it exists, is passed or not.
     *
     * A run is passed when the ID of its last status is flagged as
     * [passed][ValidationRunStatusID.isPassed] - this covers `PASSED` but also `FIXED`.
     *
     * If there was no run at all or the last one is not passed, the function returns `false`.
     */
    fun isValidationRunPassed(build: Build, validationStamp: ValidationStamp): Boolean

}