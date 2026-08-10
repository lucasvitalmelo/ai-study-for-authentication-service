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
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Testcontainers
class RefreshTokenTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired private TestRestTemplate restTemplate;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @BeforeEach
    void useApacheHttpClient() {
        restTemplate.getRestTemplate().setRequestFactory(new HttpComponentsClientHttpRequestFactory());
    }

    @Test
    void refreshWithValidToken_returns200WithNewAccessTokenAndKeepsRefreshTokenUnchanged()
            throws Exception {
        String email = "refresh.valido@example.com";
        String password = "senha-valida-123";
        ObjectMapper objectMapper = new ObjectMapper();

        restTemplate.postForEntity(
                "/auth/register", Map.of("email", email, "password", password), Void.class);

        ResponseEntity<String> loginResponse =
                restTemplate.postForEntity(
                        "/auth/login", Map.of("email", email, "password", password), String.class);
        JsonNode loginBody = objectMapper.readTree(loginResponse.getBody());
        String refreshToken = loginBody.get("refreshToken").asText();

        String refreshTokenHash =
                HexFormat.of()
                        .formatHex(
                                MessageDigest.getInstance("SHA-256")
                                        .digest(refreshToken.getBytes(StandardCharsets.UTF_8)));

        ResponseEntity<String> refreshResponse =
                restTemplate.postForEntity(
                        "/auth/refresh", Map.of("refreshToken", refreshToken), String.class);

        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode refreshBody = objectMapper.readTree(refreshResponse.getBody());
        String newAccessToken = refreshBody.get("accessToken").asText();
        assertThat(newAccessToken).isNotBlank();

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
        Jws<Claims> jws = Jwts.parser().verifyWith(key).build().parseSignedClaims(newAccessToken);
        Claims claims = jws.getPayload();

        assertThat(jws.getHeader().getAlgorithm()).isEqualTo("HS256");
        assertThat(claims.getSubject()).isEqualTo(String.valueOf(userId));
        assertThat(claims.get("role", String.class)).isEqualTo("USER");
        assertThat(claims.getExpiration().toInstant())
                .isAfter(Instant.now())
                .isBefore(Instant.now().plus(16, ChronoUnit.MINUTES));

        try (Connection connection = postgres.createConnection("");
                PreparedStatement statement =
                        connection.prepareStatement(
                                "select count(*) from refresh_tokens where token_hash = ?")) {
            statement.setString(1, refreshTokenHash);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1))
                        .as("refresh token original nao deve ser rotacionado")
                        .isEqualTo(1);
            }
        }
    }

    @Test
    void refreshWithNonExistentToken_returns401AsProblemDetail() throws Exception {
        ResponseEntity<String> response =
                restTemplate.postForEntity(
                        "/auth/refresh",
                        Map.of("refreshToken", "token-que-nunca-existiu"),
                        String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.valueOf("application/problem+json"));

        JsonNode body = new ObjectMapper().readTree(response.getBody());
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("detail").asText()).isEqualTo("Refresh token inválido");
    }

    @Test
    void refreshWithExpiredToken_returns401AsProblemDetailWithSameMessageAsNonExistentToken()
            throws Exception {
        String email = "refresh.expirado@example.com";
        String password = "senha-valida-123";

        restTemplate.postForEntity(
                "/auth/register", Map.of("email", email, "password", password), Void.class);

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

        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        String expiredToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String expiredTokenHash =
                HexFormat.of()
                        .formatHex(
                                MessageDigest.getInstance("SHA-256")
                                        .digest(expiredToken.getBytes(StandardCharsets.UTF_8)));

        try (Connection connection = postgres.createConnection("");
                PreparedStatement statement =
                        connection.prepareStatement(
                                "insert into refresh_tokens (user_id, token_hash, expires_at)"
                                        + " values (?, ?, ?)")) {
            statement.setLong(1, userId);
            statement.setString(2, expiredTokenHash);
            statement.setTimestamp(3, Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS)));
            statement.executeUpdate();
        }

        ResponseEntity<String> response =
                restTemplate.postForEntity(
                        "/auth/refresh", Map.of("refreshToken", expiredToken), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.valueOf("application/problem+json"));

        JsonNode body = new ObjectMapper().readTree(response.getBody());
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("detail").asText()).isEqualTo("Refresh token inválido");
    }

    @Test
    void refreshWithBlankToken_returns400AsProblemDetail() throws Exception {
        ResponseEntity<String> response =
                restTemplate.postForEntity(
                        "/auth/refresh", Map.of("refreshToken", ""), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.valueOf("application/problem+json"));

        JsonNode body = new ObjectMapper().readTree(response.getBody());
        assertThat(body.get("status").asInt()).isEqualTo(400);
        assertThat(body.get("title").asText()).isEqualTo("Dados de entrada inválidos");
        assertThat(body.get("detail").asText()).contains("refreshToken");
    }

    @Test
    void loginPersistsRefreshTokenExpiresAtReadableCorrectlyViaRawJdbc_regardlessOfSessionTimeZone()
            throws Exception {
        // issue #20: refresh_tokens.expires_at era TIMESTAMP sem timezone; lido via JDBC cru
        // (sem Calendar/fuso explicito) o valor vinha deslocado pelo offset do fuso local da
        // sessao. Le via JDBC cru de proposito, sem passar pelo Hibernate, para provar que a
        // leitura externa tambem esta correta apos a migration para TIMESTAMPTZ.
        String email = "refresh-fuso@example.com";
        String password = "senha-valida-123";

        restTemplate.postForEntity(
                "/auth/register", Map.of("email", email, "password", password), Void.class);
        restTemplate.postForEntity(
                "/auth/login", Map.of("email", email, "password", password), String.class);

        try (Connection connection = postgres.createConnection("");
                PreparedStatement statement =
                        connection.prepareStatement(
                                "select rt.expires_at from refresh_tokens rt"
                                        + " join users u on u.id = rt.user_id"
                                        + " where u.email = ?")) {
            statement.setString(1, email);

            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                Instant expiresAt = resultSet.getTimestamp("expires_at").toInstant();

                assertThat(expiresAt)
                        .isAfter(Instant.now().plus(7 * 24 * 60 - 1, ChronoUnit.MINUTES))
                        .isBefore(Instant.now().plus(7 * 24 * 60 + 1, ChronoUnit.MINUTES));
            }
        }
    }
}
