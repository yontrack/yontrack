package net.nemerosa.ontrack.boot.ui;

import jakarta.servlet.http.HttpServletRequest;
import net.nemerosa.ontrack.model.exceptions.InputException;
import net.nemerosa.ontrack.model.exceptions.NotFoundException;
import net.nemerosa.ontrack.model.security.AgentPolicyException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@ControllerAdvice(annotations = RestController.class)
public class UIErrorHandler {

    private final Logger logger;
    private final MessageSource messageSource;

    @Autowired
    public UIErrorHandler(MessageSource messageSource) {
        this.logger = LoggerFactory.getLogger(getClass());
        this.messageSource = messageSource;
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseBody
    public ResponseEntity<UIErrorMessage> onInputException(NotFoundException ex) {
        // Returns a message to display to the user
        String message = ex.getMessage();
        // OK
        return getMessageResponse(HttpStatus.NOT_FOUND, message);
    }

    @ExceptionHandler(InputException.class)
    @ResponseBody
    public ResponseEntity<UIErrorMessage> onInputException(InputException ex) {
        // Returns a message to display to the user
        String message = ex.getMessage();
        // OK
        return getMessageResponse(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseBody
    public ResponseEntity<UIErrorMessage> onValidationException(MethodArgumentNotValidException ex) {
        // Field errors to messages
        List<String> messages = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> {
                    String defaultMessage = fieldError.getDefaultMessage();
                    if (StringUtils.isNotBlank(defaultMessage)) {
                        return defaultMessage;
                    } else {
                        return messageSource.getMessage(fieldError, Locale.ENGLISH);
                    }
                })
                .toList();
        // Returned message
        String message;
        if (messages.size() > 1) {
            message = messages.stream().map(s -> "* " + s + "\n").collect(Collectors.joining(""));
        } else {
            message = messages.get(0);
        }
        // OK
        return getMessageResponse(
                HttpStatus.BAD_REQUEST,
                message
        );
    }

    /**
     * A refusal by the agent policy says why: an agent which only gets "Not authorized." retries.
     */
    @ExceptionHandler(AgentPolicyException.class)
    @ResponseBody
    public ResponseEntity<UIErrorMessage> onAgentPolicyException(AgentPolicyException ex) {
        return getMessageResponse(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseBody
    public ResponseEntity<UIErrorMessage> onAccessDeniedException() {
        // Logs the error?
        // Returns a 403 error
        return getMessageResponse(HttpStatus.FORBIDDEN, "Not authorized.");
    }

    @ExceptionHandler(Exception.class)
    @ResponseBody
    public ResponseEntity<UIErrorMessage> onAnyException(HttpServletRequest request, Exception ex) {
        // Not for access denied
        if (ex instanceof AccessDeniedException) {
            throw (AccessDeniedException) ex;
        }
        // Returns a message to display to the user
        String message = "An error has occurred.";
        // Logs the error in the application
        logger.error(
                String.format(
                        "Error when calling the API: %s",
                        request.getServletPath()
                ),
                ex
        );
        // OK
        return getMessageResponse(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }

    protected ResponseEntity<UIErrorMessage> getMessageResponse(HttpStatus status, String message) {
        // OK
        return new ResponseEntity<>(new UIErrorMessage(status.value(), message), status);
    }
}
