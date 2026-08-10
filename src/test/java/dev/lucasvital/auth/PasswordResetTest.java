package dev.lucasvital.auth;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
}
