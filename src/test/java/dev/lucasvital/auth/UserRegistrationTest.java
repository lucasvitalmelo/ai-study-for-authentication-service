package dev.lucasvital.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Testcontainers
class UserRegistrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired private TestRestTemplate restTemplate;

    @Test
    void registerWithValidEmailAndPassword_returns201WithLocationAndBodyAndPersistsHashedPasswordWithUserRole()
            throws Exception {
        String email = "novo.usuario@example.com";
        String password = "senha-valida-123";

        ResponseEntity<String> response =
                restTemplate.postForEntity(
                        "/auth/register", Map.of("email", email, "password", password), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        long userId;
        try (Connection connection = postgres.createConnection("");
                PreparedStatement statement =
                        connection.prepareStatement(
                                "select id, password_hash, role from users where email = ?")) {
            statement.setString(1, email);

            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();

                userId = resultSet.getLong("id");
                String passwordHash = resultSet.getString("password_hash");
                String role = resultSet.getString("role");

                assertThat(passwordHash).isNotEqualTo(password);
                assertThat(new BCryptPasswordEncoder().matches(password, passwordHash)).isTrue();
                assertThat(role).isEqualTo("USER");
            }
        }

        assertThat(response.getHeaders().getLocation()).isEqualTo(URI.create("/users/" + userId));

        JsonNode body = new ObjectMapper().readTree(response.getBody());
        assertThat(body.get("id").asLong()).isEqualTo(userId);
        assertThat(body.get("email").asText()).isEqualTo(email);
        assertThat(body.get("role").asText()).isEqualTo("USER");
    }

    @Test
    void registerWithAlreadyRegisteredEmail_returns409AsProblemDetail() throws Exception {
        String email = "duplicado@example.com";

        restTemplate.postForEntity(
                "/auth/register", Map.of("email", email, "password", "senha-valida-123"), Void.class);

        ResponseEntity<String> response =
                restTemplate.postForEntity(
                        "/auth/register",
                        Map.of("email", email, "password", "outra-senha-456"),
                        String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.valueOf("application/problem+json"));

        JsonNode body = new ObjectMapper().readTree(response.getBody());
        assertThat(body.get("status").asInt()).isEqualTo(409);
        assertThat(body.get("title").asText()).isEqualTo("E-mail já cadastrado");
        assertThat(body.get("detail").asText()).contains(email);
    }

    @Test
    void registerWithInvalidEmailFormat_returns400AsProblemDetail() throws Exception {
        ResponseEntity<String> response =
                restTemplate.postForEntity(
                        "/auth/register",
                        Map.of("email", "email-invalido", "password", "senha-valida-123"),
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
    void registerWithBlankPassword_returns400AsProblemDetail() throws Exception {
        ResponseEntity<String> response =
                restTemplate.postForEntity(
                        "/auth/register",
                        Map.of("email", "outro.usuario@example.com", "password", ""),
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
    void registerWithEmailDifferingOnlyInCase_returns409AsProblemDetail() throws Exception {
        restTemplate.postForEntity(
                "/auth/register",
                Map.of("email", "user@example.com", "password", "senha-valida-123"),
                Void.class);

        ResponseEntity<String> response =
                restTemplate.postForEntity(
                        "/auth/register",
                        Map.of("email", "User@Example.com", "password", "outra-senha-456"),
                        String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.valueOf("application/problem+json"));

        JsonNode body = new ObjectMapper().readTree(response.getBody());
        assertThat(body.get("status").asInt()).isEqualTo(409);
        assertThat(body.get("title").asText()).isEqualTo("E-mail já cadastrado");
        assertThat(body.get("detail").asText()).contains("user@example.com");
    }

    @Test
    void concurrentRegistrationsWithSameEmail_neverReturn500() throws Exception {
        String email = "concorrente@example.com";
        int concurrentRequests = 5;

        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
        CountDownLatch ready = new CountDownLatch(concurrentRequests);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ResponseEntity<String>>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < concurrentRequests; i++) {
                futures.add(
                        executor.submit(
                                () -> {
                                    ready.countDown();
                                    start.await();
                                    return restTemplate.postForEntity(
                                            "/auth/register",
                                            Map.of("email", email, "password", "senha-valida-123"),
                                            String.class);
                                }));
            }

            ready.await();
            start.countDown();

            List<ResponseEntity<String>> responses = new ArrayList<>();
            for (Future<ResponseEntity<String>> future : futures) {
                responses.add(future.get(10, TimeUnit.SECONDS));
            }

            assertThat(responses)
                    .extracting(ResponseEntity::getStatusCode)
                    .doesNotContain(HttpStatus.INTERNAL_SERVER_ERROR);

            long createdCount =
                    responses.stream().filter(r -> r.getStatusCode() == HttpStatus.CREATED).count();
            assertThat(createdCount).isEqualTo(1);

            for (ResponseEntity<String> response : responses) {
                if (response.getStatusCode() != HttpStatus.CREATED) {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(response.getHeaders().getContentType())
                            .isEqualTo(MediaType.valueOf("application/problem+json"));
                }
            }
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void registerWithEmailLongerThanColumnLimit_returns400AsProblemDetail() throws Exception {
        // parte local com 50 chars (< 64, limite do Hibernate Validator para a parte local)
        // e dominio com ~247 chars (< 255, limite do Hibernate Validator para o dominio),
        // mas a soma dos dois estoura os 255 da coluna password_hash/email no Postgres.
        String localPart = "a".repeat(50);
        String label = "a".repeat(60);
        String email = localPart + "@" + label + "." + label + "." + label + "." + label + ".com";

        ResponseEntity<String> response =
                restTemplate.postForEntity(
                        "/auth/register",
                        Map.of("email", email, "password", "senha-valida-123"),
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
    void registerWithMalformedJsonBody_returns400AsProblemDetail() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>("{not-json", headers);

        ResponseEntity<String> response =
                restTemplate.postForEntity("/auth/register", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.valueOf("application/problem+json"));

        JsonNode body = new ObjectMapper().readTree(response.getBody());
        assertThat(body.get("status").asInt()).isEqualTo(400);
    }

    @Test
    void registerWithUnsupportedContentType_returns415AsProblemDetail() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        HttpEntity<String> request = new HttpEntity<>("email=x@example.com&password=123", headers);

        ResponseEntity<String> response =
                restTemplate.postForEntity("/auth/register", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.valueOf("application/problem+json"));

        JsonNode body = new ObjectMapper().readTree(response.getBody());
        assertThat(body.get("status").asInt()).isEqualTo(415);
    }

    @Test
    void registerNormalizesEmailUsingRootLocaleRegardlessOfDefaultLocale() throws Exception {
        Locale originalLocale = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));

        try {
            String email = "MAIL@EXAMPLE.COM";

            restTemplate.postForEntity(
                    "/auth/register",
                    Map.of("email", email, "password", "senha-valida-123"),
                    Void.class);

            try (Connection connection = postgres.createConnection("");
                    PreparedStatement statement =
                            connection.prepareStatement("select email from users where email = ?")) {
                statement.setString(1, "mail@example.com");

                try (ResultSet resultSet = statement.executeQuery()) {
                    assertThat(resultSet.next()).isTrue();
                }
            }
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    @Test
    void databaseRejectsEmailsThatDifferOnlyInCase() throws Exception {
        try (Connection connection = postgres.createConnection("")) {
            try (PreparedStatement insert =
                    connection.prepareStatement(
                            "insert into users (email, password_hash, role) values (?, 'hash', 'USER')")) {
                insert.setString(1, "case-check@example.com");
                insert.executeUpdate();
            }

            try (PreparedStatement insert =
                    connection.prepareStatement(
                            "insert into users (email, password_hash, role) values (?, 'hash', 'USER')")) {
                insert.setString(1, "Case-Check@Example.com");
                assertThatThrownBy(insert::executeUpdate).isInstanceOf(SQLException.class);
            }
        }
    }
}
