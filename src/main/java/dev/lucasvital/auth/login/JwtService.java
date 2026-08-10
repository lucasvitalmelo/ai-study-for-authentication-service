package dev.lucasvital.auth.login;

import dev.lucasvital.auth.user.CurrentUser;
import dev.lucasvital.auth.user.InvalidAccessTokenException;
import dev.lucasvital.auth.user.Role;
import dev.lucasvital.auth.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtService {

    private final SecretKey key;
    private final Duration accessTokenTtl;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-ttl}") String accessTokenTtl) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = Duration.parse(accessTokenTtl);
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public CurrentUser parseAccessToken(String accessToken) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(accessToken).getPayload();

            String subject = claims.getSubject();
            String roleClaim = claims.get("role", String.class);

            if (subject == null || roleClaim == null) {
                throw new InvalidAccessTokenException();
            }

            return new CurrentUser(Long.valueOf(subject), Role.valueOf(roleClaim));
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidAccessTokenException();
        }
    }
}
