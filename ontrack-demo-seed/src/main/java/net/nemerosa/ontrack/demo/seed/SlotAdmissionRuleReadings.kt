package net.nemerosa.ontrack.demo.seed

/**
 * The dataset's own reading of the admission rules a slot can carry.
 *
 * Repeated from the server rather than depended on, for the reason the rest of this module
 * gives: it talks to Yontrack over its API, not over its model classes. It is shared between
 * [validate] and `InMemoryDemoTarget` rather than written twice, because two copies of one
 * pattern reading would let the check that guards a reset drift from the fake server the unit
 * tests run the seed against - and then the tests would pass on a dataset the reset refuses.
 */

/**
 * The ids of the admission rules the dataset knows how to write and to check.
 *
 * Named rather than spelled out at each use: the same three ids are written by `DemoContent`,
 * checked by [validate] and enforced by `InMemoryDemoTarget`, and a typo in any one of them
 * would go through as a rule nobody reads rather than as a failure.
 */
internal object SlotAdmissionRules {
    /** `PromotionSlotAdmissionRule.ID` on the server side. */
    const val PROMOTION = "promotion"

    /** `EnvironmentSlotAdmissionRule.ID` on the server side. */
    const val ENVIRONMENT = "environment"

    /** `BranchPatternSlotAdmissionRule.ID` on the server side. */
    const val BRANCH_PATTERN = "branchPattern"

    /** `ManualApprovalSlotAdmissionRule.ID` on the server side. */
    const val MANUAL = "manual"
}

/**
 * What a configured admission rule may be named - `SlotAdmissionRuleConfig.PATTERN` on the
 * server side.
 */
internal val ADMISSION_RULE_NAME = Regex("[a-zA-Z][a-zA-Z0-9-]*")

/**
 * The moments of a deployment a slot workflow can be fired at - `SlotPipelineStatus` on the server
 * side, minus `CANCELLED`, which is a state a pipeline reaches rather than a moment.
 *
 * Named here, and checked by [validate], because a typo in a trigger would otherwise only be found
 * by a reset failing against a real instance.
 */
internal val SLOT_WORKFLOW_TRIGGERS = listOf("CANDIDATE", "RUNNING", "DONE")

/**
 * `FilterHelper.includes` on the server side, whose patterns are whole-string,
 * case-insensitive regular expressions.
 */
internal fun branchIncludedByPattern(branch: String, config: Map<String, Any>): Boolean {
    @Suppress("UNCHECKED_CAST")
    val includes = config["includes"] as? List<String> ?: emptyList()

    @Suppress("UNCHECKED_CAST")
    val excludes = config["excludes"] as? List<String> ?: emptyList()

    fun matches(patterns: List<String>) = patterns.any {
        it.toRegex(RegexOption.IGNORE_CASE).matches(branch)
    }
    return matches(includes) && !matches(excludes)
}
