package net.nemerosa.ontrack.extension.av.versionrules

import net.nemerosa.ontrack.model.exceptions.InputException

/**
 * A rule configuration is user input, checked when the configuration naming it is saved, so a
 * configuration the rule cannot parse has to reach the user as a validation error and not as an
 * internal failure.
 */
class VersionRuleConfigException(id: String, cause: Exception) : InputException(
    "Configuration of the auto-versioning version rule %s is not valid > %s",
    id,
    cause.message ?: cause::class.java.simpleName,
)
