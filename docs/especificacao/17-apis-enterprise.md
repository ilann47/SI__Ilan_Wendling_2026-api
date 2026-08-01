> Links: [[README]] · [[16-casos-de-uso-plataforma]] · [[18-permissoes-rbac]] · [[19-equipamentos-integracoes]]

# 17. Arquitetura de APIs

> Este documento projeta contratos; não descreve endpoints já implementados, exceto quando explicitamente marcado AS-IS.

## 17.1 Contrato geral

- Prefixo público: `/api/v1`.
- Recursos usam substantivos; transições usam sub-recursos/comandos explícitos.
- Alterações aditivas permanecem em `v1`; quebra semântica exige nova versão principal.
- Tenant é resolvido pela identidade e confrontado com o recurso; não é confiado ao corpo.
- `ProblemDetail` RFC 7807 atual é preservado e ampliado.
- Comandos assíncronos retornam `202 Accepted` e recurso de acompanhamento.

### Cabeçalhos

| Cabeçalho | Obrigatoriedade | Regra |
|---|---|---|
| `Authorization` | APIs protegidas | identidade humana, serviço ou dispositivo |
| `X-Trace-Id` | aceito/retornado em todas | correlação ponta a ponta |
| `Idempotency-Key` | eventos, pedidos, holds, pagamentos, reembolsos, credenciais, acesso, equipamento, fiscal e fechamento | escopo por tenant + identidade + operação |
| `If-Match` | mutações concorrentes sensíveis | versão/ETag esperada |
| `ETag` | consulta unitária | versão lógica do agregado |
| `X-Device-Id` | dispositivos | deve corresponder à identidade autenticada |
| `X-Webhook-Signature` | webhook recebido | assinatura e timestamp verificados |

### Códigos HTTP

| Código | Uso |
|---|---|
| `200` | consulta ou comando síncrono concluído |
| `201` | recurso criado com `Location` |
| `202` | processamento assíncrono aceito |
| `204` | comando concluído sem representação |
| `400` | formato/validação estrutural |
| `401` | autenticação ausente ou inválida |
| `403` | sem permissão ou escopo |
| `404` | inexistente no tenant visível |
| `409` | invariante, estado ou idempotência conflitante |
| `412` | versão `If-Match` desatualizada |
| `422` | comando válido recusado por regra de negócio |
| `428` | mutação que exige `If-Match` sem a precondição |
| `429` | limite de uso |
| `503` | dependência crítica indisponível sem contingência |

### Erro padrão

```json
{
  "type": "https://errors.kaneko.example/inventory/sold-out",
  "title": "Inventário esgotado",
  "status": 409,
  "detail": "Não há quantidade disponível para o produto solicitado.",
  "instance": "/api/v1/inventory-holds",
  "errorCode": "INVENTORY_SOLD_OUT",
  "traceId": "correlation-id",
  "retryable": false,
  "violations": []
}
```

## 17.2 Schemas canônicos

### Evento

```json
{
  "id": "evt_...",
  "name": "Final do Campeonato",
  "venueId": "ven_...",
  "startsAt": "2026-10-10T19:00:00-03:00",
  "endsAt": "2026-10-10T23:30:00-03:00",
  "timeZone": "America/Sao_Paulo",
  "status": "RASCUNHO",
  "version": 1
}
```

### Reserva de inventário

```json
{
  "parkingProductId": "pp_...",
  "quantity": 1,
  "channel": "DIRECT_WEB",
  "buyerReference": "optional-reference"
}
```

**Situação implementada:** a resposta inclui `id`, evento/produto, lote, canal,
`status`, `expiresAt`, `unitPrice`, `currency`, `quantity` e `version`.
Disponibilidade consultada é indicativa; somente o commit da criação atômica do
hold garante quantidade. Nesta etapa, o ator autenticado é proprietário do hold.

### Pedido

```json
{
  "inventoryHoldId": "hold_...",
  "buyer": {
    "name": "Comprador",
    "email": "comprador@example.com",
    "document": "optional"
  },
  "vehiclePlate": "ABC1D23",
  "channel": "DIRECT_WEB"
}
```

Resposta inclui número, snapshots dos itens, subtotal, descontos, taxas, total, moeda, estado e prazos.

### Check-in

```json
{
  "eventId": "evt_...",
  "parkingFacilityId": "park_...",
  "laneId": "lane_...",
  "credentialToken": "opaque-token",
  "vehiclePlate": "ABC1D23",
  "occurredAt": "2026-10-10T17:20:00-03:00",
  "offlineSequence": null
}
```

Resposta inclui `decision`, `reasonCode`, `checkInId`, `movementId`, `occupancy`, `barrierCommandId` e instrução ao operador.

## 17.3 Organizações e identidade

| Método e rota | Payload/Parâmetros | Resposta | Autorização/regras |
|---|---|---|---|
| `POST /organizations` | dados empresariais, região, plano | `201 Organization` | Admin plataforma; idempotência |
| `GET /organizations/{id}` | id | `200 Organization` | Admin plataforma/organização no próprio escopo |
| `PATCH /organizations/{id}` | campos aditivos + `If-Match` | `200` | Admin organização; campos de propriedade imutáveis |
| `POST /organizations/{organizationId}/memberships` | somente `userId` | `201 Membership` + `Location`/`ETag` | AS-IS: primeiro vínculo pelo Admin plataforma; demais exigem `users:invite` |
| `GET /organizations/{organizationId}/memberships/{membershipId}` | IDs no path | `200 Membership` + `ETag` | AS-IS: `organizations:admin`; vínculo no tenant ativo |
| `PATCH /organizations/{organizationId}/memberships/{membershipId}` | somente status + `If-Match` | `200 Membership` + `ETag` | AS-IS: `organizations:admin`; preserva ao menos um administrador |
| `GET /me` | token | identidade e organização ativa opcional | qualquer autenticado; contexto presente é revalidado no banco |
| `GET /me/organizations` | token | organizações acessíveis | projeção direta; somente Membership e organização ativas |
| `POST /me/active-organization` | somente `organizationId` | novo JWT contextual | Membership, estado, perfil e versão resolvidos pelo servidor |
| `POST /venues` | AS-IS: organização implícita, nome, endereço, cidade, fuso | `201 Venue`, `Location`, `ETag` | `organizations:admin`; organização/cidade ativas; fuso IANA; nome único no tenant |
| `POST /users/invitations` | email, papel, escopo, validade | `202 Invitation` | Admin/Gestor com capacidade de delegação |
| `PUT /users/{id}/role-assignments/{assignmentId}` | papel, escopo, vigência | `200 Assignment` | Admin; não pode conceder além da própria alçada |
| `DELETE /users/{id}/role-assignments/{assignmentId}` | motivo | `204` | Admin; auditoria obrigatória |
| `GET /me/permissions` | escopo opcional | permissões efetivas | qualquer autenticado |
| `POST /organizations/{organizationId}/memberships/{membershipId}/role-assignments` | `roleCode` | `201 Assignment` | AS-IS: `roles:grant`; mesmo tenant |
| `DELETE /organizations/{organizationId}/memberships/{membershipId}/role-assignments/{assignmentId}` | motivo + `If-Match` | `204` | AS-IS: `roles:revoke`; não remove último administrador |

## 17.4 Instalações e eventos

| Método e rota | Payload/Parâmetros | Resposta | Regras |
|---|---|---|---|
| `POST /parking-facilities` | AS-IS: organização implícita, nome, endereço, cidade, capacidade física/operacional | `201 Patio`, `Location`, `ETag` | `facilities:manage`; organização/cidade ativas; `0 ≤ operacional ≤ física`; nome único no tenant |
| `POST /parking-facilities/{id}/sectors` | AS-IS: código, nome, capacidade e categorias; tenant implícito | `201 Sector`, `Location`, `ETag` | `facilities:manage`; pátio ativo no tenant; código normalizado único; categorias canônicas; soma das capacidades ≤ capacidade física; auditoria/outbox |
| `POST /sectors/{id}/spaces:batch` | AS-IS: 1–500 itens com código, tipo, acessibilidade e posição opcional; tenant implícito | `201` quando todos são criados; `207` com resultado por item em replay/conflito | `facilities:manage`; setor ativo; código normalizado; tipo aceito pelo setor; capacidade; idempotência natural; auditoria/outbox por vaga criada |
| `GET /parking-facilities/{id}/occupancy` | por setor/categoria | projeção atual + atraso | leitura no escopo |
| `POST /events` | AS-IS: schema Evento + `Idempotency-Key`; tenant implícito | `201 Evento`, checklist, `Location`, `ETag` | `events:create`; venue ativo do tenant; período/fuso válidos; replay equivalente devolve o mesmo evento e conflito retorna `409` |
| `PATCH /events/{id}` | AS-IS: `reentryPolicy` + `If-Match` | `200 Evento`, `ETag` | `events:create`; somente `RASCUNHO`; lista fechada; política obrigatória antes da publicação |
| `POST /events/{id}/publication` | AS-IS: confirmação + `If-Match` | `200 Evento`, checklist, `ETag` | `events:publish`; alocação vendável e produto publicado; ausência de versão retorna `428`, divergência `412`; auditoria e outbox atômicas |
| `POST /events/{id}/sales-opening` | AS-IS: `startsAt` opcional + `If-Match` | `200 Evento`, `ETag`; `202` permanece TARGET | `events:publish`; evento publicado; abertura imediata auditada e com outbox; instante futuro retorna `409` até existir scheduler; ausência de versão `428`, divergência `412` |
| `POST /events/{id}/sales-closing` | motivo | `200` | Organizador/Gestor |
| `POST /events/{id}/operation-opening` | checklist | `200` | Supervisor |
| `POST /events/{id}/operation-closing` | AS-IS: `confirmed`, `financialPendingAcknowledged` + `If-Match` | `200 Evento`, `ETag` | `access:operate`; somente `EM_OPERACAO`; bloqueia ocupação, presença ou sessão aberta; registra ressalva financeira, auditoria e outbox |
| `POST /events/{id}/cancellation` | motivo, política | `202` | Gestor; inicia compensações |
| `POST /events/{id}/completion` | fechamentoId | `200` | fechamento aprovado |

## 17.5 Alocações, produtos e inventário

| Método e rota | Payload/Parâmetros | Resposta | Regras |
|---|---|---|---|
| `POST /events/{id}/parking-allocations` | AS-IS: pátio, período, capacidades + `Idempotency-Key`; tenant implícito | `201 Allocation`, `Location`, `ETag` | `inventory:manage`; evento configurável, pátio ativo, janela intersecta evento, hierarquia e saldo simultâneo válidos; replay equivalente devolve a alocação original |
| `PATCH /parking-allocations/{id}` | AS-IS: capacidades + `If-Match` | `200 Allocation`, `ETag` | `inventory:manage`; lista fechada de campos; não reduzir abaixo da hierarquia nem sobrealocar o pátio; versão divergente retorna `412` |
| `POST /events/{id}/parking-products` | AS-IS: nome, categoria, alocação, direito, janela, quota, benefícios/restrições + `Idempotency-Key`; tenant implícito | `201 Product`, `Location`, `ETag` | `pricing:manage`; evento configurável; alocação do evento/tenant; janela contida; soma de quotas não excede vendável; produto nasce em rascunho |
| `POST /parking-products/{id}/publication` | AS-IS: confirmação + `If-Match` | `200 Product`, `ETag` | `pricing:manage`; produto em rascunho com ao menos um lote; ausência de versão retorna `428`, versão divergente `412`; auditoria e outbox atômicas |
| `POST /parking-products/{id}/price-tiers` | AS-IS: nome, preço, moeda, período, quantidade, prioridade + `Idempotency-Key`; tenant implícito | `201 Tier`, `Location`, `ETag` | `pricing:manage`; produto em rascunho; moeda da organização; quantidade ≤ quota; sobreposição somente com prioridades distintas; replay equivalente devolve o lote original |
| `GET /events/{id}/availability` | AS-IS: filtro opcional de categoria; tenant implícito | produtos, lote/preço vigente, saldo projetado e instante de cálculo | exige contexto ativo e evento publicado ou com vendas abertas; maior prioridade numérica vence; não garante hold; filtro por canal permanece TARGET |
| `POST /inventory-holds` | AS-IS: produto, quantidade, canal, buyerReference + `Idempotency-Key` | `201 Hold`, `Location`, `ETag` | `inventory:hold`; evento com vendas abertas; produto/lote vigentes; lock pessimista; idempotência; auditoria/outbox |
| `POST /inventory-holds/{id}/release` | AS-IS: motivo | `200 Hold`, `ETag` | proprietário autenticado; devolve saldo uma vez; reserva expirada é materializada |
| `GET /inventory-holds/{id}` | AS-IS: id | `200 Hold`, `ETag` | proprietário autenticado; materializa expiração vencida; outro ator/tenant recebe `404` |

## 17.6 Pedidos, pagamentos e credenciais

| Método e rota | Payload/Parâmetros | Resposta | Regras |
|---|---|---|---|
| `POST /orders` | AS-IS: inventoryHoldId, buyer, vehiclePlate, channel + `Idempotency-Key` | `201 Order`, `Location`, `ETag` | `orders:create`; hold mantido do próprio ator; canal coerente; snapshots e total derivados; idempotência, auditoria e outbox |
| `GET /orders/{id}` | AS-IS: id | `200 Order`, `ETag` | tenant e proprietário autenticado com `orders:create`; outro ator/tenant recebe `404` |
| `GET /orders` | cursor, evento, estado, canal | página por cursor | escopo e filtros publicados |
| `POST /orders/{id}/manual-confirmation` | AS-IS: paymentMethod, evidenceReference, reason + `If-Match` + `Idempotency-Key` | `202 Order` + `ETag` | `orders:manual-confirm`; Admin/Gestor; pedido aguardando e hold vigente; converte reservado em vendido; evidência `PENDENTE_CONCILIACAO`; auditoria/outbox/idempotência; não publica aprovação de gateway |
| `POST /orders/{id}/cancellation` | AS-IS: reason + `If-Match` + `Idempotency-Key` | `202 CancellationProcess` + novo `ETag` do pedido | `orders:cancel`; tenant e proprietário; cancela diretamente antes da confirmação ou marca `EM_REEMBOLSO`; compensa inventário uma vez, bloqueia credenciais/QR quando aplicável; auditoria/outbox/idempotência; não executa reembolso |
| `POST /orders/{id}/payments` | método, provider, return URLs | `201/202 Payment` | sem cartão bruto; idempotência |
| `GET /payments/{id}` | id | `200 Payment` | cliente próprio ou financeiro |
| `POST /payments/{id}/refunds` | valor, motivo | `202 Refund` | alçada e saldo |
| `POST /payment-providers/{provider}/webhooks` | payload do provedor | `200/202` | assinatura, replay, inbox |
| `POST /orders/{id}/credentials` | AS-IS: preferredMedium + `Idempotency-Key` | `201 Credential` + `Location` + `ETag` | `credentials:issue`; pedido próprio confirmado; uma unidade por chamada; última unidade atende pedido; código público não autoriza acesso |
| `GET /credentials/{id}` | AS-IS: id | `200 Credential` + `ETag` | tenant e proprietário autenticado com `credentials:issue`; outro ator/tenant recebe `404` |
| `POST /credentials/{id}/qr-code` | AS-IS: id + `If-Match` | `201 QRRepresentation` + `Location` + novo `ETag` da credencial | `credentials:issue`; credencial própria ativa, meio QR, não expirada e versão atual; token opaco retornado uma única vez; apenas hash persistido |
| `POST /credentials/{id}/reissuance` | motivo | `201 Credential` | anterior revogada |
| `POST /credentials/{id}/blocking` | motivo | `200` | supervisor/atendimento/fraude |
| `POST /credentials/{id}/unblocking` | motivo | `200` | estado e alçada |
| `PUT /credentials/{id}/vehicle` | placa e versão | `200` | política de troca |

## 17.7 Acesso, movimentação e caixa

| Método e rota | Payload/Parâmetros | Resposta | Regras |
|---|---|---|---|
| `POST /access-validations` | token/placa, evento, pátio, faixa | decisão sem consumo | operação/dispositivo |
| `POST /check-ins` | AS-IS: `qrToken`, `eventId`, `parkingFacilityId`, `lane` + `Idempotency-Key` | `200` decisão, tentativa e IDs dos efeitos | `access:checkin`; QR por hash; evento em operação; janela; tenant/pátio; idempotência; antiduplicidade; capacidade serializada; sem comando de barreira nesta etapa |
| `POST /check-outs` | AS-IS: `qrToken`, `eventId`, `parkingFacilityId`, `lane` + `Idempotency-Key` | `200` decisão, tentativa, efeitos, ocupação e estado da credencial | `access:checkout`; evento em operação; QR por hash; presença/sessão abertas; política publicada; locks; idempotência; sem comando físico de barreira |
| `POST /manual-admissions` | dados, motivo, aprovador | `201 Attempt` | exceção auditada |
| `GET /access-attempts` | cursor, evento, decisão, motivo | feed | operação/auditoria |
| `POST /movements` | somente fluxo legado/controlado | `201 Movement` | origem de direito obrigatória no target |
| `POST /movements/{id}/closing` | saída, regra | `200 Movement` | aberta |
| `POST /movements/{id}/cancellation` | motivo | `200` | aberta e supervisor conforme policy |
| `POST /cash-shifts` | terminal, fundo | `201 Shift` | um turno incompatível |
| `POST /cash-shifts/{id}/closing` | contagem e justificativas | `200/202` | divergência pode exigir aprovação |
| `POST /cash-shifts/{id}/cash-outs` | valor, motivo | `201` | alçada e caixa aberto |

## 17.8 Mensalistas, PDV, fiscal e financeiro

| Método e rota | Payload/Parâmetros | Resposta | Regras |
|---|---|---|---|
| `POST /subscription-plans` | regras, preço, pátios | `201 Plan` | Gestor |
| `POST /subscriptions` | cliente, plano, veículos, vigência | `201 Contract` | limites/vigência |
| `POST /subscriptions/{id}/activation` | confirmação | `200` | elegível |
| `POST /subscriptions/{id}/suspension` | motivo | `200` | Gestor/Financeiro/policy |
| `POST /subscriptions/{id}/renewal` | nova vigência | `201 Renewal` | contrato elegível |
| `POST /pos-sales` | caixa, itens, cliente | `201 Sale` | estoque/caixa |
| `POST /pos-sales/{id}/payment` | meios e valores | `200/202` | soma e gateway |
| `POST /pos-sales/{id}/cancellation` | motivo | `202` | compensações |
| `POST /fiscal-documents` | origem e tipo | `202 FiscalDocument` | idempotência |
| `POST /fiscal-documents/{id}/cancellation` | motivo | `202` | prazo/alçada |
| `POST /receivables/{id}/settlements` | valor, data, origem | `201 Settlement` | ≤ saldo |
| `POST /payables/{id}/settlements` | valor, data, origem | `201 Settlement` | ≤ saldo |
| `POST /reconciliations` | período/fontes | `202 Reconciliation` | Financeiro |
| `POST /events/{id}/reconciliation` | política | `202` | operação encerrada |
| `GET /events/{id}/income-statement` | versão/preliminar | DRE + lineage | Financeiro/Gestor/Auditor |

## 17.9 Equipamentos e integrações

| Método e rota | Payload/Parâmetros | Resposta | Regras |
|---|---|---|---|
| `POST /devices` | identidade, tipo, capacidades | `201 Device` | Admin/Gestor técnico |
| `POST /devices/{id}/installations` | local e vigência | `201 Installation` | compatibilidade |
| `POST /devices/{id}/commands` | capacidade, parâmetros, deadline | `202 Command` | idempotência e autorização |
| `GET /device-commands/{id}` | id | estado e execuções | operação/suporte |
| `POST /edge-gateways/{id}/synchronizations` | versão e lotes | `202 Sync` | identidade de dispositivo |
| `GET /events/{id}/offline-packages/latest` | site/device | pacote assinado | somente edge provisionado |
| `POST /integrations` | tipo, provider, escopos, secretRef | `201 Connection` | Admin |
| `POST /integrations/{id}/test` | nenhum segredo em retorno | diagnóstico | Admin |
| `GET /webhook-deliveries` | cursor/status/destino | feed | integração/auditoria |
| `POST /webhook-deliveries/{id}/replay` | motivo | `202` | Admin técnico; idempotência downstream |

## 17.10 Idempotência e concorrência

A identidade idempotente é:

```text
organização + cliente/dispositivo + operação lógica + Idempotency-Key
```

- Primeira execução armazena hash canônico do payload e resposta.
- Repetição com mesmo payload devolve exatamente o resultado lógico anterior.
- Mesma chave com payload diferente retorna `409 IDEMPOTENCY_KEY_REUSED`.
- Inventário usa atualização atômica e impede saldo negativo.
- Agregados sensíveis usam `ETag`/versão.
- Webhook entra em inbox antes dos efeitos.
- Eventos saem por outbox no mesmo commit.

## 17.11 Eventos públicos e webhooks

Eventos mínimos: `event.published.v1`, `inventory.changed.v1`, `order.confirmed.v1`, `payment.approved.v1`, `payment.refunded.v1`, `credential.issued.v1`, `access.authorized.v1`, `access.denied.v1`, `checkin.completed.v1`, `movement.closed.v1`, `device.offline.v1` e `event.reconciled.v1`.

Entrega é ao menos uma vez. Destinos deduplicam `eventId`. O Kaneko mantém assinatura, timestamp, tentativas, backoff com jitter, dead-letter/quarentena e reprocessamento auditado.

## 17.12 Compatibilidade com API atual

Os endpoints atuais `/api/**` permanecem durante transição. Uma facade de compatibilidade traduz chamadas legadas para casos de uso novos. Não deve haver migração “big bang”. Deprecações exigem aviso, telemetria de uso, prazo e documentação de substituição.
