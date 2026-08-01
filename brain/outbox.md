> Links: [[core]] · [[eventos]] · [[organizacoes]] · [[instalacoes]] · [[reservas-inventario]] · [[pedidos]] · [[acesso]]

# Outbox

## Objetivo

Persistir fatos de domínio no mesmo commit do agregado para que sua publicação
futura não dependa de efeitos em memória ou de chamada externa na transação.

## Contexto

A fundação persiste `SetorCriado`, `EventoCriado`, `EventoConfigurado`, `EventoPublicado`,
`VendasDoEventoAbertas`, `CapacidadeAlocadaAoEvento` e
`ProdutoEstacionamentoPublicado`, além de `InventarioReservado`,
`ReservaExpirada`, `InventarioLiberado`, `InventarioConfirmado`, `PedidoCriado`,
`PedidoAguardandoPagamento`, `PedidoExpirado`, `PedidoConfirmado`,
`CredencialEmitida`, `PedidoAtendido`, `QRCodeGerado`, `PedidoCancelado`,
`AcessoAutorizado`, `AcessoRecusado`, `CheckinRealizado`, `MovimentacaoAberta` e
`OcupacaoAlterada`. Dispatcher, retry, dead-letter lógico e consumidores ainda
não existem; os efeitos síncronos do check-in permanecem na mesma transação.

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
- [[instalacoes]]
- [[eventos]]
- [[inventario]]
- [[produtos-estacionamento]]
- [[reservas-inventario]]
- [[pedidos]]
- [[credenciais]]

## Histórico (data + ação)

| Data | Ação |
|---|---|
| 2026-07-30 | Persiste EventoCriado atomicamente com o agregado Evento. |
| 2026-07-30 | Persiste CapacidadeAlocadaAoEvento atomicamente com a alocação. |
| 2026-07-30 | Persiste ProdutoEstacionamentoPublicado atomicamente com a publicação. |
| 2026-07-30 | Persiste EventoConfigurado e EventoPublicado nas transições versionadas. |
| 2026-07-30 | Persiste VendasDoEventoAbertas na abertura comercial versionada. |
| 2026-07-30 | Persiste reserva, expiração e liberação de inventário atomicamente. |
| 2026-07-30 | Persiste criação, espera e expiração de pedido atomicamente. |
| 2026-07-30 | Persiste confirmação de inventário e pedido manual sem simular pagamento aprovado. |
| 2026-07-30 | Persiste emissão de credencial e atendimento integral do pedido. |
| 2026-07-30 | Persiste QRCodeGerado sem incluir token ou hash no payload. |
| 2026-07-30 | Persiste PedidoCancelado e InventarioLiberado na compensação UC713. |
| 2026-08-01 | Persiste decisões e efeitos do check-in online sem segredo QR. |
| 2026-08-01 | Persiste política, check-out, encerramento da sessão e ocupação resultante. |
| 2026-08-01 | Persiste `OperacaoDoEventoEncerrada` com snapshot e ressalva financeira. |
| 2026-08-01 | Persiste `SetorCriado` atomicamente com o setor físico. |
