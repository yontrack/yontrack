package net.nemerosa.ontrack.model.exceptions;

public abstract class DuplicationException extends InputException {

    /**
     * A message, used as it is: it never goes through {@link String#format(String, Object...)}.
     */
    public DuplicationException(String message) {
        super(message);
    }

    public DuplicationException(String pattern, Object... parameters) {
        super(pattern, parameters);
    }
}
