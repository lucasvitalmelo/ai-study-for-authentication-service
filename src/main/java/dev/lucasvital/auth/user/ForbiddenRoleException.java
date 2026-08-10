package dev.lucasvital.auth.user;

public class ForbiddenRoleException extends RuntimeException {

    public ForbiddenRoleException() {
        super("Acesso negado para o seu papel");
    }
}
