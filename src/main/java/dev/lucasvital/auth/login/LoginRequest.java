package dev.lucasvital.auth.login;

import dev.lucasvital.auth.validation.ValidEmail;
import dev.lucasvital.auth.validation.ValidPassword;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @ValidEmail @Size(max = 255) String email, @ValidPassword String password) {}
