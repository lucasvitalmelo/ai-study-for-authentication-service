package dev.lucasvital.auth.user;

import dev.lucasvital.auth.validation.ValidEmail;
import dev.lucasvital.auth.validation.ValidPassword;
import jakarta.validation.constraints.Size;

public record RegisterUserRequest(
        @ValidEmail @Size(max = 255) String email,
        @ValidPassword @Size(max = 72) String password) {}
