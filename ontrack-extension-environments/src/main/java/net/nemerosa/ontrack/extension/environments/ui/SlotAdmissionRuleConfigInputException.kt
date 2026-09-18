package net.nemerosa.ontrack.extension.environments.ui

import net.nemerosa.ontrack.model.exceptions.InputException

/**
 * What `saveSlotAdmissionRuleConfig` refuses, and why.
 *
 * An [InputException] rather than a `BaseException`: only a `UserException` is turned into an entry
 * of the mutation's own `errors` list, and everything else surfaces as an `INTERNAL_ERROR` with no
 * message a dialog could show. Both of these are the caller's mistake - an id that names nothing,
 * or an id and a slot at once - so both belong in the payload where the Setup tab's rule dialog can
 * put them in front of whoever typed them.
 */
sealed class SlotAdmissionRuleConfigInputException(message: String) : InputException(message) {

    /** The id does not name a rule the caller can see. */
    class NotFound(id: String) : SlotAdmissionRuleConfigInputException(
        "Admission rule config '$id' not found"
    )

    /**
     * Both an id and a slot were given.
     *
     * Refused rather than silently resolved: a rule belongs to one slot for its whole life, so the
     * two can only ever agree or contradict each other, and there is nothing useful to do with a
     * contradiction.
     */
    class SlotNotNeeded : SlotAdmissionRuleConfigInputException(
        "If ID is provided, the ID of slot is not needed."
    )
}
