package net.nemerosa.ontrack.extension.bitbucket.cloud.config

import net.nemerosa.ontrack.model.exceptions.InputException

class BitbucketCloudSCMRepositoryNotDetectedException(scmUrl: String) : InputException(
    "Could not find any Bitbucket Cloud workspace and repository for SCM URL: $scmUrl"
)

class BitbucketCloudSCMNoConfigException : InputException(
    "Could not find any Bitbucket Cloud configuration. Create one before configuring a Bitbucket Cloud project."
)

class BitbucketCloudSCMAmbiguousConfigException(names: List<String>) : InputException(
    "Several Bitbucket Cloud configurations exist (${names.joinToString(", ")}). " +
            "Set the configuration to use with the `scmConfig` project property of the CI configuration."
)
