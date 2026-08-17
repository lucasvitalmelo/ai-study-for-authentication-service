# auth-service — Frontend de Validação Manual (React + Vite)

Front visualmente básico, pra exercitar o backend de verdade — login, RBAC, troca de senha e administração de usuários. Sem tela de cadastro público de propósito: quem cria usuário novo é o admin, pelo próprio painel (que usa o mesmo `POST /auth/register` do backend).

## Pré-requisitos
1. `auth-service` rodando localmente (`docker compose up -d` + `mvn spring-boot:run`, na raiz do repositório).
2. Pelo menos um usuário ADMIN no banco (ver "Promovendo o primeiro admin" abaixo) — sem isso, ninguém consegue acessar o painel de administração pra criar os outros.
3. Node.js instalado.

O CORS pro `http://localhost:5173` já vem liberado no backend (`WebConfig.java`, só para desenvolvimento local) — ver a seção "Configurar o CORS" no [README principal](../README.md) se precisar mudar a porta.

## Como rodar
```bash
cd auth-service-frontend
npm install
npm run dev
```
Abre em `http://localhost:5173`.

## Promovendo o primeiro admin
Todo usuário nasce como `USER` (o `POST /auth/register` não permite escolher papel). Pra ter o primeiro ADMIN, promova direto no banco (DBeaver ou `psql`):
```sql
UPDATE users SET role = 'ADMIN' WHERE email = 'seu-email-de-teste@exemplo.com';
```
Depois faça login de novo nesta página — o papel vai gravado no token na hora do login, então só refletir no banco não é suficiente enquanto o token antigo não expira.

## O que a tela faz

**Login** — funciona pra USER e ADMIN com o mesmo formulário; o backend decide o papel, o front só lê o que voltou.

**AppBar** (depois de logado) — à esquerda, "Área do Usuário" ou "Área do Administrador" conforme o papel; à direita, um avatar clicável.

**Popover do avatar** — nome (derivado da parte antes do `@` do e-mail — o backend não tem um campo "nome" hoje, só id/email/role) e o e-mail completo. Duas ações: **Alterar senha** e **Sair**.

**Alterar senha** — o backend não tem um endpoint dedicado de "trocar senha logado", então esta tela reaproveita o fluxo de esqueci-minha-senha: gera o token (que só aparece no console do backend, sem provedor de e-mail real no v1), você cola o token e a senha nova. Como confirmar o reset revoga todas as sessões daquele usuário, você é deslogado(a) automaticamente ao final e precisa entrar de novo com a senha nova.

**Área do Administrador** — lista todos os usuários (`GET /users`) e tem um formulário pra criar um novo (`POST /auth/register` — sempre nasce como `USER`, o endpoint não permite criar já como `ADMIN`).

**Área do Usuário** — tela simples, só confirma visualmente que o RBAC te levou pro lugar certo; não há mais nada pro papel `USER` fazer neste projeto.

## O que NÃO é
Não é uma feature do `auth-service` nem segue o processo de doc → issue → TDD → revisão adversarial do projeto principal — é uma ferramenta de apoio pra validação manual, deliberadamente fora desse fluxo.
