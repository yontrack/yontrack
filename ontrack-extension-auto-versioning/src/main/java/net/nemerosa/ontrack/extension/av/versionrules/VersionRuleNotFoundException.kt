package net.nemerosa.ontrack.extension.av.versionrules

import net.nemerosa.ontrack.common.BaseException

class VersionRuleNotFoundException(id: String) :
    BaseException("Cannot find auto-versioning version rule with ID = $id")
