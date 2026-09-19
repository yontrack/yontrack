package net.nemerosa.ontrack.common;

public abstract class BaseException extends RuntimeException {

    public BaseException(String message) {
        super(message);
    }

    public BaseException(String pattern, Object... parameters) {
        this(format(pattern, parameters));
    }

    public BaseException(Exception ex, String message) {
        super(message, ex);
    }

    public BaseException(Exception ex, String pattern, Object... parameters) {
        super(format(pattern, parameters), ex);
    }

    /**
     * Formats a message.
     * <p>
     * When no parameter is given, the pattern <i>is</i> the message and is returned as-is. Running it
     * through {@link String#format(String, Object...)} anyway would break on any {@code %} it contains -
     * a URL-encoded path like {@code /projects/nemerosa%2Fyontrack/} throws an
     * {@link java.util.UnknownFormatConversionException}, and when the message is being built to wrap
     * another error, that error is then lost entirely.
     *
     * @param pattern    Message, or format pattern when parameters are given
     * @param parameters Format parameters, possibly none
     * @return Formatted message
     */
    public static String format(String pattern, Object... parameters) {
        return (parameters == null || parameters.length == 0)
                ? pattern
                : String.format(pattern, parameters);
    }
}
