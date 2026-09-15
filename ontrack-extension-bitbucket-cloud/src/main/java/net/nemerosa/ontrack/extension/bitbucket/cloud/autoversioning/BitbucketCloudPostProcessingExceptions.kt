package net.nemerosa.ontrack.extension.bitbucket.cloud.autoversioning

import net.nemerosa.ontrack.common.BaseException
import net.nemerosa.ontrack.extension.av.postprocessing.PostProcessingFailureException
import net.nemerosa.ontrack.model.exceptions.InputException

/**
 * The Bitbucket Cloud post-processing cannot be run with its configuration and the settings.
 */
class BitbucketCloudPostProcessingConfigException(message: String) : InputException(message)

/**
 * The post-processing pipeline did not complete successfully.
 */
class BitbucketCloudPostProcessingFailureException(
    message: String,
    override val link: String,
) : BaseException(message), PostProcessingFailureException
