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
class LoginTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired private TestRestTemplate restTemplate;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @BeforeEach
    void useApacheHttpClient() {
        // O HttpURLConnection padrao do JDK (usado pelo SimpleClientHttpRequestFactory) quebra
        // com "cannot retry due to server authentication, in streaming mode" ao ler uma resposta
        // 401 de um POST — ele sempre usa setFixedLengthStreamingMode internamente, mesmo com
        // outputStreaming=false. Troca pro Apache HttpClient, que nao tem esse problema. E'
        // questao do client HTTP do teste, nao da aplicacao.
        restTemplate.getRestTemplate().setRequestFactory(new HttpComponentsClientHttpRequestFactory());
    }

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

    @Test
    void loginWithNonExistentEmail_returns401AsProblemDetail() throws Exception {
        ResponseEntity<String> response =
                restTemplate.postForEntity(
                        "/auth/login",
                        Map.of("email", "inexistente@example.com", "password", "qualquer-senha"),
                        String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.valueOf("application/problem+json"));

        JsonNode body = new ObjectMapper().readTree(response.getBody());
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("detail").asText()).isEqualTo("Credenciais inválidas");
    }

    @Test
    void loginWithWrongPassword_returns401AsProblemDetailWithSameMessageAsNonExistentEmail()
            throws Exception {
        String email = "login.senha.errada@example.com";

        restTemplate.postForEntity(
                "/auth/register",
                Map.of("email", email, "password", "senha-correta-123"),
                Void.class);

        ResponseEntity<String> response =
                restTemplate.postForEntity(
                        "/auth/login",
                        Map.of("email", email, "password", "senha-errada-456"),
                        String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.valueOf("application/problem+json"));

        JsonNode body = new ObjectMapper().readTree(response.getBody());
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("detail").asText()).isEqualTo("Credenciais inválidas");
    }

    @Test
    void loginWithNonExistentEmail_paysSameBcryptCostAsWrongPassword() throws Exception {
        // Se o path de e-mail inexistente pular a verificacao de senha (BCrypt leva
        // algumas dezenas de ms), a resposta volta quase instantanea — revelando por
        // timing que o e-mail nao existe, mesmo com o corpo da resposta identico.
        long start = System.nanoTime();

        restTemplate.postForEntity(
                "/auth/login",
                Map.of("email", "sem-essa-conta@example.com", "password", "qualquer-senha"),
                String.class);

        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMillis).isGreaterThanOrEqualTo(20);
    }

    @Test
    void loginWithInvalidEmailFormat_returns400AsProblemDetail() throws Exception {
        ResponseEntity<String> response =
                restTemplate.postForEntity(
                        "/auth/login",
                        Map.of("email", "email-invalido", "password", "qualquer-senha"),
                        String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.valueOf("application/problem+json"));

        JsonNode body = new ObjectMapper().readTree(response.getBody());
        assertThat(body.get("status").asInt()).isEqualTo(400);
        assertThat(body.get("title").asText()).isEqualTo("Dados de entrada inválidos");
        assertThat(body.get("detail").asText()).contains("email");
    }

    @Test
    void loginWithBlankPassword_returns400AsProblemDetail() throws Exception {
        ResponseEntity<String> response =
                restTemplate.postForEntity(
                        "/auth/login",
                        Map.of("email", "alguem@example.com", "password", ""),
                        String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.valueOf("application/problem+json"));

        JsonNode body = new ObjectMapper().readTree(response.getBody());
        assertThat(body.get("status").asInt()).isEqualTo(400);
        assertThat(body.get("title").asText()).isEqualTo("Dados de entrada inválidos");
        assertThat(body.get("detail").asText()).contains("password");
    }

    @Test
    void secondLoginDoesNotInvalidatePreviousRefreshToken() throws Exception {
        String email = "multisessao@example.com";
        String password = "senha-valida-123";

        restTemplate.postForEntity(
                "/auth/register", Map.of("email", email, "password", password), Void.class);

        ObjectMapper objectMapper = new ObjectMapper();

        ResponseEntity<String> firstLogin =
                restTemplate.postForEntity(
                        "/auth/login", Map.of("email", email, "password", password), String.class);
        ResponseEntity<String> secondLogin =
                restTemplate.postForEntity(
                        "/auth/login", Map.of("email", email, "password", password), String.class);

        String firstRefreshToken =
                objectMapper.readTree(firstLogin.getBody()).get("refreshToken").asText();
        String secondRefreshToken =
                objectMapper.readTree(secondLogin.getBody()).get("refreshToken").asText();

        assertThat(firstRefreshToken).isNotEqualTo(secondRefreshToken);

        String firstTokenHash =
                HexFormat.of()
                        .formatHex(
                                MessageDigest.getInstance("SHA-256")
                                        .digest(firstRefreshToken.getBytes(StandardCharsets.UTF_8)));

        try (Connection connection = postgres.createConnection("");
                PreparedStatement statement =
                        connection.prepareStatement(
                                "select count(*) from refresh_tokens where token_hash = ?")) {
            statement.setString(1, firstTokenHash);

            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1))
                        .as("primeiro refresh token deve continuar na tabela apos o 2o login")
                        .isEqualTo(1);
            }
        }
    }
}
