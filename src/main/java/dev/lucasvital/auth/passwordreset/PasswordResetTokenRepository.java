package dev.lucasvital.auth.passwordreset;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    // Lock pessimista: garante que duas requisicoes concorrentes com o mesmo token nao
    // possam ambas passar pela checagem de validade antes de qualquer uma consumir o token
    // (issue #19). A segunda so prossegue apos a primeira commitar, e nesse ponto o token
    // ja foi apagado — a leitura corretamente retorna vazio.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PasswordResetToken t where t.tokenHash = :tokenHash")
    Optional<PasswordResetToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Transactional
    void deleteByTokenHash(String tokenHash);

    @Transactional
    void deleteByUserId(Long userId);
}
