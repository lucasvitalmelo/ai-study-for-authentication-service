package dev.lucasvital.auth.passwordreset;

import dev.lucasvital.auth.validation.ValidEmail;

public record PasswordResetRequest(@ValidEmail String email) {}
