package dev.lucasvital.auth.user;

public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException(String email) {
        super("E-mail já cadastrado: " + email);
    }
}
