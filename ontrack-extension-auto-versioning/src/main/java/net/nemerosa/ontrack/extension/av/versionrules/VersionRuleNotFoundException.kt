package net.nemerosa.ontrack.extension.av.versionrules

import net.nemerosa.ontrack.model.exceptions.InputException

/**
 * A rule ID is user input, checked when the configuration naming it is saved, so the error has to
 * reach the user as a validation error and not as an internal failure.
 */
class VersionRuleNotFoundException(id: String) :
    InputException("Cannot find auto-versioning version rule with ID = %s", id)
