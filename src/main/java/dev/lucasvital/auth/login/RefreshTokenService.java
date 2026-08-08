package dev.lucasvital.auth.login;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final Duration refreshTokenTtl;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${app.jwt.refresh-token-ttl}") String refreshTokenTtl) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenTtl = Duration.parse(refreshTokenTtl);
    }

    public String issue(Long userId) {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        refreshTokenRepository.save(
                new RefreshToken(userId, hash(token), Instant.now().plus(refreshTokenTtl)));

        return token;
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
