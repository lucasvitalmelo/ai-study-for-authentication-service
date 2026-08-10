package dev.lucasvital.auth.login;

import dev.lucasvital.auth.validation.ValidEmail;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@ValidEmail String email, @NotBlank String password) {}
