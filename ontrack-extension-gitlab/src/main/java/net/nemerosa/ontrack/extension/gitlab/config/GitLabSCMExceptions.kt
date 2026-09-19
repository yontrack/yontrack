package net.nemerosa.ontrack.extension.gitlab.config

import net.nemerosa.ontrack.model.exceptions.InputException

class GitLabSCMRepositoryNotDetectedException(scmUrl: String) : InputException(
    "Could not find any GitLab project for SCM URL: $scmUrl"
)

class GitLabSCMNoConfigException(scmUrl: String) : InputException(
    "Could not find any GitLab configuration matching the SCM URL: $scmUrl. " +
            "Create a configuration for this GitLab instance before configuring the project."
)

class GitLabSCMAmbiguousConfigException(scmUrl: String, names: List<String>) : InputException(
    "Several GitLab configurations match the SCM URL $scmUrl (${names.joinToString(", ")}). " +
            "Set the configuration to use with the `scmConfig` project property of the CI configuration."
)
