package net.nemerosa.ontrack.extension.recordings

import net.nemerosa.ontrack.extension.recordings.store.RecordingsStore
import net.nemerosa.ontrack.extension.recordings.store.toRecording
import net.nemerosa.ontrack.extension.recordings.store.toStoredRecording
import org.springframework.transaction.annotation.Transactional

abstract class AbstractRecordingsService(
        private val recordingsStore: RecordingsStore,
) : RecordingsService {

    override fun <R : Recording, F : Any> record(extension: RecordingsExtension<R, F>, recording: R) {
        // Gets the storage representation for the recordings
        val storedRecording = recording.toStoredRecording(extension)
        // Saving the recordings
        recordingsStore.save(
                store = extension.id,
                recording = storedRecording,
        )
    }

    override fun <R : Recording, F : Any> updateRecord(extension: RecordingsExtension<R, F>, id: String, updating: (R) -> R) {
        val storedRecording = recordingsStore.findById(extension.id, id)
                ?: throw RecordingNotFoundException(extension.id, id)
        val recording = storedRecording.toRecording(extension)
        val newRecording = updating(recording)
        record(extension, newRecording)
    }

    /**
     * Transactional, so that the lock on the recording is held until its new version is written,
     * even when the caller has no transaction of its own: the class-level annotations of the
     * subclasses do not apply to the methods they inherit.
     */
    @Transactional
    override fun <R : Recording, F : Any> updateLockedRecord(
            extension: RecordingsExtension<R, F>,
            id: String,
            updating: (R) -> R?,
    ): R? {
        val storedRecording = recordingsStore.findByIdForUpdate(extension.id, id)
                ?: throw RecordingNotFoundException(extension.id, id)
        val recording = storedRecording.toRecording(extension)
        val newRecording = updating(recording)
        if (newRecording != null) {
            record(extension, newRecording)
        }
        return newRecording
    }
}
