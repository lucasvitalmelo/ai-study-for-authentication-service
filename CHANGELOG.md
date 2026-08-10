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

### Fixed
- `GlobalExceptionHandler` sem precedência explícita deixava o `ProblemDetailsExceptionHandler`
  interno do Spring Boot descartar a mensagem de detalhe por campo nos erros 400 de validação
  desde a feature de cadastro; corrigido com `@Order(HIGHEST_PRECEDENCE)`. (#4)
