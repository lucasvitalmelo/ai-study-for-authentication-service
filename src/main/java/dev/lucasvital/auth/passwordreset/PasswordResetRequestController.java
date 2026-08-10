package dev.lucasvital.auth.passwordreset;

import dev.lucasvital.auth.user.User;
import dev.lucasvital.auth.user.UserRepository;
import jakarta.validation.Valid;
import java.util.Locale;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class PasswordResetRequestController {

    private final UserRepository userRepository;
    private final PasswordResetService passwordResetService;

    public PasswordResetRequestController(
            UserRepository userRepository, PasswordResetService passwordResetService) {
        this.userRepository = userRepository;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/password-reset")
    public ResponseEntity<Void> requestReset(@Valid @RequestBody PasswordResetRequest request) {
        String email = request.email().toLowerCase(Locale.ROOT);
        Optional<User> user = userRepository.findByEmail(email);

        user.ifPresent(existingUser -> passwordResetService.issue(existingUser.getId(), email));

        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
