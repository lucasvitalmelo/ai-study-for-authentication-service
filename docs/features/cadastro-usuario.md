## Objetivo
Permitir que um novo usuário se registre no sistema com e-mail e senha, sendo
armazenado com senha em hash (BCrypt) e papel padrão USER.

## Porquê
É a porta de entrada de todo o sistema — sem cadastro não há usuário pra logar,
testar RBAC, ou testar reset de senha. Por isso é a primeira feature: todas as
outras dependem dela existir.

## Fora de escopo
- Login (feature separada)
- Confirmação de e-mail / verificação de conta
- Validação de força de senha além de "não vazia" (pode virar issue própria depois)
- E-mail de boas-vindas

## Critério de aceite
- POST /auth/register com e-mail e senha válidos → 201, usuário persistido com
  role=USER e senha em hash (nunca texto puro)
- POST /auth/register com e-mail já cadastrado → 409 (RFC 7807)
- POST /auth/register com e-mail inválido ou senha vazia → 400 (RFC 7807)

## Categoria de risco
Livre — só escreve no Postgres local de dev, nenhuma ação toca produção,
segredo ou dado real de usuário.

## Esclarecimentos
- Formato de e-mail validado via Bean Validation (`@Email` + `@NotBlank`);
  e-mail ou senha inválidos retornam 400.
- E-mail é normalizado para minúsculas antes de salvar e de checar
  duplicidade (duplicidade é case-insensitive).
