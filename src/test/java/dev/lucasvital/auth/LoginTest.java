package dev.lucasvital.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Testcontainers
class LoginTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired private TestRestTemplate restTemplate;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Test
    void loginWithValidCredentials_returns200WithAccessAndRefreshTokens() throws Exception {
        String email = "login.valido@example.com";
        String password = "senha-valida-123";

        restTemplate.postForEntity(
                "/auth/register", Map.of("email", email, "password", password), Void.class);

        ResponseEntity<String> response =
                restTemplate.postForEntity(
                        "/auth/login", Map.of("email", email, "password", password), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = new ObjectMapper().readTree(response.getBody());
        String accessToken = body.get("accessToken").asText();
        String refreshToken = body.get("refreshToken").asText();

        assertThat(accessToken).isNotBlank();
        assertThat(refreshToken).isNotBlank();

        long userId;
        try (Connection connection = postgres.createConnection("");
                PreparedStatement statement =
                        connection.prepareStatement("select id from users where email = ?")) {
            statement.setString(1, email);

            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                userId = resultSet.getLong("id");
            }
        }

        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Jws<Claims> jws = Jwts.parser().verifyWith(key).build().parseSignedClaims(accessToken);
        Claims claims = jws.getPayload();

        assertThat(jws.getHeader().getAlgorithm()).isEqualTo("HS256");
        assertThat(claims.getSubject()).isEqualTo(String.valueOf(userId));
        assertThat(claims.get("role", String.class)).isEqualTo("USER");
        assertThat(claims.getExpiration().toInstant())
                .isAfter(Instant.now())
                .isBefore(Instant.now().plus(16, ChronoUnit.MINUTES));

        String refreshTokenHash =
                HexFormat.of()
                        .formatHex(
                                MessageDigest.getInstance("SHA-256")
                                        .digest(refreshToken.getBytes(StandardCharsets.UTF_8)));

        try (Connection connection = postgres.createConnection("");
                PreparedStatement statement =
                        connection.prepareStatement(
                                "select expires_at from refresh_tokens"
                                        + " where user_id = ? and token_hash = ?")) {
            statement.setLong(1, userId);
            statement.setString(2, refreshTokenHash);

            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();

                Timestamp expiresAt = resultSet.getTimestamp("expires_at");
                assertThat(expiresAt.toInstant())
                        .isAfter(Instant.now().plus(6, ChronoUnit.DAYS))
                        .isBefore(Instant.now().plus(8, ChronoUnit.DAYS));
            }
        }
    }
}
