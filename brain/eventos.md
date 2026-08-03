> Links: [[core]] · [[organizacoes]] · [[venues]] · [[inventario]] · [[produtos-estacionamento]] · [[acesso]] · [[rbac]] · [[auditoria]] · [[idempotencia]] · [[outbox]]

# Eventos

## Objetivo

Iniciar e controlar o ciclo comercial e operacional de um evento dentro do
tenant, sem misturar inventário, pedidos, pagamentos ou acesso no agregado.

## Contexto

UC701 cria o evento em `RASCUNHO`. [[inventario]] associa pátios sem transferir
essa responsabilidade ao agregado e [[produtos-estacionamento]] publica a oferta.
UC702 publica o evento somente após validar capacidade vendável e produto
publicado. Comandos explícitos abrem e encerram vendas; o Supervisor inicia a
operação que habilita [[acesso]]. Antes da publicação, uma política explícita de
reentrada é definida sem default comercial.

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

POST /api/v1/events/{eventoId}/sales-opening + If-Match
  -> JWT contextual + events:publish
  -> EventoService + lock pessimista do agregado
  -> PUBLICADO -> VENDAS_ABERTAS
  -> AuditoriaService + VendasDoEventoAbertas na outbox
  -> 200 + ETag

POST /api/v1/events/{eventoId}/sales-closing + If-Match
  -> VENDAS_ABERTAS -> VENDAS_ENCERRADAS
  -> auditoria + VendasDoEventoEncerradas

POST /api/v1/events/{eventoId}/operation-start + If-Match
  -> access:operate
  -> VENDAS_ENCERRADAS -> EM_OPERACAO
  -> auditoria + OperacaoDoEventoIniciada

POST /api/v1/events/{eventoId}/operation-closing + If-Match
  -> access:operate + reconhecimento da pendência financeira
  -> locks das alocações + ocupação/presença/sessão zeradas
  -> EM_OPERACAO -> OPERACAO_ENCERRADA
  -> auditoria + OperacaoDoEventoEncerrada
```

`PATCH /api/v1/events/{eventoId}` define `reentryPolicy` somente em `RASCUNHO`,
incrementa o ETag e registra auditoria/outbox. A publicação rejeita política
ausente.

## Endpoints (se houver)

- `POST /api/v1/events`: cria evento em `RASCUNHO` no tenant autenticado.
- `PATCH /api/v1/events/{eventoId}`: define `ENTRADA_UNICA` ou
  `REENTRADA_PERMITIDA` com `If-Match`.
- `POST /api/v1/events/{eventoId}/publication`: publica evento pronto usando a
  versão atual.
- `POST /api/v1/events/{eventoId}/sales-opening`: abre vendas imediatamente
  usando a versão atual; agendamento ainda não é aceito.
- `POST /api/v1/events/{eventoId}/sales-closing`: encerra vendas.
- `POST /api/v1/events/{eventoId}/operation-start`: inicia a operação de acesso.
- `POST /api/v1/events/{eventoId}/operation-closing`: encerra somente a parte
  operacional, mantendo a conciliação financeira explicitamente pendente.
- Repetição da mesma chave e payload devolve o agregado original; payload
  divergente com a mesma chave retorna `409`.

## Estrutura de Dados (DTOs, Entidades)

`Evento` possui organização, venue, nome, início/fim, fuso IANA, estado,
política de reentrada,
`externalId` opcional, versão e timestamps. O DTO de resposta inclui checklist;
na criação apenas venue está pronto e, na publicação, todos os requisitos da
primeira entrega ficam satisfeitos.

## Integrações externas (se houver)

Não há chamada externa. `externalId` é preservado para origem parceira futura.
`EventoCriado`, `EventoConfigurado`, `EventoPublicado` e
`VendasDoEventoAbertas` são persistidos em [[outbox]], ainda sem dispatcher ou
broker.

## Tratamento de Erros

- `400`: chave ausente, payload, período, fuso ou campo controlado inválido.
- `403`: contexto ausente ou sem `events:create`.
- `404`: venue/organização fora do tenant visível.
- `409`: chave reutilizada com payload diferente, externalId duplicado ou constraint.
- `409`: publicação sem capacidade vendável ou produto publicado.
- `409`: abertura fora de `PUBLICADO` ou solicitada para instante futuro.
- `428`: publicação ou abertura sem `If-Match`.
- `412`: versão esperada divergente ou conflito otimista.

## Testes (curl ou equivalente)

Há testes de domínio, API, tenant, papéis, idempotência, replay, auditoria,
outbox, publicação, abertura de vendas, RBAC, concorrência, OpenAPI e constraints
PostgreSQL para período, estado e FK composta.

## Decisões Técnicas

- IDs permanecem `Long` para coerência com o monólito atual.
- Instantes são persistidos em `TIMESTAMPTZ`; a resposta os interpreta no fuso do evento.
- A chave é opaca, tem até 100 caracteres e escopo tenant + ator + operação.
- `GESTOR` e `ORGANIZADOR` criam no escopo organizacional; escopo por evento só
  existe depois que o agregado puder receber participantes.
- A publicação percorre e persiste `CONFIGURADO` antes de `PUBLICADO`; cada
  transição incrementa a versão e produz seu fato na outbox.
- O Gestor autenticado assume a responsabilidade da publicação nesta entrega.
  Publicar não abre vendas nem habilita acesso; a exposição comercial exige o
  comando versionado próprio e acesso permanece fora deste escopo.
- `startsAt` futuro é rejeitado até existir scheduler transacional; aceitar e
  esquecer um agendamento violaria o contrato operacional.
- Encerramento de vendas preserva `events:publish`; início operacional exige o
  papel Supervisor com `access:operate` e não ocorre implicitamente no check-in.
- A política não é inferida nem escolhida pelo operador no portão; fica
  versionada no agregado antes da publicação.
- Repetir o PATCH com a mesma política e a versão atual é um no-op idempotente:
  não incrementa versão nem duplica auditoria ou fato na outbox.
- O encerramento operacional não cria números financeiros. O reconhecimento da
  ressalva fica no contrato, auditoria e outbox até existirem fontes canônicas.

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
| 2026-07-30 | Implementa abertura imediata `PUBLICADO → VENDAS_ABERTAS` com ETag, auditoria e outbox. |
| 2026-08-01 | Implementa fechamento de vendas e início explícito de operação para [[acesso]]. |
| 2026-08-01 | Implementa política de reentrada explícita e imutável após o rascunho. |
| 2026-08-01 | Implementa encerramento operacional concorrente e ressalva financeira explícita da UC720. |
| 2026-08-03 | Evita duplicidade de outbox ao repetir a mesma política de reentrada. |
