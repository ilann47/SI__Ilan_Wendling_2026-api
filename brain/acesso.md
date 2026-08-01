> Links: [[core]] · [[organizacoes]] · [[eventos]] · [[instalacoes]] · [[inventario]] · [[pedidos]] · [[credenciais]] · [[rbac]] · [[auditoria]] · [[idempotencia]] · [[outbox]]

# Controle de Acesso

## Objetivo

Decidir e registrar check-in online de evento sem expor o segredo QR, consumindo
o direito e materializando presença, sessão e ocupação de forma atômica.

## Contexto

UC715 e UC718 estão implementadas para a origem QR de pedido de evento. O evento
precisa alcançar `EM_OPERACAO` pelos comandos versionados de encerramento de
vendas e início da operação. Placa, RFID, override, hardware e offline não foram
antecipados.

## Fluxo (camadas da arquitetura)

```text
POST /api/v1/check-ins + Idempotency-Key
  -> JWT contextual + access:checkin
  -> tentativa RECEBIDA -> AVALIANDO
  -> evento/alocação/janelas + QR por SHA-256 + credencial com locks
  -> recusa terminal auditada; ou
  -> credencial ATIVA -> DENTRO
  -> Presenca DENTRO + SessaoEstacionamento ABERTA
  -> ocupação da alocação incrementada sob lock pessimista
  -> auditoria + outbox + resposta idempotente
```

## Endpoints (se houver)

- `POST /api/v1/check-ins`: recebe `qrToken`, `eventId`, `parkingFacilityId` e
  `lane`; retorna decisão, motivo estável, tentativa e IDs dos efeitos.
- `POST /api/v1/events/{eventoId}/sales-closing`: executa
  `VENDAS_ABERTAS → VENDAS_ENCERRADAS` com `If-Match`.
- `POST /api/v1/events/{eventoId}/operation-start`: executa
  `VENDAS_ENCERRADAS → EM_OPERACAO` com `If-Match` e `access:operate`.

## Estrutura de Dados (DTOs, Entidades)

`TentativaAcesso` guarda escopo, ator, faixa, direção, origem, credencial/QR
resolvidos, decisão, motivo, ocupação resultante e instantes. `Presenca` representa
o estado físico dentro do evento. `SessaoEstacionamento` preserva pedido, item,
credencial, presença, tentativa, placa opcional e origem `PEDIDO_EVENTO`.
`AlocacaoPatioEvento.ocupacaoAtual` é a projeção operacional concorrente.

## Integrações externas (se houver)

Nenhuma. A resposta informa `barrierCommandRequested = false`; não existe adapter
de cancela ou identidade de dispositivo nesta entrega. Os fatos
`AcessoAutorizado`, `AcessoRecusado`, `CheckinRealizado`, `MovimentacaoAberta` e
`OcupacaoAlterada` são persistidos em [[outbox]].

## Tratamento de Erros

- `400`: cabeçalho, token estrutural, payload ou campo desconhecido inválido.
- `403`: ausência de `access:checkin`/`access:operate`.
- `404`: evento ou alocação invisível no tenant.
- `409`: replay idempotente com payload divergente.
- Recusas de negócio retornam `200` com `RECUSADA` e código estável; não são erro
  de transporte.

## Testes (curl ou equivalente)

Há testes de domínio, contrato HTTP, RBAC, idempotência, segredo QR, decisão
autorizada/recusada, duplicidade, lotação, presença, sessão, ocupação, auditoria,
outbox, Flyway, Hibernate validate, OpenAPI e disputa concorrente da última vaga.

## Decisões Técnicas

- O token em claro existe apenas no request e na resposta original de geração;
  acesso resolve `SHA-256` e nunca persiste/loga o token.
- A alocação é o lock de serialização de ocupação; leitura preliminar usa apenas
  existência para não manter versão JPA obsoleta antes do lock.
- A tentativa é persistida em avaliação antes dos efeitos e termina no mesmo
  commit. Falha técnica reverte tudo; recusa de negócio é preservada.
- Constraints compostas defendem organização, evento, pátio, credencial, QR,
  tentativa, presença e sessão também contra escrita fora da aplicação.
- O legado `Movimentacao` não foi alterado; sessão enterprise é aditiva.

## Módulos relacionados

- [[core]]
- [[eventos]]
- [[inventario]]
- [[credenciais]]
- [[rbac]]
- [[auditoria]]
- [[idempotencia]]
- [[outbox]]

## Histórico (data + ação)

| Data | Ação |
|---|---|
| 2026-08-01 | Implementa UC715/UC718 com check-in QR online, presença, sessão e ocupação concorrente. |
