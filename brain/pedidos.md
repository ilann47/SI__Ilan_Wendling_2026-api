> Links: [[core]] · [[organizacoes]] · [[eventos]] · [[reservas-inventario]] · [[produtos-estacionamento]] · [[credenciais]] · [[rbac]] · [[auditoria]] · [[idempotencia]] · [[outbox]]

# Pedidos

## Objetivo

Materializar a intenção comercial do comprador com snapshots e total
reprodutíveis, permitir a confirmação manual excepcional e fornecer o snapshot
imutável usado por [[credenciais]], sem antecipar gateway ou acesso.

## Contexto

UC710 está implementada até `AGUARDANDO_PAGAMENTO`. O ator autenticado usa
somente seu próprio hold `MANTIDA`; cliente público, caixa e parceiro terão
identidades próprias em histórias posteriores. Admin e Gestor podem confirmar
um pedido excepcionalmente mediante evidência, deixando-o pendente de
conciliação financeira.

## Fluxo (camadas da arquitetura)

```text
POST /api/v1/orders + Idempotency-Key
  -> JWT contextual + orders:create
  -> PedidoService
  -> ReservaInventarioService.obterMantidaParaPedido (lock do hold)
  -> Pedido + CompradorPedido + ItemPedido
  -> CRIADO -> AGUARDANDO_PAGAMENTO
  -> PedidoRepository -> PostgreSQL V21
  -> auditoria + outbox + idempotência
  -> 201 + Location + ETag

job de expiração
  -> pedidos vencidos sob lock
  -> EXPIRADO + auditoria + PedidoExpirado

POST /api/v1/orders/{id}/manual-confirmation + If-Match + Idempotency-Key
  -> JWT contextual + orders:manual-confirm
  -> lock pedido -> lock produto -> lock hold
  -> hold CONFIRMADA + reservado convertido em vendido
  -> evidência PENDENTE_CONCILIACAO + pedido CONFIRMADO
  -> auditoria + InventarioConfirmado + PedidoConfirmado + idempotência
  -> 202 + ETag
```

## Endpoints (se houver)

- `POST /api/v1/orders`: cria pedido idempotente a partir de um hold próprio.
- `GET /api/v1/orders/{orderId}`: consulta somente pedido do ator autenticado.
- `POST /api/v1/orders/{orderId}/manual-confirmation`: confirmação excepcional
  por Admin/Gestor, com versão esperada, evidência e chave idempotente.

## Estrutura de Dados (DTOs, Entidades)

`Pedido` conserva tenant, evento, hold, ator, número, canal, placa, estado,
valores, moeda e prazo. `CompradorPedido` guarda nome, email normalizado e
documento normalizado. `ItemPedido` congela produto, alocação, pátio, lote, nome,
categoria, direito, janela, quantidade, preço e moeda.

## Integrações externas (se houver)

Não há gateway nesta entrega. `PedidoCriado`, `PedidoAguardandoPagamento` e
`PedidoExpirado`, `InventarioConfirmado` e `PedidoConfirmado` são persistidos
pela [[outbox]]. A confirmação manual não publica `PagamentoAprovado`, porque
não representa retorno de um provedor.

## Tratamento de Erros

- `400`: chave, comprador, placa, canal ou campo controlado inválido.
- `403`: ausência de `orders:create` ou de contexto tenant ativo.
- `404`: hold/pedido invisível, de outro tenant ou de outro ator.
- `409`: hold vencido, liberado, reutilizado, canal divergente ou replay com
  payload diferente.
- `412`: versão do pedido divergente na confirmação manual.
- `428`: `If-Match` ausente na confirmação manual.

## Testes (curl ou equivalente)

Há testes de domínio, API, snapshots, total, idempotência, RBAC, ownership,
campos controlados, canal, hold terminal, expiração, confirmação manual,
concorrência pela confirmação, auditoria, outbox, OpenAPI, Flyway e Hibernate
validate.

## Decisões Técnicas

- O módulo consome `ReservaParaPedido`, contrato imutável fornecido por
  [[reservas-inventario]], sem acessar repositories internos do inventário.
- Um hold origina no máximo um pedido, defendido por aplicação e PostgreSQL.
- O número `PED-` + UUID sem hífens é opaco e único; IDs internos continuam
  `Long`.
- A criação não confirma nem libera o hold. O contador permanece reservado até
  pagamento posterior ou expiração/liberação pelo módulo de inventário.
- A confirmação manual é uma exceção operacional: exige meio, referência de
  evidência e motivo, preserva o ator e nasce `PENDENTE_CONCILIACAO`.
- A transação serializa pedido, produto e hold; somente uma requisição pode
  converter o contador reservado em vendido.
- [[credenciais]] serializa o pedido confirmado e o leva a `ATENDIDO` somente
  depois de emitir todas as unidades compradas.
- O documento é persistido normalizado e retornado somente mascarado.

## Módulos relacionados

- [[core]]
- [[organizacoes]]
- [[eventos]]
- [[reservas-inventario]]
- [[produtos-estacionamento]]
- [[credenciais]]
- [[rbac]]
- [[auditoria]]
- [[idempotencia]]
- [[outbox]]

## Histórico (data + ação)

| Data | Ação |
|---|---|
| 2026-07-30 | Implementa UC710 até pedido aguardando pagamento e expiração automática. |
| 2026-07-30 | Implementa confirmação manual versionada, idempotente e pendente de conciliação. |
| 2026-07-30 | Snapshot do item inclui pátio e pedido atendido depende de todas as credenciais. |
