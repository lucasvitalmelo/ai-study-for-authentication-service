package dev.lucasvital.auth.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RegisterUserRequest(
        @NotBlank @Email String email, @NotBlank String password) {}
