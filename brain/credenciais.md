> Links: [[core]] · [[organizacoes]] · [[pedidos]] · [[instalacoes]] · [[eventos]] · [[rbac]] · [[auditoria]] · [[idempotencia]] · [[outbox]]

# Credenciais

## Objetivo

Materializar cada unidade de estacionamento comprada como direito de acesso
tenant-aware, independente da futura representação QR, placa ou RFID.

## Contexto

UC711 está implementada para pedidos confirmados do próprio ator. Cada chamada
emite uma credencial para a próxima unidade ainda não atendida. Quando todas as
unidades recebem credencial, o pedido transiciona para `ATENDIDO`. UC712, token
QR, bloqueio, reemissão e consumo pertencem às histórias seguintes.

## Fluxo (camadas da arquitetura)

```text
POST /api/v1/orders/{orderId}/credentials + Idempotency-Key
  -> JWT contextual + credentials:issue
  -> CredencialAcessoService
  -> lock pessimista do pedido próprio CONFIRMADO
  -> próxima unidade sem credencial
  -> PENDENTE -> ATIVA antes da resposta
  -> CredencialAcessoRepository -> PostgreSQL V23/V24
  -> auditoria + CredencialEmitida + idempotência
  -> última unidade: pedido ATENDIDO + PedidoAtendido
  -> 201 + Location + ETag
```

## Endpoints (se houver)

- `POST /api/v1/orders/{orderId}/credentials`: emite a próxima unidade de um
  pedido próprio confirmado.
- `GET /api/v1/credentials/{credentialId}`: consulta credencial do próprio ator.

## Estrutura de Dados (DTOs, Entidades)

`CredencialAcesso` congela organização, evento, pátio, pedido, item, proprietário,
código público não autorizador, número da unidade, origem, categoria, direito,
validade, meio preferido, placa não ambígua, estado e versão. O `ItemPedido`
passa a congelar também o pátio resolvido da alocação.

## Integrações externas (se houver)

Nenhuma. `CredencialEmitida` e `PedidoAtendido` são gravados na [[outbox]]. O
código público identifica a credencial, mas não é token de acesso e não autoriza
entrada. QR e entrega serão implementados em UC712.

## Tratamento de Erros

- `400`: chave ausente/inválida, meio ausente ou campo controlado.
- `403`: ausência de `credentials:issue` ou tenant ativo.
- `404`: pedido/credencial de outro ator ou tenant.
- `409`: pedido não confirmado/atendido, unidades esgotadas, replay divergente
  ou preferência por placa em pedido não unitário/sem placa.

## Testes (curl ou equivalente)

Há testes de domínio, API, RBAC, ownership, idempotência, uma credencial por
unidade, snapshot de pátio/direito/validade, transição do pedido, constraints,
OpenAPI, Flyway, Hibernate validate e duas emissões concorrentes para a única
unidade.

## Decisões Técnicas

- Uma credencial representa exatamente uma unidade do item; unicidade
  `(item_pedido_id, numero_unidade)` é defendida no PostgreSQL.
- O pedido é o lock de serialização. Emissões concorrentes nunca excedem a
  quantidade comprada.
- O pátio é snapshot do item, evitando consulta ao inventário durante acesso.
- Placa só é copiada automaticamente quando o pedido possui uma única unidade;
  múltiplas unidades exigirão associação individual futura.
- `PENDENTE` não é exposto: criação e ativação ocorrem no mesmo caso de uso.
- O código `CRD-...` não contém segredo e não substitui representação segura.

## Módulos relacionados

- [[core]]
- [[organizacoes]]
- [[pedidos]]
- [[instalacoes]]
- [[eventos]]
- [[rbac]]
- [[auditoria]]
- [[idempotencia]]
- [[outbox]]

## Histórico (data + ação)

| Data | Ação |
|---|---|
| 2026-07-30 | Implementa UC711 com uma credencial ativa por unidade confirmada. |
