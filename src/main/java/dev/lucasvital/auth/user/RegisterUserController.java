package dev.lucasvital.auth.user;

import jakarta.validation.Valid;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class RegisterUserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public RegisterUserController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterUserRequest request) {
        String email = request.email().toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException(email);
        }

        User user = new User(email, passwordEncoder.encode(request.password()), Role.USER);

        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            // corrida: outra requisicao concorrente registrou o mesmo e-mail entre o
            // existsByEmail acima e este save; a constraint unica do banco e quem pega.
            throw new EmailAlreadyRegisteredException(email);
        }

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
