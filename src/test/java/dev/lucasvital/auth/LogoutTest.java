package dev.lucasvital.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
}
