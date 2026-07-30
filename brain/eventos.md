> Links: [[core]] · [[organizacoes]] · [[venues]] · [[rbac]] · [[auditoria]] · [[idempotencia]] · [[outbox]]

# Eventos

## Objetivo

Iniciar e controlar o ciclo comercial e operacional de um evento dentro do
tenant, sem misturar inventário, pedidos, pagamentos ou acesso no agregado.

## Contexto

A História 7 implementa exclusivamente UC701. O evento nasce em `RASCUNHO` e
nenhuma transição posterior é exposta. Alocação de pátios, oferta, inventário,
produtos e publicação continuam nas histórias seguintes.

## Fluxo (camadas da arquitetura)

```text
POST /api/v1/events + Idempotency-Key
  -> JWT contextual + events:create
  -> EventoService
  -> lock idempotente + organização/venue/período/fuso
  -> EventoRepository -> PostgreSQL V15
  -> AuditoriaService + OutboxService + resultado idempotente
  -> 201 + Location + ETag + checklist
```

## Endpoints (se houver)

- `POST /api/v1/events`: cria evento em `RASCUNHO` no tenant autenticado.
- Repetição da mesma chave e payload devolve o agregado original; payload
  divergente com a mesma chave retorna `409`.

## Estrutura de Dados (DTOs, Entidades)

`Evento` possui organização, venue, nome, início/fim, fuso IANA, estado,
`externalId` opcional, versão e timestamps. O DTO de resposta inclui o checklist
inicial: venue presente e alocação, produto, responsável e políticas pendentes.

## Integrações externas (se houver)

Não há chamada externa. `externalId` é preservado para origem parceira futura.
`EventoCriado` é persistido em [[outbox]], ainda sem dispatcher ou broker.

## Tratamento de Erros

- `400`: chave ausente, payload, período, fuso ou campo controlado inválido.
- `403`: contexto ausente ou sem `events:create`.
- `404`: venue/organização fora do tenant visível.
- `409`: chave reutilizada com payload diferente, externalId duplicado ou constraint.

## Testes (curl ou equivalente)

Há testes de domínio, API, tenant, papéis, idempotência, replay, auditoria,
outbox, OpenAPI e constraints PostgreSQL para período, estado e FK composta.

## Decisões Técnicas

- IDs permanecem `Long` para coerência com o monólito atual.
- Instantes são persistidos em `TIMESTAMPTZ`; a resposta os interpreta no fuso do evento.
- A chave é opaca, tem até 100 caracteres e escopo tenant + ator + operação.
- `GESTOR` e `ORGANIZADOR` criam no escopo organizacional; escopo por evento só
  existe depois que o agregado puder receber participantes.
- A enumeração contém a máquina aprovada, mas somente `RASCUNHO` pode ser criado.

## Módulos relacionados

- [[core]]
- [[organizacoes]]
- [[venues]]
- [[rbac]]
- [[auditoria]]
- [[idempotencia]]
- [[outbox]]

## Histórico (data + ação)

| Data | Ação |
|---|---|
| 2026-07-30 | Implementa UC701 com criação tenant-aware, idempotência e EventoCriado. |
