## Objetivo
Permitir que o cliente troque um refresh token válido por um novo access
token JWT, sem precisar reenviar e-mail/senha.

## Porquê
O access token expira em 15 minutos (curta duração por design, ver
`docs/features/login.md`). Sem um jeito de renová-lo, o cliente seria
forçado a re-autenticar a cada 15 minutos. Depende do login (issue #4) já
emitir e persistir o refresh token (hash SHA-256, TTL 7 dias).

## Fora de escopo
- Rotação do refresh token a cada uso — decisão já fechada em
  `docs/features/login.md`: o refresh token emitido no login continua
  válido até expirar ou ser revogado por uma feature futura de logout.
  Este endpoint NÃO troca o refresh token por um novo.
- Logout / revogação de refresh token (feature "logout" separada).
- Renovação/extensão do TTL do refresh token.
- RBAC / proteção de outros endpoints com o access token.
- Rate limiting.

## Critério de aceite
- POST /auth/refresh com `refreshToken` válido (existe no banco, não
  expirado) → 200, corpo com `accessToken` novo (JWT HS256; mesmas claims
  do login — `sub`, `role`, `iat`, `exp`; expira em 15 minutos); o
  refresh token original permanece inalterado no banco (sem rotação).
- POST /auth/refresh com `refreshToken` inexistente ou expirado → 401
  (RFC 7807), mensagem genérica igual nos dois casos (evita diferenciar
  "não existe" de "expirou").
- POST /auth/refresh com `refreshToken` vazio/ausente → 400 (RFC 7807).

## Esclarecimentos
- Necessário buscar `RefreshToken` por `token_hash` (`WHERE token_hash =
  ?`), consulta que não existia antes desta feature. Isso motiva resolver
  agora o item 1 da issue #5 (índice ausente em `token_hash`): a migration
  desta feature cria um índice único em `token_hash`, que também blinda
  (de graça) contra colisão de SHA-256.
- Comparação do token recebido é feita hasheando-o (SHA-256) e comparando
  com `token_hash` no banco — mesmo esquema já usado na emissão (login).

## Categoria de risco
Livre — só lê/escreve no Postgres local de dev, nenhuma ação toca
produção, segredo real ou dado real de usuário.
