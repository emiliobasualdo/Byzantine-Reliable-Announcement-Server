package pt.tecnico.model;

/**
 * Exception thrown in case of a bad response
 */
public class BadResponseException extends Exception {
    public BadResponseException(String message) {
        super(message);
    }
}