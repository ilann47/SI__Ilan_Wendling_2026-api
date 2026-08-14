> Links: [[core]] · [[conveniencia]] · [[rbac]] · [[auditoria]] · [[idempotencia]] · [[outbox]] · [[unificacao-multitenant]]

# Estoque

## Objetivo

Manter o saldo físico tenant-aware por Produto e Local em um razão imutável,
sem permitir saldo negativo ou misturar organizações.

## Contexto

A V54 torna o razão canônico somente para Produto tenant-aware. O campo legado
`produto.quantidade` permanece histórico e é congelado em zero nesse recorte.
Local de Estoque é lógico e pertence diretamente à organização.

## Fluxo (camadas da arquitetura)

Controllers validam JWT contextual, RBAC, precondições HTTP e idempotência. Os
services validam Produto/Local ativos do mesmo tenant, serializam por Produto e
saldo, persistem Saldo e Movimento na mesma transação e registram [[auditoria]],
[[idempotencia]] e [[outbox]]. Repositories usam projeções com nomes para evitar
dependência de permissão de catálogo na consulta do razão.
PUT/DELETE de Local obtêm lock pessimista de escrita antes de conferir versão e
saldo; movimentações mantêm lock pessimista de leitura no Local. Assim ajuste e
inativação concorrentes não deixam saldo em Local inativo.

## Endpoints (se houver)

- `/api/v1/stock-locations`: CRUD lógico com `ETag`/`If-Match`.
- `/api/v1/stock-balances`: saldo paginado por Produto/Local.
- `/api/v1/stock-positions`: posição agregada e filtro abaixo do mínimo.
- `/api/v1/stock-movements`: razão paginado, detalhe e compensação.
- `/api/v1/stock-adjustments`: ajuste manual idempotente.

## Estrutura de Dados (DTOs, Entidades)

- `LocalEstoque`: nome único normalizado no tenant, ativo e versionado.
- `SaldoEstoque`: uma linha por organização, Produto e Local; quantidade não negativa.
- `MovimentoEstoque`: append-only, tipo, delta, saldo anterior/posterior, origem,
  custo opcional, ator, motivo e vínculo de compensação.
- Tipos: `RECEBIMENTO`, `BAIXA`, `AJUSTE`, `COMPENSACAO`; a API inicial expõe
  somente ajuste e compensação.

## Integrações externas (se houver)

Não há broker nem integração externa. Eventos oficiais usam a outbox PostgreSQL
existente; auditoria e idempotência participam da mesma transação.

## Tratamento de Erros

Sem contexto/permissão retorna `401/403`; histórico/cross-tenant retorna `404`;
If-Match ausente, inválido ou divergente retorna `428/400/412`; saldo insuficiente,
compensação repetida e chave idempotente divergente retornam `409`.
Produto com saldo não zero e Local com qualquer saldo não zero não podem ser
inativados. Concorrência otimista de Local também é traduzida para `412`.

## Testes (curl ou equivalente)

Há testes de migration/preflight, constraints PostgreSQL, isolamento/RBAC/API,
idempotência por ator, conteúdo de auditoria/outbox, compensação, limiar agregado,
cadeia do razão, mutação direta de saldo, concorrência sobre o mesmo saldo,
ajuste versus inativação e dois PUTs com o mesmo ETag.

## Decisões Técnicas

- Locks consultivos por Produto combinados com lock pessimista do saldo.
- Constraint triggers diferidos validam, no estado final da transação, a cadeia
  iniciada em zero e a igualdade entre último movimento e saldo materializado.
- Movimento append-only e compensação, nunca edição/exclusão.
- Compensação não pode ser autorreferente, compensar outra compensação nem usar
  delta diferente do inverso exato do original.
- Origem única inclui Produto, Local e tipo para suportar documentos multi-item.
- Ajustes/compensações manuais incorporam o ator à chave de origem, alinhados ao
  escopo persistido de idempotência; origens documentais preservam a unicidade.
- Local só pode ser inativado com todos os saldos zerados.
- Produto tenant-aware só pode ser inativado com posição agregada zerada.
- Eventos normais expõem allowlist completa do movimento; o alerta de mínimo
  contém somente identificação do produto, movimento, total e mínimo.
- Ordem de Compra usa o recebimento V63 como origem física; transferência, PDV
  e custo médio permanecem fora deste recorte.

## Módulos relacionados

[[conveniencia]] fornece Produto tenant-aware; [[rbac]] autoriza; [[auditoria]],
[[idempotencia]] e [[outbox]] registram os efeitos transacionais.

## Histórico (data + ação)

| Data | Ação |
|---|---|
| 2026-08-03 | Fundação tenant-aware de estoque e razão adicionada na V54. |
| 2026-08-03 | Hardenings P1 de concorrência, cadeia do razão, compensação, idempotência e payloads aplicados. |
| 2026-08-14 | Recebimento de Ordem de Compra passa a registrar entrada física idempotente no razão. |
| 2026-08-14 | Confirmação de Venda Administrativa passa a registrar baixa física no razão. |
