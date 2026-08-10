package dev.lucasvital.auth.login;

import dev.lucasvital.auth.user.User;
import dev.lucasvital.auth.user.UserRepository;
import jakarta.validation.Valid;
import java.util.Locale;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class LoginController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final String dummyPasswordHash;

    public LoginController(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        // Hash fixo so para a comparacao pagar o mesmo custo de BCrypt quando o e-mail nao
        // existe, evitando que o tempo de resposta revele se o e-mail esta cadastrado.
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-para-mitigar-timing-attack");
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        Optional<User> user = userRepository.findByEmail(request.email().toLowerCase(Locale.ROOT));

        String hashToCheck = user.map(User::getPasswordHash).orElse(dummyPasswordHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hashToCheck);

        if (user.isEmpty() || !passwordMatches) {
            throw new InvalidCredentialsException();
        }

        User authenticatedUser = user.get();
        String accessToken = jwtService.generateAccessToken(authenticatedUser);
        String refreshToken = refreshTokenService.issue(authenticatedUser.getId());

        return ResponseEntity.ok(new LoginResponse(accessToken, refreshToken));
    }
}
