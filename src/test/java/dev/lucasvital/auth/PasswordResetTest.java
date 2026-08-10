package dev.lucasvital.auth;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
class PasswordResetTest {

    private static final Pattern TOKEN_LOG_PATTERN = Pattern.compile("token=(\\S+)");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired private TestRestTemplate restTemplate;

    @BeforeEach
    void useApacheHttpClient() {
        restTemplate.getRestTemplate().setRequestFactory(new HttpComponentsClientHttpRequestFactory());
    }

    private ListAppender<ILoggingEvent> attachLogAppender() {
        Logger logger =
                (Logger) LoggerFactory.getLogger("dev.lucasvital.auth.passwordreset.PasswordResetService");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private String extractLoggedToken(ListAppender<ILoggingEvent> appender) {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .map(TOKEN_LOG_PATTERN::matcher)
                .filter(Matcher::find)
                .map(matcher -> matcher.group(1))
                .findFirst()
                .orElseThrow(() -> new AssertionError("nenhum token de reset foi logado"));
    }

    @Test
    void passwordResetRequestWithExistingEmail_returns202AndLogsToken() {
        String email = "reset.solicitar@example.com";
        String password = "senha-valida-123";

        restTemplate.postForEntity(
                "/auth/register", Map.of("email", email, "password", password), Void.class);

        ListAppender<ILoggingEvent> appender = attachLogAppender();

        ResponseEntity<Void> response =
                restTemplate.postForEntity(
                        "/auth/password-reset", Map.of("email", email), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(extractLoggedToken(appender)).isNotBlank();
    }

    @Test
    void passwordResetRequestWithNonExistentEmail_returns202SameAsExistingWithoutLoggingToken() {
        ListAppender<ILoggingEvent> appender = attachLogAppender();

        ResponseEntity<Void> response =
                restTemplate.postForEntity(
                        "/auth/password-reset",
                        Map.of("email", "nao.cadastrado@example.com"),
                        Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(appender.list)
                .as("nenhum token deve ser gerado/logado para e-mail nao cadastrado")
                .isEmpty();
    }

    @Test
    void passwordResetRequestWithBlankEmail_returns400AsProblemDetail() throws Exception {
        ResponseEntity<String> response =
                restTemplate.postForEntity(
                        "/auth/password-reset", Map.of("email", ""), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.valueOf("application/problem+json"));

        JsonNode body = new ObjectMapper().readTree(response.getBody());
        assertThat(body.get("status").asInt()).isEqualTo(400);
        assertThat(body.get("title").asText()).isEqualTo("Dados de entrada inválidos");
        assertThat(body.get("detail").asText()).contains("email");
    }

    @Test
    void passwordResetConfirmWithValidToken_returns204UpdatesPasswordConsumesTokenAndRevokesRefreshTokens()
            throws Exception {
        String email = "reset.confirmar@example.com";
        String oldPassword = "senha-antiga-123";
        String newPassword = "senha-nova-456";
        ObjectMapper objectMapper = new ObjectMapper();

        restTemplate.postForEntity(
                "/auth/register", Map.of("email", email, "password", oldPassword), Void.class);

        JsonNode loginBody =
                objectMapper.readTree(
                        restTemplate
                                .postForEntity(
                                        "/auth/login",
                                        Map.of("email", email, "password", oldPassword),
                                        String.class)
                                .getBody());
        String oldRefreshToken = loginBody.get("refreshToken").asText();

        ListAppender<ILoggingEvent> appender = attachLogAppender();
        restTemplate.postForEntity("/auth/password-reset", Map.of("email", email), Void.class);
        String resetToken = extractLoggedToken(appender);

        ResponseEntity<Void> confirmResponse =
                restTemplate.postForEntity(
                        "/auth/password-reset/confirm",
                        Map.of("token", resetToken, "newPassword", newPassword),
                        Void.class);

        assertThat(confirmResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> loginWithOldPassword =
                restTemplate.postForEntity(
                        "/auth/login", Map.of("email", email, "password", oldPassword), String.class);
        assertThat(loginWithOldPassword.getStatusCode())
                .as("senha antiga nao deve mais funcionar")
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> loginWithNewPassword =
                restTemplate.postForEntity(
                        "/auth/login", Map.of("email", email, "password", newPassword), String.class);
        assertThat(loginWithNewPassword.getStatusCode())
                .as("nova senha deve funcionar")
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<String> refreshWithOldToken =
                restTemplate.postForEntity(
                        "/auth/refresh", Map.of("refreshToken", oldRefreshToken), String.class);
        assertThat(refreshWithOldToken.getStatusCode())
                .as("refresh tokens anteriores ao reset devem ser revogados")
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<Void> reuseResetToken =
                restTemplate.postForEntity(
                        "/auth/password-reset/confirm",
                        Map.of("token", resetToken, "newPassword", "outra-senha-789"),
                        Void.class);
        assertThat(reuseResetToken.getStatusCode())
                .as("token de reset ja usado nao pode ser reutilizado")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void passwordResetConfirmWithNonExistentToken_returns401AsProblemDetail() throws Exception {
        ResponseEntity<String> response =
                restTemplate.postForEntity(
                        "/auth/password-reset/confirm",
                        Map.of("token", "token-que-nunca-existiu", "newPassword", "senha-valida-123"),
                        String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.valueOf("application/problem+json"));

        JsonNode body = new ObjectMapper().readTree(response.getBody());
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("detail").asText()).isEqualTo("Token de reset de senha inválido");
    }

    @Test
    void passwordResetConfirmWithExpiredToken_returns401AsProblemDetail() throws Exception {
        String email = "reset.expirado@example.com";
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
                                "insert into password_reset_tokens (user_id, token_hash, expires_at)"
                                        + " values (?, ?, ?)")) {
            statement.setLong(1, userId);
            statement.setString(2, expiredTokenHash);
            statement.setTimestamp(3, Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS)));
            statement.executeUpdate();
        }

        ResponseEntity<String> response =
                restTemplate.postForEntity(
                        "/auth/password-reset/confirm",
                        Map.of("token", expiredToken, "newPassword", "senha-nova-456"),
                        String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.valueOf("application/problem+json"));

        JsonNode body = new ObjectMapper().readTree(response.getBody());
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("detail").asText()).isEqualTo("Token de reset de senha inválido");
    }
}
