package net.nemerosa.ontrack.model.exceptions

import net.nemerosa.ontrack.common.BaseException
import net.nemerosa.ontrack.common.UserException

abstract class InputException : UserException {

    /**
     * A message, used as it is. Most subclasses interpolate their input into it: it never goes
     * through [String.format], so a `%` that came from the request cannot be read as a format
     * specifier.
     */
    constructor(message: String) : super(message)

    /**
     * A format pattern and its parameters.
     */
    constructor(pattern: String, vararg parameters: Any) : super(BaseException.format(pattern, *parameters))
}
