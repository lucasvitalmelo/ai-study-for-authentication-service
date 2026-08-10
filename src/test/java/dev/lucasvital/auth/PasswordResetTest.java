package dev.lucasvital.auth;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.lucasvital.auth.passwordreset.PasswordResetTokenRepository;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
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
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;

    private final Logger passwordResetServiceLogger =
            (Logger) LoggerFactory.getLogger("dev.lucasvital.auth.passwordreset.PasswordResetService");
    private ListAppender<ILoggingEvent> attachedAppender;

    @BeforeEach
    void useApacheHttpClient() {
        restTemplate.getRestTemplate().setRequestFactory(new HttpComponentsClientHttpRequestFactory());
    }

    @AfterEach
    void detachLogAppender() {
        if (attachedAppender != null) {
            passwordResetServiceLogger.detachAppender(attachedAppender);
            attachedAppender.stop();
        }
    }

    private ListAppender<ILoggingEvent> attachLogAppender() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        passwordResetServiceLogger.addAppender(appender);
        attachedAppender = appender;
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
    void passwordResetRequestWithExistingEmail_returns202AndLogsToken() throws Exception {
        String email = "reset.solicitar@example.com";
        String password = "senha-valida-123";

        restTemplate.postForEntity(
                "/auth/register", Map.of("email", email, "password", password), Void.class);

        ListAppender<ILoggingEvent> appender = attachLogAppender();

        ResponseEntity<Void> response =
                restTemplate.postForEntity(
                        "/auth/password-reset", Map.of("email", email), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        String token = extractLoggedToken(appender);
        assertThat(token).isNotBlank();

        String tokenHash =
                HexFormat.of()
                        .formatHex(
                                MessageDigest.getInstance("SHA-256")
                                        .digest(token.getBytes(StandardCharsets.UTF_8)));

        try (Connection connection = postgres.createConnection("");
                PreparedStatement statement =
                        connection.prepareStatement(
                                "select token_hash from password_reset_tokens where token_hash = ?")) {
            statement.setString(1, tokenHash);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next())
                        .as("token deve estar persistido pelo hash, nao pelo valor em texto puro")
                        .isTrue();
                assertThat(resultSet.getString("token_hash"))
                        .as("hash persistido nao deve ser o token em texto puro")
                        .isNotEqualTo(token);
            }
        }

        // Le via o repositorio (mesmo caminho que a aplicacao usa) em vez de JDBC cru: uma
        // coluna TIMESTAMP sem fuso lida por JDBC puro reinterpreta os digitos gravados pelo
        // Hibernate (em UTC) usando o fuso local, introduzindo um desvio artificial de horas
        // que nao existe no caminho real da aplicacao.
        Instant expiresAt =
                passwordResetTokenRepository.findByTokenHash(tokenHash).orElseThrow().getExpiresAt();
        assertThat(expiresAt)
                .as("TTL de 15 minutos")
                .isAfter(Instant.now().plus(14, ChronoUnit.MINUTES))
                .isBefore(Instant.now().plus(16, ChronoUnit.MINUTES));
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
    void passwordResetConfirmWithValidToken_invalidatesOtherPendingResetTokensOfSameUser() {
        String email = "reset.multiplos-tokens@example.com";
        String oldPassword = "senha-antiga-123";

        restTemplate.postForEntity(
                "/auth/register", Map.of("email", email, "password", oldPassword), Void.class);

        ListAppender<ILoggingEvent> appender = attachLogAppender();

        restTemplate.postForEntity("/auth/password-reset", Map.of("email", email), Void.class);
        String firstToken = extractLoggedToken(appender);
        appender.list.clear();

        restTemplate.postForEntity("/auth/password-reset", Map.of("email", email), Void.class);
        String secondToken = extractLoggedToken(appender);

        restTemplate.postForEntity(
                "/auth/password-reset/confirm",
                Map.of("token", firstToken, "newPassword", "senha-nova-456"),
                Void.class);

        ResponseEntity<String> confirmWithSiblingToken =
                restTemplate.postForEntity(
                        "/auth/password-reset/confirm",
                        Map.of("token", secondToken, "newPassword", "outra-senha-789"),
                        String.class);

        assertThat(confirmWithSiblingToken.getStatusCode())
                .as("token de reset irmao, pendente, deve ser invalidado quando outro e usado")
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

    @Test
    void passwordResetConfirmWithBlankNewPassword_returns400AsProblemDetail() throws Exception {
        ResponseEntity<String> response =
                restTemplate.postForEntity(
                        "/auth/password-reset/confirm",
                        Map.of("token", "qualquer-token", "newPassword", ""),
                        String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.valueOf("application/problem+json"));

        JsonNode body = new ObjectMapper().readTree(response.getBody());
        assertThat(body.get("status").asInt()).isEqualTo(400);
        assertThat(body.get("title").asText()).isEqualTo("Dados de entrada inválidos");
        assertThat(body.get("detail").asText()).contains("newPassword");
    }

    @Test
    void passwordResetConfirmConcurrentRequestsWithSameToken_onlyOneSucceeds() throws Exception {
        String email = "reset.concorrencia@example.com";
        String oldPassword = "senha-antiga-123";

        restTemplate.postForEntity(
                "/auth/register", Map.of("email", email, "password", oldPassword), Void.class);

        ListAppender<ILoggingEvent> appender = attachLogAppender();
        restTemplate.postForEntity("/auth/password-reset", Map.of("email", email), Void.class);
        String token = extractLoggedToken(appender);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);

            List<Future<org.springframework.http.HttpStatusCode>> futures =
                    List.of(
                            executor.submit(() -> attemptConfirm(token, "senha-concorrente-1", ready, go)),
                            executor.submit(() -> attemptConfirm(token, "senha-concorrente-2", ready, go)));

            ready.await();
            go.countDown();

            List<org.springframework.http.HttpStatusCode> results = new java.util.ArrayList<>();
            for (Future<org.springframework.http.HttpStatusCode> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }

            assertThat(results)
                    .as("exatamente uma das duas requisicoes concorrentes deve consumir o token")
                    .containsExactlyInAnyOrder(HttpStatus.NO_CONTENT, HttpStatus.UNAUTHORIZED);
        } finally {
            executor.shutdownNow();
        }
    }

    private org.springframework.http.HttpStatusCode attemptConfirm(
            String token, String newPassword, CountDownLatch ready, CountDownLatch go)
            throws InterruptedException {
        ready.countDown();
        go.await();
        return restTemplate
                .postForEntity(
                        "/auth/password-reset/confirm",
                        Map.of("token", token, "newPassword", newPassword),
                        Void.class)
                .getStatusCode();
    }
}
