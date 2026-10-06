package net.nemerosa.ontrack.git.exceptions;

import net.nemerosa.ontrack.common.BaseException;

public abstract class GitRepositoryException extends BaseException {

    /**
     * A message, used as it is: it never goes through {@link String#format(String, Object...)}.
     */
    public GitRepositoryException(String message) {
        super(message);
    }

    /**
     * A message, used as it is, and its cause.
     */
    public GitRepositoryException(Exception ex, String message) {
        super(ex, message);
    }

    public GitRepositoryException(String pattern, Object... parameters) {
        super(pattern, parameters);
    }

    public GitRepositoryException(Exception ex, String pattern, Object... parameters) {
        super(ex, pattern, parameters);
    }
}
