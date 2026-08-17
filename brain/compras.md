> Links: [[core]] · [[fornecedores]] · [[conveniencia]] · [[estoque]] · [[fiscal-financeiro]] · [[rbac]] · [[auditoria]] · [[idempotencia]] · [[outbox]]

# Compras

## Objetivo

Controlar ordens de compra e seus recebimentos físicos por organização, ligando
Fornecedor, Produto, Local de Estoque, razão de movimentos e Nota de Entrada sem
duplicar efeitos de estoque.

## Contexto

A V63 implementa a Onda 4 da unificação multi-tenant. A ordem nasce em
`RASCUNHO`, pode ser editada, aprovada ou cancelada e recebe entregas parciais
até `RECEBIDA`. O recebimento é append-only e antecede opcionalmente a Nota de
Entrada.

## Fluxo (camadas da arquitetura)

`OrdemCompraController` valida JWT contextual, RBAC, `If-Match` e idempotência.
`OrdemCompraService` resolve as referências do tenant, serializa a ordem,
persiste o recebimento, registra movimentos `RECEBIMENTO` e grava auditoria e
outbox na mesma transação. Uma Nota de Entrada vinculada valida fornecedor,
local, produtos e quantidades do recebimento; sua confirmação gera o financeiro,
mas não movimenta o estoque novamente.

## Endpoints (se houver)

- `GET/POST /api/v1/purchase-orders`
- `GET/PUT /api/v1/purchase-orders/{id}`
- `POST /api/v1/purchase-orders/{id}/approval`
- `POST /api/v1/purchase-orders/{id}/cancellation`
- `GET/POST /api/v1/purchase-orders/{id}/receipts`

## Estrutura de Dados (DTOs, Entidades)

- `OrdemCompra`: agregado versionado, fornecedor, moeda, totais e estado.
- `ItemOrdemCompra`: produto, quantidade pedida/recebida e valores.
- `RecebimentoCompra`: entrega idempotente vinculada a Local de Estoque.
- `ItemRecebimentoCompra`: quantidade recebida por item da ordem.
- `NotaEntrada.recebimentoCompra`: vínculo fiscal opcional e único.

## Integrações externas (se houver)

Não há integração externa. Estoque, fiscal, financeiro, auditoria, idempotência e
outbox são módulos internos do monólito.

## Tratamento de Erros

Ausência de tenant ou permissão retorna `401/403`; recurso histórico,
cross-tenant ou pai inválido retorna `404`; `If-Match` ausente ou divergente
retorna `428/412`; transição, quantidade ou vínculo incompatível retorna `409`.

## Testes (curl ou equivalente)

Os testes cobrem ciclo completo, edição com preservação do item, versão
concorrente, recebimento parcial/completo, idempotência, isolamento, integração
fiscal sem estoque duplicado, seed, V1→V63 e Hibernate validate.

## Decisões Técnicas

- A ordem é a fonte da intenção comercial; o recebimento é a fonte do estoque.
- Recebimentos não são editados ou excluídos; correções usam o razão de estoque.
- A Nota de Entrada vinculada é a fonte financeira/fiscal e não repete o estoque.
- Paginação de ordens busca primeiro IDs e depois o grafo detalhado, evitando
  paginação em memória sobre coleção.
- ETag deriva exclusivamente de `@Version` e mutações exigem `If-Match`.
- A listagem administrativa abre um detalhe próprio da ordem com itens,
  quantidades pedida/recebida/pendente, valores e resumo financeiro; o histórico
  permanece separado por representar entregas físicas já registradas.

## Módulos relacionados

[[fornecedores]], [[conveniencia]], [[estoque]], [[fiscal-financeiro]], [[rbac]],
[[auditoria]], [[idempotencia]] e [[outbox]].

## Histórico (data + ação)

| Data | Ação |
|---|---|
| 2026-08-14 | V63 implementa Ordem de Compra, recebimento e integração fiscal/financeira sem estoque duplicado. |
| 2026-08-17 | Frontend passa a expor os itens comprados e o resumo financeiro no detalhe de cada ordem. |
