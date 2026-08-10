package dev.lucasvital.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.lucasvital.auth.login.JwtService;
import dev.lucasvital.auth.user.Role;
import dev.lucasvital.auth.user.User;
import dev.lucasvital.auth.user.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.stream.StreamSupport;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Testcontainers
class RbacTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired private TestRestTemplate restTemplate;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Autowired private UserRepository userRepository;

    @Autowired private PasswordEncoder passwordEncoder;

    @Autowired private JwtService jwtService;

    @BeforeEach
    void useApacheHttpClient() {
        restTemplate.getRestTemplate().setRequestFactory(new HttpComponentsClientHttpRequestFactory());
    }

    @Test
    void meWithoutAuthorizationHeader_returns401AsProblemDetail() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity("/users/me", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.valueOf("application/problem+json"));

        JsonNode body = new ObjectMapper().readTree(response.getBody());
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("title").asText()).isEqualTo("Falha na autenticação");
        assertThat(body.get("detail").asText()).isEqualTo("Token de acesso inválido ou ausente");
    }

    @Test
    void meWithMalformedAuthorizationHeader_returns401AsProblemDetail() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "isso-nao-e-bearer-token");

        ResponseEntity<String> response =
                restTemplate.exchange(
                        "/users/me", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.valueOf("application/problem+json"));

        JsonNode body = new ObjectMapper().readTree(response.getBody());
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("title").asText()).isEqualTo("Falha na autenticação");
        assertThat(body.get("detail").asText()).isEqualTo("Token de acesso inválido ou ausente");
    }

    @Test
    void meWithTokenSignedByAnotherKey_returns401AsProblemDetail() throws Exception {
        SecretKey anotherKey =
                Keys.hmacShaKeyFor(
                        "outra-chave-que-nao-e-a-configurada-no-app-32-bytes"
                                .getBytes(StandardCharsets.UTF_8));

        String tokenWithInvalidSignature =
                Jwts.builder()
                        .subject("1")
                        .claim("role", "USER")
                        .issuedAt(Date.from(Instant.now()))
                        .expiration(Date.from(Instant.now().plus(Duration.ofMinutes(15))))
                        .signWith(anotherKey, Jwts.SIG.HS256)
                        .compact();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + tokenWithInvalidSignature);

        ResponseEntity<String> response =
                restTemplate.exchange(
                        "/users/me", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.valueOf("application/problem+json"));

        JsonNode body = new ObjectMapper().readTree(response.getBody());
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("title").asText()).isEqualTo("Falha na autenticação");
        assertThat(body.get("detail").asText()).isEqualTo("Token de acesso inválido ou ausente");
    }

    @Test
    void meWithExpiredToken_returns401AsProblemDetail() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

        String expiredToken =
                Jwts.builder()
                        .subject("1")
                        .claim("role", "USER")
                        .issuedAt(Date.from(Instant.now().minus(Duration.ofMinutes(30))))
                        .expiration(Date.from(Instant.now().minus(Duration.ofMinutes(15))))
                        .signWith(key, Jwts.SIG.HS256)
                        .compact();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + expiredToken);

        ResponseEntity<String> response =
                restTemplate.exchange(
                        "/users/me", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.valueOf("application/problem+json"));

        JsonNode body = new ObjectMapper().readTree(response.getBody());
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("title").asText()).isEqualTo("Falha na autenticação");
        assertThat(body.get("detail").asText()).isEqualTo("Token de acesso inválido ou ausente");
    }

    @Test
    void meWithTokenMissingRoleClaim_returns401AsProblemDetail() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

        String tokenWithoutRole =
                Jwts.builder()
                        .subject("1")
                        .issuedAt(Date.from(Instant.now()))
                        .expiration(Date.from(Instant.now().plus(Duration.ofMinutes(15))))
                        .signWith(key, Jwts.SIG.HS256)
                        .compact();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + tokenWithoutRole);

        ResponseEntity<String> response =
                restTemplate.exchange(
                        "/users/me", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.valueOf("application/problem+json"));

        JsonNode body = new ObjectMapper().readTree(response.getBody());
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("title").asText()).isEqualTo("Falha na autenticação");
        assertThat(body.get("detail").asText()).isEqualTo("Token de acesso inválido ou ausente");
    }

    @Test
    void meWithTokenHavingInvalidRoleValue_returns401AsProblemDetail() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

        String tokenWithInvalidRole =
                Jwts.builder()
                        .subject("1")
                        .claim("role", "SUPERUSER")
                        .issuedAt(Date.from(Instant.now()))
                        .expiration(Date.from(Instant.now().plus(Duration.ofMinutes(15))))
                        .signWith(key, Jwts.SIG.HS256)
                        .compact();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + tokenWithInvalidRole);

        ResponseEntity<String> response =
                restTemplate.exchange(
                        "/users/me", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.valueOf("application/problem+json"));

        JsonNode body = new ObjectMapper().readTree(response.getBody());
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("title").asText()).isEqualTo("Falha na autenticação");
        assertThat(body.get("detail").asText()).isEqualTo("Token de acesso inválido ou ausente");
    }

    @Test
    void meWithTokenHavingNonNumericSubject_returns401AsProblemDetail() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

        String tokenWithNonNumericSubject =
                Jwts.builder()
                        .subject("nao-e-um-id-numerico")
                        .claim("role", "USER")
                        .issuedAt(Date.from(Instant.now()))
                        .expiration(Date.from(Instant.now().plus(Duration.ofMinutes(15))))
                        .signWith(key, Jwts.SIG.HS256)
                        .compact();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + tokenWithNonNumericSubject);

        ResponseEntity<String> response =
                restTemplate.exchange(
                        "/users/me", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.valueOf("application/problem+json"));

        JsonNode body = new ObjectMapper().readTree(response.getBody());
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("title").asText()).isEqualTo("Falha na autenticação");
        assertThat(body.get("detail").asText()).isEqualTo("Token de acesso inválido ou ausente");
    }

    @Test
    void meWithValidToken_returns200WithOwnIdEmailAndRole() throws Exception {
        String email = "rbac.me.valido@example.com";
        String password = "senha-valida-123";
        ObjectMapper objectMapper = new ObjectMapper();

        restTemplate.postForEntity(
                "/auth/register", Map.of("email", email, "password", password), Void.class);

        ResponseEntity<String> loginResponse =
                restTemplate.postForEntity(
                        "/auth/login", Map.of("email", email, "password", password), String.class);
        String accessToken =
                objectMapper.readTree(loginResponse.getBody()).get("accessToken").asText();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);

        ResponseEntity<String> response =
                restTemplate.exchange(
                        "/users/me", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.get("email").asText()).isEqualTo(email);
        assertThat(body.get("role").asText()).isEqualTo("USER");
        assertThat(body.get("id").asLong()).isPositive();
    }

    @Test
    void listUsersWithAdminToken_returns200WithAllUsers() throws Exception {
        String adminEmail = "rbac.admin@example.com";
        User admin =
                userRepository.save(
                        new User(adminEmail, passwordEncoder.encode("senha-valida-123"), Role.ADMIN));
        String adminAccessToken = jwtService.generateAccessToken(admin);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + adminAccessToken);

        ResponseEntity<String> response =
                restTemplate.exchange(
                        "/users", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = new ObjectMapper().readTree(response.getBody());
        assertThat(body.isArray()).isTrue();
        boolean containsAdmin =
                StreamSupport.stream(body.spliterator(), false)
                        .anyMatch(node -> node.get("email").asText().equals(adminEmail));
        assertThat(containsAdmin).isTrue();
    }

    @Test
    void listUsersWithUserToken_returns403AsProblemDetail() throws Exception {
        String email = "rbac.user.sem.acesso@example.com";
        String password = "senha-valida-123";
        ObjectMapper objectMapper = new ObjectMapper();

        restTemplate.postForEntity(
                "/auth/register", Map.of("email", email, "password", password), Void.class);

        ResponseEntity<String> loginResponse =
                restTemplate.postForEntity(
                        "/auth/login", Map.of("email", email, "password", password), String.class);
        String accessToken =
                objectMapper.readTree(loginResponse.getBody()).get("accessToken").asText();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);

        ResponseEntity<String> response =
                restTemplate.exchange(
                        "/users", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.valueOf("application/problem+json"));

        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.get("status").asInt()).isEqualTo(403);
        assertThat(body.get("title").asText()).isEqualTo("Acesso negado");
        assertThat(body.get("detail").asText()).isEqualTo("Acesso negado para o seu papel");
    }
}
