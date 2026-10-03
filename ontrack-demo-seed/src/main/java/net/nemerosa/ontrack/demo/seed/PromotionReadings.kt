package net.nemerosa.ontrack.demo.seed

import java.util.regex.Pattern

/**
 * The dataset's own reading of the two promotion properties a promotion level can carry.
 *
 * Repeated from the server rather than depended on, for the reason the rest of this module gives: it
 * talks to Yontrack over its API, not over its model classes. Shared between [validate] and
 * `InMemoryDemoTarget` rather than written twice, for the reason `SlotAdmissionRuleReadings` gives:
 * two copies of one rule let the check that guards a reset drift from the fake server the unit tests
 * run the seed against, and the tests would then pass on a dataset the reset refuses.
 */

/**
 * Whether an auto promotion pattern selects a validation stamp by name.
 *
 * `AutoPromotionProperty.contains` on the server side, whose patterns are whole-string regular
 * expressions - `Pattern.matches`, not `find` - and whose blank pattern matches nothing at all
 * rather than everything.
 */
internal fun autoPromotionSelectsStamp(name: String, spec: AutoPromotionSpec): Boolean {
    fun matches(pattern: String) = pattern.isNotBlank() && Pattern.matches(pattern, name)
    return name in spec.validationStamps || (matches(spec.include) && !matches(spec.exclude))
}

/**
 * Whether a validation of this status, on this stamp, counts as passed for an auto promotion.
 *
 * `ValidationRunService.isValidationRunPassedForAutoPromotion` on the server side, where `PASSED` and
 * `FIXED` pass and `WARNING` does not - *unless the stamp opts in*: a CHML stamp carrying
 * `warningPassesAutoPromotion` lets a `WARNING` through to the auto promotion (#1943). Read through
 * here rather than compared to `PASSED` at each use: the dataset's [ValidationStatus] is a subset of
 * what Yontrack knows, and the day it gains `FIXED` - which is how it gains any status - a comparison
 * against `PASSED` alone starts answering wrongly about a build the server would have promoted.
 *
 * @param stamp The stamp the validation ran on, or null when the branch does not declare it - which
 * [validate] refuses anyway, and which reads as a stamp without any data type
 */
internal fun validationStatusPasses(status: ValidationStatus, stamp: ValidationStampSpec?): Boolean =
    status == ValidationStatus.PASSED || status == ValidationStatus.FIXED ||
            (status == ValidationStatus.WARNING && stamp?.chml?.warningPassesAutoPromotion == true)

/**
 * The first promotion a build is missing out of the ones its promotion requires, or null when it is
 * missing none.
 *
 * `PromotionRunDependenciesCheckExtension` on the server side, which refuses the promotion outright.
 * The order matters as much as the set: the check runs as the promotion is created, so a build
 * promoted to GOLD before SILVER is refused even though it ends up carrying both.
 *
 * @param alreadyPromoted The promotions the build carries at that moment, in order
 * @param dependencies What the promotion being granted requires
 */
internal fun missingPromotionDependency(alreadyPromoted: List<String>, dependencies: List<String>): String? =
    dependencies.firstOrNull { it !in alreadyPromoted }
