# Estacionamento Kaneko — API

Sistema de gestão de um estacionamento com loja de conveniência. API REST
(Spring Boot 4 + Java 25 + PostgreSQL), com interface via **Swagger**. Projeto
acadêmico cuja documentação completa está em [`docs/especificacao/`](docs/especificacao/).

## Funcionalidades

- **Pátio** — entrada/saída de veículos (avulso e mensalista) com cálculo de
  tarifa (tolerância de cortesia, valor/hora com teto de diária).
- **Cadastros** — geografia (país/estado/cidade), clientes, fornecedores (com
  e-mails e telefones), RH (cargos/funcionários), formas e condições de
  pagamento (parceladas).
- **Conveniência** — categorias, marcas, unidades, serviços, produtos (estoque)
  e o vínculo produto-fornecedor.
- **Fiscal** — notas de **entrada** (compra: rateio de despesas, soma de estoque,
  geração de contas a pagar), de **saída** (venda: baixa de estoque com validação,
  geração de contas a receber) e de **serviço** (NFS-e com ISS).
- **Financeiro** — contas a pagar, a receber e despesas avulsas, com baixa
  parcial/total. Mensalidade do mensalista gerada como conta a receber.
- **Relatórios** (`/api/relatorios`) — pátio atual, faturamento do dia/período,
  contas a vencer, estoque mínimo e mensalistas ativos.
- **Filtros** — todas as listagens de cadastro aceitam filtros opcionais
  (por nome/documento/situação etc.) como query params.
- **Segurança** — usuários (senha BCrypt) e autenticação JWT.
- **Organizações** (`/api/v1/organizations`) — fundação multiempresa com
  documento empresarial, moeda, fuso, região, plano e concorrência otimista.
- **Membership** — vínculo temporal entre o `Usuario` global e organizações,
  seleção server-side do tenant ativo e emissão de JWT contextual revogável por versão.
- **RBAC contextual** — papéis e permissões por organização, com concessão,
  revogação imediata e bootstrap controlado do primeiro administrador do tenant.
- **Auditoria imutável** — trilha transacional append-only de Organização,
  Membership e RBAC, com ator, origem, trace, versões e snapshots redigidos.
- **Instalações** (`POST /api/v1/parking-facilities`) — criação tenant-aware de
  pátios físicos, com capacidade operacional limitada pela capacidade física,
  autorização contextual e auditoria.
- **Venues** (`POST /api/v1/venues`) — unidades operacionais que localizam os
  eventos por endereço, cidade e fuso IANA no tenant ativo.
- **Eventos** (`POST /api/v1/events`) — criação idempotente no estado
  `RASCUNHO`, com venue tenant-aware, checklist, auditoria e outbox transacional.
- **Publicação de eventos** (`POST /api/v1/events/{id}/publication`) — checklist
  de venue, capacidade e produto publicado, versão esperada e outbox atômica.
- **Abertura de vendas** (`POST /api/v1/events/{id}/sales-opening`) — transição
  imediata e versionada para `VENDAS_ABERTAS`, com auditoria e outbox atômica.
- **Disponibilidade** (`GET /api/v1/events/{id}/availability`) — projeção
  tenant-aware por categoria, lote vigente e prioridade, sem garantir reserva.
- **Hold de inventário** (`/api/v1/inventory-holds`) — reserva temporária,
  idempotente e atômica, com expiração automática e liberação exata do saldo.
- **Pedidos** (`POST /api/v1/orders`) — contrato comercial idempotente criado a
  partir do hold do próprio ator, com snapshots, total derivado e expiração
  automática enquanto aguarda pagamento.
- **Confirmação manual conciliável**
  (`POST /api/v1/orders/{id}/manual-confirmation`) — exceção administrativa
  versionada e idempotente que exige evidência, confirma o inventário e registra
  pendência de conciliação sem simular aprovação de gateway.
- **Alocação de pátios** — associação temporal multipátio por evento, com
  capacidades operacional/vendável/reservada, prevenção transacional de
  sobrealocação, idempotência, auditoria e concorrência otimista.
- **Produtos de estacionamento** (`POST /api/v1/events/{id}/parking-products`) —
  ofertas em rascunho vinculadas à alocação, com categoria, direito, janela,
  quota, benefícios/restrições, idempotência e proteção do saldo vendável.
- **Lotes de preço** (`POST /api/v1/parking-products/{id}/price-tiers`) — preço,
  moeda, janela comercial, quantidade e prioridade determinística por produto.
- **Publicação de produto** (`POST /api/v1/parking-products/{id}/publication`) —
  valida lote, versão esperada e publica o fato pela outbox transacional.
- **Observabilidade** — trace id de correlação (`X-Trace-Id`), erros padronizados
  (RFC 7807) e health check (actuator).

## Pré-requisitos

- **JDK 25**
- **PostgreSQL 17** acessível em `localhost:5433` (usuário `postgres`, senha `root`
  por padrão — configurável por variáveis de ambiente).
- Maven Wrapper incluso (`mvnw` / `mvnw.cmd`) — não é preciso instalar o Maven.

## Configuração do banco

Crie os bancos de desenvolvimento e de testes:

```sql
CREATE DATABASE estacionamento_kaneko;
CREATE DATABASE estacionamento_kaneko_test;
```

O schema é versionado e aplicado automaticamente pelo **Flyway** na subida por
migrations incrementais em `src/main/resources/db/migration/` (`V1` a `V19`). O
Hibernate roda em `ddl-auto=validate` — apenas confere o mapeamento contra o
schema, nunca o altera.

## Variáveis de ambiente

Todas têm um valor padrão de desenvolvimento (nada é obrigatório para rodar local):

| Variável               | Padrão                          | Descrição                                   |
|------------------------|---------------------------------|---------------------------------------------|
| `DB_HOST`              | `localhost`                     | Host do PostgreSQL                          |
| `DB_PORT`              | `5433`                          | Porta do PostgreSQL                         |
| `DB_NAME`              | `estacionamento_kaneko`         | Banco de desenvolvimento                    |
| `DB_USERNAME`          | `postgres`                      | Usuário do banco                            |
| `DB_PASSWORD`          | `root`                          | Senha do banco                              |
| `SERVER_PORT`          | `8086`                          | Porta HTTP da aplicação                     |
| `APP_SECURITY_ENABLED` | `true`                          | Exige JWT em `/api/**`                       |
| `APP_JWT_SECRET`       | *(chave de dev — troque!)*      | Segredo HMAC do JWT (≥ 32 bytes em produção) |
| `APP_JWT_EXP_MIN`      | `480`                           | Validade do token, em minutos               |
| `APP_JWT_ISSUER`       | `estacionamento-kaneko`         | Emissor esperado nos novos tokens           |
| `APP_JWT_AUDIENCE`     | `estacionamento-kaneko-api`     | Audiência esperada nos novos tokens          |
| `APP_INVENTORY_HOLD_MINUTES` | `15`                    | Duração de um hold de inventário              |
| `APP_INVENTORY_EXPIRATION_ENABLED` | `true`              | Habilita o job interno de expiração           |
| `APP_INVENTORY_EXPIRATION_SWEEP_MS` | `30000`             | Intervalo do job de expiração, em ms          |

## Como executar

```bash
./mvnw spring-boot:run          # Linux/macOS
mvnw.cmd spring-boot:run        # Windows
```

A API sobe em `http://localhost:8086`.

- **Swagger UI:** http://localhost:8086/swagger-ui.html
- **OpenAPI JSON:** http://localhost:8086/v3/api-docs
- **Health check:** http://localhost:8086/actuator/health

## Autenticação

Com a segurança ligada, os endpoints `/api/**` exigem um token JWT (exceto
`/api/auth/**`, o Swagger e o actuator).

1. Cadastre um usuário (`POST /api/usuarios`):

   ```json
   { "nome": "Administrador", "login": "admin", "senha": "segredo123", "perfil": "ADMIN" }
   ```

2. Faça login (`POST /api/auth/login`) e copie o `token`:

   ```json
   { "login": "admin", "senha": "segredo123" }
   ```

3. Envie o token nas demais chamadas: `Authorization: Bearer <token>` (ou use o
   botão **Authorize** no Swagger).

O login continua emitindo um token sem tenant. O usuário consulta seus vínculos
ativos em `GET /api/v1/me/organizations` e seleciona uma organização com
`POST /api/v1/me/active-organization`, enviando somente `organizationId`. A API
devolve um novo JWT com `org_id`, `membership_id` e `membership_version`; headers
não alteram esse contexto. Suspensão, reativação ou encerramento da Membership
invalidam imediatamente os tokens contextuais anteriores.

> Para explorar a API sem token em ambiente local, suba com
> `APP_SECURITY_ENABLED=false`.

## Testes

Os testes de integração usam o banco `estacionamento_kaneko_test` (Flyway aplica o
schema; a cada classe a transação sofre rollback):

```bash
./mvnw test
```

São 436 testes cobrindo todos os fluxos de cada módulo (criar, listar, buscar,
404, atualizar, excluir, filtros, e os conflitos/validações 409/400/401), além
dos processos do pátio, fiscais, financeiros e dos relatórios.

## Arquitetura

Organização **por funcionalidade** (package-by-feature), sob
`com.kaneko.estacionamento`:

```
common/          BaseEntity/AuditableEntity, exceções, ProblemDetail, segurança (JWT), trace id, OpenAPI
common/idempotencia e common/outbox/  garantias transacionais para comandos e eventos confiáveis
geografia/       país, estado, cidade
pessoas/         cliente
pagamento/       forma e condição de pagamento (parcelas)
rh/              cargo, funcionário
fornecedor/      fornecedor + e-mails/telefones
patio/           veículo, tarifa, mensalista, movimentação (cálculo de tarifa)
conveniencia/    categoria, marca, unidade, serviço, produto, produto-fornecedor
fiscal/          notas de entrada, saída e serviço (+ itens)
financeiro/      contas a pagar, a receber e avulsas
usuario/         cadastro de usuários
organizacao/     tenant empresarial, lifecycle, ETag e If-Match
membership/      vínculo usuário–organização, contexto autenticado e JWT contextual
rbac/            papéis, permissões, atribuições e autorização por organização
auditoria/       trilha imutável de ator, origem, versões e estados redigidos
evento/          agregado Evento, estado, criação idempotente e checklist
relatorio/       relatórios gerenciais (consultas consolidadas)
```

Cada módulo segue a fatia `Controller → Service → Repository → Entity`, com DTOs
(`record` + Bean Validation) na borda e respostas de erro padronizadas em
`ProblemDetail` (RFC 7807).

## Frontend (interface web)

Além da API + Swagger, há uma **SPA moderna** (React + TypeScript + MUI) em
[`frontend/`](frontend/) que consome esta API: login JWT, dashboard, operação de
pátio em tempo real, CRUD de todos os cadastros (com filtros e paginação), notas
fiscais (com itens), financeiro (com baixa) e relatórios.

```bash
cd frontend && npm install && npm run dev   # http://localhost:5173
```

O dev server faz proxy de `/api` para o backend (sem CORS). No primeiro acesso use
**`admin` / `admin123`** — um administrador é criado automaticamente quando não há
usuários (troque a senha depois). Detalhes em [frontend/README.md](frontend/README.md).

## Documentação

A especificação acadêmica completa (introdução, análise de negócio, DER,
requisitos, casos de uso, dicionário de dados, diagramas UML/sequência e
requisitos não funcionais) está em [`docs/especificacao/`](docs/especificacao/).
