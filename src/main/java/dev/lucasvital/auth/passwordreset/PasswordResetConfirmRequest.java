package dev.lucasvital.auth.passwordreset;

import dev.lucasvital.auth.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequest(
        @NotBlank String token, @ValidPassword @Size(max = 72) String newPassword) {}
