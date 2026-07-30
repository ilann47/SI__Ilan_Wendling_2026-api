> Links: [[core]] · [[eventos]] · [[organizacoes]]

# Outbox

## Objetivo

Persistir fatos de domínio no mesmo commit do agregado para que sua publicação
futura não dependa de efeitos em memória ou de chamada externa na transação.

## Contexto

A fundação persiste `EventoCriado`, `CapacidadeAlocadaAoEvento` e
`ProdutoEstacionamentoPublicado`. Dispatcher, retry, dead-letter lógico e
consumidores serão implementados quando existir o primeiro efeito entre contextos.

## Fluxo (camadas da arquitetura)

```text
EventoService (transação ativa)
  -> OutboxService
  -> envelope + payload mínimo
  -> EventoOutboxRepository
  -> evento_outbox PENDENTE
```

## Endpoints (se houver)

Nenhum endpoint público.

## Estrutura de Dados (DTOs, Entidades)

O envelope contém UUID, tipo, instante UTC, organização, agregado/ID/versão,
correlação, causação opcional, versão de schema, payload JSONB, estado e tentativas.

## Integrações externas (se houver)

Nenhuma nesta história. Não há Kafka, RabbitMQ ou chamada de parceiro.

## Tratamento de Erros

Falha de validação ou persistência da outbox reverte a criação do agregado.
O estado inicial é sempre `PENDENTE`.

## Testes (curl ou equivalente)

O teste de UC701 prova um único envelope `EventoCriado`, versão zero e payload
com o estado `RASCUNHO`, inclusive após replay idempotente.

## Decisões Técnicas

- PostgreSQL é suficiente para a primeira fase; nenhum broker foi antecipado.
- Payloads são allowlists produzidas pelo módulo, não serialização da entidade.
- A unicidade agregado + versão + tipo evita duplicidade do mesmo fato.
- Estado mutável existe apenas para o processamento futuro da entrega.

## Módulos relacionados

- [[core]]
- [[organizacoes]]
- [[eventos]]
- [[inventario]]
- [[produtos-estacionamento]]

## Histórico (data + ação)

| Data | Ação |
|---|---|
| 2026-07-30 | Persiste EventoCriado atomicamente com o agregado Evento. |
| 2026-07-30 | Persiste CapacidadeAlocadaAoEvento atomicamente com a alocação. |
| 2026-07-30 | Persiste ProdutoEstacionamentoPublicado atomicamente com a publicação. |
