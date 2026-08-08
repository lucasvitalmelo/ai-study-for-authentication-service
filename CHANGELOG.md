# Changelog

Todas as mudanças notáveis deste projeto serão documentadas aqui.
Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/).

## [Unreleased]
### Added
- Configuração inicial do Claude Code (CLAUDE.md, settings.json, hooks) e esqueleto Spring Boot com Testcontainers.
- Cadastro de usuário (`POST /auth/register`): senha em hash BCrypt, papel padrão USER,
  409 RFC 7807 para e-mail já cadastrado (case-insensitive), 400 RFC 7807 para e-mail
  inválido ou senha vazia. (#1)
