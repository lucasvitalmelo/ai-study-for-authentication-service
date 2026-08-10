---
name: nova-feature
description: Processo completo de uma feature nova - doc, issue, TDD por critério, revisão adversarial, PR, changelog. Use quando eu pedir pra iniciar uma feature nova.
---

# Nova Feature — Processo Completo

Quando eu pedir uma feature nova, siga TODOS os passos, em ordem, sem pular:

## 1. Doc da feature
Escreva docs/features/<nome-kebab-case>.md usando docs/features/TEMPLATE.md
(objetivo, porquê, fora de escopo, critério de aceite, categoria de risco).

## 2. Revisão do doc
Releia como se fosse a primeira vez. Se achar ambiguidade real, PARE e
pergunte antes de seguir.

## 3. Issue
Abra uma issue (MCP ou gh CLI) referenciando o doc.

## 4. Branch
Crie feature/<nº-issue>-<nome>.

## 5. Ciclo por critério de aceite
Para cada critério, nesta ordem: RED (teste falha) → confirma que falha pelo
motivo certo → implementação mínima → GREEN → commit citando a issue.
Repita pro próximo critério.

## 6. Revisão adversarial
Depois de tudo verde, use /superpowers:requesting-code-review comparando a
branch com main, de forma adversarial (achar problema, não confirmar).

## 7. Triagem dos achados
Bug real dentro do escopo já aceito: RED, corrige, commit.
Fora do escopo: issue nova (regra 5), não mexe nesta branch.
Contradiz decisão já registrada no doc: PARE e pergunte antes de mudar.

## 8. Rastreabilidade
Confirme doc commitado e adicione entrada no CHANGELOG.md.

## 9. PR
Abra o PR linkando "Closes #<nº>", descrição resumindo critérios e revisão.

## 10. Merge
NUNCA faça merge sozinho — pergunte antes, sempre.

## Vale em toda etapa
Nunca aceite seu próprio resumo como prova, sempre rode o comando real.
Nunca junte descoberta fora de escopo no mesmo PR.
Se uma decisão de arquitetura nova aparecer, PARE e pergunte — não decida
sozinho.
