> Links: [[core]] · [[organizacoes]] · [[eventos]] · [[reservas-inventario]] · [[produtos-estacionamento]] · [[rbac]] · [[auditoria]] · [[idempotencia]] · [[outbox]]

# Pedidos

## Objetivo

Materializar a intenção comercial do comprador com snapshots e total
reprodutíveis, sem antecipar pagamento, credencial ou acesso.

## Contexto

UC710 está implementada até `AGUARDANDO_PAGAMENTO`. O ator autenticado usa
somente seu próprio hold `MANTIDA`; cliente público, caixa e parceiro terão
identidades próprias em histórias posteriores.

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
```

## Endpoints (se houver)

- `POST /api/v1/orders`: cria pedido idempotente a partir de um hold próprio.
- `GET /api/v1/orders/{orderId}`: consulta somente pedido do ator autenticado.

## Estrutura de Dados (DTOs, Entidades)

`Pedido` conserva tenant, evento, hold, ator, número, canal, placa, estado,
valores, moeda e prazo. `CompradorPedido` guarda nome, email normalizado e
documento normalizado. `ItemPedido` congela produto, alocação, lote, nome,
categoria, direito, janela, quantidade, preço e moeda.

## Integrações externas (se houver)

Não há gateway nesta entrega. `PedidoCriado`, `PedidoAguardandoPagamento` e
`PedidoExpirado` são persistidos pela [[outbox]].

## Tratamento de Erros

- `400`: chave, comprador, placa, canal ou campo controlado inválido.
- `403`: ausência de `orders:create` ou de contexto tenant ativo.
- `404`: hold/pedido invisível, de outro tenant ou de outro ator.
- `409`: hold vencido, liberado, reutilizado, canal divergente ou replay com
  payload diferente.

## Testes (curl ou equivalente)

Há testes de domínio, API, snapshots, total, idempotência, RBAC, ownership,
campos controlados, canal, hold terminal, expiração, auditoria, outbox, OpenAPI,
Flyway e Hibernate validate.

## Decisões Técnicas

- O módulo consome `ReservaParaPedido`, contrato imutável fornecido por
  [[reservas-inventario]], sem acessar repositories internos do inventário.
- Um hold origina no máximo um pedido, defendido por aplicação e PostgreSQL.
- O número `PED-` + UUID sem hífens é opaco e único; IDs internos continuam
  `Long`.
- A criação não confirma nem libera o hold. O contador permanece reservado até
  pagamento posterior ou expiração/liberação pelo módulo de inventário.
- O documento é persistido normalizado e retornado somente mascarado.

## Módulos relacionados

- [[core]]
- [[organizacoes]]
- [[eventos]]
- [[reservas-inventario]]
- [[produtos-estacionamento]]
- [[rbac]]
- [[auditoria]]
- [[idempotencia]]
- [[outbox]]

## Histórico (data + ação)

| Data | Ação |
|---|---|
| 2026-07-30 | Implementa UC710 até pedido aguardando pagamento e expiração automática. |
