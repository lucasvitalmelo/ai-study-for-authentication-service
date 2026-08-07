# auth-service — Sistema de Autenticação (Projeto Cumulativo Módulo 5)

## Stack
- Java 21, Spring Boot 3.3.4, Maven
- PostgreSQL (Docker Compose local; Testcontainers em testes — nunca H2)
- JWT HS256 para access token; refresh token opaco, persistido (hash) no banco

## Comandos
- Subir Postgres local: `docker compose up -d`
- Rodar testes: `mvn test`
- Rodar aplicação: `mvn spring-boot:run`
- Formatar código: `mvn com.coveo:fmt-maven-plugin:format`

## As 8 regras do projeto
1. Nenhuma feature começa a ser implementada sem doc escrito antes (`docs/features/<nome>.md`, a partir de `docs/features/TEMPLATE.md`).
2. Nenhum doc de feature vira código sem uma revisão do próprio doc primeiro.
3. Nenhum PR é aberto sem passar por revisão adversarial.
4. Toda mudança rastreia o elo completo: doc → issue → branch/PR → changelog → versão.
5. Descoberta fora do escopo durante uma implementação vira issue nova, nunca mudança dentro do mesmo PR.
6. Categorias de risco (livre / exige confirmação / proibido) valem pro projeto inteiro.
7. Nunca aceitar o resumo de um sub-agente/workflow como prova — sempre rodar o teste/comando real.
8. Para cada feature, o teste que falha (RED) é escrito antes da implementação.

## Decisões de arquitetura já fechadas
- Refresh token: armazenado no banco (hash), revogável no logout.
- RBAC: enum `role` (ADMIN/USER) direto na entidade User.
- Reset de senha: token gerado e logado no v1 — sem provedor de e-mail real.
- Erros: RFC 7807 (`ProblemDetail`) via `@ControllerAdvice` global.
- Testes: Testcontainers com Postgres real, nunca H2.
- Hash de senha: BCrypt.

## Onde NÃO mexer sem confirmação
- Qualquer migration fora do Postgres local de dev.
- `.env` (se vier a existir) — nunca ler/commitar.
