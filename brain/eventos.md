> Links: [[core]] · [[organizacoes]] · [[venues]] · [[inventario]] · [[produtos-estacionamento]] · [[rbac]] · [[auditoria]] · [[idempotencia]] · [[outbox]]

# Eventos

## Objetivo

Iniciar e controlar o ciclo comercial e operacional de um evento dentro do
tenant, sem misturar inventário, pedidos, pagamentos ou acesso no agregado.

## Contexto

UC701 cria o evento em `RASCUNHO`. [[inventario]] associa pátios sem transferir
essa responsabilidade ao agregado e [[produtos-estacionamento]] publica a oferta.
UC702 publica o evento somente após validar capacidade vendável e produto
publicado, sem abrir vendas ou antecipar pedidos e acesso.

## Fluxo (camadas da arquitetura)

```text
POST /api/v1/events + Idempotency-Key
  -> JWT contextual + events:create
  -> EventoService
  -> lock idempotente + organização/venue/período/fuso
  -> EventoRepository -> PostgreSQL V15
  -> AuditoriaService + OutboxService + resultado idempotente
  -> 201 + Location + ETag + checklist

POST /api/v1/events/{eventoId}/publication + If-Match
  -> JWT contextual + events:publish
  -> EventoService + lock pessimista do agregado
  -> alocação vendável + produto publicado no tenant
  -> RASCUNHO -> CONFIGURADO -> PUBLICADO
  -> AuditoriaService + EventoConfigurado/EventoPublicado na outbox
  -> 200 + ETag + checklist pronto
```

## Endpoints (se houver)

- `POST /api/v1/events`: cria evento em `RASCUNHO` no tenant autenticado.
- `POST /api/v1/events/{eventoId}/publication`: publica evento pronto usando a
  versão atual.
- Repetição da mesma chave e payload devolve o agregado original; payload
  divergente com a mesma chave retorna `409`.

## Estrutura de Dados (DTOs, Entidades)

`Evento` possui organização, venue, nome, início/fim, fuso IANA, estado,
`externalId` opcional, versão e timestamps. O DTO de resposta inclui checklist;
na criação apenas venue está pronto e, na publicação, todos os requisitos da
primeira entrega ficam satisfeitos.

## Integrações externas (se houver)

Não há chamada externa. `externalId` é preservado para origem parceira futura.
`EventoCriado`, `EventoConfigurado` e `EventoPublicado` são persistidos em
[[outbox]], ainda sem dispatcher ou broker.

## Tratamento de Erros

- `400`: chave ausente, payload, período, fuso ou campo controlado inválido.
- `403`: contexto ausente ou sem `events:create`.
- `404`: venue/organização fora do tenant visível.
- `409`: chave reutilizada com payload diferente, externalId duplicado ou constraint.
- `409`: publicação sem capacidade vendável ou produto publicado.
- `428`: publicação sem `If-Match`.
- `412`: versão esperada divergente ou conflito otimista.

## Testes (curl ou equivalente)

Há testes de domínio, API, tenant, papéis, idempotência, replay, auditoria,
outbox, publicação, RBAC, concorrência, OpenAPI e constraints PostgreSQL para
período, estado e FK composta.

## Decisões Técnicas

- IDs permanecem `Long` para coerência com o monólito atual.
- Instantes são persistidos em `TIMESTAMPTZ`; a resposta os interpreta no fuso do evento.
- A chave é opaca, tem até 100 caracteres e escopo tenant + ator + operação.
- `GESTOR` e `ORGANIZADOR` criam no escopo organizacional; escopo por evento só
  existe depois que o agregado puder receber participantes.
- A publicação percorre e persiste `CONFIGURADO` antes de `PUBLICADO`; cada
  transição incrementa a versão e produz seu fato na outbox.
- O Gestor autenticado assume a responsabilidade da publicação nesta entrega.
  A política permanece fechada: publicar não abre vendas nem habilita acesso;
  esses comandos futuros exigirão seus contratos explícitos.

## Módulos relacionados

- [[core]]
- [[organizacoes]]
- [[venues]]
- [[rbac]]
- [[auditoria]]
- [[idempotencia]]
- [[outbox]]
- [[inventario]]
- [[produtos-estacionamento]]

## Histórico (data + ação)

| Data | Ação |
|---|---|
| 2026-07-30 | Implementa UC701 com criação tenant-aware, idempotência e EventoCriado. |
| 2026-07-30 | Passa a fornecer evento configurável para a alocação temporal de pátios em [[inventario]]. |
| 2026-07-30 | Implementa UC702 com checklist, versão, auditoria e outbox transacional. |
