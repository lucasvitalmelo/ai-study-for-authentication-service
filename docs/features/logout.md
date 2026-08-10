## Objetivo
Permitir que o cliente revogue um refresh token específico, para que ele não
possa mais ser trocado por um novo access token via `POST /auth/refresh`.

## Porquê
Login e refresh já emitem/renovam access tokens a partir de um refresh token
persistido (TTL de 7 dias, ver `docs/features/login.md` e
`docs/features/refresh-token.md`), mas hoje não existe forma de encerrar uma
sessão antes desse prazo. Sem logout, um refresh token vazado ou de um
dispositivo perdido continua válido até expirar sozinho.

## Fora de escopo
- Revogação de todos os refresh tokens do usuário de uma vez (logout de
  todos os dispositivos) — feature futura separada, se vier a ser necessária.
- Invalidação do access token JWT já emitido — stateless por design, expira
  sozinho em 15 minutos (ver `docs/features/login.md`).
- Autenticação do próprio endpoint de logout via access token — RBAC/proteção
  de endpoints é feature separada ainda não implementada; segue o mesmo
  padrão de `/auth/refresh`, que recebe o refresh token no corpo.
- Rate limiting.

## Critério de aceite
- POST /auth/logout com `refreshToken` existente e válido no banco → 204 (sem
  corpo); o refresh token é removido/revogado e uma tentativa posterior de
  `POST /auth/refresh` com o mesmo token passa a retornar 401.
- POST /auth/logout com `refreshToken` inexistente ou já expirado → 204
  (mesmo efeito prático: o token não pode mais ser usado; idempotente, evita
  diferenciar "não existe" de "já revogado" para quem chama).
- POST /auth/logout com `refreshToken` vazio/ausente → 400 (RFC 7807).

## Esclarecimentos
- Decisão consultada e confirmada: diferente de `/auth/refresh` (que usa 401
  genérico para token inexistente/expirado), `/auth/logout` retorna 204 em
  ambos os casos — inexistente, expirado ou já revogado — porque o objetivo
  (token não pode mais ser usado) já está alcançado, e revogar é uma
  operação idempotente por natureza.

## Categoria de risco
Livre — só lê/escreve no Postgres local de dev, nenhuma ação toca produção,
segredo real ou dado real de usuário.
