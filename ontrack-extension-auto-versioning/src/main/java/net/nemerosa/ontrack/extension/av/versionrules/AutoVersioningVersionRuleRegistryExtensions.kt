package net.nemerosa.ontrack.extension.av.versionrules

import com.fasterxml.jackson.databind.JsonNode

/**
 * Gets the [AutoVersioningVersionRule] named by [id].
 *
 * @throws VersionRuleNotFoundException when no rule carries this ID
 */
fun <T> AutoVersioningVersionRuleRegistry.getVersionRuleById(id: String): AutoVersioningVersionRule<T> =
    findVersionRuleById<T>(id) ?: throw VersionRuleNotFoundException(id)

/**
 * Checks the [versionRule] ID and its [versionRuleConfig], as every _saving_ of a configuration naming
 * a version rule must do.
 *
 * A typo in the ID of a _safety_ rule, or a configuration the rule cannot parse, must not wait until the
 * next auto-versioning to surface: that run is exactly the one the rule was meant to guard. Version rules
 * are opt-in, so a blank [versionRule] is not an error, it just means no check.
 *
 * @throws VersionRuleNotFoundException when the rule ID is not known
 * @throws VersionRuleConfigException when the rule cannot parse its configuration
 */
fun AutoVersioningVersionRuleRegistry.validateVersionRule(versionRule: String?, versionRuleConfig: JsonNode?) {
    if (versionRule.isNullOrBlank()) return
    val rule = getVersionRuleById<Any>(versionRule)
    try {
        rule.parseAndValidate(versionRuleConfig)
    } catch (ex: Exception) {
        throw VersionRuleConfigException(versionRule, ex)
    }
}
