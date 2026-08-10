package dev.lucasvital.auth.user;

public class InvalidAccessTokenException extends RuntimeException {

    public InvalidAccessTokenException() {
        super("Token de acesso inválido ou ausente");
    }
}
