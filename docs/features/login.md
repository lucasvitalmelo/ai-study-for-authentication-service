## Objetivo
Permitir que um usuário já cadastrado se autentique com e-mail e senha,
recebendo um access token JWT (HS256, curta duração) e um refresh token
opaco (longa duração, persistido em hash no banco).

## Porquê
É o segundo passo da porta de entrada do sistema — sem login não há como
emitir os tokens que as próximas features (refresh, logout, RBAC) precisam.
Depende do cadastro de usuário (issue #1) já existir.

## Fora de escopo
- Troca de refresh token por um novo access token (feature "refresh" separada)
- Logout / revogação de refresh token (feature "logout" separada)
- Proteção de endpoints via RBAC/JWT (feature "RBAC" separada)
- Rotação de refresh token a cada uso — o token emitido no login vale até
  expirar ou ser revogado por uma feature futura
- Rate limiting / bloqueio por tentativas de login

## Critério de aceite
- POST /auth/login com e-mail e senha corretos de um usuário existente →
  200, corpo com `accessToken` (JWT HS256; claims `sub`=id do usuário,
  `role`, `iat`, `exp`; expira em 15 minutos) e `refreshToken` (opaco,
  expira em 7 dias, persistido em hash no banco associado ao usuário)
- POST /auth/login com e-mail inexistente ou senha incorreta → 401 (RFC
  7807), com a mesma mensagem genérica nos dois casos (evita enumeração
  de usuário)
- POST /auth/login com e-mail ou senha vazios/formato inválido → 400 (RFC
  7807)

## Esclarecimentos
- Cada login gera uma nova linha na tabela de refresh tokens; refresh
  tokens de logins anteriores do mesmo usuário continuam válidos até
  expirar (múltiplas sessões/dispositivos simultâneos são permitidos).
- Refresh token é hasheado com SHA-256 antes de persistir — diferente da
  senha (BCrypt): o token já nasce aleatório de alta entropia, não
  precisa de hash lento/memory-hard.

## Categoria de risco
Livre — só lê/escreve no Postgres local de dev; o secret de assinatura do
JWT fica em `application.yml` (mesmo padrão já usado para as credenciais
do datasource local), nenhuma ação toca produção, segredo real ou dado
real de usuário.
