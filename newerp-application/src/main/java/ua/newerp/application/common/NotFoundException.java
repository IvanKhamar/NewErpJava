package ua.newerp.application.common;

/** The requested record does not exist. The API turns it into a 404 response. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
