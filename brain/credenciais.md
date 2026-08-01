> Links: [[core]] · [[organizacoes]] · [[pedidos]] · [[instalacoes]] · [[eventos]] · [[acesso]] · [[rbac]] · [[auditoria]] · [[idempotencia]] · [[outbox]]

# Credenciais

## Objetivo

Materializar cada unidade de estacionamento comprada como direito de acesso
tenant-aware e gerar sua representação QR segura sem acoplar o direito ao meio.

## Contexto

UC711 está implementada para pedidos confirmados do próprio ator. Cada chamada
emite uma credencial para a próxima unidade ainda não atendida. Quando todas as
unidades recebem credencial, o pedido transiciona para `ATENDIDO`. UC712 gera
uma representação QR para credencial própria ativa com meio QR. UC713 bloqueia
credenciais de pedido cancelado e revoga o QR vigente. UC715 consome a
credencial QR autorizada em [[acesso]], executando
`ATIVA → DENTRO` sob lock pessimista. UC717 executa `DENTRO → CONSUMIDA` ou
`DENTRO → ATIVA` conforme a política publicada do evento.

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

POST /api/v1/credentials/{credentialId}/qr-code + If-Match
  -> JWT contextual + credentials:issue
  -> lock pessimista da credencial própria
  -> valida ATIVA + meio QR + validade + versão esperada
  -> SecureRandom 256 bits -> token opaco -> SHA-256
  -> RepresentacaoQrRepository -> PostgreSQL V25/V26
  -> incrementa qrVersao e @Version da credencial
  -> auditoria + QRCodeGerado sem token/hash
  -> 201 + token retornado uma única vez + novo ETag

cancelamento do pedido confirmado/atendido
  -> lock das credenciais do pedido
  -> ATIVA -> BLOQUEADA + revogação do QR vigente
  -> auditoria sem token/hash

POST /api/v1/credentials/{credentialId}/blocking
  -> JWT contextual + credentials:block (Supervisor)
  -> lock da credencial no tenant
  -> ATIVA -> BLOQUEADA + motivo
  -> revoga QR vigente
  -> auditoria + CredencialBloqueada
  -> replay retorna estado sem repetir efeitos
```

## Endpoints (se houver)

- `POST /api/v1/orders/{orderId}/credentials`: emite a próxima unidade de um
  pedido próprio confirmado.
- `GET /api/v1/credentials/{credentialId}`: consulta credencial do próprio ator.
- `POST /api/v1/credentials/{credentialId}/qr-code`: cria a representação QR
  única vigente e retorna o token somente nessa resposta.
- `POST /api/v1/credentials/{credentialId}/blocking`: Supervisor bloqueia uma
  credencial ativa do tenant com motivo e revogação do QR vigente.

## Estrutura de Dados (DTOs, Entidades)

`CredencialAcesso` congela organização, evento, pátio, pedido, item, proprietário,
código público não autorizador, número da unidade, origem, categoria, direito,
validade, meio preferido, placa não ambígua, estado, bloqueio/motivo,
`qrVersao` e versão JPA. O
`ItemPedido` congela também o pátio resolvido da alocação. `RepresentacaoQr`
guarda escopo tenant, credencial, número da versão, hash SHA-256, emissão,
expiração e revogação opcional; o token em claro não integra a entidade.

## Integrações externas (se houver)

Nenhuma. `CredencialEmitida`, `PedidoAtendido` e `QRCodeGerado` são gravados na
[[outbox]]. O código público identifica a credencial, mas não é token de acesso.
O token QR é entregue diretamente uma única vez; integração de notificação e a
validação física no acesso permanecem fora deste caso de uso.

## Tratamento de Erros

- `400`: chave ausente/inválida, meio/campo controlado ou `If-Match` malformado.
- `412`: versão da credencial divergente.
- `428`: geração QR sem `If-Match`.
- `403`: ausência de `credentials:issue`/`credentials:block` ou tenant ativo.
- `404`: pedido/credencial de outro ator ou tenant.
- `409`: pedido não confirmado/atendido, unidades esgotadas, replay divergente,
  meio QR não permitido, credencial inativa/expirada ou placa ambígua.

## Testes (curl ou equivalente)

Há testes de domínio, API, RBAC, ownership, idempotência, uma credencial por
unidade, snapshot de pátio/direito/validade, transição do pedido, token opaco,
ausência de segredo no banco, precondição, constraints, OpenAPI, Flyway,
Hibernate validate, emissão concorrente, geração QR concorrente, bloqueio
compensatório durante cancelamento e bloqueio operacional idempotente por
Supervisor.

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
- O token `KQ1-...` contém 256 bits aleatórios, não possui PII e é retornado
  somente na criação; persistência, auditoria e outbox recebem apenas metadados.
- `qrVersao` pertence ao agregado e incrementa o `ETag`; lock pessimista,
  `If-Match` e índice parcial garantem uma representação não revogada.
- Cancelamento do pedido revoga a representação ativa no mesmo commit do
  bloqueio; o histórico e o hash permanecem para rastreabilidade.
- O bloqueio operacional usa o lock da credencial como serializador e aceita
  replay terminal sem reescrever o motivo ou duplicar auditoria/outbox.
- `meioPreferido = QR` é a permissão efetiva enquanto não existir uma política
  de meios híbridos. QR rotativo, revogação e reemissão não foram antecipados.

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
| 2026-07-30 | Implementa UC712 com token QR de entrega única e hash persistido. |
| 2026-07-30 | UC713 bloqueia credenciais e revoga QR de pedido cancelado. |
| 2026-08-01 | UC715 consome QR ativo uma única vez e registra a credencial como DENTRO. |
| 2026-08-01 | UC717 consome ou reativa a credencial no check-out conforme a política do evento. |
| 2026-08-01 | Implementa bloqueio operacional idempotente por Supervisor com revogação QR. |
