package dev.lucasvital.auth.login;

import dev.lucasvital.auth.user.User;
import dev.lucasvital.auth.user.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class RefreshController {

    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    public RefreshController(
            RefreshTokenService refreshTokenService,
            UserRepository userRepository,
            JwtService jwtService) {
        this.refreshTokenService = refreshTokenService;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        RefreshToken refreshToken =
                refreshTokenService
                        .findValid(request.refreshToken())
                        .orElseThrow(InvalidRefreshTokenException::new);

        // Defensivo: hoje inalcancavel — refresh_tokens.user_id tem FK NOT NULL para users
        // sem cascade, entao nao ha como excluir um usuario com refresh token ativo.
        User user =
                userRepository
                        .findById(refreshToken.getUserId())
                        .orElseThrow(InvalidRefreshTokenException::new);

        String accessToken = jwtService.generateAccessToken(user);

        return ResponseEntity.ok(new RefreshResponse(accessToken));
    }
}
