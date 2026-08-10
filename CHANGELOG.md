# Changelog

Todas as mudanças notáveis deste projeto serão documentadas aqui.
Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/).

## [Unreleased]
### Added
- Configuração inicial do Claude Code (CLAUDE.md, settings.json, hooks) e esqueleto Spring Boot com Testcontainers.
- Cadastro de usuário (`POST /auth/register`): senha em hash BCrypt, papel padrão USER,
  409 RFC 7807 para e-mail já cadastrado (case-insensitive), 400 RFC 7807 para e-mail
  inválido ou senha vazia. (#1)
- Login com JWT (`POST /auth/login`): access token JWT HS256 (expira em 15min) e refresh
  token opaco persistido em hash SHA-256 (expira em 7 dias, múltiplas sessões permitidas),
  401 RFC 7807 genérico para credenciais inválidas (sem enumeração de usuário, incluindo
  proteção contra timing side-channel), 400 RFC 7807 para e-mail/senha inválidos. (#4)
- Refresh de access token (`POST /auth/refresh`): troca refresh token válido por novo
  access token JWT, sem rotacionar o refresh token original; 401 RFC 7807 genérico para
  token inexistente ou expirado (mesma mensagem nos dois casos); 400 RFC 7807 para
  refreshToken vazio. Adiciona índice único em `refresh_tokens.token_hash`. (#7)
- Logout (`POST /auth/logout`): revoga (remove) o refresh token informado; 204 idempotente
  tanto para token existente/válido quanto para inexistente ou já expirado; 400 RFC 7807
  para refreshToken vazio. Não afeta o access token JWT já emitido (stateless, expira
  sozinho em 15min). (#9)
- RBAC e endpoint protegido: `GET /users/me` (qualquer usuário autenticado, devolve
  id/email/role do próprio token) e `GET /users` (somente `ADMIN`, lista todos os
  usuários); 401 RFC 7807 genérico para access token ausente, malformado, com assinatura
  inválida ou expirado; 403 RFC 7807 para role sem permissão. Sem Spring Security — um
  único `HandlerMethodArgumentResolver` valida o token com o `JwtService` já existente.
  (#11)

### Fixed
- `GlobalExceptionHandler` sem precedência explícita deixava o `ProblemDetailsExceptionHandler`
  interno do Spring Boot descartar a mensagem de detalhe por campo nos erros 400 de validação
  desde a feature de cadastro; corrigido com `@Order(HIGHEST_PRECEDENCE)`. (#4)
