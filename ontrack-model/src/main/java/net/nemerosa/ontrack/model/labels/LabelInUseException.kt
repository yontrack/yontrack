package net.nemerosa.ontrack.model.labels

import net.nemerosa.ontrack.model.exceptions.InputException

/**
 * Deleting a label which some [guard][LabelDeletionGuard] needs.
 */
class LabelInUseException(label: Label, reasons: List<String>) : InputException(
    "Label %s cannot be deleted: %s.",
    label.getDisplay(),
    reasons.joinToString("; "),
)
