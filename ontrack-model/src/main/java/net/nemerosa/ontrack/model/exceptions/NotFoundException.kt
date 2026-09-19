package net.nemerosa.ontrack.model.exceptions

import net.nemerosa.ontrack.common.BaseException
import net.nemerosa.ontrack.common.UserException

abstract class NotFoundException(
        pattern: String,
        vararg parameters: Any
) : UserException(BaseException.format(pattern, *parameters))
