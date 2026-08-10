package dev.lucasvital.auth.passwordreset;

import dev.lucasvital.auth.login.RefreshTokenRepository;
import dev.lucasvital.auth.security.OpaqueTokenGenerator;
import dev.lucasvital.auth.user.User;
import dev.lucasvital.auth.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final OpaqueTokenGenerator opaqueTokenGenerator;
    private final Duration tokenTtl;

    public PasswordResetService(
            PasswordResetTokenRepository passwordResetTokenRepository,
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            OpaqueTokenGenerator opaqueTokenGenerator,
            @Value("${app.password-reset.token-ttl}") String tokenTtl) {
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.opaqueTokenGenerator = opaqueTokenGenerator;
        this.tokenTtl = Duration.parse(tokenTtl);
    }

    public void requestReset(String email) {
        Optional<User> user = userRepository.findByEmail(email);

        // Gera e hasheia o token sempre, mesmo que o e-mail nao exista, para que os dois
        // casos paguem o mesmo custo de CPU e nao revelem por timing quais e-mails estao
        // cadastrados — mesmo motivo do dummyPasswordHash em LoginController.
        String token = opaqueTokenGenerator.generate();
        String tokenHash = opaqueTokenGenerator.hash(token);

        if (user.isPresent()) {
            passwordResetTokenRepository.save(
                    new PasswordResetToken(user.get().getId(), tokenHash, Instant.now().plus(tokenTtl)));

            log.info("Token de reset de senha gerado para email={}: token={}", email, token);
        }
    }

    @Transactional
    public void confirm(String token, String newPassword) {
        String tokenHash = opaqueTokenGenerator.hash(token);
        PasswordResetToken resetToken =
                passwordResetTokenRepository
                        .findByTokenHash(tokenHash)
                        .filter(t -> t.getExpiresAt().isAfter(Instant.now()))
                        .orElseThrow(InvalidPasswordResetTokenException::new);

        // Defensivo: hoje inalcancavel — password_reset_tokens.user_id tem FK NOT NULL para
        // users sem cascade, entao nao ha como excluir um usuario com token de reset ativo.
        User user =
                userRepository
                        .findById(resetToken.getUserId())
                        .orElseThrow(InvalidPasswordResetTokenException::new);

        user.changePasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        passwordResetTokenRepository.deleteByUserId(user.getId());
        refreshTokenRepository.deleteByUserId(user.getId());
    }
}
