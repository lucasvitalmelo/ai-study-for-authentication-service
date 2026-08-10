package dev.lucasvital.auth.user;

import dev.lucasvital.auth.validation.ValidEmail;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterUserRequest(
        @ValidEmail @Size(max = 255) String email, @NotBlank String password) {}
