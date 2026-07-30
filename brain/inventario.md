> Links: [[core]] · [[organizacoes]] · [[eventos]] · [[instalacoes]] · [[rbac]] · [[auditoria]] · [[idempotencia]] · [[outbox]]

# Inventário

## Objetivo

Comprometer capacidade de pátios para eventos sem sobrealocação temporal e
preparar a base para ofertas, produtos e disponibilidade sem antecipá-los.

## Contexto

A História 8 implementa UC706 com `AlocacaoPatioEvento`. Um evento pode usar
vários pátios e um pátio pode atender eventos simultâneos enquanto a soma da
capacidade operacional comprometida não exceder seu limite operacional.

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

## Estrutura de Dados (DTOs, Entidades)

`AlocacaoPatioEvento` contém organização, evento, pátio, início/fim,
capacidades operacional, vendável e reservada, versão e timestamps. As FKs
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
saldo, sobrealocação, ETag, auditoria, outbox e constraints PostgreSQL.

## Decisões Técnicas

- O lock pessimista ocorre na raiz `PatioInstalacao`, serializando decisões de
  capacidade para todas as alocações da mesma instalação.
- A consulta usa sobreposição de intervalos semiabertos (`início < fim consultado`
  e `fim > início consultado`), permitindo janelas apenas adjacentes.
- `vendável + reservada <= operacional`, e o operacional alocado simultâneo
  não pode exceder a capacidade operacional do pátio.
- Alterar período ou vínculos não está no PATCH; override e consumo real ficam
  para histórias aprovadas posteriores.

## Módulos relacionados

- [[core]]
- [[organizacoes]]
- [[eventos]]
- [[instalacoes]]
- [[rbac]]
- [[auditoria]]
- [[idempotencia]]
- [[outbox]]

## Histórico (data + ação)

| Data | Ação |
|---|---|
| 2026-07-30 | Implementa UC706 com alocação temporal e prevenção de sobrealocação. |
