> Links: [[core]] · [[clientes]] · [[conveniencia]] · [[estoque]] · [[fiscal-financeiro]] · [[rbac]] · [[auditoria]] · [[idempotencia]] · [[outbox]]

# Vendas Administrativas

## Objetivo

Registrar vendas fora do PDV com baixa física e geração de contas a receber,
mantendo correlação opcional com a Nota de Saída.

## Contexto

A V64 implementa a Onda 5 para mercadorias. A venda nasce em `RASCUNHO` e pode
ser alterada, confirmada ou cancelada. `CONFIRMADA` e `CANCELADA` são terminais.

## Fluxo (camadas da arquitetura)

O controller exige JWT contextual, `sales:read/manage`, idempotência e
`If-Match`. O service valida Cliente, Local e Produtos do tenant. A confirmação
serializa o agregado, baixa o razão, cria as parcelas a receber, audita e publica
outbox na mesma transação. Nota de Saída vinculada apenas documenta a venda.

## Endpoints (se houver)

- `GET/POST /api/v1/administrative-sales`
- `GET/PUT /api/v1/administrative-sales/{id}`
- `POST /api/v1/administrative-sales/{id}/confirmation`
- `POST /api/v1/administrative-sales/{id}/cancellation`

## Estrutura de Dados (DTOs, Entidades)

`VendaAdministrativa`, `ItemVendaAdministrativa`, vínculo opcional em
`NotaSaida` e origem em `ContaReceber`.

## Integrações externas (se houver)

Nenhuma. A Nota de Saída é documento interno; transmissão fiscal permanece fora.

## Tratamento de Erros

Contexto/permissão `401/403`; cross-tenant `404`; versão `428/412`; estoque,
duplicidade ou transição inválida `409`.

## Testes (curl ou equivalente)

Há cobertura de isolamento, confirmação, estoque, recebível, ETag e Nota de
Saída sem efeitos duplicados, além de seed e V1→V64.

## Decisões Técnicas

- A venda é a origem comercial, física e financeira.
- A Nota de Saída vinculada é documental e não repete estoque/recebível.
- Confirmação é atômica; saldo insuficiente reverte toda a operação.
- ETag deriva de `@Version`; números são únicos por organização.

## Módulos relacionados

[[clientes]], [[conveniencia]], [[estoque]], [[fiscal-financeiro]], [[rbac]],
[[auditoria]], [[idempotencia]] e [[outbox]].

## Histórico (data + ação)

| Data | Ação |
|---|---|
| 2026-08-14 | V64 implementa Venda Administrativa e integração fiscal/financeira. |
