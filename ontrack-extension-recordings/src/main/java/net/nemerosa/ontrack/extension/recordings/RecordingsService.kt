package net.nemerosa.ontrack.extension.recordings

interface RecordingsService {

    fun <R : Recording, F : Any> record(
            extension: RecordingsExtension<R, F>,
            recording: R,
    )

    fun <R : Recording, F : Any> updateRecord(
            extension: RecordingsExtension<R, F>,
            id: String,
            updating: (R) -> R,
    )

    /**
     * Updates a recording while holding a lock on it, so that two concurrent updates of the same
     * recording are applied one after the other, each seeing the result of the previous one.
     *
     * @param id ID of the recording
     * @param updating Given the current recording, returns its new version, or `null` to leave it
     * unchanged
     * @return The new version of the recording, or `null` if it was left unchanged
     * @throws RecordingNotFoundException If the recording does not exist
     */
    fun <R : Recording, F : Any> updateLockedRecord(
            extension: RecordingsExtension<R, F>,
            id: String,
            updating: (R) -> R?,
    ): R?

}