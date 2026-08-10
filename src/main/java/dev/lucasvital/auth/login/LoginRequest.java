package dev.lucasvital.auth.login;

import dev.lucasvital.auth.validation.ValidEmail;
import dev.lucasvital.auth.validation.ValidPassword;

public record LoginRequest(@ValidEmail String email, @ValidPassword String password) {}
