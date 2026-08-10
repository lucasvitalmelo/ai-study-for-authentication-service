package dev.lucasvital.auth.login;

import dev.lucasvital.auth.security.OpaqueTokenGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final OpaqueTokenGenerator opaqueTokenGenerator;
    private final Duration refreshTokenTtl;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            OpaqueTokenGenerator opaqueTokenGenerator,
            @Value("${app.jwt.refresh-token-ttl}") String refreshTokenTtl) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.opaqueTokenGenerator = opaqueTokenGenerator;
        this.refreshTokenTtl = Duration.parse(refreshTokenTtl);
    }

    public String issue(Long userId) {
        String token = opaqueTokenGenerator.generate();

        refreshTokenRepository.save(
                new RefreshToken(
                        userId, opaqueTokenGenerator.hash(token), Instant.now().plus(refreshTokenTtl)));

        return token;
    }

    public Optional<RefreshToken> findValid(String token) {
        return refreshTokenRepository
                .findByTokenHash(opaqueTokenGenerator.hash(token))
                .filter(refreshToken -> refreshToken.getExpiresAt().isAfter(Instant.now()));
    }

    public void revoke(String token) {
        refreshTokenRepository.deleteByTokenHash(opaqueTokenGenerator.hash(token));
    }
}
