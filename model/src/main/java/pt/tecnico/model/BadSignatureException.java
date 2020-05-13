package pt.tecnico.model;

/**
 * Exception thrown in case of a signature
 */
public class BadSignatureException extends Exception {
    public BadSignatureException(String message) {
        super(message);
    }
}
