package net.nemerosa.ontrack.extension.bitbucket.cloud.model

import net.nemerosa.ontrack.common.BaseException

class BitbucketCloudNoResponseException(path: String): BaseException("""Did not get any answer from $path""")

class BitbucketCloudBranchNotFoundException(workspace: String, repository: String, branch: String) :
    BaseException("""Branch $branch cannot be found in $workspace/$repository""")
