package net.nemerosa.ontrack.extension.av.versionrules

import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.model.extension.Extension

/**
 * Rule checking that the version an auto-versioning order wants to set into a target file is
 * acceptable, given the version currently present in this file.
 *
 * Version rules are opt-in: they are run only when a rule ID is set on the auto-versioning
 * source configuration.
 *
 * @param T Configuration type
 */
interface AutoVersioningVersionRule<T> : Extension {

    /**
     * ID of the rule, as used in the `versionRule` field of the auto-versioning configuration.
     */
    val id: String

    /**
     * Display name of the rule
     */
    val name: String

    /**
     * Given the configuration as JSON, parses it and validates it.
     */
    fun parseAndValidate(config: JsonNode?): T

    /**
     * Checks the version change described by the [context].
     *
     * @param config Configuration of the rule
     * @param context Version change to check
     * @return Result of the check, carrying a rejection reason when the change is not accepted
     */
    fun check(config: T, context: AutoVersioningVersionRuleContext): AutoVersioningVersionRuleResult

}
