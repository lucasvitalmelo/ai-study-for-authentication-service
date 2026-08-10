package dev.lucasvital.auth.passwordreset;

public class InvalidPasswordResetTokenException extends RuntimeException {

    public InvalidPasswordResetTokenException() {
        super("Token de reset de senha inválido");
    }
}
