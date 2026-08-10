package dev.lucasvital.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Testcontainers
class LogoutTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired private TestRestTemplate restTemplate;

    @BeforeEach
    void useApacheHttpClient() {
        restTemplate.getRestTemplate().setRequestFactory(new HttpComponentsClientHttpRequestFactory());
    }

    @Test
    void logoutWithValidToken_returns204AndRevokesTokenSoRefreshStopsWorking() throws Exception {
        String email = "logout.valido@example.com";
        String password = "senha-valida-123";
        ObjectMapper objectMapper = new ObjectMapper();

        restTemplate.postForEntity(
                "/auth/register", Map.of("email", email, "password", password), Void.class);

        JsonNode loginBody =
                objectMapper.readTree(
                        restTemplate
                                .postForEntity(
                                        "/auth/login",
                                        Map.of("email", email, "password", password),
                                        String.class)
                                .getBody());
        String refreshToken = loginBody.get("refreshToken").asText();

        var logoutResponse =
                restTemplate.postForEntity(
                        "/auth/logout", Map.of("refreshToken", refreshToken), Void.class);

        assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        var refreshResponse =
                restTemplate.postForEntity(
                        "/auth/refresh", Map.of("refreshToken", refreshToken), String.class);

        assertThat(refreshResponse.getStatusCode())
                .as("refresh token revogado nao deve mais funcionar em /auth/refresh")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void logoutWithNonExistentToken_returns204Idempotently() {
        var response =
                restTemplate.postForEntity(
                        "/auth/logout",
                        Map.of("refreshToken", "token-que-nunca-existiu"),
                        Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void logoutWithExpiredToken_returns204IdempotentlyAndRemovesRow() throws Exception {
        String email = "logout.expirado@example.com";
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

        var response =
                restTemplate.postForEntity(
                        "/auth/logout", Map.of("refreshToken", expiredToken), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        try (Connection connection = postgres.createConnection("");
                PreparedStatement statement =
                        connection.prepareStatement(
                                "select count(*) from refresh_tokens where token_hash = ?")) {
            statement.setString(1, expiredTokenHash);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1))
                        .as("linha do token expirado deve ser removida pelo logout")
                        .isEqualTo(0);
            }
        }
    }

    @Test
    void logoutWithBlankToken_returns400AsProblemDetail() throws Exception {
        var response =
                restTemplate.postForEntity("/auth/logout", Map.of("refreshToken", ""), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(org.springframework.http.MediaType.valueOf("application/problem+json"));

        JsonNode body = new ObjectMapper().readTree(response.getBody());
        assertThat(body.get("status").asInt()).isEqualTo(400);
        assertThat(body.get("title").asText()).isEqualTo("Dados de entrada inválidos");
        assertThat(body.get("detail").asText()).contains("refreshToken");
    }
}
