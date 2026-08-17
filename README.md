# auth-service

Sistema de autenticação em Java/Spring Boot — projeto cumulativo do Módulo 5 de um estudo sobre engenharia com IA agentic (Claude Code). Aplica, de ponta a ponta e num repositório real, tudo o que o estudo cobriu: documentação antes de código, TDD, revisão adversarial, modelo de risco e rastreabilidade completa (doc → issue → branch → PR → changelog → versão).

**Versão atual: `v0.1.0`**

## Stack
- Java 21, Spring Boot 3.3.4, Maven
- PostgreSQL (Docker Compose local; Testcontainers em teste — nunca H2)
- JWT HS256 para access token; refresh token opaco, persistido (hash) no banco
- Migrations versionadas com Flyway

## Funcionalidades
| Endpoint | O que faz |
|---|---|
| `POST /auth/register` | Cadastro de usuário (senha em hash BCrypt, papel `USER` por padrão) |
| `POST /auth/login` | Login — emite access token (JWT, 15min) e refresh token (opaco, 7 dias) |
| `POST /auth/refresh` | Troca o refresh token por um access token novo |
| `POST /auth/logout` | Revoga o refresh token (invalidação real, não cosmética) |
| `GET /users/me` | Perfil do usuário autenticado |
| `GET /users` | Lista todos os usuários — só para papel `ADMIN` |
| `POST /auth/password-reset` | Gera token de reset (logado no console — sem provedor de e-mail real no v1) |
| `POST /auth/password-reset/confirm` | Confirma o reset com o token e a nova senha |

Erros seguem RFC 7807 (`ProblemDetail`) em todas as rotas.

## Como rodar
```bash
docker compose up -d       # sobe o Postgres local
mvn spring-boot:run        # sobe a aplicação em localhost:8080
```

## Como testar
```bash
mvn test                   # suíte completa, Testcontainers com Postgres real
```
Também tem uma **coleção do Postman** pronta em [`postman/`](postman/README.md), e um **frontend de validação manual** (React + Vite) em [`auth-service-frontend/`](auth-service-frontend/README.md) — ver o README de lá pra instruções.

## Configurar o CORS (necessário pro frontend de validação)
Esta API foi desenhada pra ser consumida por outro serviço, não por um navegador direto — por isso o CORS vem **desligado por padrão** e precisa ser liberado manualmente pra rodar o frontend local.

Já está configurado em `src/main/java/dev/lucasvital/auth/web/WebConfig.java` (método `addCorsMappings`), liberando `/auth/**` e `/users/**` só para `http://localhost:5173` (a porta padrão do Vite), métodos `GET`/`POST` e o header `Authorization`.

Se precisar liberar outra origem (por exemplo, rodando o frontend numa porta diferente), edite os `allowedOrigins(...)` desse método. **Essa liberação existe só para desenvolvimento local — não deveria ir para um ambiente de produção sem revisão.**

## Estrutura do projeto
```
docs/features/          — um doc por feature, escrito antes do código (objetivo, porquê, fora de escopo, critério de aceite, risco)
src/main/.../db/migration/  — migrations Flyway, o schema é 100% versionado
auth-service-frontend/  — frontend React/Vite de validação manual (fora do processo de doc→issue→PR do backend)
CLAUDE.md               — stack, convenções, as 8 regras do processo, decisões de arquitetura já fechadas
DEVLOG.md               — log cronológico de decisões técnicas e gotchas reais encontrados
CHANGELOG.md            — histórico de versões
postman/                — coleção pra testar os fluxos manualmente
```

## O processo (resumo — detalhes completos no `CLAUDE.md`)
Nenhuma feature entra sem doc antes; nenhum doc vira código sem revisão; nenhum PR abre sem revisão adversarial; toda mudança rastreia o elo completo doc → issue → branch/PR → changelog → versão; descoberta fora de escopo vira issue nova, nunca mudança no mesmo PR.

## Onde não mexer sem confirmação
Qualquer migration fora do Postgres local de dev; `.env` (se vier a existir) — nunca lido/commitado.
