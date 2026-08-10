## Objetivo
Proteger endpoints com o access token JWT já emitido pelo login/refresh,
introduzindo um mecanismo de autenticação (quem é o usuário da requisição) e
de autorização por role/RBAC (o que esse usuário pode acessar). Entrega dois
endpoints de exemplo: `GET /users/me` (qualquer usuário autenticado) e
`GET /users` (somente `ADMIN`).

## Porquê
Login, refresh e logout emitem/consomem tokens, mas nenhum endpoint hoje
exige um access token válido para responder — `role` já existe na entidade
`User` e é incluída como claim no JWT (ver `docs/features/login.md`), mas
nada a lê ou impõe. Sem isso, não há como construir qualquer endpoint que só
deveria responder para o próprio usuário autenticado ou para um ADMIN.

## Decisão de arquitetura (consultada e confirmada)
- **Sem Spring Security.** O projeto não usa a dependência hoje (login,
  refresh e logout validam tudo "na mão", com `JwtService` puro, sem
  `SecurityFilterChain`/`SecurityContext`). Mantém o mesmo estilo: um único
  `HandlerMethodArgumentResolver` (parâmetro `@AuthenticatedUser CurrentUser`
  no controller) lê o header `Authorization: Bearer <token>` e valida com o
  `JwtService` existente, sem filtro/interceptor separado — só há dois
  endpoints consumindo isso, não compensa uma camada genérica a mais. A
  restrição por role do endpoint admin-only é uma checagem direta no corpo
  do próprio controller (`if (role != ADMIN) throw ...`), não uma anotação
  genérica — mesmo motivo.
- **RBAC de exemplo.** Como só existe um endpoint pedido (`GET /users/me`,
  aberto a qualquer usuário autenticado), a restrição por role não teria
  como ser testada. Por isso esta feature também cria `GET /users`
  (lista todos os usuários, somente `ADMIN`) — endpoint mínimo, sem
  paginação/filtros, cuja única razão de existir é provar que a restrição
  de role funciona (403 para role errada).

## Fora de escopo
- Qualquer endpoint de gerenciamento de usuários que não seja os dois acima
  (editar, deletar, promover/alterar role via API). Criar um usuário `ADMIN`
  continua sendo uma operação manual no banco (fora da API) — decisão que
  fica registrada aqui, não é esquecimento.
- Registro público se autodeclarando `ADMIN` — `POST /auth/register`
  continua sempre criando `USER` (decisão já fechada no cadastro de
  usuário).
- Refresh token / revogação — não mexe no que já existe (refresh, logout).
- Rotação, refresh ou invalidação do access token a partir do RBAC — o
  access token continua stateless, expira sozinho em 15 minutos.
- Rate limiting.
- Paginação, filtros ou qualquer parâmetro de busca em `GET /users`.

## Critério de aceite
- `GET /users/me` sem header `Authorization` → 401 (RFC 7807).
- `GET /users/me` com header `Authorization` malformado (não é
  `Bearer <token>`, ou token com formato inválido) → 401 (RFC 7807).
- `GET /users/me` com access token de assinatura inválida (não emitido por
  este serviço) → 401 (RFC 7807).
- `GET /users/me` com access token expirado → 401 (RFC 7807).
- `GET /users/me` com access token válido → 200, corpo com `id`, `email` e
  `role` do usuário do próprio token (`sub`), buscado no banco.
- `GET /users` com access token válido de usuário `ADMIN` → 200, corpo com
  a lista de todos os usuários (`id`, `email`, `role`).
- `GET /users` com access token válido de usuário `USER` (role errada) →
  403 (RFC 7807).

## Esclarecimentos
- Mensagem de erro 401 é genérica e igual para os quatro casos (ausente,
  malformado, assinatura inválida, expirado) — mesmo padrão de não
  diferenciar motivos já usado em login/refresh.
- Endpoints públicos (`/auth/**`) não têm o parâmetro `@AuthenticatedUser`,
  então nunca disparam a resolução — não precisam de nenhuma lista de
  exclusão explícita.
- Testes populam o usuário `ADMIN` inserindo direto via `UserRepository`
  (sem passar pela API) e geram o token com o `JwtService` já existente —
  não há via pela API para criar um `ADMIN` (ver "Fora de escopo").

## Categoria de risco
Livre — só lê/escreve no Postgres local de dev, nenhuma ação toca produção,
segredo real ou dado real de usuário.
