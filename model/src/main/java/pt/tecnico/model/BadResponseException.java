package pt.tecnico.model;

public class BadResponseException extends Exception {
    public BadResponseException(String message) {
        super(message);
    }
}