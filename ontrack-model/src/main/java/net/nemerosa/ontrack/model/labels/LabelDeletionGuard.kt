package net.nemerosa.ontrack.model.labels

/**
 * Asked before a label is deleted, by any extension which relies on labels staying in place.
 *
 * A guard does not delete anything: it says why the label must stay, and the deletion is then
 * refused with a [LabelInUseException] gathering the reasons of every guard.
 */
interface LabelDeletionGuard {

    /**
     * Says why the label cannot be deleted.
     *
     * @param label Label about to be deleted
     * @return A reason, like "it selects the estates A, B", or `null` if the label can be deleted
     */
    fun checkLabelDeletion(label: Label): String?
}
