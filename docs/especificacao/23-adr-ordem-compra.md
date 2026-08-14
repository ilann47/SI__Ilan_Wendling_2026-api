> Links: [[README]] · [[22-unificacao-multitenant]] · [[21-status-implementacao]]

# 23. ADR — Ordem de Compra e Recebimento

## Status

Aceita e implementada em 2026-08-14 pela migration V63.

## Contexto

A compra administrativa precisa separar intenção comercial, recebimento físico
e documento fiscal. Usar a Nota de Entrada como origem única impediria registrar
entregas parciais e faria o estoque depender da emissão fiscal.

## Decisão

`OrdemCompra` é o agregado versionado da negociação e possui os estados
`RASCUNHO`, `APROVADA`, `PARCIALMENTE_RECEBIDA`, `RECEBIDA` e `CANCELADA`.
`RecebimentoCompra` é registro append-only e idempotente de uma entrega em um
Local de Estoque. Cada recebimento atualiza as quantidades da ordem e registra o
movimento físico na mesma transação.

A Nota de Entrada pode referenciar um recebimento uma única vez. Quando o vínculo
existe, fornecedor, local, produtos e quantidades devem coincidir. A confirmação
da nota gera os efeitos financeiros, mas não gera novo movimento de estoque.

Todas as raízes carregam `organizacao_id`; referências críticas usam FKs
compostas same-tenant. Mutações da ordem usam lock, `@Version`, ETag e `If-Match`.
Recebimentos usam chave de idempotência, auditoria e outbox PostgreSQL.

## Consequências

- entregas parciais são rastreáveis antes da nota;
- estoque e financeiro têm origens distintas e conciliáveis;
- não existe dupla entrada de estoque ao confirmar a nota vinculada;
- correção de recebimento exige compensação no razão, não edição histórica;
- venda administrativa, pagamento real e transmissão fiscal não são antecipados.
