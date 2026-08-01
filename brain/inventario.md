> Links: [[core]] · [[organizacoes]] · [[eventos]] · [[instalacoes]] · [[produtos-estacionamento]] · [[reservas-inventario]] · [[acesso]] · [[rbac]] · [[auditoria]] · [[idempotencia]] · [[outbox]]

# Inventário

## Objetivo

Comprometer capacidade de pátios para eventos sem sobrealocação temporal e
controlar a soma das quotas de produtos sobre a capacidade vendável.

## Contexto

A História 8 implementa UC706 com `AlocacaoPatioEvento`. Um evento pode usar
vários pátios e um pátio pode atender eventos simultâneos enquanto a soma da
capacidade operacional comprometida não exceder seu limite operacional.
UC707 acrescenta [[produtos-estacionamento]], cuja soma de quotas não pode
ultrapassar a capacidade vendável da alocação.
UC708 acrescenta lotes comerciais; cada lote respeita a quota do produto e
prioridades distintas resolvem períodos sobrepostos sem ambiguidade.
[[disponibilidade]] projeta a quota e o lote vigente. UC709 acrescenta
[[reservas-inventario]], que compromete e devolve saldo sob lock do produto.
UC713 também compensa venda confirmada ao cancelar o pedido, sem permitir
contador negativo ou devolução duplicada.

## Fluxo (camadas da arquitetura)

```text
POST /api/v1/events/{eventoId}/parking-allocations + Idempotency-Key
  -> JWT contextual + inventory:manage
  -> AlocacaoPatioEventoService
  -> evento configurável + pátio ativo com lock pessimista
  -> saldo temporal + invariantes do agregado
  -> AlocacaoPatioEventoRepository -> PostgreSQL V16
  -> AuditoriaService + OutboxService + resultado idempotente
  -> 201 + Location + ETag

PATCH /api/v1/parking-allocations/{id} + If-Match
  -> mesma autorização e tenant
  -> versão + lock do pátio + novo saldo temporal
  -> capacidades + auditoria
  -> 200 + novo ETag
```

## Endpoints (se houver)

- `POST /api/v1/events/{eventoId}/parking-allocations`: cria uma alocação
  idempotente para um pátio do tenant.
- `PATCH /api/v1/parking-allocations/{id}`: altera somente capacidades e exige
  a versão exata do agregado em `If-Match`.
- `GET /api/v1/events/{eventoId}/availability`: consulta saldo comercial
  projetado; não cria hold.
- `POST /api/v1/inventory-holds`: garante saldo temporário de forma atômica.

## Estrutura de Dados (DTOs, Entidades)

`AlocacaoPatioEvento` contém organização, evento, pátio, início/fim,
capacidades operacional, vendável e reservada, ocupação atual, versão e timestamps. As FKs
compostas impedem relacionamentos entre tenants. O par evento–pátio é único.

## Integrações externas (se houver)

Não há chamada externa. `CapacidadeAlocadaAoEvento` é persistido em [[outbox]]
e ainda não possui dispatcher ou consumidor.

## Tratamento de Erros

- `400`: chave/payload/período/hierarquia ou campo controlado inválido.
- `403`: contexto sem `inventory:manage`.
- `404`: evento, pátio ou alocação invisível no tenant.
- `409`: chave divergente, duplicidade ou sobrealocação.
- `412`: versão divergente; `428`: ausência de `If-Match`.

## Testes (curl ou equivalente)

Há testes de domínio, API, RBAC, tenant, idempotência, compartilhamento do
saldo, sobrealocação, ETag, auditoria, outbox, expiração, cancelamento
compensatório e concorrência real no PostgreSQL.

## Decisões Técnicas

- O lock pessimista ocorre na raiz `PatioInstalacao`, serializando decisões de
  capacidade para todas as alocações da mesma instalação.
- A consulta usa sobreposição de intervalos semiabertos (`início < fim consultado`
  e `fim > início consultado`), permitindo janelas apenas adjacentes.
- `vendável + reservada <= operacional`, e o operacional alocado simultâneo
  não pode exceder a capacidade operacional do pátio.
- Alterar período ou vínculos não está no PATCH; override e consumo real ficam
  para histórias aprovadas posteriores.
- [[acesso]] bloqueia pessimisticamente a alocação antes de incrementar a
  ocupação; capacidade nunca pode ficar abaixo da ocupação atual.

## Módulos relacionados

- [[core]]
- [[organizacoes]]
- [[eventos]]
- [[instalacoes]]
- [[rbac]]
- [[auditoria]]
- [[idempotencia]]
- [[outbox]]
- [[produtos-estacionamento]]
- [[disponibilidade]]
- [[reservas-inventario]]

## Histórico (data + ação)

| Data | Ação |
|---|---|
| 2026-07-30 | Implementa UC706 com alocação temporal e prevenção de sobrealocação. |
| 2026-07-30 | Protege a capacidade vendável contra soma excessiva de quotas de produtos. |
| 2026-07-30 | Vincula lotes de preço à quota do produto e impede períodos ambíguos. |
| 2026-07-30 | Expõe disponibilidade projetada sem consumir inventário. |
| 2026-07-30 | Implementa UC709 e serializa o consumo temporário por produto. |
| 2026-07-30 | UC713 compensa contadores reservado/vendido sob o mesmo lock do produto. |
| 2026-08-01 | UC715 passa a serializar ocupação física na alocação Evento–Pátio. |
