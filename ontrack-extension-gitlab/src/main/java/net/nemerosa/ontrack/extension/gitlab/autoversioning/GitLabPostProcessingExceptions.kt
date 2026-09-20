package net.nemerosa.ontrack.extension.gitlab.autoversioning

import net.nemerosa.ontrack.common.BaseException
import net.nemerosa.ontrack.extension.av.postprocessing.PostProcessingFailureException
import net.nemerosa.ontrack.model.exceptions.InputException

/**
 * The GitLab post-processing cannot be run with its configuration and the settings.
 */
class GitLabPostProcessingConfigException(message: String) : InputException(message)

/**
 * The post-processing pipeline did not complete successfully.
 */
class GitLabPostProcessingFailureException(
    message: String,
    override val link: String,
) : BaseException(message), PostProcessingFailureException
