> Links: [[core]] · [[clientes]] · [[conveniencia]] · [[estoque]] · [[fiscal-financeiro]] · [[rbac]] · [[auditoria]] · [[idempotencia]] · [[outbox]]

# Vendas Administrativas

## Objetivo

Registrar vendas fora do PDV com baixa física e geração de contas a receber,
mantendo correlação opcional com a Nota de Saída.

## Contexto

A V64 implementa a Onda 5 para mercadorias. A venda nasce em `RASCUNHO` e pode
ser alterada, confirmada ou cancelada. `CONFIRMADA` e `CANCELADA` são terminais.

## Fluxo (camadas da arquitetura)

O controller exige JWT contextual, `sales:read/manage`, idempotência e
`If-Match`. O service valida Cliente, Local e Produtos do tenant. A confirmação
serializa o agregado, baixa o razão, cria as parcelas a receber, audita e publica
outbox na mesma transação. Nota de Saída vinculada apenas documenta a venda.

## Endpoints (se houver)

- `GET/POST /api/v1/administrative-sales`
- `GET/PUT /api/v1/administrative-sales/{id}`
- `POST /api/v1/administrative-sales/{id}/confirmation`
- `POST /api/v1/administrative-sales/{id}/cancellation`
- `GET /api/v1/administrative-sales/{id}/documents`: nota, recebíveis e movimentos, com `null` nas seções sem permissão de leitura do respectivo módulo.
- `POST /api/v1/administrative-sales/{id}/outbound-note`: prepara nota `PENDENTE` a partir da venda confirmada. Número obrigatório; modelo, série e datas opcionais. `Idempotency-Key` obrigatório; exige `sales:read` e `fiscal:manage`.

A listagem aceita `numero` (trecho literal, sem diferenciar maiúsculas e
minúsculas, removendo espaços das extremidades) e `status` (`RASCUNHO`,
`CONFIRMADA` ou `CANCELADA`). Os filtros e a organização são aplicados no
PostgreSQL antes de `page`, `size` e `sort`; `totalElements` representa todo o
resultado filtrado, inclusive em página vazia. `%` e `_` são caracteres literais,
não curingas. Status desconhecido retorna 400.

## Estrutura de Dados (DTOs, Entidades)

`VendaAdministrativa`, `ItemVendaAdministrativa`, vínculo opcional em
`NotaSaida` e origem em `ContaReceber`.

## Integrações externas (se houver)

Nenhuma. A Nota de Saída é documento interno; transmissão fiscal permanece fora.

## Tratamento de Erros

Contexto/permissão `401/403`; cross-tenant `404`; versão `428/412`; estoque,
duplicidade ou transição inválida `409`.

## Testes (curl ou equivalente)

Há cobertura de isolamento, confirmação, estoque, recebível, ETag e Nota de
Saída sem efeitos duplicados, além de seed e V1→V64.

`VendaAdministrativaConsultaTest` cobre normalização, total e ordenação da
consulta com filtros, preservando a assinatura `listar(organizacaoId, pageable)`.
`ConsultasComerciaisTenantApiTest` exercita os filtros pela API com banco real,
paginação, tenant, total, caracteres literais e compatibilidade sem filtros.
`VendaFluxoConcorrenciaTenantApiTest` verifica confirmação simultânea da mesma
versão, geração concorrente com chaves iguais/diferentes e RBAC de cada seção de
documentos, com contagens reais de nota, títulos e razão de estoque.

## Decisões Técnicas

- A venda é a origem comercial, física e financeira.
- A Nota de Saída vinculada é documental e não repete estoque/recebível.
- Confirmação é atômica; saldo insuficiente reverte toda a operação.
- ETag deriva de `@Version`; números são únicos por organização.
- Cliente, local, itens, descontos e condição de pagamento da nota vinculada são copiados no servidor. A nova operação não permite sobrescrever os dados comerciais da venda.
- Geração bloqueia a venda e usa idempotência persistida; repetição com mesmo corpo retorna a nota existente. Corpo divergente ou segunda intenção sobre venda já documentada retorna 409. A unicidade V64 permanece a última proteção no banco.
- Listagem mantém paginação de IDs antes de carregar os relacionamentos com
  `EntityGraph`, preserva a ordem solicitada e o total filtrado. O overload sem
  filtros continua disponível aos consumidores legados.

## Módulos relacionados

[[clientes]], [[conveniencia]], [[estoque]], [[fiscal-financeiro]], [[rbac]],
[[auditoria]], [[idempotencia]] e [[outbox]].

## Histórico (data + ação)

| Data | Ação |
|---|---|
| 2026-09-12 | Conecta geração idempotente de nota, documentos, contas e movimentos ao pedido; frontend mantém rota/entidade e usa o rótulo Pedidos de venda. |
| 2026-09-12 | Adiciona filtros reais de número/situação antes da paginação e testes dedicados de consulta, concorrência de confirmação/nota e seções documentais com RBAC. |
| 2026-08-14 | V64 implementa Venda Administrativa e integração fiscal/financeira. |
