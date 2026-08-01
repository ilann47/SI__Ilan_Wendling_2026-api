> Links: [[core]] · [[organizacoes]] · [[eventos]] · [[instalacoes]] · [[inventario]] · [[pedidos]] · [[credenciais]] · [[rbac]] · [[auditoria]] · [[idempotencia]] · [[outbox]]

# Controle de Acesso

## Objetivo

Decidir e registrar check-in/check-out online de evento sem expor o segredo QR,
controlando direito, presença, sessão e ocupação de forma atômica.

## Contexto

UC715, UC717, UC718 e UC719 estão implementadas para a origem QR de pedido de evento. O evento
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

POST /api/v1/check-outs + Idempotency-Key
  -> JWT contextual + access:checkout
  -> tentativa SAIDA RECEBIDA -> AVALIANDO
  -> QR/credencial + presença/sessão + alocação sob locks
  -> presença DENTRO -> FORA + sessão ABERTA -> ENCERRADA
  -> credencial DENTRO -> CONSUMIDA ou ATIVA conforme política publicada
  -> ocupação decrementada + auditoria + outbox + resposta idempotente

GET /api/v1/access-attempts
  -> JWT contextual + audit:read
  -> filtros tenant-aware por evento, decisão e motivo
  -> paginação keyset por ID decrescente
  -> feed sem token ou hash QR
```

## Endpoints (se houver)

- `POST /api/v1/check-ins`: recebe `qrToken`, `eventId`, `parkingFacilityId` e
  `lane`; retorna decisão, motivo estável, tentativa e IDs dos efeitos.
- `POST /api/v1/check-outs`: usa o mesmo identificador QR e escopo, encerra os
  efeitos abertos e retorna ocupação e estado resultantes.
- `GET /api/v1/access-attempts`: lista tentativas do tenant com cursor,
  `eventId`, `decision`, `reason` e limite de 1 a 100 itens.
- `POST /api/v1/events/{eventoId}/sales-closing`: executa
  `VENDAS_ABERTAS → VENDAS_ENCERRADAS` com `If-Match`.
- `POST /api/v1/events/{eventoId}/operation-start`: executa
  `VENDAS_ENCERRADAS → EM_OPERACAO` com `If-Match` e `access:operate`.
- `POST /api/v1/events/{eventoId}/operation-closing`: bloqueia novas decisões
  depois de comprovar ocupação, presença e sessão abertas iguais a zero.

## Estrutura de Dados (DTOs, Entidades)

`TentativaAcesso` guarda escopo, ator, faixa, direção, origem, credencial/QR
resolvidos, decisão, motivo, ocupação resultante e instantes. `Presenca` representa
o estado físico dentro do evento. `SessaoEstacionamento` preserva pedido, item,
credencial, presença, tentativa, placa opcional e origem `PEDIDO_EVENTO`.
`AlocacaoPatioEvento.ocupacaoAtual` é a projeção operacional concorrente.
Presença e sessão guardam também a tentativa de saída que as encerrou.

## Integrações externas (se houver)

Nenhuma. A resposta informa `barrierCommandRequested = false`; não existe adapter
de cancela ou identidade de dispositivo nesta entrega. Os fatos
`AcessoAutorizado`, `AcessoRecusado`, `CheckinRealizado`, `CheckoutRealizado`,
`MovimentacaoAberta`, `MovimentacaoEncerrada` e `OcupacaoAlterada` são
persistidos em [[outbox]].

## Tratamento de Erros

- `400`: cabeçalho, token estrutural, payload ou campo desconhecido inválido.
- `403`: ausência de `access:checkin`, `access:checkout` ou `access:operate`.
- `403`: feed sem `audit:read`; permissões operacionais não implicam leitura
  organizacional.
- `404`: evento ou alocação invisível no tenant.
- `409`: replay idempotente com payload divergente.
- Recusas de negócio retornam `200` com `RECUSADA` e código estável; não são erro
  de transporte.

## Testes (curl ou equivalente)

Há testes de domínio, contrato HTTP, RBAC, idempotência, segredo QR, decisão
autorizada/recusada, duplicidade, lotação, presença, sessão, ocupação, política
de entrada única/reentrada, auditoria, outbox, Flyway, Hibernate validate,
OpenAPI, feed paginado/filtrado, isolamento tenant e disputa concorrente da
última vaga.

## Decisões Técnicas

- O token em claro existe apenas no request e na resposta original de geração;
  acesso resolve `SHA-256` e nunca persiste/loga o token.
- A alocação é o lock de serialização de ocupação; leitura preliminar usa apenas
  existência para não manter versão JPA obsoleta antes do lock.
- O estado do evento é revalidado depois do lock da alocação para serializar o
  acesso com o encerramento operacional sem criar um lock global por evento.
- A tentativa é persistida em avaliação antes dos efeitos e termina no mesmo
  commit. Falha técnica reverte tudo; recusa de negócio é preservada.
- Constraints compostas defendem organização, evento, pátio, credencial, QR,
  tentativa, presença e sessão também contra escrita fora da aplicação.
- O legado `Movimentacao` não foi alterado; sessão enterprise é aditiva.
- O portão nunca escolhe a política de reentrada; ele consome o snapshot
  versionado no evento e não simula comando de barreira.
- O feed usa `audit:read` somente para Administrador da Organização e Gestor.
  Perfis operacionais aguardam ABAC por evento/pátio/faixa e não recebem visão
  tenant-wide por meio de `access:operate`.

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
| 2026-08-01 | Implementa UC717/UC719 com check-out QR, reentrada explícita e encerramento transacional. |
| 2026-08-01 | Serializa admissões e saídas concorrentes com o encerramento operacional da UC720. |
| 2026-08-01 | Publica feed auditável tenant-aware de tentativas com cursor e filtros. |
