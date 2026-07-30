> Links: [[core]] · [[organizacoes]] · [[eventos]] · [[inventario]] · [[produtos-estacionamento]] · [[disponibilidade]] · [[rbac]] · [[auditoria]] · [[idempotencia]] · [[outbox]]

# Reservas de Inventário

## Objetivo

Garantir temporariamente uma quantidade vendável durante a compra, impedindo
sobrevenda mesmo quando requisições disputam a última vaga.

## Contexto

UC709 materializa `ReservaInventario` somente para eventos em
`VENDAS_ABERTAS`. Pedido, confirmação como vendido e pagamento permanecem fora
desta história. O prazo padrão provisório é 15 minutos e pode ser configurado.

## Fluxo (camadas da arquitetura)

```text
POST /api/v1/inventory-holds + Idempotency-Key
  -> JWT contextual + inventory:hold
  -> ReservaInventarioService
  -> produto PUBLICADO com lock pessimista
  -> evento VENDAS_ABERTAS + lote vigente de maior prioridade
  -> expiração vencida + update do contador reservado
  -> ReservaInventarioRepository -> PostgreSQL V20
  -> auditoria + InventarioReservado + resultado idempotente
  -> 201 + Location + ETag

release/job/consulta vencida
  -> lock produto -> lock reserva
  -> transição terminal + devolução exata do contador
  -> auditoria + outbox
```

## Endpoints (se houver)

- `POST /api/v1/inventory-holds`: cria hold idempotente.
- `GET /api/v1/inventory-holds/{holdId}`: consulta somente hold do ator.
- `POST /api/v1/inventory-holds/{holdId}/release`: libera uma única vez.

## Estrutura de Dados (DTOs, Entidades)

`ReservaInventario` conserva tenant, evento, produto, ator, quantidade, canal,
referência opcional do comprador, chave idempotente, expiração, estado e o
snapshot imutável de lote, preço e moeda. `ProdutoEstacionamento` mantém
contadores reservado, vendido e bloqueado, todos defendidos por constraints.

## Integrações externas (se houver)

Não há broker ou gateway. `InventarioReservado`, `ReservaExpirada` e
`InventarioLiberado` são gravados na [[outbox]] da transação.

## Tratamento de Erros

- `400`: chave, quantidade, canal, motivo ou campo controlado inválido.
- `403`: ausência de `inventory:hold` ou de tenant ativo.
- `404`: produto/hold invisível ou hold pertencente a outro ator.
- `409`: vendas fechadas, produto/lote indisponível ou saldo insuficiente.

## Testes (curl ou equivalente)

Há testes de domínio, API, RBAC, ownership, idempotência, expiração, liberação,
saldo projetado, constraints e duas transações concorrentes pela última vaga.

## Decisões Técnicas

- Lock pessimista do produto é a autoridade de serialização do saldo.
- `reservado + vendido + bloqueado <= quota` também é constraint PostgreSQL.
- A disponibilidade é indicativa; somente o commit do hold garante quantidade.
- Expiração usa job interno em lotes de 100 e também é aplicada ao consultar ou
  reservar, sempre na ordem de locks produto → reserva.
- O prazo de 15 minutos é premissa operacional configurável até definição de negócio.
- Nesta etapa, Admin, Gestor e Organizador autenticados representam os canais;
  identidade pública de cliente/caixa/integração permanece no backlog próprio.

## Módulos relacionados

- [[core]]
- [[organizacoes]]
- [[eventos]]
- [[inventario]]
- [[produtos-estacionamento]]
- [[disponibilidade]]
- [[rbac]]
- [[auditoria]]
- [[idempotencia]]
- [[outbox]]

## Histórico (data + ação)

| Data | Ação |
|---|---|
| 2026-07-30 | Implementa UC709 com hold atômico, expiração e liberação única. |
