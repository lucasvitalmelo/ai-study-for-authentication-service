## Objetivo
Permitir que um usuário que esqueceu a senha recupere o acesso à própria
conta em dois passos: solicitar o reset (informando o e-mail) e confirmar o
reset (informando o token recebido e a nova senha).

## Porquê
Hoje não existe forma de um usuário recuperar o acesso caso esqueça a senha
— a única opção seria um admin trocar diretamente no banco. Decisão já
registrada no `CLAUDE.md`: nesta v1 o token é gerado e logado (log da
aplicação), sem provedor de e-mail real; a entrega por e-mail fica para uma
feature futura.

## Fora de escopo
- Envio real de e-mail (provedor SMTP/transacional) — token só é logado.
- Rate limiting / bloqueio por número de solicitações.
- Histórico de senhas usadas anteriormente (impedir reuso da mesma senha).
- Reset de senha autenticado (usuário logado trocando a própria senha
  sabendo a senha atual) — fluxo diferente, feature separada se vier a ser
  necessária.
- Limpeza/expurgo em background de tokens expirados não usados — ficam no
  banco e são apenas ignorados por `expiresAt` no passado, mesmo padrão já
  aceito para refresh tokens expirados.

## Critério de aceite
- POST /auth/password-reset com `email` de um usuário existente → 202
  (sem corpo); um token de reset é gerado, hasheado (SHA-256, mesmo padrão
  do refresh token) e persistido no banco associado ao usuário, com TTL de
  15 minutos; o token em texto puro é logado (nunca o hash).
- POST /auth/password-reset com `email` de um usuário inexistente → 202
  (mesma resposta do caso anterior, nenhum token é gerado; evita
  enumeração de usuário, mesmo padrão já usado em `/auth/login`).
- POST /auth/password-reset com `email` vazio/formato inválido → 400 (RFC
  7807).
- POST /auth/password-reset/confirm com `token` válido (existente, não
  expirado, não usado) e `newPassword` válida → 204 (sem corpo); a senha do
  usuário é atualizada (hash BCrypt), o token de reset é consumido
  (não pode ser reutilizado) e todos os refresh tokens existentes do
  usuário são revogados (força novo login em todos os dispositivos).
- POST /auth/password-reset/confirm com `token` inexistente, expirado ou já
  usado → 401 (RFC 7807).
- POST /auth/password-reset/confirm com `newPassword` que não atende aos
  requisitos de `@ValidPassword` (mesma regra do cadastro/login) → 400 (RFC
  7807).

## Esclarecimentos
- Decisão consultada e confirmada: um reset de senha bem-sucedido revoga
  todos os refresh tokens existentes do usuário — trata-se de um evento de
  segurança (troca de credencial), então sessões antigas não devem
  continuar válidas, mesmo em outros dispositivos.
- Decisão consultada e confirmada: TTL do token de reset é 15 minutos —
  mesma janela do access token JWT (`app.jwt.access-token-ttl`), nova
  propriedade de configuração dedicada (ex.: `app.password-reset.token-ttl`).
- Token de reset segue o mesmo padrão do refresh token: 32 bytes aleatórios
  (`SecureRandom`), codificados em Base64 URL-safe, hasheados com SHA-256
  antes de persistir — nunca o texto puro é gravado no banco.
- Resposta de `/auth/password-reset` é sempre 202 independente de o e-mail
  existir ou não, para não revelar quais e-mails estão cadastrados (mesmo
  motivo do 401 genérico em `/auth/login`).

## Categoria de risco
Livre — só lê/escreve no Postgres local de dev; o token é apenas logado
(nenhum envio real de e-mail, nenhum segredo de provedor externo), nenhuma
ação toca produção ou dado real de usuário.
