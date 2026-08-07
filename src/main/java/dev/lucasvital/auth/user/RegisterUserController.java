package dev.lucasvital.auth.user;

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
    public ResponseEntity<Void> register(@RequestBody RegisterUserRequest request) {
        User user =
                new User(request.email(), passwordEncoder.encode(request.password()), Role.USER);
        userRepository.save(user);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
