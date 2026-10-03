package net.nemerosa.ontrack.demo.seed

/**
 * The dataset's own reading of the statuses of a validation run - `ValidationRunStatusServiceImpl`
 * on the server side, as far as the statuses of [ValidationStatus] go.
 *
 * Shared between [validate] and `InMemoryDemoTarget`, for the reason `SlotAdmissionRuleReadings`
 * gives: the server refuses a change of status it does not allow, and finding that out on a real
 * instance leaves the demo deleted and half rebuilt.
 */

/**
 * Whether a run can be created with [status]: only a root status, as the server reads it.
 */
internal fun isInitialStatus(status: ValidationStatus): Boolean = status != ValidationStatus.FIXED

/**
 * Whether a run whose last status is [from] can be given [to].
 */
internal fun statusChangeAllowed(from: ValidationStatus, to: ValidationStatus): Boolean =
    when (from) {
        ValidationStatus.FAILED, ValidationStatus.WARNING -> to == ValidationStatus.FIXED
        ValidationStatus.PASSED, ValidationStatus.FIXED -> false
    }
