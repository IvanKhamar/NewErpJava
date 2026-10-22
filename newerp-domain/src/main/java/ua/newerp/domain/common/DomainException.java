package ua.newerp.domain.common;

/**
 * A business rule was broken. The API turns it into a 400 response.
 * It extends RuntimeException, so it is "unchecked": callers do not have to declare or catch it.
 */
public class DomainException extends RuntimeException {

    public DomainException(String message) {
        super(message);
    }
}
