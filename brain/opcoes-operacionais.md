# Opções operacionais

## Objetivo

Permitir seleção por nome ou número sem informar IDs manualmente nem limitar as
escolhas aos registros recentes do navegador.

## Contexto

Eventos de acesso, pátios, produtos, reservas, pedidos, credenciais e vínculos
usam os registros existentes. As permissões e os comandos originais continuam
sendo aplicados independentemente da busca.

## Fluxo (camadas da arquitetura)

O controller valida o contexto e o filtro. O serviço resolve as permissões
efetivas do vínculo em [[rbac]], seleciona a projeção correspondente e consulta
PostgreSQL, sempre com a organização do JWT e o autor quando exigido pela ação.

## Endpoints

`GET /api/v1/operational-options/{kind}` aceita `q` (busca literal,
sem distinção de maiúsculas ou acentos), `id` (opção exata), `page` e `size`
(20 por padrão, até 50). `access-facilities` também aceita `eventId`.

| Tipo | Visibilidade |
|---|---|
| `access-events` | Eventos em operação, com permissão de validar/registrar acessos. |
| `audit-events` | Eventos com tentativas, com permissão de auditoria. |
| `access-facilities` | Pátios ativos alocados na operação, com permissão de validar/registrar. |
| `blocking-credentials` | Credenciais não bloqueadas da organização, com autorização para bloquear. |
| `sales-products` | Produtos publicados em eventos com vendas abertas, com autorização para reservar. |
| `sales-holds` | Reservas do próprio operador, com autorização para consultar/reservar. |
| `ordering-holds` | Reservas próprias mantidas e não expiradas, com autorização para criar pedido. |
| `sales-orders` | Pedidos próprios, conforme permissão de consulta ou operação de confirmação/cancelamento. |
| `issuing-orders` | Pedidos próprios confirmados, com autorização para emitir credencial. |
| `sales-credentials` | Credenciais próprias, com autorização para emissão/consulta. |
| `memberships` | Vínculos da organização acessíveis para administração, concessão ou revogação. |

## Estrutura de Dados (DTOs, Entidades)

Página com `totalElements` e `content` de `{ id, label, version }`. O
rótulo pode incluir o identificador público do pedido ou da credencial;
não inclui dados do comprador, login, e-mail, placa, token de acesso ou QR.

## Integrações externas

Nenhuma. A busca não cadastra nem altera registros e não exige migração.

## Tratamento de Erros

400 para filtros inválidos, 403 sem contexto/permissão e 404 para tipo
desconhecido. Uma opção fora do escopo é indistinguível de opção inexistente
na busca: página vazia. Respostas não são armazenadas em cache.

## Testes

`OperationalOptionsApiTest`: requisições reais na pilha MockMvc com JWT,
RBAC e PostgreSQL de teste; consulta paginada e isolamento por organização.

## Decisões Técnicas

A busca executa no banco, com parâmetros vinculados e ordenação por
rótulo/identificador. Não concede permissões, não repassa tokens ou códigos
operacionais e tem o mesmo escopo autenticado da ação correspondente.

## Módulos relacionados

[[core]] · [[acesso]] · [[eventos]] · [[instalacoes]] · [[credenciais]] ·
[[produtos-estacionamento]] · [[reservas-inventario]] · [[pedidos]] · [[membership]] · [[rbac]]

## Histórico (data + ação)

| Data | Ação |
|---|---|
| 2026-11-22 | Introduz projeções mínimas, filtradas e paginadas para os seletores operacionais. |
