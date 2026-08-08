package dev.lucasvital.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
    void registerWithValidEmailAndPassword_returns201AndPersistsHashedPasswordWithUserRole()
            throws Exception {
        String email = "novo.usuario@example.com";
        String password = "senha-valida-123";

        ResponseEntity<Void> response =
                restTemplate.postForEntity(
                        "/auth/register", Map.of("email", email, "password", password), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        try (Connection connection = postgres.createConnection("");
                PreparedStatement statement =
                        connection.prepareStatement(
                                "select password_hash, role from users where email = ?")) {
            statement.setString(1, email);

            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();

                String passwordHash = resultSet.getString("password_hash");
                String role = resultSet.getString("role");

                assertThat(passwordHash).isNotEqualTo(password);
                assertThat(new BCryptPasswordEncoder().matches(password, passwordHash)).isTrue();
                assertThat(role).isEqualTo("USER");
            }
        }
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
        assertThat(body.has("title")).isTrue();
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
    }
}
