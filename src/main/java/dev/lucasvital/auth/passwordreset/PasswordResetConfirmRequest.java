package dev.lucasvital.auth.passwordreset;

import dev.lucasvital.auth.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;

public record PasswordResetConfirmRequest(
        @NotBlank String token, @ValidPassword String newPassword) {}
