package net.nemerosa.ontrack.demo.seed

/**
 * The dataset's own reading of what Yontrack accepts as a label.
 *
 * Repeated from the server rather than depended on, for the reason the rest of this module gives: it
 * talks to Yontrack over its API, not over its model classes. Shared between [validate] and
 * `InMemoryDemoTarget` rather than written twice, for the reason `SlotAdmissionRuleReadings` gives:
 * two copies of one rule let the check that guards a reset drift from the fake server the unit tests
 * run the seed against, and the tests would then pass on a dataset the reset refuses.
 */

/**
 * `LabelForm.LABEL_REGEX` on the server side, which applies to the category and to the name alike.
 * The `:` of a label's display form is in neither half, so a display string is split before it is
 * checked.
 */
internal val LABEL_NAME = Regex("[A-Za-z0-9.\\-_]+")

/**
 * `RGBColor.RGB_COLOR_REGEX` on the server side: a label's colour is a `#RRGGBB` string and nothing
 * else — no named colour, no short form.
 */
internal val LABEL_COLOR = Regex("#([a-fA-F0-9]{2})([a-fA-F0-9]{2})([a-fA-F0-9]{2})")
